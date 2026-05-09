// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.chip.MatterConstants

@Composable
fun EndpointDetails(endpointInfo: DeviceMatterInfo, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text(
        text = stringResource(R.string.inspect_device_types),
        style = MaterialTheme.typography.titleSmall,
    )
    if (endpointInfo.types.isEmpty()) {
      Text(
          text = stringResource(R.string.inspect_none),
          style = MaterialTheme.typography.bodySmall,
      )
    } else {
      endpointInfo.types.sorted().forEach { deviceType ->
        val hex = String.format("0x%04X", deviceType)
        val typeString =
            MatterConstants.DeviceTypesMap.getOrDefault(
                deviceType,
                stringResource(R.string.inspect_unknown),
            )
        Text(text = "[${hex}] $typeString", style = MaterialTheme.typography.bodySmall)
      }
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
