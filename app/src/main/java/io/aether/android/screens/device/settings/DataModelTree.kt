// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aether.android.R
import io.aether.android.chip.DeviceMatterInfo

@Composable
fun EndpointTree(
    endpoint: Int,
    infosByEndpoint: Map<Int, DeviceMatterInfo>,
    expandedEndpoints: MutableMap<Int, Boolean>,
    clustersMap: Map<Long, String>,
    deviceTypesMap: Map<Long, String>,
    depth: Int,
    visited: Set<Int>,
) {
  if (endpoint in visited) return
  val endpointInfo = infosByEndpoint[endpoint] ?: return
  val nextVisited = visited + endpoint
  val isExpanded = expandedEndpoints[endpoint] ?: true
  val startPadding = dimensionResource(R.dimen.margin_normal) * depth

  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = startPadding),
  ) {
    IconButton(onClick = { expandedEndpoints[endpoint] = !isExpanded }) {
      val icon =
          if (isExpanded) Icons.Filled.KeyboardArrowDown
          else Icons.AutoMirrored.Filled.KeyboardArrowRight
      Icon(
          imageVector = icon,
          contentDescription =
              if (isExpanded) stringResource(R.string.device_data_model_endpoint_collapse)
              else stringResource(R.string.device_data_model_endpoint_expand),
      )
    }
    Text(
        text = stringResource(R.string.device_data_model_endpoint_title, endpoint),
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.clickable { expandedEndpoints[endpoint] = !isExpanded }.padding(start = 4.dp),
    )
  }

  if (!isExpanded) return

  EndpointDetails(
      endpointInfo = endpointInfo,
      clustersMap = clustersMap,
      deviceTypesMap = deviceTypesMap,
      modifier = Modifier.padding(start = startPadding + 28.dp, bottom = 8.dp),
  )
  endpointInfo.parts.forEach { child ->
    EndpointTree(
        endpoint = child,
        infosByEndpoint = infosByEndpoint,
        expandedEndpoints = expandedEndpoints,
        clustersMap = clustersMap,
        deviceTypesMap = deviceTypesMap,
        depth = depth + 1,
        visited = nextVisited,
    )
  }
}
