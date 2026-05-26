// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.aether.android.R
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerRoute(
    onBackClick: () -> Unit,
    nodeId: Long,
    viewModel: ExplorerViewModel = hiltViewModel(),
) {
  val deviceMatterInfoList by viewModel.deviceMatterInfoList.collectAsState()
  val navStack by viewModel.navStack.collectAsState()
  val endpointSearchQuery by viewModel.endpointSearchQuery.collectAsState()
  val clusterSearchQuery by viewModel.clusterSearchQuery.collectAsState()
  val attributeSearchQuery by viewModel.attributeSearchQuery.collectAsState()
  val commandSearchQuery by viewModel.commandSearchQuery.collectAsState()
  val eventSearchQuery by viewModel.eventSearchQuery.collectAsState()
  val loadingClusterKeys by viewModel.loadingClusterKeys.collectAsState()
  val clusterDetailsByKey by viewModel.clusterDetailsByKey.collectAsState()
  val attributeValueByKey by viewModel.attributeValueByKey.collectAsState()
  val attributeReadSuccessCount by viewModel.attributeReadSuccessCount.collectAsState()
  val attributeWriteSuccessCount by viewModel.attributeWriteSuccessCount.collectAsState()
  val commandInvokeSuccessCount by viewModel.commandInvokeSuccessCount.collectAsState()
  val msgDialogInfo by viewModel.msgDialogInfo.collectAsState()
  val knownClustersById by viewModel.knownClustersById.collectAsState()

  var showSearch by rememberSaveable { mutableStateOf(false) }

  val atRoot = navStack.size <= 1
  BackHandler(enabled = !atRoot) { viewModel.navigateBack() }

  LifecycleResumeEffect(nodeId) {
    viewModel.loadExplorer(nodeId)
    onPauseOrDispose {}
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.device_settings_admin_explorer)) },
            navigationIcon = {
              IconButton(onClick = if (atRoot) onBackClick else viewModel::navigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button),
                )
              }
            },
            actions = {
              IconButton(onClick = { showSearch = !showSearch }) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.device_explorer_search_toggle),
                )
              }
            },
        )
      }
  ) { innerPadding ->
    MsgAlertDialog(msgDialogInfo, viewModel::dismissMsgDialog)
    if (deviceMatterInfoList == null) {
      LoadingIndicator(stringResource(R.string.device_explorer_loading_endpoints), innerPadding)
      return@Scaffold
    }

    val infos = deviceMatterInfoList!!
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      BreadcrumbBar(
          navStack = navStack,
          deviceMatterInfoList = infos,
          onPopToIndex = viewModel::popToIndex,
      )

      when (val level = navStack.last()) {
        ExplorerLevel.EndpointList ->
            EndpointListContent(
                infos = infos,
                showSearch = showSearch,
                searchQuery = endpointSearchQuery,
                onSearchQueryChange = viewModel::onEndpointSearchQueryChange,
                onSelectEndpoint = viewModel::selectEndpoint,
            )
        is ExplorerLevel.ClusterList ->
            ClusterListContent(
                endpoint = level.endpoint,
                infos = infos,
                knownClustersById = knownClustersById,
                showSearch = showSearch,
                searchQuery = clusterSearchQuery,
                onSearchQueryChange = viewModel::onClusterSearchQueryChange,
                onSelectCluster = { clusterId ->
                  viewModel.selectCluster(nodeId, level.endpoint, clusterId)
                },
            )
        is ExplorerLevel.ClusterDetail -> {
          val key = ExplorerClusterKey(level.endpoint, level.clusterId)
          ClusterDetailContent(
              tab = level.tab,
              isLoading = loadingClusterKeys.contains(key),
              details = clusterDetailsByKey[key],
              showSearch = showSearch,
              attributeSearchQuery = attributeSearchQuery,
              commandSearchQuery = commandSearchQuery,
              eventSearchQuery = eventSearchQuery,
              onAttributeSearchQueryChange = viewModel::onAttributeSearchQueryChange,
              onCommandSearchQueryChange = viewModel::onCommandSearchQueryChange,
              onEventSearchQueryChange = viewModel::onEventSearchQueryChange,
              onTabSelected = { tab ->
                viewModel.setClusterDetailTab(level.endpoint, level.clusterId, tab)
              },
              onAttributeSelected = { attribute ->
                viewModel.openAttributeDetail(level.endpoint, level.clusterId, attribute)
              },
              onCommandSelected = { command ->
                viewModel.openCommandInvoke(level.endpoint, level.clusterId, command)
              },
          )
        }
        is ExplorerLevel.AttributeDetail -> {
          val currentValue =
              attributeValueByKey[
                  viewModel.attributeKey(
                      level.endpoint,
                      level.clusterId,
                      level.attribute.id,
                  )]
          AttributeDetailContent(
              attribute = level.attribute,
              currentValue = currentValue,
              readSuccessCount = attributeReadSuccessCount,
              writeSuccessCount = attributeWriteSuccessCount,
              onRead = {
                viewModel.readAttribute(
                    nodeId,
                    level.endpoint,
                    level.clusterId,
                    level.attribute.id,
                )
              },
              onWrite = { value ->
                viewModel.writeAttribute(
                    nodeId,
                    level.endpoint,
                    level.clusterId,
                    level.attribute.id,
                    value,
                )
              },
          )
        }
        is ExplorerLevel.CommandInvoke ->
            CommandInvokeContent(
                command = level.command,
                invokeSuccessCount = commandInvokeSuccessCount,
                onInvoke = { argumentValues ->
                  viewModel.invokeCommand(
                      nodeId,
                      level.endpoint,
                      level.clusterId,
                      level.command.id,
                      argumentValues,
                  )
                },
            )
      }
    }
  }
}
