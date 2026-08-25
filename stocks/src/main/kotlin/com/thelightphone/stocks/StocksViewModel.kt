package com.thelightphone.stocks

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "Stocks"
private const val NETWORK_ERROR = "Couldn't reach the market data service. Please check your connection and try again."

public sealed class StocksMode {
    data object Watchlist : StocksMode()
    data class Searching(val session: Int) : StocksMode()
    data class SymbolResult(
        val session: Int,
        val symbol: String,
        val asset: AlpacaAsset,
        val price: Double?,
        val dayChange: Double?,
        val dayChangePercent: Double?,
    ) : StocksMode()
    data class StockDetail(
        val symbol: String,
        val name: String,
        val price: Double?,
        val dayChange: Double?,
        val dayChangePercent: Double?,
        val dayPrices: List<Double> = emptyList(),
    ) : StocksMode()
}

public data class StocksUiState(
    val mode: StocksMode = StocksMode.Watchlist,
    val watchlist: List<WatchlistEntry> = emptyList(),
    val searchSession: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isConfigured: Boolean
        get() = BuildConfig.ALPACA_API_KEY.isNotBlank() && BuildConfig.ALPACA_SECRET.isNotBlank()
}

public class StocksViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {

    private val _uiState = MutableStateFlow(StocksUiState())
    val uiState: StateFlow<StocksUiState> = _uiState.asStateFlow()

