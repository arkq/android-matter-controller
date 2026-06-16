// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.data.DiagnosticsRepository
import io.aether.android.data.models.EthernetNetworkDiagnosticsData
import io.aether.android.data.models.GeneralDiagnosticsData
import io.aether.android.data.models.SoftwareDiagnosticsData
import io.aether.android.data.models.ThreadNetworkDiagnosticsData
import io.aether.android.data.models.WiFiNetworkDiagnosticsData
import io.aether.android.matter.NodeId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val ethernetNetworkDiagnostics: EthernetNetworkDiagnosticsData? = null,
    val wifiNetworkDiagnostics: WiFiNetworkDiagnosticsData? = null,
    val threadNetworkDiagnostics: ThreadNetworkDiagnosticsData? = null,
    @field:StringRes val errorRes: Int? = null,
)

private data class RefreshRequest(val nodeId: NodeId)

private sealed interface PartialState {
  data object Loading : PartialState

  data class GeneralDiagnosticsSuccess(val generalDiagnostics: GeneralDiagnosticsData) :
      PartialState

  data class OtherDiagnosticsSuccess(
      val softwareDiagnostics: SoftwareDiagnosticsData?,
      val ethernetNetworkDiagnostics: EthernetNetworkDiagnosticsData?,
      val wifiNetworkDiagnostics: WiFiNetworkDiagnosticsData?,
      val threadNetworkDiagnostics: ThreadNetworkDiagnosticsData?,
  ) : PartialState

  data class Error(@field:StringRes val errorRes: Int) : PartialState
}

@HiltViewModel
class DiagnosticsViewModel
@Inject
constructor(private val diagnosticsRepository: DiagnosticsRepository) : ViewModel() {

  private val refreshTrigger = MutableSharedFlow<RefreshRequest>(replay = 1)

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<DiagnosticsUiState> =
      refreshTrigger
          .flatMapLatest { request ->
            flow {
              emit(PartialState.Loading)
              coroutineScope {
                val generalDef = async {
                  diagnosticsRepository.readGeneralDiagnostics(request.nodeId)
                }
                val softwareDef = async {
                  diagnosticsRepository.readSoftwareDiagnostics(request.nodeId)
                }
                val ethernetDef = async {
                  diagnosticsRepository.readEthernetNetworkDiagnostics(request.nodeId)
                }
                val wifiDef = async {
                  diagnosticsRepository.readWiFiNetworkDiagnostics(request.nodeId)
                }
                val threadDef = async {
                  diagnosticsRepository.readThreadNetworkDiagnostics(request.nodeId)
                }

                val general = generalDef.await()
                if (general == null) {
                  emit(PartialState.Error(R.string.device_diagnostics_load_failed))
                  return@coroutineScope
                }

                emit(PartialState.GeneralDiagnosticsSuccess(general))
                emit(
                    PartialState.OtherDiagnosticsSuccess(
                        softwareDiagnostics = softwareDef.await(),
                        ethernetNetworkDiagnostics = ethernetDef.await(),
                        wifiNetworkDiagnostics = wifiDef.await(),
                        threadNetworkDiagnostics = threadDef.await(),
                    )
                )
              }
            }
          }
          .scan(DiagnosticsUiState(isInitialLoading = true)) { previousState, partial ->
            when (partial) {
              is PartialState.Loading ->
                  previousState.copy(
                      isRefreshing = previousState.generalDiagnostics != null,
                      isInitialLoading = previousState.generalDiagnostics == null,
                      errorRes = null,
                  )
              is PartialState.GeneralDiagnosticsSuccess ->
                  previousState.copy(
                      isInitialLoading = false,
                      isRefreshing = false,
                      generalDiagnostics = partial.generalDiagnostics,
                      errorRes = null,
                  )
              is PartialState.OtherDiagnosticsSuccess ->
                  previousState.copy(
                      softwareDiagnostics = partial.softwareDiagnostics,
                      ethernetNetworkDiagnostics = partial.ethernetNetworkDiagnostics,
                      wifiNetworkDiagnostics = partial.wifiNetworkDiagnostics,
                      threadNetworkDiagnostics = partial.threadNetworkDiagnostics,
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
            uiState.value.generalDiagnostics != null
    ) {
      return
    }
    viewModelScope.launch { refreshTrigger.emit(RefreshRequest(nodeId)) }
  }
}
