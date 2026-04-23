// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningWindow
import com.google.android.gms.home.matter.commissioning.ShareDeviceRequest
import com.google.android.gms.home.matter.common.DeviceDescriptor
import com.google.android.gms.home.matter.common.Discriminator
import io.aether.android.DISCRIMINATOR
import io.aether.android.Device
import io.aether.android.DeviceState
import io.aether.android.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import io.aether.android.R
import io.aether.android.SETUP_PIN_CODE
import io.aether.android.formatTimestamp
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.home.DeviceUiModel
import io.aether.android.screens.thread.getActivity
import io.aether.android.stateDisplayString
import com.google.protobuf.Timestamp
import timber.log.Timber

/**
 * The Device Screen shows all the information about the device that was selected in the Home
 * screen. It supports the following actions:
 * ```
 * - toggle the on/off state of the device
 * - share the device with another Matter commissioner app
 * - remove the device
 * - inspect the device (get all info we can from the clusters supported by the device)
 * ```
 *
 * When the screen is shown, state monitoring is activated to get the device's latest state. This
 * makes it possible to update the device's online status dynamically.
 */
@Composable
internal fun DeviceRoute(
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  navigateToHome: () -> Unit,
  navigateToInspect: (deviceId: Long) -> Unit,
  deviceId: Long,
  deviceViewModel: DeviceViewModel = hiltViewModel(),
) {
  Timber.d("DeviceRoute deviceId [$deviceId]")

  // Launching GPS commissioning requires Activity.
  val activity = LocalContext.current.getActivity()

  // Observes values needed by the DeviceScreen.
  val deviceUiModel by deviceViewModel.deviceUiModel.collectAsState()
  Timber.d("DeviceRoute deviceUiModel [${deviceUiModel?.device?.deviceId}]")

  // All endpoint models for the same physical node.
  val allEndpointUiModels by deviceViewModel.allEndpointUiModels.collectAsState()

  // When the device has been removed by the ViewModel, navigate back to the Home screen.
  val deviceRemovalCompleted by deviceViewModel.deviceRemovalCompleted.collectAsState()
  if (deviceRemovalCompleted) {
    navigateToHome()
    deviceViewModel.resetDeviceRemovalCompleted()
  }

  // Controls the Msg AlertDialog.
  // When the user dismisses the Msg AlertDialog, we "consume" the dialog.
  val msgDialogInfo by deviceViewModel.msgDialogInfo.collectAsState()
  val onDismissMsgDialog: () -> Unit = remember {
    { deviceViewModel.dismissMsgDialog() }
  }

  // Controls whether the "remove device" alert dialog should be shown.
  val showRemoveDeviceAlertDialog by deviceViewModel.showRemoveDeviceAlertDialog.collectAsState()
  val onRemoveDeviceClick: () -> Unit = remember {
    { deviceViewModel.showRemoveDeviceAlertDialog() }
  }
  val onRemoveDeviceOutcome: (doIt: Boolean) -> Unit = remember {
    { doIt ->
      deviceViewModel.dismissRemoveDeviceDialog()
      if (doIt) {
        deviceViewModel.removeDevice(deviceUiModel!!.device.deviceId)
      }
    }
  }

  // Controls whether the "confirm device removal" alert dialog should be shown.
  val showConfirmDeviceRemovalAlertDialog by
    deviceViewModel.showConfirmDeviceRemovalAlertDialog.collectAsState()
  val onConfirmDeviceRemovalOutcome: (doIt: Boolean) -> Unit = remember {
    { doIt ->
      deviceViewModel.dismissConfirmDeviceRemovalDialog()
      if (doIt) {
        deviceViewModel.removeDeviceWithoutUnlink(deviceUiModel!!.device.deviceId)
      }
    }
  }

  val lastUpdatedDeviceState by
    deviceViewModel.devicesStateRepository.lastUpdatedDeviceState.observeAsState()

  // Per-endpoint callbacks: each accepts the specific endpoint DeviceUiModel.
  val onOnOffClick: (endpointModel: DeviceUiModel, value: Boolean) -> Unit = remember {
    { endpointModel, value ->
      deviceViewModel.updateDeviceStateOn(endpointModel, value)
    }
  }

  val onBrightnessChange: (endpointModel: DeviceUiModel, value: Int) -> Unit = remember {
    { endpointModel, value ->
      deviceViewModel.updateDeviceStateLevel(endpointModel, value)
    }
  }

  val onColorTemperatureChange: (endpointModel: DeviceUiModel, value: Int) -> Unit = remember {
    { endpointModel, value ->
      deviceViewModel.updateDeviceStateColorTemperature(endpointModel, value)
    }
  }

  // Inspect button click handler.
  // isOnline must be provided in InspectScreen because it is updated there.
  val onInspect: (isOnline: Boolean) -> Unit = remember {
    { isOnline ->
      if (isOnline) {
        val nodeId =
          if (deviceUiModel!!.device.nodeId != 0L) {
            deviceUiModel!!.device.nodeId
          } else {
            deviceUiModel!!.device.deviceId
          }
        navigateToInspect(nodeId)
      } else {
        deviceViewModel.showMsgDialog(
          "Inspect Device",
          "Device is offline, so it cannot be inspected.",
        )
      }
    }
  }

  // The device sharing flow involves multiple steps as it is based on an Activity
  // that is launched on the Google Play Services (GPS).
  // Step 1 (here) is where an activity launcher is registered.
  // At step 2, the user triggers the "Share Device" action by clicking on the
  // "Share" button on this screen. This creates the proper IntentSender that is then
  // used in step 3 to call shareDeviceLauncher.launch().
  // Step 4 is when GPS takes over the sharing flow.
  // Step 5 is when the GPS activity completes and the result is handled here.
  val shareDeviceLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
      // Commission Device Step 5.
      // The Share Device activity in GPS (step 4) has completed.
      val resultCode = result.resultCode
      if (resultCode == Activity.RESULT_OK) {
        deviceViewModel.shareDeviceSucceeded()
      } else {
        deviceViewModel.shareDeviceFailed(resultCode)
      }
    }
  // When the pairing window has been open for device sharing.
  val pairingWindowOpenForDeviceSharing by
    deviceViewModel.pairingWindowOpenForDeviceSharing.collectAsState()
  if (pairingWindowOpenForDeviceSharing) {
    deviceViewModel.resetPairingWindowOpenForDeviceSharing()
    shareDevice(activity!!.applicationContext, shareDeviceLauncher, deviceViewModel)
  }

  // Share Device button click.
  val onShareDevice: () -> Unit = remember {
    {
      deviceViewModel.openPairingWindow(deviceUiModel!!.device.deviceId)
    }
  }

  // When app is sent to the background, and pulled back, this kicks in.
  LifecycleResumeEffect {
    Timber.d("LifecycleResumeEffect: deviceUiModel [${deviceUiModel?.device?.deviceId}]")
    deviceViewModel.loadDevice(deviceId)
    deviceViewModel.startMonitoringStateChanges()
    onPauseOrDispose {
      // do any needed clean up here
      Timber.d(
        "LifecycleResumeEffect:onPauseOrDispose deviceUiModel [${deviceUiModel?.device?.deviceId}]"
      )
      deviceViewModel.stopMonitoringStateChanges()
    }
  }

  LaunchedEffect(Unit) {
    updateTitle("Device")
  }

  DeviceScreen(
    innerPadding,
    deviceUiModel,
    allEndpointUiModels,
    lastUpdatedDeviceState,
    onOnOffClick,
    onBrightnessChange,
    onColorTemperatureChange,
    onRemoveDeviceClick,
    onShareDevice,
    onInspect,
    msgDialogInfo,
    onDismissMsgDialog,
    showRemoveDeviceAlertDialog,
    onRemoveDeviceOutcome,
    showConfirmDeviceRemovalAlertDialog,
    onConfirmDeviceRemovalOutcome,
  )
}

