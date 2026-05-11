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
import io.aether.android.nodeIdFor
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class ManagedFabric(
    val fabricIndex: Int,
    val rootPublicKey: ByteArray?,
    val vendorId: Int?,
    val fabricId: Long?,
    val nodeId: Long?,
    val label: String?,
    val isCurrentFabric: Boolean,
)

/** ViewModel for the Controllers screen. */
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

  fun loadFabrics(deviceId: Long) {
    Timber.d("FabricsViewModel.loadFabrics: deviceId [$deviceId]")
    viewModelScope.launch {
      val previousState = _uiState.value
      if (previousState !is UiState.Loaded) {
        _uiState.value = UiState.Loading
      }
      try {
        val device = devicesRepository.getDevice(deviceId)
        val nodeId = nodeIdFor(device)
        val fabrics = clustersHelper.readFabricsAttribute(nodeId)
        val nocs = clustersHelper.readNOCsAttribute(nodeId)
        if (fabrics == null || nocs == null) {
          _uiState.value =
              if (previousState is UiState.Loaded) previousState
              else UiState.Error(R.string.controllers_offline)
          return@launch
        }
        val deviceCurrentFabricIndex = clustersHelper.readCurrentFabricIndexAttribute(nodeId)
        val controllerFabricIndex = chipClient.chipDeviceController.getFabricIndex()
        val currentFabricIndex = deviceCurrentFabricIndex ?: controllerFabricIndex
        val fabricsByIndex = fabrics.associateBy { it.fabricIndex }
        val fabricIndexes =
            (fabrics.mapNotNull { it.fabricIndex } + nocs.mapNotNull { it.fabricIndex }).distinct()
        val mergedFabrics =
            fabricIndexes
                .map { fabricIndex ->
                  val fabric = fabricsByIndex[fabricIndex]
                  val isCurrentFabric = fabricIndex == currentFabricIndex
                  ManagedFabric(
                      fabricIndex = fabricIndex,
                      rootPublicKey = fabric?.rootPublicKey,
                      vendorId = fabric?.vendorID,
                      fabricId = fabric?.fabricID,
                      nodeId = fabric?.nodeID,
                      label = fabric?.label,
                      isCurrentFabric = isCurrentFabric,
                  )
                }
                .sortedBy { it.fabricIndex }
        _uiState.value = UiState.Loaded(mergedFabrics)
      } catch (e: Exception) {
        Timber.e(e, "loadFabrics failed")
        _uiState.value =
            if (previousState is UiState.Loaded) previousState
            else UiState.Error(R.string.controllers_offline)
      }
    }
  }

  fun removeFabric(deviceId: Long, fabricIndex: Int) {
    Timber.d("FabricsViewModel.removeFabric: deviceId [$deviceId] fabricIndex [$fabricIndex]")
    viewModelScope.launch {
      val currentState = _uiState.value
      _uiState.value = UiState.Loading
      try {
        val device = devicesRepository.getDevice(deviceId)
        val nodeId = nodeIdFor(device)
        val deviceCurrentFabricIndex = clustersHelper.readCurrentFabricIndexAttribute(nodeId)
        val controllerFabricIndex = chipClient.chipDeviceController.getFabricIndex()
        val currentFabricIndex = deviceCurrentFabricIndex ?: controllerFabricIndex
        if (fabricIndex == currentFabricIndex) {
          Timber.w("Refusing to remove current fabric index [$fabricIndex].")
          _uiState.value = currentState
          return@launch
        }
        clustersHelper.removeFabric(nodeId, fabricIndex)
        // Reload the list after removal.
        loadFabrics(deviceId)
      } catch (e: Exception) {
        Timber.e(e, "removeFabric failed")
        // Restore previous state and show error.
        _uiState.value =
            if (currentState is UiState.Loaded) currentState
            else UiState.Error(R.string.controllers_offline)
      }
    }
  }
}
