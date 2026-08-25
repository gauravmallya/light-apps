package com.thelightphone.locationapprover

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Admin token for the location-gate backend, configured per-install via local.properties.
// See README.md for setup instructions. Never hardcode a real token here.
private val ADMIN_TOKEN: String = BuildConfig.ADMIN_TOKEN

sealed class ApproverScreenMode {
    data object Loading : ApproverScreenMode()
    data class PendingList(val requests: List<PendingRequest>) : ApproverScreenMode()
    data class History(val requests: List<AllRequest>) : ApproverScreenMode()
    data class Error(val message: String) : ApproverScreenMode()
}

data class ApproverUiState(
    val mode: ApproverScreenMode = ApproverScreenMode.Loading,
    val actionInFlightId: Int? = null,
)

private val POLL_INTERVAL_MS = 5000L

class ApproverViewModel : LightViewModel<Unit>() {
    private val api = ApproverApi(ADMIN_TOKEN)

    private val _uiState = MutableStateFlow(ApproverUiState())
    val uiState: StateFlow<ApproverUiState> = _uiState.asStateFlow()

    private var showingHistory = false
    private var pollingJob: kotlinx.coroutines.Job? = null

    init {
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch(Dispatchers.IO) {
            refresh()
        }
    }

    private suspend fun refresh() {
        if (showingHistory) {
            api.fetchAll().fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(mode = ApproverScreenMode.History(list)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(mode = ApproverScreenMode.Error(err.message ?: "Failed to load")) }
                },
            )
        } else {
            api.fetchPending().fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(mode = ApproverScreenMode.PendingList(list)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(mode = ApproverScreenMode.Error(err.message ?: "Failed to load")) }
                },
            )
        }
    }

    fun showHistory() {
        showingHistory = true
        refreshNow()
    }

    fun showPending() {
        showingHistory = false
        refreshNow()
    }

    fun approve(requestId: Int) {
        act(requestId) { api.decide(requestId, approve = true) }
    }

    fun deny(requestId: Int) {
        act(requestId) { api.decide(requestId, approve = false) }
    }

    fun revoke(requestId: Int) {
        act(requestId) { api.revoke(requestId) }
    }

    private fun act(requestId: Int, block: suspend () -> Result<Unit>) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(actionInFlightId = requestId) }
            block()
            _uiState.update { it.copy(actionInFlightId = null) }
            refresh()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        api.close()
    }
}
