// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.model.NodeState
import io.aether.android.Device
import io.aether.android.DISCRIMINATOR
import io.aether.android.ITERATION
import io.aether.android.OPEN_COMMISSIONING_WINDOW_API
import io.aether.android.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import io.aether.android.OpenCommissioningWindowApi
import io.aether.android.PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS
import io.aether.android.SETUP_PIN_CODE
import io.aether.android.STATE_CHANGES_MONITORING_MODE
import io.aether.android.StateChangesMonitoringMode
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.MatterConstants.ColorControlClusterId
import io.aether.android.chip.MatterConstants.LevelControlClusterId
import io.aether.android.chip.MatterConstants.OnOffClusterId
import io.aether.android.chip.MatterConstants.OnOffAttribute
import io.aether.android.chip.MatterConstants.LevelAttribute
import io.aether.android.chip.MatterConstants.ColorTemperatureAttribute
import io.aether.android.chip.SubscriptionHelper
import io.aether.android.convertToAppDeviceType
import io.aether.android.data.DevicesRepository
import io.aether.android.data.DevicesStateRepository
import io.aether.android.endpointFor
import io.aether.android.nodeIdFor
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.home.DeviceUiModel
import io.aether.android.supportsColorTemperature
import io.aether.android.supportsLevelControl
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** The ViewModel for the Device Screen. */
@HiltViewModel
class DeviceViewModel
@Inject
constructor(
  private val devicesRepository: DevicesRepository,
  val devicesStateRepository: DevicesStateRepository,
  private val chipClient: ChipClient,
  private val clustersHelper: ClustersHelper,
  private val subscriptionHelper: SubscriptionHelper,
) : ViewModel() {

  // The UI model for device shown on the Device screen.
  private var _deviceUiModel = MutableStateFlow<DeviceUiModel?>(null)
  val deviceUiModel: StateFlow<DeviceUiModel?> = _deviceUiModel.asStateFlow()

  // All endpoint UI models for the same physical node shown on the Device screen.
  // Sorted by ascending endpoint number.
  private var _allEndpointUiModels = MutableStateFlow<List<DeviceUiModel>>(emptyList())
  val allEndpointUiModels: StateFlow<List<DeviceUiModel>> = _allEndpointUiModels.asStateFlow()

  // Controls whether a periodic ping to the device is enabled or not.
  private var devicePeriodicPingEnabled: Boolean = true

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
  // See resetDeviceRemovalCompleted() to reset this state after being handled by the UI.
  private var _deviceRemovalCompleted = MutableStateFlow(false)
  val deviceRemovalCompleted: StateFlow<Boolean> = _deviceRemovalCompleted.asStateFlow()

  // Communicates to the UI that the pairing window is open for device sharing.
  // See resetPairingWindowOpenForDeviceSharing() to reset this state after being handled by the UI.
  private var _pairingWindowOpenForDeviceSharing = MutableStateFlow(false)
  val pairingWindowOpenForDeviceSharing: StateFlow<Boolean> =
    _pairingWindowOpenForDeviceSharing.asStateFlow()

  private suspend fun removeAllLogicalDevicesForNode(nodeId: Long) {
    val devices = devicesRepository.getAllDevices().devicesList
    devices
      .filter { nodeIdFor(it) == nodeId }
      .forEach { devicesRepository.removeDevice(it.deviceId) }
  }

  // -----------------------------------------------------------------------------------------------
  // Load device

  fun loadDevice(deviceId: Long) {
    if (deviceId == deviceUiModel.value?.device?.deviceId) {
      Timber.d("loadDevice: [${deviceId}] was already loaded")
      return
    } else {
      Timber.d("loadDevice: loading [${deviceId}]")
      viewModelScope.launch {
        val device = devicesRepository.getDevice(deviceId)
        val deviceState = devicesStateRepository.loadDeviceState(deviceId)
        var isOnline = false
        var isOn = false
        var level = 0
        var colorTemperature = 0
        if (deviceState != null) {
          isOnline = deviceState.online
          isOn = deviceState.on
          level = deviceState.level
          colorTemperature = deviceState.colorTemperature
        }
        _deviceUiModel.value = DeviceUiModel(device, isOnline, isOn, level, colorTemperature)

        // Load all endpoint devices (siblings) for the same physical node.
        val nId = nodeIdFor(device)
        val allDevices = devicesRepository.getAllDevices().devicesList
        val siblings = allDevices
          .filter { nodeIdFor(it) == nId }
          .sortedBy { endpointFor(it) }
        val models = siblings.map { sibling ->
          val siblingState = devicesStateRepository.loadDeviceState(sibling.deviceId)
          if (siblingState != null) {
            DeviceUiModel(sibling, siblingState.online, siblingState.on, siblingState.level, siblingState.colorTemperature)
          } else {
            DeviceUiModel(sibling, false, false)
          }
        }
        _allEndpointUiModels.value = models

        // If only 1 sibling was found, the device may have been commissioned before
        // multi-endpoint support was added (nodeId=0) or the commissioning may have
        // fallen through to the single-endpoint fallback path. Try to discover any
        // additional endpoints from the live Matter node and persist them so the
        // user sees all endpoint controls without needing to re-commission.
        if (siblings.size == 1) {
          discoverLiveEndpoints(device, nId)
        }
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Endpoint discovery / migration

  /**
   * Attempts to enumerate the Matter node's application endpoints from the live device and
   * persists any that are missing from the local repository.
   *
   * This covers two situations that result in only one Device record being stored for a physical
   * node that has multiple endpoints:
   *  1. Old-format devices commissioned before multi-endpoint support was added. In those records
   *     [Device.nodeId] is 0 and [Device.deviceId] carries the Matter node ID. The function also
   *     patches the existing record to set the proper [Device.nodeId] and [Device.endpoint] fields
   *     so future loads find all siblings without another network round-trip.
   *  2. New-format devices where the commissioning fell through to the single-endpoint fallback
   *     path (e.g. because [ClustersHelper.fetchDeviceMatterInfo] returned an empty parts list for
   *     the root node).
   *
   * On success [_allEndpointUiModels] is updated to include all discovered endpoints. On any
   * error the function logs the failure and leaves [_allEndpointUiModels] unchanged.
   */
  private suspend fun discoverLiveEndpoints(primaryDevice: Device, nodeId: Long) {
    try {
      Timber.d("discoverLiveEndpoints: probing node $nodeId for additional endpoints")
      val deviceMatterInfoList = clustersHelper.fetchDeviceMatterInfo(nodeId)
      val liveAppEndpoints = deviceMatterInfoList.filter { info ->
        info.endpoint != 0 && info.serverClusters.contains(OnOffClusterId)
      }
      if (liveAppEndpoints.size <= 1) {
        Timber.d("discoverLiveEndpoints: node $nodeId has only 1 app endpoint; nothing to migrate")
        return
      }
      Timber.d("discoverLiveEndpoints: found ${liveAppEndpoints.size} endpoints for node $nodeId; migrating")

      val allDevices = devicesRepository.getAllDevices().devicesList

      // For old-format devices (nodeId == 0) the deviceId IS the Matter node ID. Patch the
      // existing record so that nodeId and endpoint are filled in; subsequent loads will then
      // find all siblings via the normal nodeIdFor filter without a network call.
      if (primaryDevice.nodeId == 0L) {
        val smallestEndpoint = liveAppEndpoints.minOf { it.endpoint }
        val updatedPrimary = Device.newBuilder(primaryDevice)
          .setNodeId(nodeId)
          .setEndpoint(smallestEndpoint)
          .build()
        devicesRepository.updateDevice(updatedPrimary)
        Timber.d("discoverLiveEndpoints: patched primary device ${primaryDevice.deviceId} to nodeId=$nodeId endpoint=$smallestEndpoint")
      }

      // Create Device and DeviceState records for endpoints not yet in the repository.
      liveAppEndpoints.forEach { info ->
        val alreadyStored = allDevices.any {
          nodeIdFor(it) == nodeId && endpointFor(it) == info.endpoint
        }
        if (alreadyStored) return@forEach

        val newDeviceId = devicesRepository.incrementAndReturnLastDeviceId()
        val endpointType =
          if (info.types.isNotEmpty()) convertToAppDeviceType(info.types.first())
          else primaryDevice.deviceType
        val newDevice = Device.newBuilder()
          .setName(primaryDevice.name)
          .setDeviceId(newDeviceId)
          .setNodeId(nodeId)
          .setEndpoint(info.endpoint)
          .setSupportsLevelControl(info.serverClusters.contains(LevelControlClusterId))
          .setSupportsColorTemperature(info.serverClusters.contains(ColorControlClusterId))
          .setVendorId(primaryDevice.vendorId)
          .setVendorName(primaryDevice.vendorName)
          .setProductId(primaryDevice.productId)
          .setProductName(primaryDevice.productName)
          .setDeviceType(endpointType)
          .build()
        devicesRepository.addDevice(newDevice)
        devicesStateRepository.addDeviceState(newDeviceId, isOnline = false, isOn = false, level = 0, colorTemperature = 0)
        Timber.d("discoverLiveEndpoints: added device $newDeviceId for nodeId=$nodeId endpoint=${info.endpoint}")
      }

      // Reload siblings from the now-updated repository and refresh the UI state.
      val updatedAllDevices = devicesRepository.getAllDevices().devicesList
      val updatedSiblings = updatedAllDevices
        .filter { nodeIdFor(it) == nodeId }
        .sortedBy { endpointFor(it) }
      val updatedModels = updatedSiblings.map { sibling ->
        val siblingState = devicesStateRepository.loadDeviceState(sibling.deviceId)
        if (siblingState != null) {
          DeviceUiModel(sibling, siblingState.online, siblingState.on, siblingState.level, siblingState.colorTemperature)
        } else {
          DeviceUiModel(sibling, false, false)
        }
      }
      _allEndpointUiModels.value = updatedModels
      Timber.d("discoverLiveEndpoints: updated _allEndpointUiModels to ${updatedModels.size} entries")
    } catch (e: Exception) {
      Timber.e(e, "discoverLiveEndpoints: failed to enumerate live endpoints for node $nodeId")
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Share Device (aka Multi-Admin)

  fun openPairingWindow(deviceId: Long) {
    stopMonitoringStateChanges()
    showMsgDialog("Opening pairing window", "This may take a few seconds...", false)
    viewModelScope.launch {
      val nodeId = nodeIdFor(devicesRepository.getDevice(deviceId))
      // First we need to open a commissioning window.
      try {
        when (OPEN_COMMISSIONING_WINDOW_API) {
          OpenCommissioningWindowApi.ChipDeviceController ->
            openCommissioningWindowUsingOpenPairingWindowWithPin(nodeId)
          OpenCommissioningWindowApi.AdministratorCommissioningCluster ->
            openCommissioningWindowWithAdministratorCommissioningCluster(nodeId)
        }
        dismissMsgDialog()
        // Communicate to the UI that the pairing window is open.
        // UI can then launch the GPS activity for device sharing.
        _pairingWindowOpenForDeviceSharing.value = true
      } catch (e: Throwable) {
        dismissMsgDialog()
        val msg = "Failed to open the commissioning window"
        Timber.d("ShareDevice: $msg [$e]")
        showMsgDialog(msg, e.toString())
      }
    }
  }

  // Called by the fragment in Step 5 of the Device Sharing flow when the GPS activity for
  // Device Sharing has succeeded.
  fun shareDeviceSucceeded() {
    showMsgDialog("Device sharing completed successfully", null)
    startDevicePeriodicPing()
  }

  // Called by the fragment in Step 5 of the Device Sharing flow when the GPS activity for
  // Device Sharing has failed.
  fun shareDeviceFailed(resultCode: Int) {
    Timber.d("ShareDevice: Failed with errorCode [${resultCode}]")
    showMsgDialog("Device sharing failed", "error code: [$resultCode]")
    startDevicePeriodicPing()
  }

  private suspend fun openCommissioningWindowUsingOpenPairingWindowWithPin(deviceId: Long) {
    // TODO: Should generate random 64 bit value for SETUP_PIN_CODE (taking into account
    // spec constraints)
    Timber.d("ShareDevice: chipClient.awaitGetConnectedDevicePointer(${deviceId})")
    val connectedDevicePointer = chipClient.awaitGetConnectedDevicePointer(deviceId)

    try {
      // Check if there is a commission window that's already open.
      // See [CommissioningWindowStatus] for complete details.
      val isOpen = clustersHelper.isCommissioningWindowOpen(connectedDevicePointer)
      Timber.d("ShareDevice: isCommissioningWindowOpen [$isOpen]")
      if (isOpen) {
        // close commission window
        clustersHelper.closeCommissioningWindow(connectedDevicePointer)
      }
    } catch (ex: Exception) {
      val errorMsg = "Failed to setup Administrator Commissioning Cluster"
      Timber.d("$errorMsg. Cause: ${ex.localizedMessage}")
      // ToDo() decide whether to terminate the OCW task if we fail to configure the window status.
    }

    val duration = OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
    Timber.d(
      "ShareDevice: chipClient.chipClient.awaitOpenPairingWindowWithPIN " +
        "duration [${duration}] iteration [${ITERATION}] discriminator [${DISCRIMINATOR}] " +
        "setupPinCode [${SETUP_PIN_CODE}]"
    )
    chipClient.awaitOpenPairingWindowWithPIN(
      connectedDevicePointer,
      duration,
      ITERATION,
      DISCRIMINATOR,
      SETUP_PIN_CODE,
    )
    Timber.d("ShareDevice: After chipClient.awaitOpenPairingWindowWithPIN")
  }

  // TODO: Was not working when tested. Use openCommissioningWindowUsingOpenPairingWindowWithPin
  // for now.
  private suspend fun openCommissioningWindowWithAdministratorCommissioningCluster(deviceId: Long) {
    Timber.d(
      "ShareDevice: openCommissioningWindowWithAdministratorCommissioningCluster [${deviceId}]"
    )
    val salt = Random.nextBytes(32)
    val timedInvokeTimeoutMs = 10000
    val devicePtr = chipClient.awaitGetConnectedDevicePointer(deviceId)
    val verifier = chipClient.computePaseVerifier(devicePtr, SETUP_PIN_CODE, ITERATION, salt)
    clustersHelper.openCommissioningWindowAdministratorCommissioningCluster(
      deviceId,
      0,
      180,
      verifier.pakeVerifier,
      DISCRIMINATOR,
      ITERATION,
      salt,
      timedInvokeTimeoutMs,
    )
  }

  // -----------------------------------------------------------------------------------------------
  // Remove device

  // Removes the device. First we remove the fabric from the device, and then we remove the
  // device from the app's devices repository.
  // Note that unlinking the device may take a while if the device is offline. Because of that,
  // a MsgAlertDIalog is shown, without any confirm button, to let the user know that unlinking
  // may take a while. That way the user is not left hanging wondering what is going on.
  // If removing the fabric from the device fails (e.g. device is offline), then another dialog
  // is shown so the user has the option to force remove the device without unlinking
  // the fabric at the device. If a forced removal is selected, then function
  // removeDeviceWithoutUnlink is called.
  // TODO: The device will still be linked to the local Android fabric. We should remove all the
  // fabrics at the device.
  fun removeDevice(deviceId: Long) {
    Timber.d("Removing device [${deviceId}]")
    showMsgDialog(
      "Unlinking the device",
      "Calling the device to remove the sample app's fabric. " +
        "If the device is offline, this will fail when the call times out, " +
        "and this may take a while.\n\n" +
        "Unlinking the device...",
      false,
    )
    viewModelScope.launch {
      val device = devicesRepository.getDevice(deviceId)
      val nodeId = nodeIdFor(device)
      try {
        chipClient.awaitUnpairDevice(nodeId)
      } catch (e: Exception) {
        Timber.e(e, "Unlinking the device failed.")
        dismissMsgDialog()
        // Show a dialog so the user has the option to force remove without unlinking the device.
        _showConfirmDeviceRemovalAlertDialog.value = true
        return@launch
      }
      // Remove device from the app's devices repository.
      Timber.d("removeDevice succeeded! [$deviceId]")
      dismissMsgDialog()
      removeAllLogicalDevicesForNode(nodeId)
      // Notify UI so we navigate back to Home screen.
      _deviceRemovalCompleted.value = true
    }
  }

  // Removes the device from the app's devices repository, and does not unlink the fabric
  // from the device.
  // This function is called after removeDevice() has failed trying to unlink the device
  // and the user has confirmed that the device should still be removed from the app's device
  // repository.
  fun removeDeviceWithoutUnlink(deviceId: Long) {
    Timber.d("removeDeviceWithoutUnlink: [${deviceId}]")
    viewModelScope.launch {
      val nodeId = nodeIdFor(devicesRepository.getDevice(deviceId))
      // Remove device from the app's devices repository.
      removeAllLogicalDevicesForNode(nodeId)
      _deviceRemovalCompleted.value = true
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Device state

  // On/Off
  fun updateDeviceStateOn(deviceUiModel: DeviceUiModel, isOn: Boolean) {
    Timber.d("updateDeviceStateOn: isOn [${isOn}]")
    val deviceId = deviceUiModel.device.deviceId
    viewModelScope.launch {
      Timber.d("Handling real device")
      try {
        clustersHelper.setOnOffDeviceStateOnOffCluster(
          nodeIdFor(deviceUiModel.device),
          isOn,
          endpointFor(deviceUiModel.device),
        )
        // Read the current stored state to avoid overwriting fresh level/colorTemperature values
        // with whatever was loaded at screen-open time.
        val current = devicesStateRepository.loadDeviceState(deviceId)
        devicesStateRepository.updateDeviceState(
          deviceId, true, isOn,
          current?.level ?: deviceUiModel.level,
          current?.colorTemperature ?: deviceUiModel.colorTemperature,
        )
      } catch (e: Throwable) {
        Timber.e("Failed setting on/off state")
      }
    }
  }

  // Level
  fun updateDeviceStateLevel(deviceUiModel: DeviceUiModel, level: Int) {
    Timber.d("updateDeviceStateLevel: level [${level}]")
    val deviceId = deviceUiModel.device.deviceId
    viewModelScope.launch {
      if (!supportsLevelControl(deviceUiModel.device)) {
        return@launch
      }

      Timber.d("Handling real device")
      try {
        clustersHelper.setLevelStateLevelControlCluster(
          nodeIdFor(deviceUiModel.device),
          level,
          endpointFor(deviceUiModel.device),
        )
        // Read the current stored state to avoid overwriting fresh isOn/colorTemperature values.
        val current = devicesStateRepository.loadDeviceState(deviceId)
        devicesStateRepository.updateDeviceState(
          deviceId, true,
          current?.on ?: deviceUiModel.isOn,
          level,
          current?.colorTemperature ?: deviceUiModel.colorTemperature,
        )
      } catch (e: Throwable) {
        Timber.e("Failed setting level")
      }
    }
  }

  // Color Temperature
  fun updateDeviceStateColorTemperature(deviceUiModel: DeviceUiModel, colorTemperature: Int) {
    Timber.d("updateDeviceStateColorTemperature: level [${colorTemperature}]")
    val deviceId = deviceUiModel.device.deviceId
    viewModelScope.launch {
      if (!supportsColorTemperature(deviceUiModel.device)) {
        return@launch
      }

      Timber.d("Handling real device")
      try {
        clustersHelper.setColorTemperatureColorControlCluster(
          nodeIdFor(deviceUiModel.device),
          colorTemperature,
          endpointFor(deviceUiModel.device),
        )
        // Read the current stored state to avoid overwriting fresh isOn/level values.
        val current = devicesStateRepository.loadDeviceState(deviceId)
        devicesStateRepository.updateDeviceState(
          deviceId, true,
          current?.on ?: deviceUiModel.isOn,
          current?.level ?: deviceUiModel.level,
          colorTemperature,
        )
      } catch (e: Throwable) {
        Timber.e("Failed setting color temperature")
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Inspect device

  fun inspectDescriptorCluster(deviceUiModel: DeviceUiModel) {
    val nodeId = nodeIdFor(deviceUiModel.device)
    val name = deviceUiModel.device.name
    val divider = "-".repeat(20)

    Timber.d("\n${divider} Inspect Device [${name}] [${nodeId}] $divider")
    viewModelScope.launch {
      val partsListAttribute =
        clustersHelper.readDescriptorClusterPartsListAttribute(
          chipClient.getConnectedDevicePointer(nodeId),
          0,
        )
      Timber.d("partsListAttribute [${partsListAttribute}]")

      partsListAttribute?.forEach { part ->
        Timber.d("part [$part] is [${part.javaClass}]")
        val endpoint =
          when (part) {
            is Int -> part.toInt()
            else -> return@forEach
          }
        Timber.d("Processing part [$part]")

        val deviceListAttribute =
          clustersHelper.readDescriptorClusterDeviceListAttribute(
            chipClient.getConnectedDevicePointer(nodeId),
            endpoint,
          )
        deviceListAttribute.forEach { Timber.d("device attribute: [${it}]") }

        val serverListAttribute =
          clustersHelper.readDescriptorClusterServerListAttribute(
            chipClient.getConnectedDevicePointer(nodeId),
            endpoint,
          )
        serverListAttribute.forEach { Timber.d("server attribute: [${it}]") }
      }
    }
  }

  fun inspectApplicationBasicCluster(nodeId: Long) {
    Timber.d("inspectApplicationBasicCluster: nodeId [${nodeId}]")
    viewModelScope.launch {
      val attributeList = clustersHelper.readApplicationBasicClusterAttributeList(nodeId, 1)
      attributeList.forEach { Timber.d("inspectDevice attribute: [$it]") }
    }
  }

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
  // State Changes Monitoring

  /**
   * The way we monitor state changes is defined by constant [StateChangesMonitoringMode].
   * [StateChangesMonitoringMode.Subscription] is the preferred mode.
   * [StateChangesMonitoringMode.PeriodicRead] was used initially because of issues with
   * subscriptions. We left its associated code as it could be useful to some developers.
   */
  fun startMonitoringStateChanges() {
    Timber.d("startMonitoringStateChanges(): mode [$STATE_CHANGES_MONITORING_MODE]")
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> subscribeToPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> startDevicePeriodicPing()
    }
  }

  fun stopMonitoringStateChanges() {
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> unsubscribeToPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> stopDevicePeriodicPing()
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Subscription to periodic device updates.
  // See:
  //   - Spec section "8.5 Subscribe Interaction"
  //   - Matter primer:
  // https://developers.home.google.com/matter/primer/interaction-model-reading#subscription_transaction
  //
  // TODO:
  //   - Properly implement unsubscribe behavior
  //   - Implement algorithm for online/offline detection.
  //     (Issue is that not clear how to register a callback for messages coming at maxInterval.

  /*
    Sample message coming at maxInterval.
  ```
  01-06 05:27:53.736 16814 16850 D EM      : >>> [E:59135r M:51879653] (S) Msg RX from 1:0000000000000001 [171D] --- Type 0001:05 (IM:ReportData)
  01-06 05:27:53.736 16814 16850 D EM      : Handling via exchange: 59135r, Delegate: 0x76767a7668
  01-06 05:27:53.736 16814 16850 D DMG     : ReportDataMessage =
  01-06 05:27:53.737 16814 16850 D DMG     : {
  01-06 05:27:53.737 16814 16850 D DMG     : 	SubscriptionId = 0x7e169ca8,
  01-06 05:27:53.737 16814 16850 D DMG     : 	InteractionModelRevision = 1
  01-06 05:27:53.737 16814 16850 D DMG     : }
  01-06 05:27:53.737 16814 16850 D DMG     : Refresh LivenessCheckTime for 35000 milliseconds with SubscriptionId = 0x7e169ca8 Peer = 01:0000000000000001
  01-06 05:27:53.737 16814 16850 D EM      : <<< [E:59135r M:213699489 (Ack:51879653)] (S) Msg TX to 1:0000000000000001 [171D] --- Type 0001:01 (IM:StatusResponse)
  01-06 05:27:53.737 16814 16850 D IN      : (S) Sending msg 213699489 on secure session with LSID: 25418
  01-06 05:27:53.838 16814 16850 D EM      : >>> [E:59135r M:51879654 (Ack:213699489)] (S) Msg RX from 1:0000000000000001 [171D] --- Type 0000:10 (SecureChannel:StandaloneAck)
  01-06 05:27:53.839 16814 16850 D EM      : Found matching exchange: 59135r, Delegate: 0x0
  01-06 05:27:53.839 16814 16850 D EM      : Rxd Ack; Removing MessageCounter:213699489 from Retrans Table on exchange 59135r
  ```
  */
  private fun subscribeToPeriodicUpdates() {
    Timber.d("subscribeToPeriodicUpdates()")
    val primaryDevice = deviceUiModel.value?.device ?: run {
      Timber.w("subscribeToPeriodicUpdates(): deviceUiModel not yet loaded, skipping subscription")
      return
    }
    val reportCallback =
      object : SubscriptionHelper.ReportCallbackForDevice(primaryDevice.deviceId) {
        override fun onReport(nodeState: NodeState) {
          super.onReport(nodeState)
          // Look up the current endpoint list on every report rather than capturing a snapshot at
          // subscription time, so endpoints that finish loading after the subscription is set up
          // are also updated.
          val currentEndpoints = allEndpointUiModels.value.map { it.device }
          val devicesToUpdate = currentEndpoints.ifEmpty { listOf(primaryDevice) }
          devicesToUpdate.forEach { device ->
            val endpoint = endpointFor(device)
            val onOffState =
              subscriptionHelper.extractAttribute(nodeState, endpoint, OnOffAttribute) as Boolean?
            val levelState =
              subscriptionHelper.extractAttribute(nodeState, endpoint, LevelAttribute) as Int?
            val colorTemperatureState =
              subscriptionHelper.extractAttribute(nodeState, endpoint, ColorTemperatureAttribute) as Int?
            Timber.d("onOffState [${onOffState}] for endpoint $endpoint")
            if (onOffState == null) {
              Timber.e("onReport(): WARNING -> onOffState is NULL for endpoint $endpoint. Ignoring.")
              return@forEach
            }
            if (supportsLevelControl(device) && levelState == null) {
              Timber.e("onReport(): WARNING -> levelState is NULL for endpoint $endpoint. Ignoring.")
              return@forEach
            }
            if (supportsColorTemperature(device) && colorTemperatureState == null) {
              Timber.e("onReport(): WARNING -> colorTemperatureState is NULL for endpoint $endpoint. Ignoring.")
              return@forEach
            }
            val level = if (supportsLevelControl(device)) levelState!! else 0
            val colorTemperature =
              if (supportsColorTemperature(device)) colorTemperatureState!! else 0
            viewModelScope.launch {
              devicesStateRepository.updateDeviceState(
                device.deviceId,
                isOnline = true,
                isOn = onOffState,
                level = level,
                colorTemperature = colorTemperature,
              )
            }
          }
        }
      }
    viewModelScope.launch {
      try {
        val connectedDevicePointer =
          chipClient.getConnectedDevicePointer(nodeIdFor(primaryDevice))
        subscriptionHelper.awaitSubscribeToPeriodicUpdates(
          connectedDevicePointer,
          SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(primaryDevice.deviceId),
          SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(primaryDevice.deviceId),
          reportCallback,
        )
      } catch (e: IllegalStateException) {
        Timber.e("Can't get connectedDevicePointer for ${primaryDevice.deviceId}.")
        return@launch
      }
    }
  }

  private fun unsubscribeToPeriodicUpdates() {
    Timber.d("unsubscribeToPeriodicUpdates()")
    val primaryDevice = deviceUiModel.value?.device ?: run {
      Timber.d("unsubscribeToPeriodicUpdates(): nothing to unsubscribe, deviceUiModel is null")
      return
    }
    viewModelScope.launch {
      try {
        val connectedDevicePtr =
          chipClient.getConnectedDevicePointer(nodeIdFor(primaryDevice))
        subscriptionHelper.awaitUnsubscribeToPeriodicUpdates(connectedDevicePtr)
      } catch (e: IllegalStateException) {
        Timber.e("Can't get connectedDevicePointer for ${primaryDevice.deviceId}.")
        return@launch
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Task that runs periodically to get and update the device state.
  // Periodic monitoring of a device state should be done with Subscription mode.
  // This code is left here in case it might be useful to some developers.

  private fun startDevicePeriodicPing() {
    Timber.d(
      "${LocalDateTime.now()} startDevicePeriodicPing every $PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS seconds"
    )
    devicePeriodicPingEnabled = true
    runDevicePeriodicUpdate(allEndpointUiModels.value.ifEmpty { listOfNotNull(deviceUiModel.value) })
  }

  private fun runDevicePeriodicUpdate(endpointModels: List<DeviceUiModel>) {
    if (PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS == -1) {
      return
    }
    viewModelScope.launch {
      while (devicePeriodicPingEnabled) {
        endpointModels.forEach { endpointUiModel ->
          var isOn: Boolean?
          var isOnline: Boolean
          var level: Int
          var colorTemperature: Int
          val nodeId = nodeIdFor(endpointUiModel.device)
          val endpoint = endpointFor(endpointUiModel.device)
          val hasLevelControl = supportsLevelControl(endpointUiModel.device)
          val hasColorTemperature = supportsColorTemperature(endpointUiModel.device)
          isOn = clustersHelper.getDeviceStateOnOffCluster(nodeId, endpoint)
          val levelRead =
            if (hasLevelControl) {
              clustersHelper.getDeviceStateLevelControlCluster(nodeId, endpoint)
            } else {
              null
            }
          val colorTemperatureRead =
            if (hasColorTemperature) {
              clustersHelper.getColorTemperatureColorControlCluster(nodeId, endpoint)
            } else {
              null
            }
          if (
            isOn == null ||
              (hasLevelControl && levelRead == null) ||
              (hasColorTemperature && colorTemperatureRead == null)
          ) {
            Timber.e("[device ping] failed for endpoint $endpoint")
            isOn = false
            isOnline = false
            level = 0
            colorTemperature = 0
          } else {
            level = if (hasLevelControl) levelRead!! else 0
            colorTemperature = if (hasColorTemperature) colorTemperatureRead!! else 0
            Timber.d("[device ping] success [${isOn}] for endpoint $endpoint")
            isOnline = true
          }
          devicesStateRepository.updateDeviceState(
            endpointUiModel.device.deviceId,
            isOnline = isOnline,
            isOn = isOn == true,
            level = level,
            colorTemperature = colorTemperature,
          )
        }
        delay(PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS * 1000L)
      }
    }
  }

  private fun stopDevicePeriodicPing() {
    devicePeriodicPingEnabled = false
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  fun showMsgDialog(title: String?, msg: String?, showConfirmButton: Boolean = true) {
    Timber.d("showMsgDialog [$title]")
    _msgDialogInfo.value = DialogInfo(title, msg, showConfirmButton)
  }

  // Called after user dismisss the Info dialog. If we don't consume, a config change redisplays the
  // alert dialog.
  fun dismissMsgDialog() {
    Timber.d("dismissMsgDialog()")
    _msgDialogInfo.value = null
  }

  fun showRemoveDeviceAlertDialog() {
    Timber.d("showRemoveDeviceAlertDialog")
    _showRemoveDeviceAlertDialog.value = true
  }

  fun dismissRemoveDeviceDialog() {
    Timber.d("dismissRemoveDeviceDialog")
    _showRemoveDeviceAlertDialog.value = false
  }

  fun dismissConfirmDeviceRemovalDialog() {
    Timber.d("dismissConfirmDeviceRemovalDialog")
    _showConfirmDeviceRemovalAlertDialog.value = false
  }

  fun resetDeviceRemovalCompleted() {
    _deviceRemovalCompleted.value = false
  }

  fun resetPairingWindowOpenForDeviceSharing() {
    _pairingWindowOpenForDeviceSharing.value = false
  }
}
