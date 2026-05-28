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
import io.aether.android.matter.ClusterId
import io.aether.android.matter.DeviceTypeId

@Composable
fun EndpointDetails(
    endpointInfo: DeviceMatterInfo,
    clustersMap: Map<ClusterId, String>,
    devicesMap: Map<DeviceTypeId, String>,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
        text = stringResource(R.string.device_data_model_device_types),
        style = MaterialTheme.typography.titleSmall,
    )
    if (endpointInfo.types.isEmpty()) {
      Text(
          text = stringResource(R.string.device_data_model_none),
          style = MaterialTheme.typography.bodySmall,
      )
    } else {
      endpointInfo.types.sorted().forEach { deviceType ->
        val typeString =
            devicesMap.getOrDefault(
                deviceType,
                stringResource(R.string.device_data_model_unknown),
            )
        Text(text = "[${deviceType}] $typeString", style = MaterialTheme.typography.bodySmall)
      }
    }

    Text(
        text = stringResource(R.string.device_data_model_server_clusters),
        style = MaterialTheme.typography.titleSmall,
    )
    ClusterList(endpointInfo.serverClusters, clustersMap)

    Text(
        text = stringResource(R.string.device_data_model_client_clusters),
        style = MaterialTheme.typography.titleSmall,
    )
    ClusterList(endpointInfo.clientClusters, clustersMap)
  }
}

@Composable
private fun ClusterList(clusters: List<ClusterId>, clustersMap: Map<ClusterId, String>) {
  if (clusters.isEmpty()) {
    Text(
        text = stringResource(R.string.device_data_model_none),
        style = MaterialTheme.typography.bodySmall,
    )
    return
  }
  clusters.sorted().forEach { cluster ->
    val clusterName =
        clustersMap.getOrDefault(
            cluster,
            stringResource(R.string.device_data_model_unknown),
        )
    Text(text = "[${cluster}] $clusterName", style = MaterialTheme.typography.bodySmall)
  }
}
