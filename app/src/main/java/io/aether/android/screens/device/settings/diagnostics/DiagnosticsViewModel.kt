// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.data.DiagnosticsRepository
import io.aether.android.data.models.GeneralDiagnosticsData
import io.aether.android.data.models.SoftwareDiagnosticsData
import io.aether.android.matter.NodeId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val generalDiagnostics: GeneralDiagnosticsData? = null,
    val softwareDiagnostics: SoftwareDiagnosticsData? = null,
    @StringRes val errorRes: Int? = null,
)

private data class RefreshRequest(val nodeId: NodeId, val timestamp: Long)

private sealed interface PartialState {
  data object Loading : PartialState

  data class Success(
      val generalDiagnostics: GeneralDiagnosticsData?,
      val softwareDiagnostics: SoftwareDiagnosticsData?,
  ) : PartialState

  data class Error(@StringRes val errorRes: Int) : PartialState
}

@HiltViewModel
class DiagnosticsViewModel
@Inject
constructor(private val diagnosticsRepository: DiagnosticsRepository) : ViewModel() {

  private val refreshTrigger = MutableSharedFlow<RefreshRequest>(replay = 1)

  val uiState: StateFlow<DiagnosticsUiState> =
      refreshTrigger
          .flatMapLatest { request ->
            flow {
              emit(PartialState.Loading)

              val generalDiagnostics = diagnosticsRepository.readGeneralDiagnostics(request.nodeId)
              val softwareDiagnostics =
                  diagnosticsRepository.readSoftwareDiagnostics(request.nodeId)

              if (generalDiagnostics == null && softwareDiagnostics == null) {
                emit(PartialState.Error(R.string.device_diagnostics_load_failed))
              } else {
                emit(PartialState.Success(generalDiagnostics, softwareDiagnostics))
              }
            }
          }
          .scan(DiagnosticsUiState(isInitialLoading = true)) { previousState, partial ->
            when (partial) {
              is PartialState.Loading ->
                  previousState.copy(
                      isRefreshing =
                          previousState.generalDiagnostics != null ||
                              previousState.softwareDiagnostics != null,
                      isInitialLoading =
                          previousState.generalDiagnostics == null &&
                              previousState.softwareDiagnostics == null,
                      errorRes = null,
                  )
              is PartialState.Success ->
                  previousState.copy(
                      isInitialLoading = false,
                      isRefreshing = false,
                      generalDiagnostics = partial.generalDiagnostics,
                      softwareDiagnostics = partial.softwareDiagnostics,
                      errorRes = null,
                  )
              is PartialState.Error ->
                  previousState.copy(
                      isInitialLoading = false,
                      isRefreshing = false,
                      errorRes = partial.errorRes,
                  )
            }
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(5000),
              DiagnosticsUiState(isInitialLoading = true),
          )

  fun loadDiagnostics(nodeId: NodeId, forceRefresh: Boolean = false) {
    val currentRequest = refreshTrigger.replayCache.firstOrNull()
    if (
        !forceRefresh &&
            currentRequest?.nodeId == nodeId &&
            (uiState.value.generalDiagnostics != null || uiState.value.softwareDiagnostics != null)
    ) {
      return
    }

    viewModelScope.launch {
      refreshTrigger.emit(RefreshRequest(nodeId, System.currentTimeMillis()))
    }
  }
}
