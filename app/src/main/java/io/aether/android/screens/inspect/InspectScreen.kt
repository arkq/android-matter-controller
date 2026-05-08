// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.inspect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.protobuf.Timestamp
import io.aether.android.Device
import io.aether.android.R
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.chip.MatterConstants
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.common.MsgAlertDialog
import timber.log.Timber

/**
 * The Inspect Screen shows all the "cluster" information about the currently selected device in the
 * Device screen.
 */
@Composable
fun InspectRoute(
    innerPadding: PaddingValues,
    updateTitle: (title: String) -> Unit,
    deviceId: Long,
    inspectViewModel: InspectViewModel = hiltViewModel(),
) {
  Timber.d("InspectRoute deviceId [$deviceId]")

  // Controls the Msg AlertDialog.
  // When the user dismisses the Msg AlertDialog, we "consume" the dialog.
  val msgDialogInfo by inspectViewModel.msgDialogInfo.collectAsState()
  val onDismissMsgDialog: () -> Unit = remember { { inspectViewModel.dismissMsgDialog() } }

  // Observes values needed by the InspectScreen.
  val deviceMatterInfoList by inspectViewModel.deviceMatterInfoList.collectAsState()

  LifecycleResumeEffect(Unit) {
    Timber.d("LifecycleResumeEffect: selectedDeviceId [$deviceId]")
    inspectViewModel.inspectDevice(deviceId)
    onPauseOrDispose {
      // do any needed clean up here
      Timber.d("LifecycleResumeEffect:onPauseOrDispose")
    }
  }

  val title = stringResource(R.string.inspect)
  LaunchedEffect(title) { updateTitle(title) }

  InspectScreen(innerPadding, deviceMatterInfoList, msgDialogInfo, onDismissMsgDialog)
}

@Composable
private fun InspectScreen(
    innerPadding: PaddingValues,
    deviceMatterInfoList: List<DeviceMatterInfo>?,
    msgDialogInfo: DialogInfo?,
    onDismissMsgDialog: () -> Unit,
) {
  // The various AlertDialog's that may pop up to inform the user of important information.
  MsgAlertDialog(msgDialogInfo, onDismissMsgDialog)

  Surface(modifier = Modifier.padding(innerPadding)) {
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
      if (deviceMatterInfoList == null) {
        Text(
            text =
                stringResource(R.string.inspect_fetching_device_information) +
                    "\n" +
                    stringResource(R.string.inspect_fetching_device_information_note),
            style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        if (deviceMatterInfoList.isEmpty()) {
          Text(
              text = stringResource(R.string.inspect_no_information_offline),
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
                depth = 0,
                visited = emptySet(),
            )
          }
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Composable Previews

@Preview(widthDp = 300)
@Composable
private fun InspectScreenLoadingPreview() {
  MaterialTheme { InspectScreen(PaddingValues(), null, null, {}) }
}

@Preview(widthDp = 300)
@Composable
private fun InspectScreenOfflinePreview() {
  MaterialTheme { InspectScreen(PaddingValues(), emptyList(), null, {}) }
}

@Preview(widthDp = 300)
@Composable
private fun InspectScreenOnlineNoClustersPreview() {
  MaterialTheme {
    InspectScreen(
        PaddingValues(),
        listOf(DeviceMatterInfo(1, listOf(15L, 22L), emptyList(), emptyList(), emptyList())),
        null,
        {},
    )
  }
}

@Preview(widthDp = 300)
@Composable
private fun InspectScreenOnlineWithClustersPreview() {
  MaterialTheme {
    InspectScreen(
        PaddingValues(),
        listOf(
            DeviceMatterInfo(0, listOf(22L), listOf(3L), listOf(43L, 48L), listOf(1, 2)),
            DeviceMatterInfo(
                1,
                listOf(256L),
                listOf(3L, 4L, 5L),
                listOf(43L, 44L, 45L, 48L),
                emptyList(),
            ),
            DeviceMatterInfo(2, listOf(266L), listOf(4L, 6L, 29L), listOf(43L, 44L), emptyList()),
        ),
        null,
        {},
    )
  }
}

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

@Composable
private fun EndpointTree(
    endpoint: Int,
    infosByEndpoint: Map<Int, DeviceMatterInfo>,
    expandedEndpoints: MutableMap<Int, Boolean>,
    depth: Int,
    visited: Set<Int>,
) {
  if (endpoint in visited) return
  val endpointInfo = infosByEndpoint[endpoint] ?: return
  val nextVisited = visited + endpoint
  val isExpanded = expandedEndpoints.getOrPut(endpoint) { true }
  val startPadding = dimensionResource(R.dimen.margin_normal) * depth

  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = startPadding),
  ) {
    IconButton(
        onClick = { expandedEndpoints[endpoint] = !isExpanded },
        modifier = Modifier.size(24.dp),
    ) {
      val icon =
          if (isExpanded) Icons.Filled.KeyboardArrowDown
          else Icons.AutoMirrored.Filled.KeyboardArrowRight
      Icon(
          imageVector = icon,
          contentDescription =
              if (isExpanded) stringResource(R.string.inspect_collapse_endpoint)
              else stringResource(R.string.inspect_expand_endpoint),
      )
    }
    Text(
        text = stringResource(R.string.inspect_endpoint_title, endpoint),
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.clickable { expandedEndpoints[endpoint] = !isExpanded }.padding(start = 4.dp),
    )
  }

  if (!isExpanded) return

  EndpointDetails(
      endpointInfo = endpointInfo,
      modifier = Modifier.padding(start = startPadding + 28.dp, bottom = 8.dp),
  )
  endpointInfo.parts.forEach { child ->
    EndpointTree(
        endpoint = child,
        infosByEndpoint = infosByEndpoint,
        expandedEndpoints = expandedEndpoints,
        depth = depth + 1,
        visited = nextVisited,
    )
  }
}

@Composable
private fun EndpointDetails(endpointInfo: DeviceMatterInfo, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text(
        text = stringResource(R.string.inspect_device_types),
        style = MaterialTheme.typography.titleSmall,
    )
    endpointInfo.types.sorted().forEach { deviceType ->
      val hex = String.format("0x%04X", deviceType)
      val typeString =
          MatterConstants.DeviceTypesMap.getOrDefault(
              deviceType,
              stringResource(R.string.inspect_unknown),
          )
      Text(text = "[${hex}] $typeString", style = MaterialTheme.typography.bodySmall)
    }

    Text(
        text = stringResource(R.string.inspect_server_clusters),
        style = MaterialTheme.typography.titleSmall,
    )
    ClusterList(endpointInfo.serverClusters)

    Text(
        text = stringResource(R.string.inspect_client_clusters),
        style = MaterialTheme.typography.titleSmall,
    )
    ClusterList(endpointInfo.clientClusters)
  }
}

@Composable
private fun ClusterList(clusters: List<Long>) {
  if (clusters.isEmpty()) {
    Text(
        text = stringResource(R.string.inspect_none),
        style = MaterialTheme.typography.bodySmall,
    )
    return
  }
  clusters.sorted().forEach { cluster ->
    val hex = String.format("0x%04X", cluster)
    val clusterName =
        MatterConstants.ClustersMap.getOrDefault(
            cluster,
            stringResource(R.string.inspect_unknown),
        )
    Text(text = "[${hex}] $clusterName", style = MaterialTheme.typography.bodySmall)
  }
}
