// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.aether.android.R
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsRoute(
    onBackClick: () -> Unit,
    nodeId: NodeId,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()

  LifecycleResumeEffect(nodeId) {
    viewModel.loadDiagnostics(nodeId)
    onPauseOrDispose {}
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.device_settings_admin_diagnostics)) },
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
    if (uiState.isInitialLoading && uiState.clusters.isEmpty()) {
      LoadingIndicator(stringResource(R.string.device_diagnostics_loading), innerPadding)
      return@Scaffold
    }

    DiagnosticsScreen(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        clusters = uiState.clusters,
        errorRes = uiState.errorRes,
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.loadDiagnostics(nodeId, forceRefresh = true) },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    clusters: List<DiagnosticsClusterUiItem>,
    errorRes: Int?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
  var selectedTab by remember(clusters.size) { mutableIntStateOf(0) }
  if (clusters.isEmpty()) {
    Column(
        modifier = modifier.padding(MaterialTheme.spacing.paddingNormal),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingSmall),
    ) {
      errorRes?.let { Text(text = stringResource(it), color = MaterialTheme.colorScheme.error) }
      Text(text = stringResource(R.string.device_diagnostics_empty))
    }
    return
  }
  if (selectedTab !in clusters.indices) {
    selectedTab = 0
  }

  Column(modifier = modifier.fillMaxSize()) {
    ScrollableTabRow(selectedTabIndex = selectedTab) {
      clusters.forEachIndexed { index, cluster ->
        Tab(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            text = {
              Text(
                  text =
                      cluster.title
                          ?: stringResource(
                              R.string.device_diagnostics_cluster_fallback,
                              cluster.clusterId.toLong(),
                          )
              )
            },
        )
      }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
      val selectedCluster = clusters[selectedTab]
      LazyColumn(
          modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.paddingNormal),
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingSmall),
      ) {
        if (errorRes != null) {
          item("error") {
            Text(
                text = stringResource(errorRes),
                color = MaterialTheme.colorScheme.error,
            )
          }
        }

        item("cluster-summary") {
          ElevatedCard(
              colors = CardDefaults.elevatedCardColors(),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.paddingNormal),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingSmall),
            ) {
              Text(
                  text =
                      selectedCluster.title
                          ?: stringResource(
                              R.string.device_diagnostics_cluster_fallback,
                              selectedCluster.clusterId.toLong(),
                          ),
                  style = MaterialTheme.typography.titleMedium,
              )
              Text(
                  text =
                      stringResource(
                          R.string.device_diagnostics_cluster_id,
                          selectedCluster.clusterId.toLong(),
                      ),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (!selectedCluster.isSupported) {
          item("unsupported") {
            Text(
                text = stringResource(R.string.device_diagnostics_cluster_not_supported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else if (selectedCluster.attributes.isEmpty()) {
          item("no-attributes") {
            Text(
                text = stringResource(R.string.device_diagnostics_no_attributes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          items(selectedCluster.attributes, key = { "${it.id}" }) { attribute ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
              Column(
                  modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.paddingNormal),
                  verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingSmall),
              ) {
                Text(
                    text =
                        attribute.name
                            ?: stringResource(
                                R.string.device_diagnostics_attribute_fallback,
                                attribute.id.toLong(),
                            ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text =
                        stringResource(
                            R.string.device_diagnostics_attribute_id,
                            attribute.id.toLong(),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = attribute.value, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }
    }
  }
}
