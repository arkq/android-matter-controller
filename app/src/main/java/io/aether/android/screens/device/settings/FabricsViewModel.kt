// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.data.DevicesRepository
import io.aether.android.matter.FabricId
import io.aether.android.matter.NodeId
import io.aether.android.matter.VendorId
import io.aether.android.matter.toFabricId
import io.aether.android.matter.toNodeId
import io.aether.android.matter.toVendorId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val devicesRepository: DevicesRepository,
    private val clustersHelper: ClustersHelper,
    private val chipClient: ChipClient,
) : ViewModel() {

  sealed class UiState {
    data object Loading : UiState()

    data class Loaded(val fabrics: List<ManagedFabric>) : UiState()

    data class Error(val messageRes: Int) : UiState()
  }

  private var _uiState = MutableStateFlow<UiState>(UiState.Loading)
  val uiState: StateFlow<UiState> = _uiState.asStateFlow()

  fun loadFabrics(nodeId: NodeId) {
    Timber.d("FabricsViewModel.loadFabrics: nodeId [$nodeId]")
    viewModelScope.launch {
      val previousState = _uiState.value
      if (previousState !is UiState.Loaded) {
        _uiState.value = UiState.Loading
      }
      _uiState.value = refreshFabrics(nodeId, previousState)
    }
  }

  fun removeFabric(nodeId: NodeId, fabricIndex: Int) {
    Timber.d("FabricsViewModel.removeFabric: nodeId [$nodeId] fabricIndex [$fabricIndex]")
    viewModelScope.launch {
      val currentState = _uiState.value
      _uiState.value = UiState.Loading
      try {
        val deviceCurrentFabricIndex = clustersHelper.readCurrentFabricIndexAttribute(nodeId)
        val controllerFabricIndex = chipClient.chipDeviceController.getFabricIndex()
        val currentFabricIndex = deviceCurrentFabricIndex ?: controllerFabricIndex
        if (fabricIndex == currentFabricIndex) {
          Timber.w("Refusing to remove current fabric index [$fabricIndex].")
          _uiState.value = currentState
          return@launch
        }
        clustersHelper.removeFabric(nodeId, fabricIndex)
        _uiState.value = refreshFabrics(nodeId, currentState)
      } catch (e: Exception) {
        Timber.e(e, "removeFabric failed")
        _uiState.value =
            if (currentState is UiState.Loaded) currentState
            else UiState.Error(R.string.controllers_offline)
      }
    }
  }

  private suspend fun refreshFabrics(nodeId: NodeId, fallbackState: UiState): UiState {
    return try {
      devicesRepository.getDeviceByNodeId(nodeId)
      val fabrics = clustersHelper.readFabricsAttribute(nodeId)
      val nocs = clustersHelper.readNOCsAttribute(nodeId)
      if (fabrics == null || nocs == null) {
        if (fallbackState is UiState.Loaded) fallbackState
        else UiState.Error(R.string.controllers_offline)
      } else {
        val deviceCurrentFabricIndex = clustersHelper.readCurrentFabricIndexAttribute(nodeId)
        val controllerFabricIndex = chipClient.chipDeviceController.getFabricIndex()
        val currentFabricIndex = deviceCurrentFabricIndex ?: controllerFabricIndex
        val fabricsByIndex = fabrics.associateBy { it.fabricIndex }
        val fabricIndexes =
            (fabrics.mapNotNull { it.fabricIndex } + nocs.mapNotNull { it.fabricIndex }).distinct()
        val mergedFabrics =
            fabricIndexes
                .mapNotNull { fabricIndex ->
                  fabricsByIndex[fabricIndex]?.run {
                    ManagedFabric(
                        fabricIndex = fabricIndex,
                        rootPublicKey = rootPublicKey,
                        vendorId = vendorID.toVendorId(),
                        fabricId = fabricID.toFabricId(),
                        nodeId = nodeID.toNodeId(),
                        label = label,
                        isCurrentFabric = fabricIndex == currentFabricIndex,
                    )
                  }
                }
                .sortedBy { it.fabricIndex }
        UiState.Loaded(mergedFabrics)
      }
    } catch (e: Exception) {
      Timber.e(e, "refreshFabrics failed")
      if (fallbackState is UiState.Loaded) fallbackState
      else UiState.Error(R.string.controllers_offline)
    }
  }
}
