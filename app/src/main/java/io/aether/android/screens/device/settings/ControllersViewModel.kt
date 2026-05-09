// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.ChipStructs
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.chip.ClustersHelper
import io.aether.android.data.DevicesRepository
import io.aether.android.nodeIdFor
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** ViewModel for the Controllers screen. */
@HiltViewModel
class ControllersViewModel
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    private val clustersHelper: ClustersHelper,
) : ViewModel() {

  sealed class UiState {
    data object Loading : UiState()

    data class Loaded(
        val fabrics: List<ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct>
    ) : UiState()

    data class Error(val message: String) : UiState()
  }

  private var _uiState = MutableStateFlow<UiState>(UiState.Loading)
  val uiState: StateFlow<UiState> = _uiState.asStateFlow()

  fun loadControllers(deviceId: Long) {
    Timber.d("ControllersViewModel.loadControllers: deviceId [$deviceId]")
    viewModelScope.launch {
      _uiState.value = UiState.Loading
      try {
        val device = devicesRepository.getDevice(deviceId)
        val nodeId = nodeIdFor(device)
        val fabrics = clustersHelper.readFabricsAttribute(nodeId)
        if (fabrics != null) {
          _uiState.value = UiState.Loaded(fabrics)
        } else {
          _uiState.value = UiState.Error("Failed to read fabrics from device.")
        }
      } catch (e: Exception) {
        Timber.e(e, "loadControllers failed")
        _uiState.value = UiState.Error(e.message ?: e.toString())
      }
    }
  }

  fun removeController(deviceId: Long, fabricIndex: Int) {
    Timber.d(
        "ControllersViewModel.removeController: deviceId [$deviceId] fabricIndex [$fabricIndex]"
    )
    viewModelScope.launch {
      val currentState = _uiState.value
      _uiState.value = UiState.Loading
      try {
        val device = devicesRepository.getDevice(deviceId)
        val nodeId = nodeIdFor(device)
        clustersHelper.removeFabric(nodeId, fabricIndex)
        // Reload the list after removal.
        loadControllers(deviceId)
      } catch (e: Exception) {
        Timber.e(e, "removeController failed")
        // Restore previous state and show error.
        _uiState.value =
            if (currentState is UiState.Loaded) currentState
            else UiState.Error(e.message ?: e.toString())
      }
    }
  }
}
