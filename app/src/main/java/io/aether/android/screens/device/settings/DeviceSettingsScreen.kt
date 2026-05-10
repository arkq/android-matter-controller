// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.aether.android.Device
import io.aether.android.R
import io.aether.android.chip.vendorLabel
import io.aether.android.formatNodeId
import io.aether.android.formatProductId
import io.aether.android.formatTimestamp
import io.aether.android.getDeviceTypeDisplayStringId
import io.aether.android.nodeIdFor
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.device.action.ConfirmDeviceRemovalAlertDialog
import io.aether.android.screens.device.action.RemoveDeviceAlertDialog
import io.aether.android.screens.device.action.ShareDeviceAlertDialog
import io.aether.android.screens.device.action.shareDevice
import io.aether.android.screens.thread.getActivity
import timber.log.Timber

/** Route composable for the Device Settings screen. Wires up the ViewModel and navigation. */
@Composable
fun DeviceSettingsRoute(
    innerPadding: PaddingValues,
    navigateToHome: () -> Unit,
    navigateToInspect: (nodeId: Long) -> Unit,
    navigateToControllers: (deviceId: Long) -> Unit,
    deviceId: Long,
    viewModel: DeviceSettingsViewModel = hiltViewModel(),
) {
  Timber.d("DeviceSettingsRoute: deviceId [$deviceId]")

  val activity = LocalContext.current.getActivity()

  val device by viewModel.device.collectAsState()
  val hardwareVersion by viewModel.hardwareVersion.collectAsState()
  val softwareVersion by viewModel.softwareVersion.collectAsState()
  val vendorName by viewModel.vendorName.collectAsState()
  val vendorId by viewModel.vendorId.collectAsState()
  val msgDialogInfo by viewModel.msgDialogInfo.collectAsState()
  val showRemoveDeviceAlertDialog by viewModel.showRemoveDeviceAlertDialog.collectAsState()
  val showConfirmDeviceRemovalAlertDialog by
      viewModel.showConfirmDeviceRemovalAlertDialog.collectAsState()
  val deviceRemovalCompleted by viewModel.deviceRemovalCompleted.collectAsState()
  val pairingWindowOpenForDeviceSharing by
      viewModel.pairingWindowOpenForDeviceSharing.collectAsState()

  // GPS share activity launcher.
  val shareDeviceLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()
      ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
          viewModel.shareDeviceSucceeded()
        } else {
          viewModel.shareDeviceFailed(result.resultCode)
        }
      }

  // Launch the GPS share activity once the pairing window is open.
  if (pairingWindowOpenForDeviceSharing) {
    val deviceName = device?.name ?: ""
    LaunchedEffect(pairingWindowOpenForDeviceSharing) {
      if (pairingWindowOpenForDeviceSharing) {
        viewModel.resetPairingWindowOpenForDeviceSharing()
        activity?.let { act ->
          shareDevice(
              act.applicationContext,
              shareDeviceLauncher,
              deviceName,
          ) { title, error ->
            viewModel.showMsgDialog(title, error)
          }
        }
      }
    }
  }

  // Navigate back to home when removal is done.
  if (deviceRemovalCompleted) {
    navigateToHome()
    viewModel.resetDeviceRemovalCompleted()
  }

  LifecycleResumeEffect(Unit) {
    viewModel.loadDevice(deviceId)
    onPauseOrDispose {}
  }

  DeviceSettingsScreen(
      innerPadding = innerPadding,
      device = device,
      vendorName = vendorName,
      vendorId = vendorId,
      hardwareVersion = hardwareVersion,
      softwareVersion = softwareVersion,
      msgDialogInfo = msgDialogInfo,
      showRemoveDeviceAlertDialog = showRemoveDeviceAlertDialog,
      showConfirmDeviceRemovalAlertDialog = showConfirmDeviceRemovalAlertDialog,
      onDismissMsgDialog = { viewModel.dismissMsgDialog() },
      onRenameDevice = { newName -> viewModel.renameDevice(deviceId, newName) },
      onChangeDeviceType = { type -> viewModel.changeDeviceType(deviceId, type) },
      onShareDevice = { viewModel.openPairingWindow(deviceId) },
      onRemoveDeviceClick = { viewModel.showRemoveDeviceAlertDialog() },
      onRemoveDeviceOutcome = { doIt ->
        viewModel.dismissRemoveDeviceDialog()
        if (doIt) {
          viewModel.removeDevice(deviceId)
        }
      },
      onConfirmDeviceRemovalOutcome = { doIt ->
        viewModel.dismissConfirmDeviceRemovalDialog()
        if (doIt) {
          viewModel.removeDeviceWithoutUnlink(deviceId)
        }
      },
      onInspect = { device?.let { navigateToInspect(nodeIdFor(it)) } },
      onManageControllers = { navigateToControllers(deviceId) },
  )
}

