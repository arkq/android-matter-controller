// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.annotation.StringRes
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.DataModelLoader
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.matter.ClusterId
import io.aether.android.matter.DeviceTypeId
import io.aether.android.screens.common.DialogInfo
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** The ViewModel for the [DataModelScreen]. */
@HiltViewModel
class DataModelViewModel
@Inject
constructor(
    private val clustersHelper: ClustersHelper,
    private val dataModelLoader: DataModelLoader,
) : ViewModel() {

  val clustersMap: Map<ClusterId, String> = dataModelLoader.clustersMap
  val devicesMap: Map<DeviceTypeId, String> = dataModelLoader.devicesMap

  // The introspection info fetched from the device.
  private var _deviceMatterInfoList = MutableStateFlow<List<DeviceMatterInfo>?>(null)
  val deviceMatterInfoList: StateFlow<List<DeviceMatterInfo>?> = _deviceMatterInfoList.asStateFlow()

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  // -----------------------------------------------------------------------------------------------
  // Inspect device

  /** Inspect the device information. */
  fun inspectDevice(nodeId: Long) {
    Timber.d("inspectDevice: nodeId [${nodeId}]")
    viewModelScope.launch {
      try {
        // Introspect the device.
        _deviceMatterInfoList.value = clustersHelper.fetchDeviceMatterInfo(nodeId)
        Timber.d("after fetch...")
      } catch (e: Exception) {
        Timber.e("*** EXCEPTION GETTING DEVICE MATTER INFO *****", e)
        _deviceMatterInfoList.value = emptyList()
        showMsgDialog(R.string.error_introspecting_device, e.message ?: e.toString())
      }
    }
  }

  // TODO: document what the ApplicationBasicCluster is...
  fun inspectApplicationBasicCluster(nodeId: Long) {
    Timber.d("inspectApplicationBasicCluster: nodeId [${nodeId}]")
    viewModelScope.launch {
      val attributeList = clustersHelper.readApplicationBasicClusterAttributeList(nodeId, 1)
      attributeList.forEach { Timber.d("inspectDevice attribute: [$it]") }
    }
  }

  // TODO: document what the BasicCluster is...
  fun inspectBasicCluster(deviceId: Long) {
    Timber.d("inspectBasicCluster: deviceId [${deviceId}]")
    viewModelScope.launch {
      val vendorId = clustersHelper.readBasicClusterVendorIDAttribute(deviceId, 0)
      Timber.d("vendorId [${vendorId}]")

      val attributeList = clustersHelper.readBasicClusterAttributeList(deviceId, 0)
      Timber.d("attributeList [${attributeList}]")
    }
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  private fun showMsgDialog(
      @StringRes titleRes: Int,
      msg: String?,
      showConfirmButton: Boolean = true,
  ) {
    Timber.d("showMsgDialog [titleRes=$titleRes]")
    _msgDialogInfo.value =
        DialogInfo(titleRes = titleRes, message = msg, showConfirmButton = showConfirmButton)
  }

  // Called after user dismiss the Info dialog. If we don't consume, a config change redisplays the
  // alert dialog.
  fun dismissMsgDialog() {
    Timber.d("dismissMsgDialog()")
    _msgDialogInfo.value = null
  }
}
