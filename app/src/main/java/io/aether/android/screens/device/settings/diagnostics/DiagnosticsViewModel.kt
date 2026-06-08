// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.DiagnosticClusterSnapshot
import io.aether.android.matter.AttributeId
import io.aether.android.matter.CLUSTERS
import io.aether.android.matter.ClusterId
import io.aether.android.matter.Clusters
import io.aether.android.matter.NodeId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

data class DiagnosticsAttributeUiItem(
    val id: AttributeId,
    val name: String,
    val value: String,
)

data class DiagnosticsClusterUiItem(
    val clusterId: ClusterId,
    val title: String,
    val isSupported: Boolean,
    val attributes: List<DiagnosticsAttributeUiItem>,
)

data class DiagnosticsUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val clusters: List<DiagnosticsClusterUiItem> = emptyList(),
    @StringRes val errorRes: Int? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val clustersHelper: ClustersHelper) :
    ViewModel() {
  private val targetClusters =
      listOf(
          Clusters.GeneralDiagnostics.ID,
          Clusters.SoftwareDiagnostics.ID,
          Clusters.ThreadNetworkDiagnostics.ID,
          Clusters.WiFiNetworkDiagnostics.ID,
          Clusters.EthernetNetworkDiagnostics.ID,
          Clusters.DiagnosticLogs.ID,
      )

  private var refreshJob: Job? = null
  private var activeNodeId: NodeId? = null

  private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(DiagnosticsUiState())
  val uiState: kotlinx.coroutines.flow.StateFlow<DiagnosticsUiState> = _uiState

  fun loadDiagnostics(nodeId: NodeId, forceRefresh: Boolean = false) {
    val currentState = _uiState.value
    if (!forceRefresh && activeNodeId == nodeId && currentState.clusters.isNotEmpty()) {
      return
    }

    activeNodeId = nodeId
    refreshJob?.cancel()
    _uiState.value =
        currentState.copy(
            isInitialLoading = currentState.clusters.isEmpty(),
            isRefreshing = currentState.clusters.isNotEmpty(),
            errorRes = null,
        )

    refreshJob = viewModelScope.launch {
      val result = runCatching { clustersHelper.readRootDiagnosticsClusters(nodeId) }
      result
          .onSuccess { snapshot ->
            _uiState.value =
                DiagnosticsUiState(
                    isInitialLoading = false,
                    isRefreshing = false,
                    clusters = toUiClusters(snapshot),
                    errorRes = null,
                )
          }
          .onFailure { error ->
            Timber.e(error, "loadDiagnostics failed")
            _uiState.value =
                _uiState.value.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    errorRes = R.string.device_diagnostics_load_failed,
                )
          }
    }
  }

  private fun toUiClusters(
      snapshots: Map<ClusterId, DiagnosticClusterSnapshot>
  ): List<DiagnosticsClusterUiItem> {
    return targetClusters.map { clusterId ->
      val snapshot = snapshots[clusterId]
      val clusterInfo = CLUSTERS[clusterId]
      DiagnosticsClusterUiItem(
          clusterId = clusterId,
          title = clusterInfo?.name ?: formatClusterId(clusterId),
          isSupported = snapshot?.isSupported == true,
          attributes =
              snapshot
                  ?.attributes
                  ?.map { (attributeId, value) ->
                    DiagnosticsAttributeUiItem(
                        id = attributeId,
                        name =
                            clusterInfo?.attributes?.get(attributeId)?.name
                                ?: formatAttributeId(attributeId),
                        value = value,
                    )
                  }
                  .orEmpty()
                  .sortedBy { it.id },
      )
    }
  }

  private fun formatClusterId(clusterId: ClusterId): String =
      "Cluster 0x%04X".format(clusterId.toLong())

  private fun formatAttributeId(attributeId: AttributeId): String =
      "Attribute 0x%04X".format(attributeId.toLong())
}