@Composable
private fun DeviceScreen(
  innerPadding: PaddingValues,
  deviceUiModel: DeviceUiModel?,
  allEndpointUiModels: List<DeviceUiModel>,
  lastUpdatedDeviceState: DeviceState?,
  onOnOffClick: (endpointModel: DeviceUiModel, value: Boolean) -> Unit,
  onBrightnessChange: (endpointModel: DeviceUiModel, value: Int) -> Unit,
  onColorTemperatureChange: (endpointModel: DeviceUiModel, value: Int) -> Unit,
  onRemoveDeviceClick: () -> Unit,
  onShareDevice: () -> Unit,
  onInspect: (isOnline: Boolean) -> Unit,
  msgDialogInfo: DialogInfo?,
  onDismissMsgDialog: () -> Unit,
  showRemoveDeviceAlertDialog: Boolean,
  onRemoveDeviceOutcome: (Boolean) -> Unit,
  showConfirmDeviceRemovalAlertDialog: Boolean,
  onConfirmDeviceRemovalOutcome: (Boolean) -> Unit,
) {
  if (deviceUiModel == null) {
    Text("Still loading the device information")
    return
  }

  // The various AlertDialog's that may pop up to inform the user of important information.
  MsgAlertDialog(msgDialogInfo, onDismissMsgDialog)
  RemoveDeviceAlertDialog(showRemoveDeviceAlertDialog, onRemoveDeviceOutcome)
  ConfirmDeviceRemovalAlertDialog(
    showConfirmDeviceRemovalAlertDialog,
    onConfirmDeviceRemovalOutcome,
  )

  // Determine whether to use the endpoint list or fall back to the single primary model.
  val endpointsToShow = allEndpointUiModels.ifEmpty { listOf(deviceUiModel) }
  val showEndpointLabel = endpointsToShow.size > 1

  // Track live online status per endpoint so `anyOnline` stays accurate as updates arrive.
  // The map is keyed by deviceId and seeded from the loaded DeviceUiModel values.
  var endpointOnlineStates by remember(endpointsToShow) {
    mutableStateOf(endpointsToShow.associate { it.device.deviceId to it.isOnline })
  }
  LaunchedEffect(endpointsToShow, lastUpdatedDeviceState) {
    val updatedDeviceState = lastUpdatedDeviceState
    if (updatedDeviceState != null &&
      endpointsToShow.any { it.device.deviceId == updatedDeviceState.deviceId }
    ) {
      endpointOnlineStates = endpointOnlineStates.toMutableMap().apply {
        put(updatedDeviceState.deviceId, updatedDeviceState.online)
      }
    }
  }
  val anyOnline = endpointsToShow.any { endpointModel ->
    endpointOnlineStates[endpointModel.device.deviceId] ?: endpointModel.isOnline
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(innerPadding)
      .verticalScroll(rememberScrollState())
  ) {
    // Render one endpoint control section per endpoint.
    endpointsToShow.forEach { endpointModel ->
      EndpointControlSection(
        endpointModel = endpointModel,
        showEndpointLabel = showEndpointLabel,
        lastUpdatedDeviceState = lastUpdatedDeviceState,
        onOnOffClick = { value -> onOnOffClick(endpointModel, value) },
        onBrightnessChange = { value -> onBrightnessChange(endpointModel, value) },
        onColorTemperatureChange = { value -> onColorTemperatureChange(endpointModel, value) },
      )
    }
    // Shared sections (node level).
    ShareSection(name = deviceUiModel.device.name, onShareDevice)
    Spacer(modifier = Modifier)
    TechnicalInfoSection(deviceUiModel.device, onInspect, anyOnline)
    RemoveDeviceSection(onRemoveDeviceClick)
  }
}

