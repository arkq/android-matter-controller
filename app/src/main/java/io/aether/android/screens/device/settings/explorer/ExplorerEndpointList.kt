// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aether.android.R
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.screens.common.SearchTextField

@Composable
internal fun EndpointListContent(
    infos: List<DeviceMatterInfo>,
    devicesMap: Map<Long, String>,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectEndpoint: (Int) -> Unit,
) {
  val filteredInfos = infos.filter { info ->
    val endpointText = formatEndpointId(info.endpoint)
    val typeNames = info.types.joinToString(" ") { typeId -> devicesMap[typeId].orEmpty() }
    matchesExplorerQuery(searchQuery, endpointText, typeNames)
  }
  val normalizedQuery = searchQuery.trim().lowercase()

  Column(
      modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    if (showSearch) {
      SearchTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          label = { Text(stringResource(R.string.device_explorer_search_endpoint)) },
      )
    }

    if (filteredInfos.isEmpty()) {
      Text(
          text =
              if (normalizedQuery.isBlank())
                  stringResource(R.string.device_explorer_endpoints_empty)
              else stringResource(R.string.device_explorer_no_results),
          style = MaterialTheme.typography.bodyMedium,
      )
      return@Column
    }

    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(filteredInfos, key = { it.endpoint }) { info ->
        val endpointTypeLabels =
            info.types.map { typeId ->
              val name =
                  devicesMap[typeId]
                      ?: stringResource(R.string.device_explorer_endpoint_type_unknown)
              formatIdAndName(typeId, name)
            }
        val endpointTypeNames =
            info.types.map { typeId ->
              devicesMap[typeId] ?: stringResource(R.string.device_explorer_endpoint_type_unknown)
            }
        val titleName = endpointTypeNames.joinToString(" & ")
        ExplorerRow(
            text = formatEndpointLabel(info.endpoint, titleName),
            secondaryText =
                buildString {
                  append(
                      stringResource(
                          R.string.device_explorer_device_types_label,
                          endpointTypeLabels.joinToString(", "),
                      )
                  )
                  append("\n")
                  append(
                      stringResource(
                          R.string.device_explorer_endpoint_metadata,
                          info.serverClusters.size,
                          info.clientClusters.size,
                      )
                  )
                },
            onClick = { onSelectEndpoint(info.endpoint) },
        )
      }
    }
  }
}
