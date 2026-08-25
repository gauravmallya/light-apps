package com.thelightphone.stocks

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Light API client wrapping the Alpaca trading and data APIs.
 *
 * Uses the paper-trading endpoint from BuildConfig for asset lookups
 * and the data endpoint (subdomain-swapped) for market snapshots.
 */
internal class AlpacaApi(
    private val apiKey: String,
    private val secret: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** Paper-trading base URL (for asset lookup), e.g. "https://paper-api.alpaca.markets/v2". */
    private val tradingBaseUrl: String = BuildConfig.ALPACA_ENDPOINT.trimEnd('/')

    /** Market-data base URL, derived from the configured endpoint. */
    private val dataBaseUrl: String = tradingBaseUrl
        .replace("paper-api.", "data.")
        .replace("api.", "data.")
        .replaceFirst("v2", "v2") // keep v2

    /**
     * Validates a stock symbol against Alpaca's asset catalog.
     * Returns the asset on success, or throws if invalid/untradable.
     */
    suspend fun lookupSymbol(symbol: String): AlpacaAsset {
        val response = client.get("$tradingBaseUrl/assets/${symbol.uppercase()}") {
            header(HttpHeaders.Authorization, basicAuth())
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(200)
            throw AlpacaApiException("Symbol not found or invalid: $symbol (HTTP ${response.status.value}: $body)")
        }
        val asset: AlpacaAsset = response.body()
        if (asset.status != "active" || asset.tradable != true) {
            throw AlpacaApiException("'${asset.symbol}' is not currently tradable (status: ${asset.status}).")
        }
        return asset
    }

    /**
     * Fetches the latest snapshot for a list of symbols.
     * Returns a map of symbol → Snapshot.
     */
    suspend fun fetchSnapshots(symbols: List<String>): Map<String, Snapshot> {
        if (symbols.isEmpty()) return emptyMap()

        val joined = symbols.joinToString(",") { it.uppercase() }
        val response = client.get("$dataBaseUrl/stocks/snapshots") {
            header(HttpHeaders.Authorization, basicAuth())
            url {
                parameters.append("symbols", joined)
                // Free/paper accounts only have access to the IEX feed. Without this,
                // Alpaca defaults to the SIP feed and returns a 403 subscription error,
                // which silently prevents prices/snapshots from loading.
                parameters.append("feed", "iex")
            }
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText().take(200)
            throw AlpacaApiException("Failed to fetch snapshots (HTTP ${response.status.value}: $body)")
        }
        val body = response.bodyAsText()
        return json.decodeFromString(body)
    }

    /**
     * Fetches intraday (minute) bars for a single symbol, covering the most recent trading day.
     * Returns the list of bars sorted chronologically (oldest → newest), scoped to just that one
     * most recent calendar day -- e.g. Friday's session when queried over a weekend, since Alpaca
     * has no bars at all for a day the market didn't open.
     *
     * Walks backward from today, one US Eastern calendar day (the market's own timezone, not the
     * device's UTC day) at a time, querying each day individually and returning as soon as one
     * comes back with real bars -- rather than one wide multi-day query, since Alpaca returns bars
     * oldest-first up to a page `limit`, so a several-day-wide single query risks its results page
     * ending before reaching the most recent (i.e. most relevant) day at all. Gives up after two
     * weeks of empty days, which should never happen from a real trading calendar.
     */
    suspend fun fetchIntradayBars(symbol: String): List<Bar> {
        val eastern = java.time.ZoneId.of("America/New_York")
        val today = java.time.ZonedDateTime.now(eastern).toLocalDate()
        for (daysAgo in 0..14) {
            val day = today.minusDays(daysAgo.toLong())
            val start = day.atStartOfDay(eastern).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val end = day.plusDays(1).atStartOfDay(eastern).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val response = client.get("$dataBaseUrl/stocks/${symbol.uppercase()}/bars") {
                header(HttpHeaders.Authorization, basicAuth())
                url {
                    parameters.append("timeframe", "1Min")
                    parameters.append("start", start)
                    parameters.append("end", end)
                    // Free/paper accounts only have access to the IEX feed. Without this,
                    // Alpaca defaults to the SIP feed and returns a 403 subscription error,
                    // which is why the intraday chart was showing no data.
                    parameters.append("feed", "iex")
                }
            }
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText().take(200)
                throw AlpacaApiException("Failed to fetch bars for $symbol (HTTP ${response.status.value}: $body)")
            }
            val parsed: BarsResponse = json.decodeFromString(response.body())
            val bars = parsed.bars.sortedBy { it.timestamp }
            if (bars.isNotEmpty()) return bars
        }
        return emptyList()
    }

    /** HTTP Basic auth header value from API key + secret. */
    private fun basicAuth(): String {
        val combined = "$apiKey:$secret"
        val encoded = Base64.getEncoder().encodeToString(combined.toByteArray())
        return "Basic $encoded"
    }

    fun close() {
        client.close()
    }
}

internal class AlpacaApiException(message: String) : Exception(message)