    private var api: AlpacaApi? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (!BuildConfig.ALPACA_API_KEY.isNotBlank()) {
                Log.w(TAG, "Alpaca API key not configured. Stocks tool will show a configuration notice.")
            }
            initApi()
            loadWatchlist()
            refresh()
        }
    }

    private fun initApi() {
        val key = BuildConfig.ALPACA_API_KEY
        val secret = BuildConfig.ALPACA_SECRET
        if (key.isNotBlank() && secret.isNotBlank()) {
            api = AlpacaApi(key, secret)
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch(Dispatchers.IO) {
            val current = _uiState.value
            if (current.mode is StocksMode.Watchlist && !current.isLoading) {
                refresh()
            }
        }
    }

    /** Opens the search input. */
    fun openSearch() {
        val nextSession = _uiState.value.searchSession + 1
        _uiState.update { it.copy(mode = StocksMode.Searching(session = nextSession), searchSession = nextSession, errorMessage = null) }
    }

    /** Returns to the watchlist from search. */
    fun closeSearch() {
        _uiState.update { it.copy(mode = StocksMode.Watchlist, errorMessage = null) }
    }

    /** Submits a search query — validates the symbol via Alpaca and shows result with price. */
    fun searchSymbol(query: String) {
        val symbol = query.trim().uppercase()
        if (symbol.isEmpty()) return

        val session = _uiState.value.searchSession
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val a = api ?: throw AlpacaApiException("Alpaca API is not configured. Add your keys in local.properties.")
                val asset = a.lookupSymbol(symbol)
                // Fetch a snapshot to show live price preview
                val snapshots = a.fetchSnapshots(listOf(symbol))
                val snapshot = snapshots[symbol]
                val price = snapshot?.latestTrade?.price
                val prevClose = snapshot?.prevDailyBar?.close
                val open = snapshot?.dailyBar?.open
                val change = if (price != null && prevClose != null) price - prevClose else null
                val changePct = if (price != null && prevClose != null && prevClose != 0.0) ((price - prevClose) / prevClose * 100) else null
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            mode = StocksMode.SymbolResult(
                                session = session,
                                symbol = symbol,
                                asset = asset,
                                price = price,
                                dayChange = change,
                                dayChangePercent = changePct,
                            ),
                            isLoading = false,
                        )
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "Search failed for $symbol", e)
                withContext(Dispatchers.Main) {
                    val msg = when {
                        e is AlpacaApiException && e.message?.startsWith("Symbol not found") == true -> "Symbol not found"
                        else -> e.message ?: NETWORK_ERROR
                    }
                    _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
                }
            }
        }
    }

    /** Adds a symbol to the watchlist and returns to the watchlist view. */
    fun addToWatchlist(symbol: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val prefs = StocksPreferences(dataStore)
                prefs.addSymbol(symbol)
                // Re-fetch the updated watchlist with prices
                loadWatchlist()
                refresh()
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(mode = StocksMode.Watchlist) }
            }
        }
    }

    /** Removes a symbol from the watchlist and refreshes. */
    fun removeFromWatchlist(symbol: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = StocksPreferences(dataStore)
            prefs.removeSymbol(symbol)
            loadWatchlist()
            refresh()
        }
    }

    /** Opens the detail screen for a watchlist entry, fetching the day's price series and company name. */
    fun openDetail(symbol: String) {
        val entry = _uiState.value.watchlist.find { it.symbol == symbol }
        _uiState.update {
            it.copy(
                mode = StocksMode.StockDetail(
                    symbol = symbol,
                    name = "",
                    price = entry?.price,
                    dayChange = entry?.dayChange,
                    dayChangePercent = entry?.dayChangePercent,
                ),
                errorMessage = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val a = api ?: throw AlpacaApiException("Alpaca API is not configured.")
                val asset = a.lookupSymbol(symbol)
                withContext(Dispatchers.Main) {
                    val current = _uiState.value.mode as? StocksMode.StockDetail ?: return@withContext
                    if (current.symbol != symbol) return@withContext
                    _uiState.update {
                        it.copy(mode = current.copy(name = asset.name.ifBlank { symbol }))
                    }
                }
                runCatching {
                    val bars = a.fetchIntradayBars(symbol)
                    val prices = bars.mapNotNull { it.close }
                    withContext(Dispatchers.Main) {
                        val current = _uiState.value.mode as? StocksMode.StockDetail ?: return@withContext
                        if (current.symbol != symbol) return@withContext
                        _uiState.update { it.copy(mode = current.copy(dayPrices = prices)) }
                    }
                }.onFailure { e ->
                    // Previously swallowed silently, which made chart-loading failures
                    // (e.g. missing market data feed entitlement) invisible in logs.
                    Log.e(TAG, "Failed to load intraday bars for $symbol", e)
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to load detail for $symbol", e)
            }
        }
    }

    /** Returns to the watchlist from the detail screen. */
    fun closeDetail() {
        _uiState.update { it.copy(mode = StocksMode.Watchlist) }
    }

    /** Removes a symbol from the list and returns to the watchlist. */
    fun removeFromDetail(symbol: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = StocksPreferences(dataStore)
            prefs.removeSymbol(symbol)
            loadWatchlist()
            refresh()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(mode = StocksMode.Watchlist) }
            }
        }
    }

    /** Refreshes prices for all watched symbols. */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val prefs = StocksPreferences(dataStore)
                val symbols = prefs.getWatchlist()
                if (symbols.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    return@launch
                }
                loadWatchlist()

                val a = api ?: throw AlpacaApiException("Alpaca API is not configured.")
                val snapshots = a.fetchSnapshots(symbols)

                val entries = symbols.map { sym ->
                    val snapshot = snapshots[sym]
                    val price = snapshot?.latestTrade?.price
                    val prevClose = snapshot?.prevDailyBar?.close
                    val change = if (price != null && prevClose != null) price - prevClose else null
                    val changePct = if (price != null && prevClose != null && prevClose != 0.0) ((price - prevClose) / prevClose * 100) else null
                    WatchlistEntry(
                        symbol = sym,
                        price = price,
                        dayChange = change,
                        dayChangePercent = changePct,
                    )
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(watchlist = entries, isLoading = false) }
                }
            }.onFailure { e ->
                Log.e(TAG, "Refresh failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (it.watchlist.isEmpty()) e.message ?: NETWORK_ERROR else null,
                        )
                    }
                }
            }
        }
    }

    /** Loads the list of saved symbols (without prices). */
    private suspend fun loadWatchlist() {
        val prefs = StocksPreferences(dataStore)
        val symbols = prefs.getWatchlist()
        val current = _uiState.value.watchlist
        val entries = symbols.map { sym ->
            current.find { it.symbol == sym } ?: WatchlistEntry(symbol = sym)
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(watchlist = entries) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        api?.close()
    }
}