/**
 * A framed section showing all controls for a single endpoint grouped in one Surface,
 * similar in style to [ShareSection] and [TechnicalInfoSection].
 * The label "Endpoint N" is shown when the device has multiple endpoints.
 */
@Composable
private fun EndpointControlSection(
  endpointModel: DeviceUiModel,
  showEndpointLabel: Boolean,
  lastUpdatedDeviceState: DeviceState?,
  onOnOffClick: (Boolean) -> Unit,
  onBrightnessChange: (Int) -> Unit,
  onColorTemperatureChange: (Int) -> Unit,
) {
  val brightnessMax = 254f
  val colorTemperatureMax = 1667f

  var isOnline by remember(endpointModel) { mutableStateOf(endpointModel.isOnline) }
  var isOn by remember(endpointModel) { mutableStateOf(endpointModel.isOn) }
  var brightness by remember(endpointModel) { mutableFloatStateOf(endpointModel.level / brightnessMax) }
  var colorTemperature by remember(endpointModel) { mutableFloatStateOf(endpointModel.colorTemperature / colorTemperatureMax) }

  // Respond to live state updates for this specific endpoint's device.
  LaunchedEffect(endpointModel, lastUpdatedDeviceState) {
    if (lastUpdatedDeviceState != null &&
      lastUpdatedDeviceState.deviceId == endpointModel.device.deviceId
    ) {
      isOnline = lastUpdatedDeviceState.online
      isOn = lastUpdatedDeviceState.on
      brightness = lastUpdatedDeviceState.level / brightnessMax
      colorTemperature = lastUpdatedDeviceState.colorTemperature / colorTemperatureMax
    } else if (lastUpdatedDeviceState == null) {
      isOnline = endpointModel.isOnline
      isOn = endpointModel.isOn
      brightness = endpointModel.level / brightnessMax
      colorTemperature = endpointModel.colorTemperature / colorTemperatureMax
    }
  }

  val supportsLevelControl =
    endpointModel.device.supportsLevelControl ||
      endpointModel.device.deviceType == Device.DeviceType.TYPE_DIMMABLE_LIGHT ||
      endpointModel.device.deviceType == Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT ||
      endpointModel.device.deviceType == Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT
  val supportsColorTemperature =
    endpointModel.device.supportsColorTemperature ||
      endpointModel.device.deviceType == Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT ||
      endpointModel.device.deviceType == Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT

  val bgColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface
  val contentColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface

  Surface(
    modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    color = bgColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Column(
      modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content))
    ) {
      if (showEndpointLabel) {
        Text(
          text = "Endpoint ${endpointModel.device.endpoint}",
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(bottom = 4.dp),
        )
      }
      // On/Off row
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = stateDisplayString(isOnline, isOn),
          style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.weight(1f))
        Switch(
          checked = isOn,
          onCheckedChange = { value ->
            onOnOffClick(value)
          },
        )
      }
      // Brightness slider
      if (supportsLevelControl) {
        Text(
          text = stringResource(R.string.brightness),
          modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
          enabled = isOnline && isOn,
          value = brightness,
          onValueChange = { brightness = it },
          onValueChangeFinished = {
            val brightnessVal = (brightness * brightnessMax).toInt()
            onBrightnessChange(brightnessVal)
          },
          valueRange = 0f..1f,
        )
        Text(
          text = (brightness * 100).toInt().toString(),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      // Color temperature slider
      if (supportsColorTemperature) {
        Text(
          text = stringResource(R.string.color_temperature),
          modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
          enabled = isOnline && isOn,
          value = colorTemperature,
          onValueChange = { colorTemperature = it },
          onValueChangeFinished = {
            val colorTemperatureVal = (colorTemperature * colorTemperatureMax).toInt()
            onColorTemperatureChange(colorTemperatureVal)
          },
          valueRange = 0f..1f,
        )
        Text(
          text = (colorTemperature * 100).toInt().toString(),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun OnOffStateSection(
  isOnline: Boolean,
  isOn: Boolean,
  onStateChange: ((Boolean) -> Unit)?,
) {
  val bgColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface
  val contentColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface
  val text = stateDisplayString(isOnline, isOn)
  Surface(
    modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    contentColor = contentColor,
    color = bgColor,
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content)),
    ) {
      Text(text = text, style = MaterialTheme.typography.bodyLarge)
      Spacer(Modifier.weight(1f))
      Switch(checked = isOn, onCheckedChange = onStateChange)
    }
  }
}

@Composable
private fun LevelControl(
  title: String,
  isOnline: Boolean,
  isOn: Boolean,
  level: Float,
  onStateChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit,
) {
  Surface(
    modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Column (
      modifier = Modifier
        .padding(dimensionResource(R.dimen.padding_surface_content))
    ) {
      Text(text = title)
      Slider(
        enabled = isOnline && isOn,
        value = level,
        onValueChange = onStateChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = 0f..1f,
      )
      Text(
        (level * 100).toInt().toString(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

@Composable
private fun ShareSection(name: String, onShareDevice: () -> Unit) {
  Surface(
    modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Column(modifier = Modifier.padding(8.dp)) {
      Text(
        text = stringResource(R.string.share_device_name, name),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = stringResource(R.string.share_device_body),
        style = MaterialTheme.typography.bodySmall,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onShareDevice) { Text(stringResource(R.string.share)) }
      }
    }
  }
}

@Composable
private fun TechnicalInfoSection(
  device: Device,
  onInspect: (isOnline: Boolean) -> Unit,
  isOnline: Boolean,
) {
  Surface(
    modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Column(modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal))) {
      Text(
        text = stringResource(R.string.technical_information),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier,
      )
      Text(
        text =
          stringResource(
            R.string.share_device_info,
            formatTimestamp(device.dateCommissioned, null),
            device.deviceId.toString(),
            device.vendorName,
            device.vendorId,
            device.productName,
            device.productId,
            device.deviceType,
          ),
        style = MaterialTheme.typography.bodySmall,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onInspect(isOnline) }) { Text(stringResource(R.string.inspect)) }
      }
    }
  }
}

@Composable
private fun RemoveDeviceSection(onClick: () -> Unit) {
  Row {
    TextButton(onClick = onClick) {
      Icon(Icons.Outlined.Delete, contentDescription = "Localized description")
      Text(stringResource(R.string.remove_device).uppercase())
    }
  }
}

@Composable
private fun RemoveDeviceAlertDialog(
  showRemoveDeviceAlertDialog: Boolean,
  onRemoveDeviceOutcome: (doIt: Boolean) -> Unit,
) {
  Timber.d("RemoveDeviceAlertDialog [$showRemoveDeviceAlertDialog]")
  if (!showRemoveDeviceAlertDialog) {
    return
  }

  AlertDialog(
    title = { Text(text = "Remove this device?") },
    text = {
      Text(
        "This device will be removed and unlinked from this sample app. " +
          "Other services and connection-types may still have access."
      )
    },
    confirmButton = {
      Button(onClick = { onRemoveDeviceOutcome(true) }) {
        Text(stringResource(R.string.yes_remove_it))
      }
    },
    onDismissRequest = {},
    dismissButton = {
      Button(onClick = { onRemoveDeviceOutcome(false) }) { Text(stringResource(R.string.cancel)) }
    },
  )
}

@Composable
private fun ConfirmDeviceRemovalAlertDialog(
  showConfirmDeviceRemovalAlertDialog: Boolean,
  onConfirmDeviceRemovalOutcome: (doIt: Boolean) -> Unit,
) {
  if (!showConfirmDeviceRemovalAlertDialog) {
    return
  }

  var showDialog by remember { mutableStateOf(false) }

  AlertDialog(
    title = { Text(text = "Error removing the fabric from the device") },
    text = {
      Text(
        "Removing the fabric from the device failed. " +
          "Do you still want to remove the device from the application?"
      )
    },
    confirmButton = {
      Button(
        onClick = {
          showDialog = false
          onConfirmDeviceRemovalOutcome(true)
        }
      ) {
        Text(stringResource(R.string.yes_remove_it))
      }
    },
    onDismissRequest = {},
    dismissButton = {
      Button(
        onClick = {
          showDialog = false
          onConfirmDeviceRemovalOutcome(false)
        }
      ) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

// ---------------------------------------------------------------------------
// Share Device

fun shareDevice(
  context: Context,
  shareDeviceLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
  deviceViewModel: DeviceViewModel,
) {
  Timber.d("ShareDevice: starting")

  val shareDeviceRequest =
    ShareDeviceRequest.builder()
      .setDeviceDescriptor(DeviceDescriptor.builder().build())
      .setDeviceName("Æther temp device name")
      .setCommissioningWindow(
        CommissioningWindow.builder()
          .setDiscriminator(Discriminator.forLongValue(DISCRIMINATOR))
          .setPasscode(SETUP_PIN_CODE)
          .setWindowOpenMillis(SystemClock.elapsedRealtime())
          .setDurationSeconds(OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS.toLong())
          .build()
      )
      .build()
  Timber.d(
    "ShareDevice: shareDeviceRequest " +
      "onboardingPayload [${shareDeviceRequest.commissioningWindow.passcode}] " +
      "discriminator [${shareDeviceRequest.commissioningWindow.discriminator}]"
  )

  // The call to shareDevice() creates the IntentSender that will eventually be launched
  // in the fragment to trigger the multi-admin activity in GPS (step 3).
  Matter.getCommissioningClient(context)
    .shareDevice(shareDeviceRequest)
    .addOnSuccessListener { result ->
      Timber.d("ShareDevice: Success getting the IntentSender: result [${result}]")
      shareDeviceLauncher.launch(IntentSenderRequest.Builder(result).build())
    }
    .addOnFailureListener { error ->
      Timber.e(error)
      deviceViewModel.showMsgDialog("Share device failed", error.toString())
    }
}

// -----------------------------------------------------------------------------------------------
// Compose Previews

@Preview(widthDp = 300)
@Composable
private fun OnOffStateSection_OnlineOn() {
  MaterialTheme { OnOffStateSection(isOnline = true, isOn = true)
    { Timber.d("OnOff state changed to $it") }
  }
}

@Preview(widthDp = 300)
@Composable
private fun BrightnessControl_50() {
  MaterialTheme {
    LevelControl(
      title = stringResource(R.string.brightness),
      isOnline = true,
      isOn = true,
      level = 0.45f,
      onStateChange = { Timber.d("Brightness changed to $it") },
      onValueChangeFinished = { Timber.d("Brightness change finished") }
    )
  }
}

@Preview(widthDp = 300)
@Composable
private fun OnOffStateSection_Offline() {
  MaterialTheme { OnOffStateSection(false, true, { Timber.d("OnOff state changed to $it") }) }
}

@Preview(widthDp = 300)
@Composable
private fun ShareSectionPreview() {
  MaterialTheme { ShareSection("Lightbulb", {}) }
}

@Preview(widthDp = 300)
@Composable
private fun TechnicalInfoSectionPreview() {
  MaterialTheme { TechnicalInfoSection(DeviceTest, {}, true) }
}

@Preview
@Composable
private fun RemoveDeviceSectionPreview() {
  MaterialTheme { RemoveDeviceSection({ Timber.d("preview", "button clicked") }) }
}

@Preview(widthDp = 300)
@Composable
private fun DeviceScreenOnlineOnPreview() {
  val deviceState = DeviceState_OnlineOn
  val device = DeviceTest
  val deviceUiModel = DeviceUiModel(device, true, true, level = 127)
  val onOnOffClick: (endpointModel: DeviceUiModel, value: Boolean) -> Unit =
    { _, value ->
      Timber.d("deviceUiModel [$deviceUiModel] value [$value]")
    }
  val onBrightnessChange: (endpointModel: DeviceUiModel, value: Int) -> Unit =
    { _, value ->
      Timber.d("deviceUiModel [$deviceUiModel] value [$value]")
    }
  val onColorTemperatureChange: (endpointModel: DeviceUiModel, value: Int) -> Unit =
    { _, value ->
      Timber.d("deviceUiModel [$deviceUiModel] value [$value]")
    }
  MaterialTheme {
    DeviceScreen(
      PaddingValues(),
      deviceUiModel,
      listOf(deviceUiModel),
      deviceState,
      onOnOffClick,
      onBrightnessChange,
      onColorTemperatureChange,
      {},
      {},
      {},
      null,
      {},
      false,
      {},
      false,
      {},
    )
  }
}

// -----------------------------------------------------------------------------------------------
// Constant objects used in Compose Preview

// DeviceState -- Online and On
private val DeviceState_OnlineOn =
  DeviceState.newBuilder()
    .setDateCaptured(Timestamp.getDefaultInstance())
    .setDeviceId(1L)
    .setOn(true)
    .setOnline(true)
    .build()

// DeviceState -- Offline
private val DeviceState_Offline =
  DeviceState.newBuilder()
    .setDateCaptured(Timestamp.getDefaultInstance())
    .setDeviceId(1L)
    .setOn(false)
    .setOnline(false)
    .build()

private val DeviceTest =
  Device.newBuilder()
    .setDeviceId(1L)
    .setDeviceType(Device.DeviceType.TYPE_OUTLET)
    .setDateCommissioned(Timestamp.getDefaultInstance())
    .setName("MyOutlet")
    .setProductId("8785")
    .setVendorId("6006")
    .setRoom("Office")
    .build()
