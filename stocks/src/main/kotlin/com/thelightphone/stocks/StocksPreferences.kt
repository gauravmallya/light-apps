package com.thelightphone.stocks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Persistence for the user's stock watchlist — stored as a JSON list of symbols. */
internal class StocksPreferences(private val dataStore: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true }

    private val watchlistKey = stringPreferencesKey("stocks_watchlist")

    /** Emits the current watchlist of symbols whenever the store changes. */
    val watchlistFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        val raw = prefs[watchlistKey]
        if (raw.isNullOrBlank()) emptyList()
        else runCatching {
            json.decodeFromString<WatchlistData>(raw).symbols
        }.getOrDefault(emptyList())
    }

    /** Returns the current watchlist synchronously (for coroutine use). */
    suspend fun getWatchlist(): List<String> = watchlistFlow.first()

    /** Adds a symbol to the watchlist if it isn't already there. */
    suspend fun addSymbol(symbol: String) {
        dataStore.edit { prefs ->
            val current = decodeWatchlist(prefs[watchlistKey])
            if (!current.contains(symbol.uppercase())) {
                prefs[watchlistKey] = json.encodeToString(
                    WatchlistData(symbols = current + symbol.uppercase())
                )
            }
        }
    }

    /** Removes a symbol from the watchlist. */
    suspend fun removeSymbol(symbol: String) {
        dataStore.edit { prefs ->
            val current = decodeWatchlist(prefs[watchlistKey])
            prefs[watchlistKey] = json.encodeToString(
                WatchlistData(symbols = current - symbol.uppercase())
            )
        }
    }

    private fun decodeWatchlist(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<WatchlistData>(raw).symbols
        }.getOrDefault(emptyList())
    }
}