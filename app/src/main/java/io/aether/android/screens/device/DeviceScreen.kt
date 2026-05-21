// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.protobuf.Timestamp
import io.aether.android.Device
import io.aether.android.MatterFabricState
import io.aether.android.R
import io.aether.android.data.DevicesStateRepository
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.common.LoadingIndicator
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceRoute(
    navigateToDeviceSettings: (nodeId: Long) -> Unit,
    onBackClick: () -> Unit,
    nodeId: Long,
    deviceViewModel: DeviceViewModel = hiltViewModel(),
) {
  Timber.d("DeviceRoute nodeId [$nodeId]")

  // Observes values needed by the DeviceScreen.
  val deviceUiModel by deviceViewModel.deviceUiModel.collectAsState()
  Timber.d("DeviceRoute deviceUiModel [${deviceUiModel?.device?.nodeId}]")

  // All endpoint models for the same physical node.
  val allEndpointUiModels by deviceViewModel.allEndpointUiModels.collectAsState()

  // Controls the Msg AlertDialog.
  val msgDialogInfo by deviceViewModel.msgDialogInfo.collectAsState()
  val onDismissMsgDialog: () -> Unit = remember { { deviceViewModel.dismissMsgDialog() } }

  val lastUpdatedEndpointState by
      deviceViewModel.devicesStateRepository.lastUpdatedEndpointState.observeAsState()
  deviceViewModel.devicesStateRepository.devicesStateFlow.collectAsState(
      initial = MatterFabricState.getDefaultInstance()
  )

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
    deviceViewModel.loadDevice(nodeId)
    onPauseOrDispose { deviceViewModel.stopMonitoringStateChanges() }
  }

  LaunchedEffect(deviceUiModel?.device?.nodeId) {
    if (deviceUiModel != null) {
      deviceViewModel.startMonitoringStateChanges()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(deviceUiModel?.device?.name ?: stringResource(R.string.device_screen_title))
            },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button),
                )
              }
            },
            actions = {
              if (deviceUiModel != null) {
                IconButton(onClick = { navigateToDeviceSettings(nodeId) }) {
                  Icon(
                      imageVector = Icons.Filled.Settings,
                      contentDescription = stringResource(R.string.device_settings),
                  )
                }
              }
            },
        )
      },
  ) { innerPadding ->
    DeviceScreen(
        innerPadding,
        deviceUiModel,
        allEndpointUiModels,
        lastUpdatedEndpointState,
        onOnOffClick,
        onBrightnessChange,
        onColorTemperatureChange,
        msgDialogInfo,
        onDismissMsgDialog,
    )
  }
}

// -----------------------------------------------------------------------------------------------
// Node-level screen: renders one device-type control per endpoint.

@Composable
private fun DeviceScreen(
    innerPadding: PaddingValues,
    deviceUiModel: DeviceUiModel?,
    allEndpointUiModels: List<DeviceUiModel>,
    lastUpdatedEndpointState: DevicesStateRepository.EndpointStateSnapshot?,
    onOnOffClick: (endpointModel: DeviceUiModel, value: Boolean) -> Unit,
    onBrightnessChange: (endpointModel: DeviceUiModel, value: Int) -> Unit,
    onColorTemperatureChange: (endpointModel: DeviceUiModel, value: Int) -> Unit,
    msgDialogInfo: DialogInfo?,
    onDismissMsgDialog: () -> Unit,
) {
  if (deviceUiModel == null) {
    LoadingIndicator(stringResource(R.string.loading_device_info), innerPadding)
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
    if (!deviceUiModel.isOnline) {
      Text(
          text = stringResource(R.string.device_offline_label),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
      )
    }
    endpointsToShow.forEach { endpointModel ->
      Surface(
          modifier = Modifier.fillMaxWidth(),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
      ) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content))) {
          EndpointDeviceControl(
              endpointModel = endpointModel,
              lastUpdatedEndpointState = lastUpdatedEndpointState,
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
    lastUpdatedEndpointState: DevicesStateRepository.EndpointStateSnapshot?,
    onOnOffClick: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onColorTemperatureChange: (Int) -> Unit,
) {
  val device = endpointModel.device
  when {
    supportsColorTemperature(device) ->
        ColorTemperatureDeviceControl(
            endpointModel,
            lastUpdatedEndpointState,
            onOnOffClick,
            onBrightnessChange,
            onColorTemperatureChange,
        )
    supportsLevelControl(device) ->
        DimmableDeviceControl(
            endpointModel,
            lastUpdatedEndpointState,
            onOnOffClick,
            onBrightnessChange,
        )
    else -> OnOffDeviceControl(endpointModel, lastUpdatedEndpointState, onOnOffClick)
  }
}

// -----------------------------------------------------------------------------------------------
// Compose Previews

@Preview(widthDp = 300)
@Composable
private fun DeviceScreenOnlineOnPreview() {
  val endpointState =
      DevicesStateRepository.EndpointStateSnapshot(
          nodeId = 1L,
          endpointId = 1,
          dateCaptured = Timestamp.getDefaultInstance(),
          online = true,
          on = true,
          level = 127,
          colorTemperature = 0,
      )
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
        lastUpdatedEndpointState = endpointState,
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

private val DeviceTest =
    Device.newBuilder()
        .setNodeId(1L)
        .setDeviceType(Device.DeviceType.TYPE_OUTLET)
        .setDateCommissioned(Timestamp.getDefaultInstance())
        .setName("MyOutlet")
        .setProductId("8785")
        .setVendorId("6006")
        .setRoom("Office")
        .build()
