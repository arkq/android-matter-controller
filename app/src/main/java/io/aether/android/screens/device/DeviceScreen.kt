// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.device.control.ColorTemperatureDeviceControl
import io.aether.android.screens.device.control.DimmableDeviceControl
import io.aether.android.screens.device.control.OnOffDeviceControl
import io.aether.android.screens.home.DeviceUiModel
import io.aether.android.supportsColorTemperature
import io.aether.android.supportsLevelControl
import timber.log.Timber

/**
 * The Device Screen shows all the information about the device that was selected in the Home
 * screen. It supports the following actions:
 * ```
 * - toggle the on/off state of the device
 * - navigate to device settings (gear icon in the title bar)
 * ```
 *
 * When the screen is shown, state monitoring is activated to get the device's latest state. This
 * makes it possible to update the device's online status dynamically.
 */
@Composable
internal fun DeviceRoute(
    innerPadding: PaddingValues,
    updateActions: (@Composable RowScope.() -> Unit) -> Unit,
    navigateToDeviceSettings: (deviceId: Long) -> Unit,
    deviceId: Long,
    deviceViewModel: DeviceViewModel = hiltViewModel(),
) {
  Timber.d("DeviceRoute deviceId [$deviceId]")

  // Observes values needed by the DeviceScreen.
  val deviceUiModel by deviceViewModel.deviceUiModel.collectAsState()
  Timber.d("DeviceRoute deviceUiModel [${deviceUiModel?.device?.deviceId}]")

  // All endpoint models for the same physical node.
  val allEndpointUiModels by deviceViewModel.allEndpointUiModels.collectAsState()

  // Controls the Msg AlertDialog.
  val msgDialogInfo by deviceViewModel.msgDialogInfo.collectAsState()
  val onDismissMsgDialog: () -> Unit = remember { { deviceViewModel.dismissMsgDialog() } }

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

  // Per-endpoint callbacks.
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

  // When app is sent to the background, and pulled back, this kicks in.
  LifecycleResumeEffect(Unit) {
    deviceViewModel.loadDevice(deviceId)
    onPauseOrDispose { deviceViewModel.stopMonitoringStateChanges() }
  }

  LaunchedEffect(deviceUiModel?.device?.deviceId) {
    if (deviceUiModel != null) {
      deviceViewModel.startMonitoringStateChanges()
    }
  }

  // Set gear icon in the TopAppBar once the device model is loaded.
  LaunchedEffect(deviceUiModel != null) {
    if (deviceUiModel != null) {
      updateActions {
        IconButton(onClick = { navigateToDeviceSettings(deviceId) }) {
          Icon(
              imageVector = Icons.Filled.Settings,
              contentDescription = stringResource(R.string.device_settings),
          )
        }
      }
    }
  }
  DisposableEffect(Unit) { onDispose { updateActions {} } }

  DeviceScreen(
      innerPadding,
      deviceUiModel,
      allEndpointUiModels,
      lastUpdatedDeviceState,
      endpointOnlineByDeviceId,
      onOnOffClick,
      onBrightnessChange,
      onColorTemperatureChange,
      msgDialogInfo,
      onDismissMsgDialog,
  )
}

// -----------------------------------------------------------------------------------------------
// Node-level screen: renders one device-type control per endpoint.

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
    msgDialogInfo: DialogInfo?,
    onDismissMsgDialog: () -> Unit,
) {
  if (deviceUiModel == null) {
    Text(stringResource(R.string.loading_device_info))
    return
  }

  MsgAlertDialog(msgDialogInfo, onDismissMsgDialog)

  val endpointsToShow = allEndpointUiModels.ifEmpty { listOf(deviceUiModel) }

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
// Compose Previews

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
        msgDialogInfo = null,
        onDismissMsgDialog = {},
    )
  }
}

// -----------------------------------------------------------------------------------------------
// Constant objects used in Compose Preview

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