@Composable
private fun DeviceSettingsScreen(
    innerPadding: PaddingValues,
    device: Device?,
    vendorName: String?,
    vendorId: Int?,
    hardwareVersion: String?,
    softwareVersion: String?,
    msgDialogInfo: DialogInfo?,
    showRemoveDeviceAlertDialog: Boolean,
    showConfirmDeviceRemovalAlertDialog: Boolean,
    onDismissMsgDialog: () -> Unit,
    onRenameDevice: (String) -> Unit,
    onChangeDeviceType: (Device.DeviceType) -> Unit,
    onShareDevice: () -> Unit,
    onRemoveDeviceClick: () -> Unit,
    onRemoveDeviceOutcome: (Boolean) -> Unit,
    onConfirmDeviceRemovalOutcome: (Boolean) -> Unit,
    onInspect: () -> Unit,
    onManageControllers: () -> Unit,
) {
  val context = LocalContext.current
  var showShareDeviceAlertDialog by remember { mutableStateOf(false) }
  var showRenameDialog by remember { mutableStateOf(false) }
  var showTypeDialog by remember { mutableStateOf(false) }
  val scrollState =
      rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
      }

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

  if (showRenameDialog && device != null) {
    RenameDialog(
        currentName = device.name,
        onConfirm = { name ->
          onRenameDevice(name)
          showRenameDialog = false
        },
        onDismiss = { showRenameDialog = false },
    )
  }

  if (showTypeDialog && device != null) {
    DeviceTypeDialog(
        currentType = device.deviceType,
        onConfirm = { type ->
          onChangeDeviceType(type)
          showTypeDialog = false
        },
        onDismiss = { showTypeDialog = false },
    )
  }

  if (device == null) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.loading_device_info),
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    return
  }

  Column(
      modifier =
          Modifier.fillMaxWidth()
              .padding(innerPadding)
              .verticalScroll(scrollState)
              .padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    // Basic section
    SettingsSection(title = stringResource(R.string.device_settings_section_basic)) {
      val displayedVendorName =
          vendorName?.takeIf { it.isNotBlank() } ?: device.vendorName.takeIf { it.isNotBlank() }
      val displayedVendorId = vendorId ?: device.vendorId.toInt()
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_vendor),
          value = vendorLabel(displayedVendorId, displayedVendorName),
      )
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_product),
          value =
              stringResource(
                  R.string.device_settings_basic_product_value,
                  device.productName,
                  formatProductId(device.productId.toInt()),
              ),
      )
      if (!hardwareVersion.isNullOrBlank()) {
        SettingsInfoRow(
            label = stringResource(R.string.device_settings_basic_hardware_version),
            value = hardwareVersion,
        )
      }
      if (!softwareVersion.isNullOrBlank()) {
        SettingsInfoRow(
            label = stringResource(R.string.device_settings_basic_software_version),
            value = softwareVersion,
        )
      }
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_added_on),
          value = formatTimestamp(context, device.dateCommissioned),
      )
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_node_id),
          value = formatNodeId(nodeIdFor(device)),
      )
    }

    // General section
    SettingsSection(title = stringResource(R.string.device_settings_section_general)) {
      SettingsClickableRow(
          label = stringResource(R.string.device_settings_general_name),
          value = device.name,
          onClick = { showRenameDialog = true },
      )
      SettingsClickableRow(
          label = stringResource(R.string.device_settings_general_type),
          value = stringResource(getDeviceTypeDisplayStringId(device.deviceType)),
          onClick = { showTypeDialog = true },
      )
    }

    // Admin section
    SettingsSection(title = stringResource(R.string.device_settings_section_admin)) {
      SettingsActionRow(
          icon = Icons.Outlined.Settings,
          label = stringResource(R.string.device_settings_admin_fabrics),
          subtitle = stringResource(R.string.device_settings_admin_fabrics_subtitle),
          onClick = onManageControllers,
      )
      SettingsActionRow(
          icon = Icons.Outlined.Info,
          label = stringResource(R.string.device_settings_admin_inspect),
          subtitle = stringResource(R.string.device_settings_admin_inspect_subtitle),
          onClick = onInspect,
      )
      SettingsActionRow(
          icon = Icons.Outlined.Share,
          label = stringResource(R.string.device_settings_admin_share),
          subtitle = stringResource(R.string.device_settings_admin_share_subtitle),
          onClick = { showShareDeviceAlertDialog = true },
      )
      SettingsActionRow(
          icon = Icons.Outlined.Delete,
          label = stringResource(R.string.device_settings_admin_remove),
          subtitle = stringResource(R.string.device_settings_admin_remove_subtitle),
          onClick = onRemoveDeviceClick,
          iconTint = MaterialTheme.colorScheme.error,
          labelColor = MaterialTheme.colorScheme.error,
          subtitleColor = MaterialTheme.colorScheme.error,
      )
    }
  }
}
