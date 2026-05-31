// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.DISCRIMINATOR
import io.aether.android.Device
import io.aether.android.ITERATION
import io.aether.android.OPEN_COMMISSIONING_WINDOW_API
import io.aether.android.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import io.aether.android.OpenCommissioningWindowApi
import io.aether.android.R
import io.aether.android.SETUP_PIN_CODE
import io.aether.android.chip.BasicInformationAttributes
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.data.DevicesRepository
import io.aether.android.data.DevicesStateRepository
import io.aether.android.matter.NodeId
import io.aether.android.matter.VendorId
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.shared.SetDeviceNameResult
import io.aether.android.screens.shared.SetDeviceNameUseCase
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/** The ViewModel for the Device Settings Screen. */
@HiltViewModel
class DeviceSettingsViewModel
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    private val devicesStateRepository: DevicesStateRepository,
    private val chipClient: ChipClient,
    private val clustersHelper: ClustersHelper,
    private val setDeviceNameUseCase: SetDeviceNameUseCase,
) : ViewModel() {

  // The device being shown on the settings screen.
  private var _device = MutableStateFlow<Device?>(null)
  val device: StateFlow<Device?> = _device.asStateFlow()

  // Attributes fetched live from Basic Information cluster.
  private var _basicInformation = MutableStateFlow<BasicInformationAttributes?>(null)
  val basicInformation: StateFlow<BasicInformationAttributes?> = _basicInformation.asStateFlow()

  private var _isOnline = MutableStateFlow(true)
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private var _dateCommissioned = MutableStateFlow<Timestamp?>(null)
  val dateCommissioned: StateFlow<Timestamp?> = _dateCommissioned.asStateFlow()

  init {
    // Keep _isOnline in sync with the repository state as it changes.
    kotlinx.coroutines.flow
        .combine(_device, devicesStateRepository.devicesStateFlow) { device, state ->
          val node =
              device?.nodeId?.let { nodeId -> state.nodesList.firstOrNull { it.nodeId == nodeId } }
          (node?.online ?: false) to node?.dateCommissioned
        }
        .onEach { (isOnline, dateCommissioned) ->
          _isOnline.value = isOnline
          _dateCommissioned.value = dateCommissioned?.takeUnless { isDefaultTimestamp(it) }
        }
        .launchIn(viewModelScope)
  }

  // Vendor ID fetched from the device (null when not yet loaded or unavailable).
  private var _vendorId = MutableStateFlow<VendorId?>(null)
  val vendorId: StateFlow<VendorId?> = _vendorId.asStateFlow()

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  // Controls whether the "Remove Device" AlertDialog should be shown in the UI.
  private var _showRemoveDeviceAlertDialog = MutableStateFlow(false)
  val showRemoveDeviceAlertDialog: StateFlow<Boolean> = _showRemoveDeviceAlertDialog.asStateFlow()

  // Controls whether the "Confirm Device Removal" AlertDialog should be shown in the UI.
  private var _showConfirmDeviceRemovalAlertDialog = MutableStateFlow(false)
  val showConfirmDeviceRemovalAlertDialog: StateFlow<Boolean> =
      _showConfirmDeviceRemovalAlertDialog.asStateFlow()

  // Communicates to the UI that removal of the device has completed successfully.
  private var _deviceRemovalCompleted = MutableStateFlow(false)
  val deviceRemovalCompleted: StateFlow<Boolean> = _deviceRemovalCompleted.asStateFlow()

  // Communicates to the UI that the pairing window is open for device sharing.
  private var _pairingWindowOpenForDeviceSharing = MutableStateFlow(false)
  val pairingWindowOpenForDeviceSharing: StateFlow<Boolean> =
      _pairingWindowOpenForDeviceSharing.asStateFlow()

  // -----------------------------------------------------------------------------------------------
  // Load device

  fun loadDevice(nodeId: NodeId) {
    Timber.d("loadDevice: nodeId [$nodeId]")
    viewModelScope.launch {
      val shouldBlockUiUntilLoaded = _device.value == null
      try {
        val devicesForNode = devicesRepository.getDevicesByNodeId(nodeId)
        val loadedDevice =
            chooseBestDevice(devicesForNode) ?: devicesRepository.getDeviceByNodeId(nodeId)
        val basicInfo =
            try {
              clustersHelper.readBasicInformationAttributes(nodeId.toLong())
            } catch (e: Exception) {
              Timber.w(e, "loadDevice: could not read basic information attributes")
              null
            }

        _device.value = loadedDevice
        _basicInformation.value = basicInfo
      } catch (e: Exception) {
        Timber.e(e, "loadDevice failed")
        if (shouldBlockUiUntilLoaded) {
          _device.value = null
        }
        _basicInformation.value = null
        showMsgDialog(R.string.device_settings, R.string.device_settings_load_failed)
      }
    }
  }

  private fun chooseBestDevice(candidates: List<Device>): Device? {
    return candidates.maxByOrNull { candidate ->
      var score = 0
      if (candidate.deviceType != Device.DeviceType.TYPE_UNKNOWN) score += 4
      if (candidate.name.isNotBlank()) score += 2
      if (candidate.productName.isNotBlank()) score += 2
      if (candidate.productId.toIntOrNull()?.let { it != 0 } == true) score += 1
      score
    }
  }

  private fun isDefaultTimestamp(timestamp: Timestamp): Boolean {
    return timestamp.seconds == 0L && timestamp.nanos == 0
  }

  // -----------------------------------------------------------------------------------------------
  // Rename device

  fun renameDevice(nodeId: NodeId, newName: String) {
    Timber.d("renameDevice: nodeId [$nodeId] newName [$newName]")
    viewModelScope.launch {
      val device = devicesRepository.getDeviceByNodeId(nodeId.toLong())
      val result =
          setDeviceNameUseCase.execute(nodeId, newName) {
            // Immediately update local state so the UI reflects the new name.
            _device.value = _device.value?.toBuilder()?.setName(newName)?.build()
          }
      if (result is SetDeviceNameResult.LocalError) {
        showMsgDialog(R.string.set_device_name_failed, result.exception.message)
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Change device type

  fun changeDeviceType(nodeId: NodeId, deviceType: Device.DeviceType) {
    Timber.d("changeDeviceType: nodeId [$nodeId] deviceType [$deviceType]")
    viewModelScope.launch {
      try {
        devicesRepository.updateDeviceType(nodeId, deviceType)
        _device.value = _device.value?.toBuilder()?.setDeviceType(deviceType)?.build()
      } catch (e: Exception) {
        Timber.e(e, "changeDeviceType failed")
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Dialog and transient UI state

  fun showRemoveDeviceAlertDialog() {
    _showRemoveDeviceAlertDialog.value = true
  }

  fun dismissRemoveDeviceDialog() {
    _showRemoveDeviceAlertDialog.value = false
  }

  fun dismissConfirmDeviceRemovalDialog() {
    _showConfirmDeviceRemovalAlertDialog.value = false
  }

  fun resetDeviceRemovalCompleted() {
    _deviceRemovalCompleted.value = false
  }

  fun resetPairingWindowOpenForDeviceSharing() {
    _pairingWindowOpenForDeviceSharing.value = false
  }

  fun shareDeviceSucceeded() {
    Timber.d("ShareDevice: shareDeviceSucceeded")
    _pairingWindowOpenForDeviceSharing.value = false
  }

  fun shareDeviceFailed(resultCode: Int) {
    Timber.d("ShareDevice: shareDeviceFailed resultCode [$resultCode]")
    _pairingWindowOpenForDeviceSharing.value = false
  }

  // -----------------------------------------------------------------------------------------------
  // Share device

  // Open commissioning window for device sharing.
  fun openPairingWindow(nodeId: NodeId) {
    Timber.d("ShareDevice: openPairingWindow")
    viewModelScope.launch {
      showMsgDialog(
          R.string.opening_pairing_window_title,
          R.string.opening_pairing_window_message,
          false,
      )
      try {
        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId.toLong())
        val isCommissioningWindowOpen = clustersHelper.isCommissioningWindowOpen(devicePtr)
        if (isCommissioningWindowOpen) {
          Timber.d("ShareDevice: commissioning window is already open, closing it")
          clustersHelper.closeCommissioningWindow(devicePtr)
        }
        when (OPEN_COMMISSIONING_WINDOW_API) {
          OpenCommissioningWindowApi.ChipDeviceController ->
              openCommissioningWindowUsingOpenPairingWindowWithPin(nodeId)
          OpenCommissioningWindowApi.AdministratorCommissioningCluster ->
              openCommissioningWindowWithAdministratorCommissioningCluster(nodeId)
        }
        dismissMsgDialog()
        _pairingWindowOpenForDeviceSharing.value = true
      } catch (e: Exception) {
        Timber.e(e, "ShareDevice: openPairingWindow failed")
        dismissMsgDialog()
        showMsgDialog(R.string.device_share_dialog_failed, e.message)
      }
    }
  }

  private suspend fun openCommissioningWindowUsingOpenPairingWindowWithPin(nodeId: NodeId) {
    Timber.d(
        "ShareDevice: chipClient.awaitOpenPairingWindowWithPIN " +
            "duration [${OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS}] iteration [${ITERATION}] " +
            "discriminator [${DISCRIMINATOR}]"
    )
    val connectedDevicePointer = chipClient.awaitGetConnectedDevicePointer(nodeId.toLong())
    chipClient.awaitOpenPairingWindowWithPIN(
        connectedDevicePointer,
        OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS,
        ITERATION,
        DISCRIMINATOR,
        SETUP_PIN_CODE,
    )
  }

  private suspend fun openCommissioningWindowWithAdministratorCommissioningCluster(nodeId: NodeId) {
    val salt = Random.nextBytes(32)
    val timedInvokeTimeoutMs = 10000
    val connectedDevicePointer = chipClient.awaitGetConnectedDevicePointer(nodeId.toLong())
    val verifier =
        chipClient.computePaseVerifier(connectedDevicePointer, SETUP_PIN_CODE, ITERATION, salt)
    clustersHelper.openCommissioningWindowAdministratorCommissioningCluster(
        nodeId.toLong(),
        0,
        OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS,
        verifier.pakeVerifier,
        DISCRIMINATOR,
        ITERATION,
        salt,
        timedInvokeTimeoutMs,
    )
  }

  // -----------------------------------------------------------------------------------------------
  // Remove device

  fun removeDevice(nodeId: NodeId) {
    Timber.d("Removing device for nodeId [$nodeId]")
    showMsgDialog(R.string.unlinking_device_title, R.string.unlinking_device_body, false)
    viewModelScope.launch {
      try {
        chipClient.awaitUnpairDevice(nodeId.toLong())
      } catch (e: Exception) {
        Timber.e(e, "Unlinking the device failed.")
        dismissMsgDialog()
        _showConfirmDeviceRemovalAlertDialog.value = true
        return@launch
      }
      Timber.d("removeDevice succeeded for nodeId [$nodeId]")
      dismissMsgDialog()
      removePhysicalDevice(nodeId)
      _deviceRemovalCompleted.value = true
    }
  }

  fun removeDeviceWithoutUnlink(nodeId: NodeId) {
    Timber.d("removeDeviceWithoutUnlink: nodeId [$nodeId]")
    viewModelScope.launch {
      try {
        removePhysicalDevice(nodeId)
        _deviceRemovalCompleted.value = true
      } catch (e: Exception) {
        Timber.e(e, "removeDeviceWithoutUnlink failed")
        showMsgDialog(R.string.device_remove_dialog_title, e.message)
      }
    }
  }

  private suspend fun removePhysicalDevice(nodeId: Long) {
    devicesRepository.removeDevice(nodeId)
  }

  private suspend fun removePhysicalDevice(nodeId: NodeId) {
    removePhysicalDevice(nodeId.toLong())
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  fun showMsgDialog(
      title: String,
      msg: String?,
      showConfirmButton: Boolean = true,
  ) {
    Timber.d("showMsgDialog [title=$title]")
    _msgDialogInfo.value =
        DialogInfo(title = title, message = msg, showConfirmButton = showConfirmButton)
  }

  fun showMsgDialog(
      @StringRes titleRes: Int,
      @StringRes msgRes: Int,
      showConfirmButton: Boolean = true,
  ) {
    Timber.d("showMsgDialog [titleRes=$titleRes]")
    _msgDialogInfo.value =
        DialogInfo(titleRes = titleRes, messageRes = msgRes, showConfirmButton = showConfirmButton)
  }

  fun showMsgDialog(
      @StringRes titleRes: Int,
      msg: String?,
      showConfirmButton: Boolean = true,
  ) {
    Timber.d("showMsgDialog [titleRes=$titleRes]")
    _msgDialogInfo.value =
        DialogInfo(titleRes = titleRes, message = msg, showConfirmButton = showConfirmButton)
  }

  fun dismissMsgDialog() {
    Timber.d("dismissMsgDialog()")
    _msgDialogInfo.value = null
  }
}
