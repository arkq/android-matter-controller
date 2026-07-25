// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aether.android.R
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticLogsRoute(
    onBackClick: () -> Unit,
    nodeId: NodeId,
    viewModel: DiagnosticLogsViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleResumeEffect(nodeId) {
    viewModel.loadLogs(nodeId)
    onPauseOrDispose {}
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.device_diagnostic_logs_title)) },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
              }
            },
        )
      },
  ) { innerPadding ->
    val modifierWithInnerPadding = Modifier.fillMaxSize().padding(innerPadding)
    if (uiState.isInitialLoading) {
      LoadingIndicator(
          stringResource(R.string.device_diagnostic_logs_loading),
          modifier = modifierWithInnerPadding,
      )
      return@Scaffold
    }
    DiagnosticLogsScreen(
        uiState = uiState,
        onRefresh = { viewModel.loadLogs(nodeId, forceRefresh = true) },
        modifier = modifierWithInnerPadding,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticLogsScreen(
    uiState: DiagnosticLogsUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
  PullToRefreshBox(
      isRefreshing = uiState.isRefreshing,
      onRefresh = onRefresh,
      modifier = modifier,
  ) {
    if (uiState.errorRes != null) {
      Text(stringResource(uiState.errorRes), color = MaterialTheme.colorScheme.error)
      return@PullToRefreshBox
    }
    SelectionContainer(modifier = Modifier.fillMaxSize()) {
      Text(
          text = uiState.logContent.orEmpty(),
          fontFamily = FontFamily.Monospace,
          style = MaterialTheme.typography.bodySmall,
          modifier =
              Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(MaterialTheme.spacing.paddingNormal),
      )
    }
  }
}
