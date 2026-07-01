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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aether.android.R
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerRoute(
    onBackClick: () -> Unit,
    nodeId: NodeId,
    viewModel: ExplorerViewModel = hiltViewModel(),
) {
  val typedNodeId = nodeId
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navStack by viewModel.navStack.collectAsStateWithLifecycle()
  val endpointSearchQuery by viewModel.endpointSearchQuery.collectAsStateWithLifecycle()
  val clusterSearchQuery by viewModel.clusterSearchQuery.collectAsStateWithLifecycle()
  val attributeSearchQuery by viewModel.attributeSearchQuery.collectAsStateWithLifecycle()
  val commandSearchQuery by viewModel.commandSearchQuery.collectAsStateWithLifecycle()
  val eventSearchQuery by viewModel.eventSearchQuery.collectAsStateWithLifecycle()
  val loadingClusterKeys by viewModel.loadingClusterKeys.collectAsStateWithLifecycle()
  val clusterDetailsByKey by viewModel.clusterDetailsByKey.collectAsStateWithLifecycle()
  val attributeValueByKey by viewModel.attributeValueByKey.collectAsStateWithLifecycle()
  val attributeReadSuccessCount by viewModel.attributeReadSuccessCount.collectAsStateWithLifecycle()
  val attributeWriteSuccessCount by
      viewModel.attributeWriteSuccessCount.collectAsStateWithLifecycle()
  val commandInvokeSuccessCount by viewModel.commandInvokeSuccessCount.collectAsStateWithLifecycle()
  val msgDialogInfo by viewModel.msgDialogInfo.collectAsStateWithLifecycle()
  val knownClustersById by viewModel.knownClustersById.collectAsStateWithLifecycle()

  var showSearch by rememberSaveable { mutableStateOf(false) }
  val saveableStateHolder = rememberSaveableStateHolder()

  val atRoot = navStack.size <= 1
  BackHandler(enabled = !atRoot) { viewModel.navigateBack() }

  LifecycleResumeEffect(nodeId) {
    viewModel.loadExplorer(typedNodeId)
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
    val modifierWithInnerPadding = Modifier.fillMaxSize().padding(innerPadding)

    msgDialogInfo?.let { dialogInfo -> MsgAlertDialog(dialogInfo, viewModel::dismissMsgDialog) }

    if (uiState !is ExplorerViewModel.UiState.Loaded) {
      LoadingIndicator(
          stringResource(R.string.device_explorer_loading_endpoints),
          modifier = modifierWithInnerPadding,
      )
      return@Scaffold
    }

    val infos = (uiState as ExplorerViewModel.UiState.Loaded).deviceMatterInfoList
    Column(modifier = modifierWithInnerPadding) {
      BreadcrumbBar(
          navStack = navStack,
          deviceMatterInfoList = infos,
          onPopToIndex = viewModel::popToIndex,
      )

      when (val level = navStack.last()) {
        ExplorerLevel.EndpointList ->
            saveableStateHolder.SaveableStateProvider("endpoint-list") {
              EndpointListContent(
                  infos = infos,
                  showSearch = showSearch,
                  searchQuery = endpointSearchQuery,
                  onSearchQueryChange = viewModel::onEndpointSearchQueryChange,
                  onSelectEndpoint = viewModel::selectEndpoint,
              )
            }
        is ExplorerLevel.ClusterList ->
            saveableStateHolder.SaveableStateProvider("cluster-list-${level.endpointId}") {
              ClusterListContent(
                  endpointId = level.endpointId,
                  infos = infos,
                  knownClustersById = knownClustersById,
                  showSearch = showSearch,
                  searchQuery = clusterSearchQuery,
                  onSearchQueryChange = viewModel::onClusterSearchQueryChange,
                  onSelectCluster = { clusterId ->
                    viewModel.selectCluster(typedNodeId, level.endpointId, clusterId)
                  },
              )
            }
        is ExplorerLevel.ClusterDetail -> {
          val key = ExplorerClusterKey(level.endpointId, level.clusterId)
          saveableStateHolder.SaveableStateProvider(
              "cluster-detail-${level.endpointId}-${level.clusterId}-${level.tab}"
          ) {
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
                  viewModel.setClusterDetailTab(level.endpointId, level.clusterId, tab)
                },
                onAttributeSelected = { attribute ->
                  viewModel.openAttributeDetail(level.endpointId, level.clusterId, attribute)
                },
                onCommandSelected = { command ->
                  viewModel.openCommandInvoke(level.endpointId, level.clusterId, command)
                },
            )
          }
        }
        is ExplorerLevel.AttributeDetail ->
            AttributeDetailContent(
                attribute = level.attribute,
                currentValue =
                    attributeValueByKey[
                        viewModel.attributeKey(
                            level.endpointId,
                            level.clusterId,
                            level.attribute.id,
                        )],
                readSuccessCount = attributeReadSuccessCount,
                writeSuccessCount = attributeWriteSuccessCount,
                onRead = {
                  viewModel.readAttribute(
                      typedNodeId,
                      level.endpointId,
                      level.clusterId,
                      level.attribute.id,
                  )
                },
                onWrite = { value ->
                  viewModel.writeAttribute(
                      typedNodeId,
                      level.endpointId,
                      level.clusterId,
                      level.attribute.id,
                      value,
                  )
                },
            )
        is ExplorerLevel.CommandInvoke ->
            CommandInvokeContent(
                command = level.command,
                invokeSuccessCount = commandInvokeSuccessCount,
                onInvoke = { argumentValues ->
                  viewModel.invokeCommand(
                      typedNodeId,
                      level.endpointId,
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
