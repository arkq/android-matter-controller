// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.data.DiagnosticLogsRepository
import io.aether.android.matter.NodeId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagnosticLogsUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val logContent: String? = null,
    @field:StringRes val errorRes: Int? = null,
)

private data class LoadRequest(val nodeId: NodeId)

private sealed interface LogsPartialState {
  data object Loading : LogsPartialState

  data class Success(val content: String) : LogsPartialState

  data class Error(@field:StringRes val errorRes: Int) : LogsPartialState
}

@HiltViewModel
class DiagnosticLogsViewModel
@Inject
constructor(private val diagnosticLogsRepository: DiagnosticLogsRepository) : ViewModel() {

  private val loadTrigger = MutableSharedFlow<LoadRequest>(replay = 1)

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<DiagnosticLogsUiState> =
      loadTrigger
          .flatMapLatest { request ->
            flow {
              emit(LogsPartialState.Loading)
              val content = diagnosticLogsRepository.retrieveLogs(request.nodeId)
              if (content != null) {
                emit(LogsPartialState.Success(content))
              } else {
                emit(LogsPartialState.Error(R.string.device_diagnostic_logs_load_failed))
              }
            }
          }
          .scan(DiagnosticLogsUiState(isInitialLoading = true)) { previousState, partial ->
            when (partial) {
              is LogsPartialState.Loading ->
                  previousState.copy(
                      isRefreshing = previousState.logContent != null,
                      isInitialLoading = previousState.logContent == null,
                      errorRes = null,
                  )
              is LogsPartialState.Success ->
                  previousState.copy(
                      isInitialLoading = false,
                      isRefreshing = false,
                      logContent = partial.content,
                      errorRes = null,
                  )
              is LogsPartialState.Error ->
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
              DiagnosticLogsUiState(isInitialLoading = true),
          )

  fun loadLogs(nodeId: NodeId, forceRefresh: Boolean = false) {
    val currentRequest = loadTrigger.replayCache.firstOrNull()
    val state = uiState.value
    if (
        !forceRefresh &&
            currentRequest?.nodeId == nodeId &&
            state.logContent != null &&
            state.errorRes == null
    ) {
      return
    }
    viewModelScope.launch { loadTrigger.emit(LoadRequest(nodeId)) }
  }
}
