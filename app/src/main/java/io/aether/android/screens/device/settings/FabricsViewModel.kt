// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.matter.FabricId
import io.aether.android.matter.NodeId
import io.aether.android.matter.VendorId
import io.aether.android.matter.toFabricId
import io.aether.android.matter.toNodeId
import io.aether.android.matter.toVendorId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class ManagedFabric(
    val fabricIndex: Int,
    val rootPublicKey: ByteArray,
    val vendorId: VendorId,
    val fabricId: FabricId,
    val nodeId: NodeId,
    val label: String,
    val isCurrentFabric: Boolean,
)

@HiltViewModel
class FabricsViewModel
@Inject
constructor(
    private val clustersHelper: ClustersHelper,
    private val chipClient: ChipClient,
) : ViewModel() {

  sealed interface UiState {
    data object Loading : UiState

    data class Loaded(val fabrics: List<ManagedFabric>) : UiState

    data class Error(val messageRes: Int) : UiState
  }

  private val refreshTrigger = MutableSharedFlow<NodeId>(replay = 1)

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<UiState> =
      refreshTrigger
          .flatMapLatest { nodeId ->
            flow {
              emit(UiState.Loading)
              emit(fetchFabrics(nodeId))
            }
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

  fun loadFabrics(nodeId: NodeId) = refreshTrigger.tryEmit(nodeId)

  fun removeFabric(nodeId: NodeId, fabricIndex: Int) {
    viewModelScope.launch {
      val currentIdx =
          clustersHelper.readCurrentFabricIndexAttribute(nodeId)
              ?: chipClient.chipDeviceController.getFabricIndex()
      // Prevent self-removal. There is a dedicated flow for that in the UI.
      if (fabricIndex == currentIdx) return@launch
      runCatching { clustersHelper.removeFabric(nodeId, fabricIndex) }
          .onSuccess { loadFabrics(nodeId) }
          .onFailure { Timber.e(it) }
    }
  }

  private suspend fun fetchFabrics(nodeId: NodeId): UiState =
      runCatching {
            val fabrics =
                clustersHelper.readFabricsAttribute(nodeId)
                    ?: return UiState.Error(R.string.controllers_offline)
            val currentIdx =
                clustersHelper.readCurrentFabricIndexAttribute(nodeId)
                    ?: chipClient.chipDeviceController.getFabricIndex()
            UiState.Loaded(
                fabrics
                    .sortedBy { it.fabricIndex }
                    .map {
                      ManagedFabric(
                          fabricIndex = it.fabricIndex,
                          rootPublicKey = it.rootPublicKey,
                          vendorId = it.vendorID.toVendorId(),
                          fabricId = it.fabricID.toFabricId(),
                          nodeId = it.nodeID.toNodeId(),
                          label = it.label,
                          isCurrentFabric = it.fabricIndex == currentIdx,
                      )
                    }
            )
          }
          .getOrElse { UiState.Error(R.string.controllers_offline) }
}
