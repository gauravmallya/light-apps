package com.thelightphone.locationapprover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class ApproverHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ApproverViewModel>(sealedActivity) {

    override val viewModelClass: Class<ApproverViewModel>
        get() = ApproverViewModel::class.java

    override fun createViewModel(): ApproverViewModel = ApproverViewModel()

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
                    is ApproverScreenMode.Loading -> LoadingContent()
                    is ApproverScreenMode.Error -> ErrorContent(mode.message, onRetry = viewModel::refreshNow)
                    is ApproverScreenMode.PendingList -> PendingListContent(
                        requests = mode.requests,
                        actionInFlightId = state.actionInFlightId,
                        onApprove = viewModel::approve,
                        onDeny = viewModel::deny,
                        onShowHistory = viewModel::showHistory,
                    )
                    is ApproverScreenMode.History -> HistoryContent(
                        requests = mode.requests,
                        actionInFlightId = state.actionInFlightId,
                        onRevoke = viewModel::revoke,
                        onShowPending = viewModel::showPending,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(center = LightTopBarCenter.Text("Requests"))
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            LightText(text = "Loading...", variant = LightTextVariant.Copy)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(center = LightTopBarCenter.Text("Requests"))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = message,
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
        LightBottomBar(
            items = listOf(
                null,
                LightBarButton.Text(text = "RETRY", onClick = onRetry),
                null,
            ),
        )
    }
}

@Composable
private fun PendingListContent(
    requests: List<PendingRequest>,
    actionInFlightId: Int?,
    onApprove: (Int) -> Unit,
    onDeny: (Int) -> Unit,
    onShowHistory: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Requests (${requests.size})"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (requests.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LightText(text = "No pending requests.", variant = LightTextVariant.Copy)
            }
        } else {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 1f.gridUnitsAsDp()),
            ) {
                requests.forEach { req ->
                    RequestRow(
                        name = req.name,
                        busy = actionInFlightId == req.id,
                        onApprove = { onApprove(req.id) },
                        onDeny = { onDeny(req.id) },
                    )
                }
            }
        }

        LightBottomBar(
            items = listOf(
                null,
                LightBarButton.Text(text = "HISTORY", onClick = onShowHistory),
                null,
            ),
        )
    }
}

@Composable
private fun RequestRow(
    name: String,
    busy: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.75f.gridUnitsAsDp(), horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = name,
            variant = LightTextVariant.Heading,
            modifier = Modifier.weight(1f),
        )
        if (!busy) {
            Row {
                Box(
                    modifier = Modifier
                        .lightClickable(onClick = onDeny)
                        .padding(0.5f.gridUnitsAsDp()),
                ) {
                    LightIcon(
                        icon = LightIcons.DENY,
                        contentDescription = "Deny",
                    )
                }
                Box(
                    modifier = Modifier
                        .lightClickable(onClick = onApprove)
                        .padding(0.5f.gridUnitsAsDp()),
                ) {
                    LightIcon(
                        icon = LightIcons.ACCEPT,
                        contentDescription = "Approve",
                    )
                }
            }
        } else {
            LightText(text = "...", variant = LightTextVariant.Detail)
        }
    }
}

@Composable
private fun HistoryContent(
    requests: List<AllRequest>,
    actionInFlightId: Int?,
    onRevoke: (Int) -> Unit,
    onShowPending: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("History"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (requests.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LightText(text = "No requests yet.", variant = LightTextVariant.Copy)
            }
        } else {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 1f.gridUnitsAsDp()),
            ) {
                requests.forEach { req ->
                    HistoryRow(
                        request = req,
                        busy = actionInFlightId == req.id,
                        onRevoke = { onRevoke(req.id) },
                    )
                }
            }
        }

        LightBottomBar(
            items = listOf(
                null,
                LightBarButton.Text(text = "PENDING", onClick = onShowPending),
                null,
            ),
        )
    }
}

@Composable
private fun HistoryRow(
    request: AllRequest,
    busy: Boolean,
    onRevoke: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.75f.gridUnitsAsDp(), horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(text = request.name, variant = LightTextVariant.Heading)
            LightText(text = request.status, variant = LightTextVariant.Detail)
        }
        if (request.status == "approved" && !busy) {
            Box(
                modifier = Modifier
                    .lightClickable(onClick = onRevoke)
                    .padding(0.5f.gridUnitsAsDp()),
            ) {
                LightIcon(
                    icon = LightIcons.TRASH,
                    contentDescription = "Revoke",
                )
            }
        } else if (busy) {
            LightText(text = "...", variant = LightTextVariant.Detail)
        }
    }
}
