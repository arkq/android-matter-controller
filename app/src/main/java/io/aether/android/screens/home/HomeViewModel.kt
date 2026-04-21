// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.AttestationInfo
import chip.devicecontroller.DeviceAttestationDelegate
import chip.devicecontroller.model.NodeState
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import com.google.android.gms.home.matter.commissioning.DeviceInfo
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.*
import io.aether.android.Device
import io.aether.android.Devices
import io.aether.android.DevicesState
import io.aether.android.MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS
import io.aether.android.PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS
import io.aether.android.STATE_CHANGES_MONITORING_MODE
import io.aether.android.StateChangesMonitoringMode
import io.aether.android.TaskStatus
import io.aether.android.UserPreferences
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.MatterConstants.ColorControlClusterId
import io.aether.android.chip.MatterConstants.LevelAttribute
import io.aether.android.chip.MatterConstants.LevelControlClusterId
import io.aether.android.chip.MatterConstants.OnOffAttribute
import io.aether.android.chip.MatterConstants.OnOffClusterId
import io.aether.android.chip.MatterConstants.ColorTemperatureAttribute
import io.aether.android.chip.SubscriptionHelper
import io.aether.android.commissioning.AppCommissioningService
import io.aether.android.convertToAppDeviceType
import io.aether.android.data.DevicesRepository
import io.aether.android.data.DevicesStateRepository
import io.aether.android.data.UserPreferencesRepository
import io.aether.android.getTimestampForNow
import io.aether.android.screens.common.DialogInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

// -----------------------------------------------------------------------------
// Data structures

/**
 * Encapsulates all of the information on a specific device. Note that the app currently only
 * supports Matter devices with server attribute "ON/OFF".
 */
data class DeviceUiModel(
  // Device information that is persisted in a Proto DataStore. See DevicesRepository.
  val device: Device,

  // Device state information that is retrieved dynamically.
  // Whether the device is online or offline.
  var isOnline: Boolean,
  // Whether the device is on or off.
  var isOn: Boolean,
  // Level of device
  var level: Int = 0,
  // Color temperature of device
  var colorTemperature: Int = 0,
)

/**
 * UI model that encapsulates the information about the devices to be displayed on the Home screen.
 */
data class DevicesListUiModel(
  // The list of devices.
  val devices: List<DeviceUiModel>,
  // Whether offline devices should be shown.
  val showOfflineDevices: Boolean,
)

// -----------------------------------------------------------------------------
// ViewModel

