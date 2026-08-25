package com.thelightphone.stocks

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import java.text.NumberFormat
import java.util.Locale

private const val DEFAULT_SYMBOL = ""

@InitialScreen
class StocksHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, StocksViewModel>(sealedActivity) {

    override val viewModelClass: Class<StocksViewModel>
        get() = StocksViewModel::class.java

    override fun createViewModel(): StocksViewModel = StocksViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    is StocksMode.Watchlist -> {
                        WatchlistContent(
                            state = state,
                            onOpenSearch = viewModel::openSearch,
                            onRefresh = viewModel::refresh,
                            onOpenDetail = viewModel::openDetail,
                        )
                    }

                    is StocksMode.Searching -> {
                        SearchInputContent(
                            session = mode.session,
                            onBack = viewModel::closeSearch,
                            onSubmit = viewModel::searchSymbol,
                        )
                    }

                    is StocksMode.SymbolResult -> {
                        SymbolResultContent(
                            state = state,
                            asset = mode.asset,
                            price = mode.price,
                            dayChange = mode.dayChange,
                            dayChangePercent = mode.dayChangePercent,
                            isAlreadyAdded = state.watchlist.any { it.symbol == mode.symbol },
                            onAdd = { viewModel.addToWatchlist(mode.symbol) },
                            onSearchAgain = { viewModel.openSearch() },
                        )
                    }

                    is StocksMode.StockDetail -> {
                        StockDetailContent(
                            mode = mode,
                            onBack = viewModel::closeDetail,
                            onRemove = { viewModel.removeFromDetail(mode.symbol) },
                        )
                    }
                }

                state.errorMessage?.let { msg ->
                    if (msg.isNotBlank()) {
                        LightFullscreenModal(
                            message = msg,
                            onClose = viewModel::dismissError,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Watchlist
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WatchlistContent(
    state: StocksUiState,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.ADD,
                onClick = onOpenSearch,
                contentDescription = "Add stock",
            ),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SEARCH,
                onClick = onOpenSearch,
                contentDescription = "Search stock",
            ),
            modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
        )

        if (!state.isConfigured) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 2f.gridUnitsAsDp()),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = "Configure Alpaca in local.properties\nto use the Stocks tool:",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                )
            }
        } else if (state.watchlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 2f.gridUnitsAsDp()),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = if (state.isLoading) "Loading…" else "Your watchlist is empty.\nTap the + icon to add a stock.",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                )
            }
        } else {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 1f.gridUnitsAsDp()),
            ) {
                state.watchlist.forEach { entry ->
                    StockRow(
                        entry = entry,
                        onClick = { onOpenDetail(entry.symbol) },
                    )
                }
                if (state.isLoading) {
                    LightText(
                        text = "Refreshing…",
                        variant = LightTextVariant.Detail,
                        modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                    )
                }
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.REFRESH,
                    onClick = onRefresh,
                    contentDescription = "Refresh prices",
                ),
            ),
        )
    }
}

@Composable
private fun StockRow(
    entry: WatchlistEntry,
    onClick: () -> Unit,
) {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(bottom = 1f.gridUnitsAsDp()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = entry.symbol,
                variant = LightTextVariant.Copy,
                modifier = Modifier.weight(1f),
            )
            if (entry.price != null) {
                // Show an up/down arrow since the device is greyscale and color alone
                // is not enough to convey direction.
                TrendIndicator(change = entry.dayChange)
                LightText(
                    text = formatter.format(entry.price),
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.padding(start = 0.5f.gridUnitsAsDp()),
                )
            } else {
                LightText(
                    text = "—",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                )
            }
        }

        if (entry.price != null) {
            val changeText = if (entry.dayChange != null && entry.dayChangePercent != null) {
                val sign = if (entry.dayChange >= 0) "+" else ""
                "${sign}${formatter.format(entry.dayChange)} (${sign}${String.format("%.2f", entry.dayChangePercent)}%)"
            } else null
            if (changeText != null) {
                LightText(
                    text = changeText,
                    variant = LightTextVariant.Detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.5f.gridUnitsAsDp()),
                    align = TextAlign.End,
                )
            }
        }
    }
}

