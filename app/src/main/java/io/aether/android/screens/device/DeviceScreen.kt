// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.protobuf.Timestamp
import io.aether.android.Device
import io.aether.android.DeviceState
import io.aether.android.DevicesState
import io.aether.android.R
import io.aether.android.formatTimestamp
import io.aether.android.nodeIdFor
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.device.action.ConfirmDeviceRemovalAlertDialog
import io.aether.android.screens.device.action.RemoveDeviceAlertDialog
import io.aether.android.screens.device.action.RemoveDeviceSection
import io.aether.android.screens.device.action.ShareDeviceAlertDialog
import io.aether.android.screens.device.action.ShareDeviceSection
import io.aether.android.screens.device.action.shareDevice
import io.aether.android.screens.device.control.ColorTemperatureDeviceControl
import io.aether.android.screens.device.control.DimmableDeviceControl
import io.aether.android.screens.device.control.OnOffDeviceControl
import io.aether.android.screens.home.DeviceUiModel
import io.aether.android.screens.thread.getActivity
import io.aether.android.supportsColorTemperature
import io.aether.android.supportsLevelControl
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
    updateActions: (@Composable RowScope.() -> Unit) -> Unit,
    navigateToHome: () -> Unit,
    navigateToInspect: (deviceId: Long) -> Unit,
    deviceId: Long,
    deviceName: String,
    deviceViewModel: DeviceViewModel = hiltViewModel(),
) {
  Timber.d("DeviceRoute deviceId [$deviceId]")

  // Launching GPS commissioning requires Activity.
  val activity = LocalContext.current.getActivity()
  var isResumed by remember { mutableStateOf(false) }

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
  val onDismissMsgDialog: () -> Unit = remember { { deviceViewModel.dismissMsgDialog() } }

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
  val devicesState by
      deviceViewModel.devicesStateRepository.devicesStateFlow.collectAsState(
          initial = DevicesState.getDefaultInstance()
      )
  val endpointOnlineByDeviceId =
      remember(devicesState) {
        devicesState.devicesStateList.associate { it.deviceId to it.online }
      }

  // Per-endpoint callbacks: each accepts the specific endpoint DeviceUiModel.
  val onOnOffClick: (endpointModel: DeviceUiModel, value: Boolean) -> Unit = remember {
    { endpointModel, value -> deviceViewModel.updateDeviceStateOn(endpointModel, value) }
  }

  val onBrightnessChange: (endpointModel: DeviceUiModel, value: Int) -> Unit = remember {
    { endpointModel, value -> deviceViewModel.updateDeviceStateLevel(endpointModel, value) }
  }

  val onColorTemperatureChange: (endpointModel: DeviceUiModel, value: Int) -> Unit = remember {
    { endpointModel, value ->
      deviceViewModel.updateDeviceStateColorTemperature(endpointModel, value)
    }
  }

  // Inspect button click handler.
  // isOnline must be provided in InspectScreen because it is updated there.
  val inspectDeviceOfflineTitle = stringResource(R.string.inspect_device_offline_title)
  val inspectDeviceOfflineMessage = stringResource(R.string.inspect_device_offline)
  val onInspect: (isOnline: Boolean) -> Unit = { isOnline ->
    if (isOnline) {
      navigateToInspect(nodeIdFor(deviceUiModel!!.device))
    } else {
      deviceViewModel.showMsgDialog(inspectDeviceOfflineTitle, inspectDeviceOfflineMessage)
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
    shareDevice(
        activity!!.applicationContext,
        shareDeviceLauncher,
        deviceViewModel,
        deviceUiModel!!.device.name,
    )
  }

  // Share Device button click.
  val onShareDevice: () -> Unit = remember {
    { deviceViewModel.openPairingWindow(deviceUiModel!!.device.deviceId) }
  }

  // When app is sent to the background, and pulled back, this kicks in.
  LifecycleResumeEffect(Unit) {
    isResumed = true
    Timber.d("LifecycleResumeEffect: deviceUiModel [${deviceUiModel?.device?.deviceId}]")
    deviceViewModel.loadDevice(deviceId)
    onPauseOrDispose {
      // do any needed clean up here
      isResumed = false
      Timber.d(
          "LifecycleResumeEffect:onPauseOrDispose deviceUiModel [${deviceUiModel?.device?.deviceId}]"
      )
      deviceViewModel.stopMonitoringStateChanges()
    }
  }

  LaunchedEffect(isResumed, deviceUiModel?.device?.deviceId) {
    if (isResumed && deviceUiModel != null) {
      deviceViewModel.startMonitoringStateChanges()
    }
  }

  // Set the title to the device name from navigation args immediately (no lag),
  // then keep it in sync once the model is loaded (e.g. after a rename).
  val defaultDeviceTitle = stringResource(R.string.device_screen_title)
  LaunchedEffect(Unit) { updateTitle(deviceName.ifBlank { defaultDeviceTitle }) }
  LaunchedEffect(deviceUiModel?.device?.name) {
    deviceUiModel?.device?.name?.let { updateTitle(it.ifBlank { defaultDeviceTitle }) }
  }

  // Rename dialog state.
  var showRenameDialog by remember { mutableStateOf(false) }
  val onRenameDeviceClick: () -> Unit = remember { { showRenameDialog = true } }

  // Set a pencil/edit action button in the TopAppBar only once the device model is loaded.
  // LaunchedEffect installs the action when the model becomes available.
  // DisposableEffect(Unit) is kept separately so onDispose only fires when leaving composition,
  // not on the null → non-null model transition.
  LaunchedEffect(deviceUiModel != null) {
    if (deviceUiModel != null) {
      updateActions {
        IconButton(onClick = onRenameDeviceClick) {
          Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = stringResource(R.string.rename_device),
          )
        }
      }
    }
  }
  DisposableEffect(Unit) { onDispose { updateActions {} } }

  if (showRenameDialog) {
    RenameDeviceDialog(
        currentName = deviceUiModel?.device?.name ?: deviceName,
        onConfirm = { newName ->
          deviceViewModel.renameDevice(deviceId, newName)
          showRenameDialog = false
        },
        onDismiss = { showRenameDialog = false },
    )
  }

  DeviceScreen(
      innerPadding,
      deviceUiModel,
      allEndpointUiModels,
      lastUpdatedDeviceState,
      endpointOnlineByDeviceId,
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

// -----------------------------------------------------------------------------------------------
// Node-level screen: renders one device-type control per endpoint plus node-level actions.

@Composable
private fun DeviceScreen(
    innerPadding: PaddingValues,
    deviceUiModel: DeviceUiModel?,
    allEndpointUiModels: List<DeviceUiModel>,
    lastUpdatedDeviceState: DeviceState?,
    endpointOnlineByDeviceId: Map<Long, Boolean>,
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
  var showShareDeviceAlertDialog by remember { mutableStateOf(false) }

  if (deviceUiModel == null) {
    Text(stringResource(R.string.loading_device_info))
    return
  }

  // The various AlertDialog's that may pop up to inform the user of important information.
  MsgAlertDialog(msgDialogInfo, onDismissMsgDialog)
  RemoveDeviceAlertDialog(showRemoveDeviceAlertDialog, onRemoveDeviceOutcome)
  ConfirmDeviceRemovalAlertDialog(
      showConfirmDeviceRemovalAlertDialog,
      onConfirmDeviceRemovalOutcome,
  )
  ShareDeviceAlertDialog(
      showShareDeviceAlertDialog,
      onConfirm = {
        showShareDeviceAlertDialog = false
        onShareDevice()
      },
      onDismiss = { showShareDeviceAlertDialog = false },
  )

  val endpointsToShow = allEndpointUiModels.ifEmpty { listOf(deviceUiModel) }

  // Derive whether any endpoint is currently online from repository-backed state.
  val anyOnline =
      remember(endpointsToShow, endpointOnlineByDeviceId) {
        endpointsToShow.any { ep -> endpointOnlineByDeviceId[ep.device.deviceId] ?: ep.isOnline }
      }

  Column(
      modifier =
          Modifier.fillMaxWidth()
              .padding(innerPadding)
              .verticalScroll(rememberScrollState())
              .padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    endpointsToShow.forEach { endpointModel ->
      Surface(
          modifier = Modifier.fillMaxWidth(),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
      ) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content))) {
          EndpointDeviceControl(
              endpointModel = endpointModel,
              lastUpdatedDeviceState = lastUpdatedDeviceState,
              onOnOffClick = { value -> onOnOffClick(endpointModel, value) },
              onBrightnessChange = { value -> onBrightnessChange(endpointModel, value) },
              onColorTemperatureChange = { value ->
                onColorTemperatureChange(endpointModel, value)
              },
          )
        }
      }
    }
    TechnicalInfoSection(deviceUiModel.device, onInspect, anyOnline)
    ShareDeviceSection { showShareDeviceAlertDialog = true }
    RemoveDeviceSection(onRemoveDeviceClick)
  }
}