/** The ViewModel for the [HomeScreen]. */
@HiltViewModel
class HomeViewModel
@Inject
constructor(
  private val devicesRepository: DevicesRepository,
  private val devicesStateRepository: DevicesStateRepository,
  private val userPreferencesRepository: UserPreferencesRepository,
  private val clustersHelper: ClustersHelper,
  private val chipClient: ChipClient,
  private val subscriptionHelper: SubscriptionHelper,
) : ViewModel() {

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  // Controls whether the "New Device" AlertDialog should be shown in the UI.
  private var _showNewDeviceNameAlertDialog = MutableStateFlow(false)
  val showNewDeviceNameAlertDialog: StateFlow<Boolean> = _showNewDeviceNameAlertDialog.asStateFlow()

  /** The current status of multiadmin commissioning. */
  private val _multiadminCommissionDeviceTaskStatus =
    MutableStateFlow<TaskStatus>(TaskStatus.NotStarted)
  val multiadminCommissionDeviceTaskStatus: StateFlow<TaskStatus> =
    _multiadminCommissionDeviceTaskStatus.asStateFlow()

  // Controls whether a Device Attestation failure is ignored or not.
  // FIXME: set to true for now until issues with attestation resolved.
  private var _deviceAttestationFailureIgnored = MutableStateFlow(true)
  val deviceAttestationFailureIgnored: StateFlow<Boolean> =
    _deviceAttestationFailureIgnored.asStateFlow()

  // Controls whether a periodic ping to the devices is enabled or not.
  private var devicesPeriodicPingEnabled: Boolean = true

  // Saves the result of the GPS Commissioning action (step 4).
  // It is then used in step 5 to complete the commissioning.
  private var gpsCommissioningResult: CommissioningResult? = null

  // -----------------------------------------------------------------------------------------------
  // Repositories handling.

  // The initial setup event which triggers the Home screen to get the data it needs.
  // TODO: Clarify if this is really necessary and how that works?
  init {
    liveData { emit(devicesRepository.getAllDevices()) }
    liveData { emit(devicesStateRepository.getAllDevicesState()) }
    liveData { emit(userPreferencesRepository.getData()) }
  }

  private val devicesFlow = devicesRepository.devicesFlow
  private val devicesStateFlow = devicesStateRepository.devicesStateFlow
  private val userPreferencesFlow = userPreferencesRepository.userPreferencesFlow

  // Every time the list of devices or user preferences are updated (emit is triggered),
  // we recreate the DevicesListUiModel
  private val devicesListUiModelFlow =
    combine(devicesFlow, devicesStateFlow, userPreferencesFlow) {
      devices: Devices,
      devicesStates: DevicesState,
      userPreferences: UserPreferences ->
      Timber.d("*** devicesListUiModelFlow changed ***")
      return@combine DevicesListUiModel(
        devices = processDevices(devices, devicesStates, userPreferences),
        showOfflineDevices = !userPreferences.hideOfflineDevices,
      )
    }

  val devicesUiModelLiveData = devicesListUiModelFlow.asLiveData()

  private fun processDevices(
    devices: Devices,
    devicesStates: DevicesState,
    userPreferences: UserPreferences,
  ): List<DeviceUiModel> {
    val devicesUiModel = ArrayList<DeviceUiModel>()
    // Show only one entry per physical node (grouped by nodeId).
    // Among endpoints for the same node, pick the one with the lowest endpoint number as
    // the representative shown on the home screen.
    val seenNodes = mutableSetOf<Long>()
    val sortedDevices = devices.devicesList.sortedWith(compareBy({ nodeIdFor(it) }, { endpointFor(it) }))
    sortedDevices.forEach { device ->
      val nId = nodeIdFor(device)
      if (!seenNodes.add(nId)) return@forEach // skip non-primary endpoints
      Timber.d("processDevices() deviceId: [${device.deviceId}]}")
      val state = devicesStates.devicesStateList.find { it.deviceId == device.deviceId }
      if (userPreferences.hideOfflineDevices) {
        if (state?.online != true) return@forEach
      }
      if (state == null) {
        Timber.d("    deviceId setting default value for state")
        devicesUiModel.add(DeviceUiModel(device, isOnline = false, isOn = false))
      } else {
        Timber.d("    deviceId setting its own value for state")
        devicesUiModel.add(DeviceUiModel(device, state.online, state.on, state.level, state.colorTemperature))
      }
    }
    return devicesUiModel
  }

  private fun nodeIdFor(device: Device): Long = if (device.nodeId != 0L) device.nodeId else device.deviceId

  private fun endpointFor(device: Device): Int = if (device.endpoint != 0) device.endpoint else 1

  private fun supportsLevelControl(device: Device): Boolean {
    return device.supportsLevelControl ||
      device.deviceType == Device.DeviceType.TYPE_DIMMABLE_LIGHT ||
      device.deviceType == Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT ||
      device.deviceType == Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT
  }

  private fun supportsColorTemperature(device: Device): Boolean {
    return device.supportsColorTemperature ||
      device.deviceType == Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT ||
      device.deviceType == Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT
  }

  // -----------------------------------------------------------------------------------------------
  // Commission Device
  //
  // See "docs/Google Home Mobile SDK.pdf" for a good overview of all the artifacts needed
  // to transfer control from the sample app's UI to the GPS CommissionDevice UI, and get a result
  // back.

  /**
   * Sample app has been invoked for multi-admin commissionning. TODO: Can we do it without going
   * through GMSCore? All we're missing is network location.
   */
  fun multiadminCommissioning(intent: Intent, context: Context) {
    Timber.d("multiadminCommissioning: starting")

    val sharedDeviceData = fromIntent(intent)
    Timber.d("multiadminCommissioning: sharedDeviceData [${sharedDeviceData}]")
    Timber.d("multiadminCommissioning: manualPairingCode [${sharedDeviceData.manualPairingCode}]")

    val commissionRequestBuilder =
      CommissioningRequest.builder()
        .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))

    // EXTRA_COMMISSIONING_WINDOW_EXPIRATION is a hint of how much time is remaining in the
    // commissioning window for multi-admin. It is based on the current system uptime.
    // If the user takes too long to select the target commissioning app, then there's not
    // enougj time to complete the multi-admin commissioning and we message it to the user.
    val commissioningWindowExpirationMillis =
      intent.getLongExtra(EXTRA_COMMISSIONING_WINDOW_EXPIRATION, -1L)
    val currentUptimeMillis = SystemClock.elapsedRealtime()
    val timeLeftSeconds = (commissioningWindowExpirationMillis - currentUptimeMillis) / 1000
    Timber.d(
      "commissionDevice: TargetCommissioner for MultiAdmin. " +
        "uptime [${currentUptimeMillis}] " +
        "commissioningWindowExpiration [${commissioningWindowExpirationMillis}] " +
        "-> expires in $timeLeftSeconds seconds"
    )

    if (commissioningWindowExpirationMillis == -1L) {
      Timber.e(
        "EXTRA_COMMISSIONING_WINDOW_EXPIRATION not specified in multi-admin call. " +
          "Still going ahead with the multi-admin though."
      )
    } else if (timeLeftSeconds < MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS) {
      showMsgDialog(
        "Commissioning Window Expiration",
        "The commissioning window will " +
          "expire in ${timeLeftSeconds} seconds, not long enough to " +
          "complete the commissioning.\n\n" +
          "In the future, please select the target commissioning application faster " +
          "to avoid this situation.",
      )
      return
    }

    val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
    commissionRequestBuilder.setDeviceNameHint(deviceName)

    val vendorId = intent.getIntExtra(EXTRA_VENDOR_ID, -1)
    val productId = intent.getIntExtra(EXTRA_PRODUCT_ID, -1)
    val deviceInfo = DeviceInfo.builder().setProductId(productId).setVendorId(vendorId).build()
    commissionRequestBuilder.setDeviceInfo(deviceInfo)

    val manualPairingCode = intent.getStringExtra(EXTRA_MANUAL_PAIRING_CODE)
    commissionRequestBuilder.setOnboardingPayload(manualPairingCode)

    val commissioningRequest = commissionRequestBuilder.build()

    Timber.d(
      "multiadmin: commissioningRequest " +
        "onboardingPayload [${commissioningRequest.onboardingPayload}] " +
        "vendorId [${commissioningRequest.deviceInfo!!.vendorId}] " +
        "productId [${commissioningRequest.deviceInfo!!.productId}]"
    )
  }

  // This is step 4 of the commissioning flow where GPS takes over.
  // We save the result we get from GPS, which will be used by commissionedDeviceNameCaptured
  // after the device name is captured.
  fun gpsCommissioningDeviceSucceeded(activityResult: ActivityResult) {
    gpsCommissioningResult =
      CommissioningResult.fromIntentSenderResult(activityResult.resultCode, activityResult.data)
    Timber.i(
      "Device commissioned successfully! deviceName [${gpsCommissioningResult!!.deviceName}]"
    )
    Timber.i("Device commissioned successfully! room [${gpsCommissioningResult!!.room}]")
    Timber.i(
      "Device commissioned successfully! DeviceDescriptor of device:\n" +
        "productId [${gpsCommissioningResult!!.commissionedDeviceDescriptor.productId}]\n" +
        "vendorId [${gpsCommissioningResult!!.commissionedDeviceDescriptor.vendorId}]\n" +
        "hashCode [${gpsCommissioningResult!!.commissionedDeviceDescriptor.hashCode()}]"
    )

    // Now we need to capture the device name.
    _showNewDeviceNameAlertDialog.value = true
  }

  // Called when the device name has been captured in the UI.
  // This follows a successful gps commissioning (see gpsCommissioningDeviceSucceeded)
  fun onCommissionedDeviceNameCaptured(deviceName: String) {
    _showNewDeviceNameAlertDialog.value = false
    viewModelScope.launch {
      val nodeId = gpsCommissioningResult?.token?.toLong()!!
      // read device's vendor name and product name
      val vendorName =
        try {
          clustersHelper.readBasicClusterVendorNameAttribute(nodeId)
        } catch (ex: Exception) {
          Timber.e(ex, "Failed to read VendorName attribute")
          ""
        }

      val productName =
        try {
          clustersHelper.readBasicClusterProductNameAttribute(nodeId)
        } catch (ex: Exception) {
          Timber.e(ex, "Failed to read ProductName attribute")
          ""
        }

      try {
        val commissionedDeviceType =
          convertToAppDeviceType(
            gpsCommissioningResult?.commissionedDeviceDescriptor?.deviceType?.toLong()!!
          )
        val deviceMatterInfoList = clustersHelper.fetchDeviceMatterInfo(nodeId)
        val appEndpoints =
          deviceMatterInfoList.filter { info ->
            info.endpoint != 0 && info.serverClusters.contains(OnOffClusterId)
          }

        if (appEndpoints.isEmpty()) {
          // Fallback for devices that expose no application endpoints with On/Off cluster
          // (e.g. legacy or non-standard devices). Fall back to endpoint 1 and infer
          // capabilities from the device type reported by the commissioning result.
          val localDeviceId = devicesRepository.incrementAndReturnLastDeviceId()
          val device =
            Device.newBuilder()
              .setName(deviceName)
              .setDeviceId(localDeviceId)
              .setNodeId(nodeId)
              .setEndpoint(1)
              .setSupportsLevelControl(
                commissionedDeviceType == Device.DeviceType.TYPE_DIMMABLE_LIGHT ||
                  commissionedDeviceType == Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT ||
                  commissionedDeviceType == Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT
              )
              .setSupportsColorTemperature(
                commissionedDeviceType == Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT ||
                  commissionedDeviceType == Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT
              )
              .setDateCommissioned(getTimestampForNow())
              .setVendorId(gpsCommissioningResult?.commissionedDeviceDescriptor?.vendorId.toString())
              .setVendorName(vendorName)
              .setProductId(gpsCommissioningResult?.commissionedDeviceDescriptor?.productId.toString())
              .setProductName(productName)
              .setDeviceType(commissionedDeviceType)
              .build()
          devicesRepository.addDevice(device)
          devicesStateRepository.addDeviceState(localDeviceId, isOnline = true, isOn = false, level = 0, colorTemperature = 0)
        } else {
          appEndpoints.forEach { info ->
            val localDeviceId = devicesRepository.incrementAndReturnLastDeviceId()
            val endpointDisplayName = deviceName
            val endpointType =
              if (info.types.isNotEmpty()) convertToAppDeviceType(info.types.first()) else commissionedDeviceType
            val supportsLevel = info.serverClusters.contains(LevelControlClusterId)
            val supportsColorTemperature = info.serverClusters.contains(ColorControlClusterId)

            val device =
              Device.newBuilder()
                .setName(endpointDisplayName)
                .setDeviceId(localDeviceId)
                .setNodeId(nodeId)
                .setEndpoint(info.endpoint)
                .setSupportsLevelControl(supportsLevel)
                .setSupportsColorTemperature(supportsColorTemperature)
                .setDateCommissioned(getTimestampForNow())
                .setVendorId(gpsCommissioningResult?.commissionedDeviceDescriptor?.vendorId.toString())
                .setVendorName(vendorName)
                .setProductId(gpsCommissioningResult?.commissionedDeviceDescriptor?.productId.toString())
                .setProductName(productName)
                .setDeviceType(endpointType)
                .build()
            devicesRepository.addDevice(device)
            devicesStateRepository.addDeviceState(localDeviceId, isOnline = true, isOn = false, level = 0, colorTemperature = 0)
          }
        }
      } catch (e: Exception) {
        val title = "Adding device to app's repository failed"
        val msg = "Adding device [${nodeId}] [${deviceName}] to app's repository failed."
        Timber.e(msg, e)
        showMsgDialog(title, "$msg\n\n$e")
      }

      // update device name
      try {
        clustersHelper.writeBasicClusterNodeLabelAttribute(nodeId, deviceName)
      } catch (ex: Exception) {
        val title = "Failed to write NodeLabel"
        Timber.e(title, ex)
        showMsgDialog(title, "$ex")
      }
    }
  }

  // Called in Step 5 of the Device Commissioning flow when the GPS activity for
  // commissioning the device has failed.
  fun commissionDeviceFailed(resultCode: Int) {
    if (resultCode == 0) {
      // User simply wilfully exited from GPS commissioning.
      return
    }
    val title = "Commissioning the device failed"
    Timber.e(title)
    showMsgDialog(title, "result code: $resultCode")
  }

  fun updateDeviceStateOn(deviceId: Long, isOn: Boolean) {
    Timber.d("updateDeviceStateOn: Device [${deviceId}]  isOn [${isOn}]")
    viewModelScope.launch {
      val device = devicesRepository.getDevice(deviceId)
      val nodeId = nodeIdFor(device)
      val endpoint = endpointFor(device)
      Timber.d("Handling real device nodeId [$nodeId] endpoint [$endpoint]")
      clustersHelper.setOnOffDeviceStateOnOffCluster(nodeId, isOn, endpoint)
      val level =
        if (supportsLevelControl(device)) {
          clustersHelper.getDeviceStateLevelControlCluster(nodeId, endpoint) ?: 0
        } else {
          0
        }
      val colorTemperature =
        if (supportsColorTemperature(device)) {
          clustersHelper.getColorTemperatureColorControlCluster(nodeId, endpoint) ?: 0
        } else {
          0
        }
      devicesStateRepository.updateDeviceState(deviceId, true, isOn, level, colorTemperature)
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
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> subscribeToDevicesPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> startDevicesPeriodicPing()
    }
  }

  fun stopMonitoringStateChanges() {
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> unsubscribeToDevicesPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> stopDevicesPeriodicPing()
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Subscription to periodic device updates.
  // See:
  //   - Spec section "8.5 Subscribe Interaction"
  //   - Matter primer:
  //
  // https://developers.home.google.com/matter/primer/interaction-model-reading#subscription_transaction

  private fun subscribeToDevicesPeriodicUpdates() {
    Timber.d("subscribeToDevicesPeriodicUpdates()")
    viewModelScope.launch {
      // Only subscribe once per physical node (nodeId), using the primary endpoint device.
      val devicesList = devicesRepository.getAllDevices().devicesList
      val seenNodes = mutableSetOf<Long>()
      devicesList.sortedBy { endpointFor(it) }.forEach { device ->
        val nId = nodeIdFor(device)
        if (!seenNodes.add(nId)) return@forEach // skip non-primary endpoints
        val endpoint = endpointFor(device)
        val reportCallback =
          object : SubscriptionHelper.ReportCallbackForDevice(device.deviceId) {
            override fun onReport(nodeState: NodeState) {
              super.onReport(nodeState)
              val onOffState =
                subscriptionHelper.extractAttribute(nodeState, endpoint, OnOffAttribute) as Boolean?
              val levelState =
                subscriptionHelper.extractAttribute(nodeState, endpoint, LevelAttribute) as Int?
              val colorTemperatureState =
                subscriptionHelper.extractAttribute(nodeState, endpoint, ColorTemperatureAttribute) as Int?
              Timber.d("onOffState [${onOffState}]")
              if (onOffState == null) {
                Timber.e("onReport(): WARNING -> onOffState is NULL. Ignoring.")
                return
              }
              if (supportsLevelControl(device) && levelState == null) {
                Timber.e("onReport(): WARNING -> levelState is NULL. Ignoring.")
                return
              }
              if (supportsColorTemperature(device) && colorTemperatureState == null) {
                Timber.e("onReport(): WARNING -> colorTemperatureState is NULL. Ignoring.")
                return
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

        try {
          val connectedDevicePointer = chipClient.getConnectedDevicePointer(nId)
          subscriptionHelper.awaitSubscribeToPeriodicUpdates(
            connectedDevicePointer,
            SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(device.deviceId),
            SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(device.deviceId),
            reportCallback,
          )
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer for ${device.deviceId}.")
          return@forEach
        }
      }
    }
  }

  private fun unsubscribeToDevicesPeriodicUpdates() {
    Timber.d("unsubscribeToPeriodicUpdates()")
    viewModelScope.launch {
      // Only unsubscribe once per physical node (nodeId).
      val devicesList = devicesRepository.getAllDevices().devicesList
      val seenNodes = mutableSetOf<Long>()
      devicesList.sortedBy { endpointFor(it) }.forEach { device ->
        val nId = nodeIdFor(device)
        if (!seenNodes.add(nId)) return@forEach
        try {
          val connectedDevicePtr = chipClient.getConnectedDevicePointer(nId)
          subscriptionHelper.awaitUnsubscribeToPeriodicUpdates(connectedDevicePtr)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer for ${device.deviceId}.")
          return@forEach
        }
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Task that runs periodically to update the devices state.

  private fun startDevicesPeriodicPing() {
    if (PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS == -1) {
      return
    }
    Timber.d(
      "${LocalDateTime.now()} startDevicesPeriodicPing every $PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS seconds"
    )
    devicesPeriodicPingEnabled = true
    runDevicesPeriodicPing()
  }

  private fun runDevicesPeriodicPing() {
    viewModelScope.launch {
      while (devicesPeriodicPingEnabled) {
        // Only poll the primary device per physical node (deduplication by nodeId).
        val devicesList = devicesRepository.getAllDevices().devicesList
        val seenNodes = mutableSetOf<Long>()
        devicesList.sortedBy { endpointFor(it) }.forEach { device ->
          val nId = nodeIdFor(device)
          if (!seenNodes.add(nId)) return@forEach
          Timber.d("runDevicesPeriodicPing deviceId [${device.deviceId}]")
          val nodeId = nId
          val endpoint = endpointFor(device)
          val hasLevelControl = supportsLevelControl(device)
          val hasColorTemperature = supportsColorTemperature(device)
          var isOn = clustersHelper.getDeviceStateOnOffCluster(nodeId, endpoint)
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
          var level: Int
          var colorTemperature: Int
          val isOnline: Boolean
          if (
            isOn == null ||
              (hasLevelControl && levelRead == null) ||
              (hasColorTemperature && colorTemperatureRead == null)
          ) {
            Timber.e("runDevicesPeriodicUpdate: cannot get device state -> OFFLINE")
            isOn = false
            isOnline = false
            level = 0
            colorTemperature = 0
          } else {
            level = if (hasLevelControl) levelRead!! else 0
            colorTemperature = if (hasColorTemperature) colorTemperatureRead!! else 0
            isOnline = true
          }
          Timber.d("runDevicesPeriodicPing deviceId [${device.deviceId}] [${isOnline}] [${isOn}]")
          // TODO: only need to do it if state has changed
          devicesStateRepository.updateDeviceState(
            device.deviceId,
            isOnline = isOnline,
            isOn = isOn,
            level = level,
            colorTemperature = colorTemperature,
          )
        }
        delay(PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS * 1000L)
      }
    }
  }

  private fun stopDevicesPeriodicPing() {
    devicesPeriodicPingEnabled = false
  }

  // -----------------------------------------------------------------------------------------------
  // Device Attestation

  fun setDeviceAttestationDelegate(
    failureTimeoutSeconds: Int = DEVICE_ATTESTATION_FAILED_TIMEOUT_SECONDS
  ) {
    Timber.d("setDeviceAttestationDelegate")
    chipClient.chipDeviceController.setDeviceAttestationDelegate(failureTimeoutSeconds) {
      devicePtr,
      _,
      errorCode ->
      Timber.d(
        "Device attestation errorCode: $errorCode, " +
          "Look at 'src/credentials/attestation_verifier/DeviceAttestationVerifier.h' " +
          "AttestationVerificationResult enum to understand the errors"
      )

      if (errorCode == STATUS_PAIRING_SUCCESS) {
        Timber.d("DeviceAttestationDelegate: Success on device attestation.")
        viewModelScope.launch {
          chipClient.chipDeviceController.continueCommissioning(devicePtr, true)
        }
      } else {
        Timber.d("DeviceAttestationDelegate: Error on device attestation [$errorCode].")
        // Ideally, we'd want to show a Dialog and ask the user whether the attestation
        // failure should be ignored or not.
        // Unfortunately, the GPS commissioning API is in control at this point, and the
        // Dialog will only show up after GPS gives us back control.
        // So, we simply ignore the attestation failure for now.
        // TODO: Add a new setting to control that behavior.
        _deviceAttestationFailureIgnored.value = true
        Timber.w("Ignoring attestation failure.")
        viewModelScope.launch {
          chipClient.chipDeviceController.continueCommissioning(devicePtr, true)
        }
      }
    }
  }

  fun resetDeviceAttestationDelegate() {
    Timber.d("resetDeviceAttestationDelegate")
    chipClient.chipDeviceController.setDeviceAttestationDelegate(0, EmptyAttestationDelegate())
  }

  private class EmptyAttestationDelegate : DeviceAttestationDelegate {
    override fun onDeviceAttestationCompleted(
      devicePtr: Long,
      attestationInfo: AttestationInfo,
      errorCode: Int,
    ) {}
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  fun showMsgDialog(title: String, msg: String) {
    _msgDialogInfo.value = DialogInfo(title, msg)
  }

  // Called after user dismisses the Info dialog. If we don't consume, a config change redisplays
  // the
  // alert dialog.
  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }

  fun setMultiadminCommissioningTaskStatus(taskStatus: TaskStatus) {
    _multiadminCommissionDeviceTaskStatus.value = taskStatus
  }

  // ---------------------------------------------------------------------------
  // Companion object

  companion object {
    private const val STATUS_PAIRING_SUCCESS = 0

    /** Set for the fail-safe timer before onDeviceAttestationFailed is invoked. */
    private const val DEVICE_ATTESTATION_FAILED_TIMEOUT_SECONDS = 60
  }
}
