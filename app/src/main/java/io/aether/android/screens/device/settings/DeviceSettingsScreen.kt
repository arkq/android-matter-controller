// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.protobuf.Timestamp
import io.aether.android.Device
import io.aether.android.R
import io.aether.android.chip.BasicInformationAttributes
import io.aether.android.formatTimestamp
import io.aether.android.getDeviceTypeDisplayStringId
import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.NodeId
import io.aether.android.matter.toVendorId
import io.aether.android.matter.vendorLabel
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.device.action.ConfirmDeviceRemovalAlertDialog
import io.aether.android.screens.device.action.RemoveDeviceAlertDialog
import io.aether.android.screens.device.action.ShareDeviceAlertDialog
import io.aether.android.screens.device.action.shareDevice
import io.aether.android.screens.thread.getActivity
import timber.log.Timber

/** Route composable for the Device Settings screen. Wires up the ViewModel and navigation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsRoute(
    navigateToHome: () -> Unit,
    navigateToDeviceExplorer: (nodeId: NodeId) -> Unit,
    navigateToDeviceFabrics: (nodeId: NodeId) -> Unit,
    onBackClick: () -> Unit,
    nodeId: NodeId,
    viewModel: DeviceSettingsViewModel = hiltViewModel(),
) {
  Timber.d("DeviceSettingsRoute: nodeId [$nodeId]")
  val typedNodeId = nodeId

  val activity = LocalContext.current.getActivity()

  val device by viewModel.device.collectAsState()
  val basicInformation by viewModel.basicInformation.collectAsState()
  val isOnline by viewModel.isOnline.collectAsState()
  val dateCommissioned by viewModel.dateCommissioned.collectAsState()
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
    viewModel.loadDevice(typedNodeId)
    onPauseOrDispose {}
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.device_settings)) },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button),
                )
              }
            },
        )
      },
  ) { innerPadding ->
    DeviceSettingsScreen(
        innerPadding = innerPadding,
        device = device,
        basicInformation = basicInformation,
        isOnline = isOnline,
        dateCommissioned = dateCommissioned,
        msgDialogInfo = msgDialogInfo,
        showRemoveDeviceAlertDialog = showRemoveDeviceAlertDialog,
        showConfirmDeviceRemovalAlertDialog = showConfirmDeviceRemovalAlertDialog,
        onDismissMsgDialog = { viewModel.dismissMsgDialog() },
        onRenameDevice = { newName -> viewModel.renameDevice(typedNodeId, newName) },
        onChangeDeviceType = { type -> viewModel.changeDeviceType(typedNodeId, type) },
        onShareDevice = { viewModel.openPairingWindow(typedNodeId) },
        onRemoveDeviceClick = { viewModel.showRemoveDeviceAlertDialog() },
        onRemoveDeviceOutcome = { doIt ->
          viewModel.dismissRemoveDeviceDialog()
          if (doIt) {
            viewModel.removeDevice(typedNodeId)
          }
        },
        onConfirmDeviceRemovalOutcome = { doIt ->
          viewModel.dismissConfirmDeviceRemovalDialog()
          if (doIt) {
            viewModel.removeDeviceWithoutUnlink(typedNodeId)
          }
        },
        onExplorer = { navigateToDeviceExplorer(typedNodeId) },
        onManageControllers = { navigateToDeviceFabrics(typedNodeId) },
    )
  }
}

@Composable
private fun DeviceSettingsScreen(
    innerPadding: PaddingValues,
    device: Device?,
    basicInformation: BasicInformationAttributes?,
    isOnline: Boolean,
    dateCommissioned: Timestamp?,
    msgDialogInfo: DialogInfo?,
    showRemoveDeviceAlertDialog: Boolean,
    showConfirmDeviceRemovalAlertDialog: Boolean,
    onDismissMsgDialog: () -> Unit,
    onRenameDevice: (String) -> Unit,
    onChangeDeviceType: (DeviceTypeId) -> Unit,
    onShareDevice: () -> Unit,
    onRemoveDeviceClick: () -> Unit,
    onRemoveDeviceOutcome: (Boolean) -> Unit,
    onConfirmDeviceRemovalOutcome: (Boolean) -> Unit,
    onExplorer: () -> Unit,
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
        currentType = device.deviceTypeId,
        onConfirm = { type ->
          onChangeDeviceType(type)
          showTypeDialog = false
        },
        onDismiss = { showTypeDialog = false },
    )
  }

  if (device == null) {
    LoadingIndicator(stringResource(R.string.loading_device_info), innerPadding)
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
    if (!isOnline) {
      Text(
          text = stringResource(R.string.device_offline_label),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
      )
    }

    // Basic section
    SettingsSection(title = stringResource(R.string.device_settings_section_basic)) {
      val unknown = stringResource(R.string.device_type_unknown)
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_vendor),
          value =
              vendorLabel(
                  basicInformation?.vendorId ?: (device.vendorId.toIntOrNull() ?: 0).toVendorId(),
                  basicInformation?.vendorName?.takeIf { it.isNotBlank() }
                      ?: device.vendorName.takeIf { it.isNotBlank() },
              ),
      )
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_product),
          value =
              stringResource(
                  R.string.device_settings_basic_product_value,
                  basicInformation?.productName?.takeIf { it.isNotBlank() }
                      ?: device.productName.takeIf { it.isNotBlank() }
                      ?: unknown,
                  basicInformation?.productId?.takeIf { it.toInt() != 0 }?.toString()
                      ?: device.productId.toIntOrNull()?.takeIf { it != 0 }?.toString()
                      ?: unknown,
              ),
      )
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_hardware_version),
          value = basicInformation?.hardwareVersion?.takeIf { it.isNotBlank() } ?: unknown,
      )
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_software_version),
          value = basicInformation?.softwareVersion?.takeIf { it.isNotBlank() } ?: unknown,
      )
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_added_on),
          value =
              dateCommissioned
                  ?.takeUnless { it.seconds == 0L && it.nanos == 0 }
                  ?.let { formatTimestamp(context, it) } ?: unknown,
      )
      SettingsInfoRow(
          label = stringResource(R.string.device_settings_basic_node_id),
          value = device.nodeId.toString(),
      )
    }

    // General section
    SettingsSection(title = stringResource(R.string.device_settings_section_general)) {
      val unknown = stringResource(R.string.device_type_unknown)
      SettingsClickableRow(
          label = stringResource(R.string.device_settings_general_name),
          value =
              device.name.takeIf { it.isNotBlank() }
                  ?: basicInformation?.nodeLabel?.takeIf { it.isNotBlank() }
                  ?: unknown,
          onClick = { showRenameDialog = true },
      )
      SettingsClickableRow(
          label = stringResource(R.string.device_settings_general_type),
          value = getDeviceTypeDisplayStringId(device.deviceTypeId),
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
          icon = Icons.Outlined.Search,
          label = stringResource(R.string.device_settings_admin_explorer),
          subtitle = stringResource(R.string.device_settings_admin_explorer_subtitle),
          onClick = onExplorer,
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
