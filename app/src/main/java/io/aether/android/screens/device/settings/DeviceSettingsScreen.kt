// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    updateTitle: (title: String) -> Unit,
    navigateToHome: () -> Unit,
    navigateToInspect: (deviceId: Long) -> Unit,
    navigateToControllers: (deviceId: Long) -> Unit,
    deviceId: Long,
    viewModel: DeviceSettingsViewModel = hiltViewModel(),
) {
  Timber.d("DeviceSettingsRoute: deviceId [$deviceId]")

  val activity = LocalContext.current.getActivity()

  val device by viewModel.device.collectAsState()
  val hardwareVersion by viewModel.hardwareVersion.collectAsState()
  val softwareVersion by viewModel.softwareVersion.collectAsState()
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

  // Fetch version info (may fail gracefully if device is offline).
  LaunchedEffect(device) {
    device?.let { d ->
      val nodeId = nodeIdFor(d)
      viewModel.fetchVersionInfo(nodeId)
    }
  }

  val title = stringResource(R.string.device_settings)
  LaunchedEffect(title) { updateTitle(title) }

  DeviceSettingsScreen(
      innerPadding = innerPadding,
      device = device,
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
        if (doIt) {
          viewModel.removeDevice(deviceId)
        } else {
          viewModel.dismissRemoveDeviceDialog()
        }
      },
      onConfirmDeviceRemovalOutcome = { doIt ->
        if (doIt) {
          viewModel.removeDeviceWithoutUnlink(deviceId)
        } else {
          viewModel.dismissConfirmDeviceRemovalDialog()
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
  var showShareDeviceAlertDialog by remember { mutableStateOf(false) }
  var showRenameDialog by remember { mutableStateOf(false) }
  var showTypeDialog by remember { mutableStateOf(false) }

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
    Text(stringResource(R.string.loading_device_info))
    return
  }

  Column(
      modifier =
          Modifier.fillMaxWidth()
              .padding(innerPadding)
              .verticalScroll(rememberScrollState())
              .padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    // Basic section
    SettingsSection(title = stringResource(R.string.section_basic)) {
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_vendor),
          value = "${device.vendorName} (0x${device.vendorId.toInt().toString(16).padStart(4, '0').uppercase()})",
      )
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_product),
          value = "${device.productName} (0x${device.productId.toInt().toString(16).padStart(4, '0').uppercase()})",
      )
      if (!hardwareVersion.isNullOrBlank()) {
        SettingsInfoRow(
            label = stringResource(R.string.device_settings_hardware_version),
            value = hardwareVersion,
        )
      }
      if (!softwareVersion.isNullOrBlank()) {
        SettingsInfoRow(
            label = stringResource(R.string.device_settings_software_version),
            value = softwareVersion,
        )
      }
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_added_on),
          value = formatTimestamp(device.dateCommissioned),
      )
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_node_id),
          value = nodeIdFor(device).toString(),
      )
    }

    // General section
    SettingsSection(title = stringResource(R.string.section_general)) {
      SettingsClickableRow(
          label = stringResource(R.string.device_settings_name),
          value = device.name,
          onClick = { showRenameDialog = true },
      )
      SettingsClickableRow(
          label = stringResource(R.string.device_settings_type),
          value = stringResource(getDeviceTypeDisplayStringId(device.deviceType)),
          onClick = { showTypeDialog = true },
      )
    }

    // Admin section
    SettingsSection(title = stringResource(R.string.section_admin)) {
      SettingsActionRow(
          icon = Icons.Outlined.Settings,
          label = stringResource(R.string.device_settings_manage_controllers),
          subtitle = stringResource(R.string.device_settings_manage_controllers_subtitle),
          onClick = onManageControllers,
      )
      SettingsActionRow(
          icon = Icons.Outlined.Info,
          label = stringResource(R.string.device_settings_inspect),
          subtitle = stringResource(R.string.device_settings_inspect_subtitle),
          onClick = onInspect,
      )
      SettingsActionRow(
          icon = Icons.Outlined.Share,
          label = stringResource(R.string.share_device),
            subtitle = stringResource(R.string.share_device_action_subtitle),
          onClick = { showShareDeviceAlertDialog = true },
      )
      SettingsActionRow(
          icon = Icons.Outlined.Delete,
          label = stringResource(R.string.remove_device),
          subtitle = stringResource(R.string.remove_device_dialog_title),
          onClick = onRemoveDeviceClick,
          iconTint = MaterialTheme.colorScheme.error,
          labelColor = MaterialTheme.colorScheme.error,
          subtitleColor = MaterialTheme.colorScheme.error,
      )
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Settings section composables

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Column(modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal))) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(bottom = 8.dp),
      )
      content()
    }
  }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = value, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun SettingsClickableRow(label: String, value: String, onClick: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
  iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
  labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
  subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
      Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = subtitleColor,
      )
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Dialogs

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceTypeDialog(
    currentType: Device.DeviceType,
    onConfirm: (Device.DeviceType) -> Unit,
    onDismiss: () -> Unit,
) {
  val types =
      listOf(
          Device.DeviceType.TYPE_LIGHT,
          Device.DeviceType.TYPE_DIMMABLE_LIGHT,
          Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT,
          Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT,
          Device.DeviceType.TYPE_LIGHT_SWITCH,
          Device.DeviceType.TYPE_OUTLET,
          Device.DeviceType.TYPE_UNKNOWN,
      )
  var expanded by remember { mutableStateOf(false) }
  var selectedType by remember(currentType) { mutableStateOf(currentType) }

  AlertDialog(
      title = { Text(stringResource(R.string.device_type_dialog_title)) },
      text = {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
          OutlinedTextField(
              value = stringResource(getDeviceTypeDisplayStringId(selectedType)),
              onValueChange = {},
              readOnly = true,
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false },
          ) {
            types.forEach { type ->
              DropdownMenuItem(
                  text = { Text(stringResource(getDeviceTypeDisplayStringId(type))) },
                  onClick = {
                    selectedType = type
                    expanded = false
                  },
              )
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { onConfirm(selectedType) }) { Text(stringResource(R.string.ok)) }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
      onDismissRequest = onDismiss,
  )
}