/** A small up/down arrow that conveys price direction on greyscale displays. */
@Composable
private fun TrendIndicator(change: Double?) {
    if (change == null) return
    val contentColor = LightThemeTokens.colors.content
    val sizeModifier = Modifier.size(1.25f.gridUnitsAsDp())
    if (change >= 0.0) {
        Icon(
            painter = painterResource(R.drawable.up_arrow),
            contentDescription = "Price up",
            tint = contentColor,
            modifier = sizeModifier,
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.down_arrow),
            contentDescription = "Price down",
            tint = contentColor,
            modifier = sizeModifier,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchInputContent(
    session: Int,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val textFieldState = rememberTextFieldState(DEFAULT_SYMBOL)
    val keyboardOptionsFlow = rememberKeyboardOptions()

    LightTextInputEditor(
        title = "Search Stock",
        editorKey = "stocks-search-$session",
        keyboardOptionsFlow = keyboardOptionsFlow,
        state = textFieldState,
        onSubmit = { query -> onSubmit(query.toString().trim().uppercase()) },
        onBack = onBack,
        submitIcon = LightIcons.SEARCH,
        showBackButton = true,
        singleLine = true,
        initialCaps = true,
        modifier = Modifier.fillMaxSize(),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Symbol Result
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SymbolResultContent(
    state: StocksUiState,
    asset: AlpacaAsset,
    price: Double?,
    dayChange: Double?,
    dayChangePercent: Double?,
    isAlreadyAdded: Boolean,
    onAdd: () -> Unit,
    onSearchAgain: () -> Unit,
) {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onSearchAgain,
                contentDescription = "Search again",
            ),
            center = LightTopBarCenter.Text(asset.symbol),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = asset.name,
                variant = LightTextVariant.Heading,
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            LightText(
                text = "Exchange: ${asset.exchange ?: "—"}",
                variant = LightTextVariant.Detail,
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            if (price != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrendIndicator(change = dayChange)
                    LightText(
                        text = "Price: ${formatter.format(price)}",
                        variant = LightTextVariant.Heading,
                        modifier = Modifier.padding(start = 0.5f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
                    )
                }
                if (dayChange != null && dayChangePercent != null) {
                    val sign = if (dayChange >= 0) "+" else ""
                    LightText(
                        text = "Day change: ${sign}${formatter.format(dayChange)} (${sign}${String.format("%.2f", dayChangePercent)}%)",
                        variant = LightTextVariant.Copy,
                    )
                }
            } else {
                LightText(
                    text = "Price: loading…",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                )
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.BACK,
                    onClick = onSearchAgain,
                    contentDescription = "Search again",
                ),
                LightBarButton.Text(
                    text = if (isAlreadyAdded) "ADDED ✓" else "ADD",
                    onClick = if (isAlreadyAdded) null else onAdd,
                ),
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stock Detail
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StockDetailContent(
    mode: StocksMode.StockDetail,
    onBack: () -> Unit,
    onRemove: () -> Unit,
) {
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back to watchlist",
            ),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.TRASH,
                onClick = onRemove,
                contentDescription = "Remove from list",
            ),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = mode.symbol,
                variant = LightTextVariant.Heading,
                modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
            )
            mode.name.takeIf { it.isNotBlank() && it != mode.symbol }?.let { companyName ->
                LightText(
                    text = companyName,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TrendIndicator(change = mode.dayChange)
                if (mode.price != null) {
                    LightText(
                        text = formatter.format(mode.price),
                        variant = LightTextVariant.Heading,
                        modifier = Modifier.padding(start = 0.5f.gridUnitsAsDp()),
                    )
                } else {
                    LightText(
                        text = "—",
                        variant = LightTextVariant.Heading,
                        lighten = true,
                    )
                }
            }
            if (mode.dayChange != null && mode.dayChangePercent != null) {
                val sign = if (mode.dayChange >= 0) "+" else ""
                LightText(
                    text = "${sign}${formatter.format(mode.dayChange)} (${sign}${String.format("%.2f", mode.dayChangePercent)}%) today",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            } else {
                LightText(
                    text = "Today's price movement",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                PriceChart(prices = mode.dayPrices)
            }
        }
    }
}

/** Draws a simple line chart of a day's closing prices using the theme content color. */
@Composable
private fun PriceChart(prices: List<Double>) {
    if (prices.size < 2) {
        LightText(
            text = "No intraday data available yet.",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(top = 2f.gridUnitsAsDp()),
        )
        return
    }

    val lineColor = LightThemeTokens.colors.content
    val maxPrice = prices.maxOrNull()?.toFloat() ?: return
    val minPrice = prices.minOrNull()?.toFloat() ?: return
    val range = (maxPrice - minPrice).takeIf { it > 0f } ?: 1.0f

    val leftPaddingDp = 1f.gridUnitsAsDp()
    val rightPaddingDp = 1f.gridUnitsAsDp()
    val topPaddingDp = 0.5f.gridUnitsAsDp()
    val bottomPaddingDp = 1.5f.gridUnitsAsDp()

    Canvas(
        modifier = Modifier.fillMaxSize(),
    ) {
        val leftPadding = leftPaddingDp.toPx()
        val rightPadding = rightPaddingDp.toPx()
        val topPadding = topPaddingDp.toPx()
        val bottomPadding = bottomPaddingDp.toPx()
        val chartWidth = size.width - leftPadding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding

        val path = Path()
        prices.forEachIndexed { index, price ->
            val x = leftPadding + (index.toFloat() / (prices.size - 1)) * chartWidth
            val y = topPadding + ((maxPrice - price.toFloat()) / range) * chartHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // Fill area under the line for a subtle, readable chart on greyscale.
        val fill = Path().apply {
            addPath(path)
            lineTo(leftPadding + chartWidth, topPadding + chartHeight)
            lineTo(leftPadding, topPadding + chartHeight)
            close()
        }
        drawPath(fill, color = lineColor.copy(alpha = 0.12f))
        drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))

        // Mark the most recent price.
        val lastX = leftPadding + chartWidth
        val lastY = topPadding + ((maxPrice - prices.last().toFloat()) / range) * chartHeight
        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
    }
}