// -----------------------------------------------------------------------------------------------
// Endpoint dispatcher: selects the device-type control that matches the endpoint's capabilities.

@Composable
private fun EndpointDeviceControl(
    endpointModel: DeviceUiModel,
    lastUpdatedDeviceState: DeviceState?,
    onOnOffClick: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onColorTemperatureChange: (Int) -> Unit,
) {
  val device = endpointModel.device
  when {
    supportsColorTemperature(device) ->
        ColorTemperatureDeviceControl(
            endpointModel,
            lastUpdatedDeviceState,
            onOnOffClick,
            onBrightnessChange,
            onColorTemperatureChange,
        )
    supportsLevelControl(device) ->
        DimmableDeviceControl(
            endpointModel,
            lastUpdatedDeviceState,
            onOnOffClick,
            onBrightnessChange,
        )
    else -> OnOffDeviceControl(endpointModel, lastUpdatedDeviceState, onOnOffClick)
  }
}

// -----------------------------------------------------------------------------------------------
// Technical info section (node level).

@Composable
private fun TechnicalInfoSection(
    device: Device,
    onInspect: (isOnline: Boolean) -> Unit,
    isOnline: Boolean,
) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
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

// -----------------------------------------------------------------------------------------------
// Rename Device Dialog

@Composable
private fun RenameDeviceDialog(
    currentName: String,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
  var inputText by remember(currentName) { mutableStateOf(currentName) }

  AlertDialog(
      title = { Text(stringResource(R.string.rename_device)) },
      text = {
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text(stringResource(R.string.rename_device_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
      },
      confirmButton = {
        Button(
            onClick = { onConfirm(inputText.trim()) },
            enabled = inputText.trim().isNotBlank(),
        ) {
          Text(stringResource(R.string.ok))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
      onDismissRequest = onDismiss,
  )
}

// -----------------------------------------------------------------------------------------------
// Compose Previews

@Preview(widthDp = 300)
@Composable
private fun TechnicalInfoSectionPreview() {
  MaterialTheme { TechnicalInfoSection(DeviceTest, {}, true) }
}

@Preview(widthDp = 300)
@Composable
private fun DeviceScreenOnlineOnPreview() {
  val deviceState = DeviceState_OnlineOn
  val device = DeviceTest
  val deviceUiModel = DeviceUiModel(device, true, true, level = 127)
  val onOnOffClick: (endpointModel: DeviceUiModel, value: Boolean) -> Unit = { _, value ->
    Timber.d("deviceUiModel [$deviceUiModel] value [$value]")
  }
  val onBrightnessChange: (endpointModel: DeviceUiModel, value: Int) -> Unit = { _, value ->
    Timber.d("deviceUiModel [$deviceUiModel] value [$value]")
  }
  val onColorTemperatureChange: (endpointModel: DeviceUiModel, value: Int) -> Unit = { _, value ->
    Timber.d("deviceUiModel [$deviceUiModel] value [$value]")
  }
  MaterialTheme {
    DeviceScreen(
        innerPadding = PaddingValues(),
        deviceUiModel = deviceUiModel,
        allEndpointUiModels = listOf(deviceUiModel),
        lastUpdatedDeviceState = deviceState,
        endpointOnlineByDeviceId = mapOf(deviceUiModel.device.deviceId to true),
        onOnOffClick = onOnOffClick,
        onBrightnessChange = onBrightnessChange,
        onColorTemperatureChange = onColorTemperatureChange,
        onRemoveDeviceClick = {},
        onShareDevice = {},
        onInspect = {},
        msgDialogInfo = null,
        onDismissMsgDialog = {},
        showRemoveDeviceAlertDialog = false,
        onRemoveDeviceOutcome = {},
        showConfirmDeviceRemovalAlertDialog = false,
        onConfirmDeviceRemovalOutcome = {},
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
