// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.aether.android.R
import io.aether.android.chip.ClusterId
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.chip.DeviceTypeId
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog
import timber.log.Timber

/**
 * The Data Model Screen shows all the "cluster" information about the currently selected device in
 * the Device screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataModelRoute(
    onBackClick: () -> Unit,
    nodeId: Long,
    dataModelViewModel: DataModelViewModel = hiltViewModel(),
) {
  Timber.d("DataModelRoute nodeId [$nodeId]")

  // Controls the Msg AlertDialog.
  // When the user dismisses the Msg AlertDialog, we "consume" the dialog.
  val msgDialogInfo by dataModelViewModel.msgDialogInfo.collectAsState()
  val onDismissMsgDialog: () -> Unit = remember { { dataModelViewModel.dismissMsgDialog() } }

  // Observes values needed by the DataModelScreen.
  val deviceMatterInfoList by dataModelViewModel.deviceMatterInfoList.collectAsState()
  val clustersMap = dataModelViewModel.clustersMap
  val devicesMap = dataModelViewModel.devicesMap

  LifecycleResumeEffect(Unit) {
    Timber.d("LifecycleResumeEffect: selectedNodeId [$nodeId]")
    dataModelViewModel.inspectDevice(nodeId)
    onPauseOrDispose {
      // do any needed clean up here
      Timber.d("LifecycleResumeEffect:onPauseOrDispose")
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.device_settings_admin_inspect)) },
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
    DataModelScreen(
        innerPadding,
        deviceMatterInfoList,
        clustersMap,
        devicesMap,
        msgDialogInfo,
        onDismissMsgDialog,
    )
  }
}

@Composable
private fun DataModelScreen(
    innerPadding: PaddingValues,
    deviceMatterInfoList: List<DeviceMatterInfo>?,
    clustersMap: Map<ClusterId, String>,
    devicesMap: Map<DeviceTypeId, String>,
    msgDialogInfo: DialogInfo?,
    onDismissMsgDialog: () -> Unit,
) {
  // The various AlertDialog's that may pop up to inform the user of important information.
  MsgAlertDialog(msgDialogInfo, onDismissMsgDialog)

  Surface(modifier = Modifier.padding(innerPadding)) {
    if (deviceMatterInfoList == null) {
      LoadingIndicator(stringResource(R.string.device_data_model_loading))
    } else {
      Column(
          modifier =
              Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(
                      start = dimensionResource(R.dimen.margin_normal),
                      top = 0.dp,
                      end = dimensionResource(R.dimen.margin_normal),
                      bottom = dimensionResource(R.dimen.margin_normal),
                  )
      ) {
        if (deviceMatterInfoList.isEmpty()) {
          Text(
              text = stringResource(R.string.device_data_model_empty),
              style = MaterialTheme.typography.bodyMedium,
          )
        } else {
          val expandedEndpoints = remember { mutableStateMapOf<Int, Boolean>() }
          val infosByEndpoint =
              remember(deviceMatterInfoList) { deviceMatterInfoList.associateBy { it.endpoint } }
          val childEndpoints =
              remember(deviceMatterInfoList) {
                deviceMatterInfoList.flatMap { info -> info.parts }.toSet()
              }
          val rootEndpoints =
              remember(deviceMatterInfoList) {
                deviceMatterInfoList
                    .asSequence()
                    .map { it.endpoint }
                    .filterNot { endpoint -> endpoint in childEndpoints }
                    .toList()
                    .ifEmpty {
                      deviceMatterInfoList.firstOrNull()?.let { listOf(it.endpoint) } ?: emptyList()
                    }
              }

          rootEndpoints.forEach { endpoint ->
            EndpointTree(
                endpoint = endpoint,
                infosByEndpoint = infosByEndpoint,
                expandedEndpoints = expandedEndpoints,
                clustersMap = clustersMap,
                devicesMap = devicesMap,
                depth = 0,
                visited = emptySet(),
            )
          }
        }
      }
    }
  }
}
