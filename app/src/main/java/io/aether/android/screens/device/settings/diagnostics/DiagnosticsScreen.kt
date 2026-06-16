// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        Column {
          TopAppBar(
              title = { Text(stringResource(R.string.device_settings_admin_diagnostics)) },
              navigationIcon = {
                IconButton(onClick = onBackClick) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
              },
          )
        }
      },
  ) { innerPadding ->
    if (uiState.isInitialLoading) {
      LoadingIndicator(stringResource(R.string.device_diagnostics_loading), innerPadding)
      return@Scaffold
    }
    DiagnosticsScreen(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        uiState = uiState,
        onRefresh = { viewModel.loadDiagnostics(nodeId, forceRefresh = true) },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    uiState: DiagnosticsUiState,
    onRefresh: () -> Unit,
) {
  PullToRefreshBox(
      isRefreshing = uiState.isRefreshing,
      onRefresh = onRefresh,
      modifier = modifier.fillMaxSize(),
  ) {
    if (uiState.errorRes != null) {
      Text(stringResource(uiState.errorRes), color = MaterialTheme.colorScheme.error)
    }
    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.paddingNormal),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
    ) {
      uiState.generalDiagnostics?.let { GeneralDiagnostics(it) }
      uiState.softwareDiagnostics?.let { SoftwareDiagnostics(it) }
      uiState.ethernetNetworkDiagnostics?.let { EthernetNetworkDiagnostics(it) }
      uiState.wifiNetworkDiagnostics?.let { WiFiNetworkDiagnostics(it) }
      uiState.threadNetworkDiagnostics?.let { ThreadNetworkDiagnostics(it) }
    }
  }
}
