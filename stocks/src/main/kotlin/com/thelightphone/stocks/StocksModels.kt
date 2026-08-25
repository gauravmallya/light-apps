package com.thelightphone.stocks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An Alpaca trading asset — used to validate and look up a stock by symbol. */
@Serializable
public data class AlpacaAsset(
    val id: String = "",
    @SerialName("class") val assetClass: String? = null,
    val exchange: String? = null,
    val symbol: String = "",
    val name: String = "",
    val status: String? = null,
    val tradable: Boolean? = null,
)

/** A single trade from the Alpaca data API — used for latest price. */
@Serializable
internal data class Trade(
    @SerialName("p") val price: Double = 0.0,
    @SerialName("s") val size: Long? = null,
    @SerialName("t") val timestamp: String? = null,
    @SerialName("x") val exchange: String? = null,
)

/** A bar (OHLCV) returned by the Alpaca snapshots endpoint. */
@Serializable
internal data class Bar(
    @SerialName("o") val open: Double? = null,
    @SerialName("h") val high: Double? = null,
    @SerialName("l") val low: Double? = null,
    @SerialName("c") val close: Double? = null,
    @SerialName("v") val volume: Long? = null,
    @SerialName("t") val timestamp: String? = null,
)

/** Response wrapper for the intraday bars endpoint. */
@Serializable
internal data class BarsResponse(
    val bars: List<Bar> = emptyList(),
    val symbol: String = "",
)

/** The snapshot for a single symbol from the Alpaca snapshots endpoint. */
@Serializable
internal data class Snapshot(
    @SerialName("latestTrade") val latestTrade: Trade? = null,
    @SerialName("dailyBar") val dailyBar: Bar? = null,
    @SerialName("prevDailyBar") val prevDailyBar: Bar? = null,
)

/**
 * A watched stock entry persisted in the user's watchlist.
 * Only [symbol] is stored; name and price data are fetched live from Alpaca.
 */
@Serializable
data class WatchlistEntry(
    val symbol: String,
    val name: String = "",
    val price: Double? = null,
    val dayChange: Double? = null,
    val dayChangePercent: Double? = null,
)

/** The list of watched symbols, serialized as JSON for DataStore persistence. */
@Serializable
internal data class WatchlistData(
    val symbols: List<String> = emptyList(),
)