// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.aether.android.R
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.chip.MatterConstants
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog

data class ExplorerAttributeSheetTarget(
    val endpoint: Int,
    val clusterId: Long,
    val attribute: ExplorerAttributeUiItem,
)

data class ExplorerCommandSheetTarget(
    val endpoint: Int,
    val clusterId: Long,
    val command: ExplorerCommandUiItem,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerRoute(
    onBackClick: () -> Unit,
    nodeId: Long,
    viewModel: ExplorerViewModel = hiltViewModel(),
) {
  val deviceMatterInfoList by viewModel.deviceMatterInfoList.collectAsState()
  val selectedEndpoint by viewModel.selectedEndpoint.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val expandedClusters by viewModel.expandedClusters.collectAsState()
  val selectedTabByCluster by viewModel.selectedTabByCluster.collectAsState()
  val clusterDetailsByKey by viewModel.clusterDetailsByKey.collectAsState()
  val msgDialogInfo by viewModel.msgDialogInfo.collectAsState()

  var attributeSheetTarget by remember { mutableStateOf<ExplorerAttributeSheetTarget?>(null) }
  var commandSheetTarget by remember { mutableStateOf<ExplorerCommandSheetTarget?>(null) }
  val commandArguments = remember { mutableStateMapOf<String, String>() }

  LifecycleResumeEffect(nodeId) {
    viewModel.loadExplorer(nodeId)
    onPauseOrDispose {}
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.device_settings_admin_explorer)) },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button),
                )
              }
            },
        )
      }
  ) { innerPadding ->
    ExplorerScreen(
        innerPadding = innerPadding,
        nodeId = nodeId,
        deviceMatterInfoList = deviceMatterInfoList,
        selectedEndpoint = selectedEndpoint,
        searchQuery = searchQuery,
        expandedClusters = expandedClusters,
        selectedTabByCluster = selectedTabByCluster,
        clusterDetailsByKey = clusterDetailsByKey,
        msgDialogInfo = msgDialogInfo,
        onDismissMsgDialog = viewModel::dismissMsgDialog,
        onEndpointSelected = viewModel::selectEndpoint,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onToggleCluster = { endpoint, clusterId -> viewModel.toggleCluster(nodeId, endpoint, clusterId) },
        onTabSelected = viewModel::setClusterTab,
        onAttributeSelected = { endpoint, clusterId, attribute ->
          attributeSheetTarget = ExplorerAttributeSheetTarget(endpoint, clusterId, attribute)
        },
        attributeValue = { endpoint, clusterId, attributeId ->
          viewModel.attributeValue(endpoint, clusterId, attributeId)
        },
        onCommandSelected = { endpoint, clusterId, command ->
          commandArguments.clear()
          command.arguments.forEach { commandArguments[it.key] = "" }
          commandSheetTarget = ExplorerCommandSheetTarget(endpoint, clusterId, command)
        },
        onInvokeCommand = viewModel::invokeCommand,
    )
  }

  val currentAttributeSheetTarget = attributeSheetTarget
  if (currentAttributeSheetTarget != null) {
    var editValue by remember(currentAttributeSheetTarget) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { attributeSheetTarget = null }) {
      Column(
          modifier =
              Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.margin_normal)),
          verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small)),
      ) {
        val attributeName =
            currentAttributeSheetTarget.attribute.nameRes?.let { stringResource(it) }
                ?: stringResource(R.string.device_explorer_attribute_unknown)
        Text(
            text =
                stringResource(
                    R.string.device_explorer_attribute_title,
                    attributeName,
                    formatExplorerId(currentAttributeSheetTarget.attribute.id),
                ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text =
                stringResource(
                    R.string.device_explorer_current_value,
                    viewModel.attributeValue(
                            currentAttributeSheetTarget.endpoint,
                            currentAttributeSheetTarget.clusterId,
                            currentAttributeSheetTarget.attribute.id,
                        )
                        ?: stringResource(R.string.device_explorer_value_not_read),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (currentAttributeSheetTarget.attribute.writable) {
          OutlinedTextField(
              value = editValue,
              onValueChange = { editValue = it },
              label = { Text(stringResource(R.string.device_explorer_edit_value)) },
              modifier = Modifier.fillMaxWidth(),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small))) {
          Button(
              onClick = {
                viewModel.readAttribute(
                    nodeId,
                    currentAttributeSheetTarget.endpoint,
                    currentAttributeSheetTarget.clusterId,
                    currentAttributeSheetTarget.attribute.id,
                )
              }
          ) {
            Text(stringResource(R.string.device_explorer_read))
          }
          if (currentAttributeSheetTarget.attribute.writable) {
            Button(
                onClick = {
                  viewModel.writeAttribute(
                      nodeId,
                      currentAttributeSheetTarget.endpoint,
                      currentAttributeSheetTarget.clusterId,
                      currentAttributeSheetTarget.attribute.id,
                      editValue,
                  )
                }
            ) {
              Text(stringResource(R.string.device_explorer_write))
            }
          }
        }
      }
    }
  }

  val currentCommandSheetTarget = commandSheetTarget
  if (currentCommandSheetTarget != null) {
    ModalBottomSheet(onDismissRequest = { commandSheetTarget = null }) {
      Column(
          modifier =
              Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.margin_normal)),
          verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small)),
      ) {
        val commandName =
            currentCommandSheetTarget.command.nameRes?.let { stringResource(it) }
                ?: stringResource(R.string.device_explorer_command_unknown)
        Text(
            text =
                stringResource(
                    R.string.device_explorer_command_title,
                    commandName,
                    formatExplorerId(currentCommandSheetTarget.command.id),
                ),
            style = MaterialTheme.typography.titleMedium,
        )
        currentCommandSheetTarget.command.arguments.forEach { argument ->
          OutlinedTextField(
              value = commandArguments[argument.key].orEmpty(),
              onValueChange = { commandArguments[argument.key] = it },
              label = {
                val rangeLabel =
                    if (argument.minValue != null && argument.maxValue != null) {
                      stringResource(
                          R.string.device_explorer_argument_with_range,
                          stringResource(argument.nameRes),
                          argument.minValue,
                          argument.maxValue,
                      )
                    } else {
                      stringResource(argument.nameRes)
                    }
                Text(rangeLabel)
              },
              modifier = Modifier.fillMaxWidth(),
          )
        }
        Button(
            onClick = {
              viewModel.invokeCommand(
                  nodeId,
                  currentCommandSheetTarget.endpoint,
                  currentCommandSheetTarget.clusterId,
                  currentCommandSheetTarget.command.id,
                  commandArguments.toMap(),
              )
            }
        ) {
          Text(stringResource(R.string.device_explorer_invoke))
        }
      }
    }
  }
}

@Composable
private fun ExplorerScreen(
    innerPadding: PaddingValues,
    nodeId: Long,
    deviceMatterInfoList: List<DeviceMatterInfo>?,
    selectedEndpoint: Int?,
    searchQuery: String,
    expandedClusters: Set<Long>,
    selectedTabByCluster: Map<Long, ExplorerTab>,
    clusterDetailsByKey: Map<ExplorerClusterKey, ExplorerClusterDetails>,
    msgDialogInfo: DialogInfo?,
    onDismissMsgDialog: () -> Unit,
    onEndpointSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleCluster: (Int, Long) -> Unit,
    onTabSelected: (Long, ExplorerTab) -> Unit,
    onAttributeSelected: (Int, Long, ExplorerAttributeUiItem) -> Unit,
    attributeValue: (Int, Long, Long) -> String?,
    onCommandSelected: (Int, Long, ExplorerCommandUiItem) -> Unit,
    onInvokeCommand: (Long, Int, Long, Long, Map<String, String>) -> Unit,
) {
  MsgAlertDialog(msgDialogInfo, onDismissMsgDialog)
  if (deviceMatterInfoList == null) {
    LoadingIndicator(stringResource(R.string.device_explorer_loading), innerPadding)
    return
  }

  val endpointInfos = deviceMatterInfoList.sortedBy { it.endpoint }
  val endpoint = selectedEndpoint ?: endpointInfos.firstOrNull()?.endpoint
  if (endpoint == null) {
    LoadingIndicator(stringResource(R.string.device_explorer_loading), innerPadding)
    return
  }

  val endpointInfo = endpointInfos.firstOrNull { it.endpoint == endpoint }
  val availableClusters = endpointInfo?.serverClusters.orEmpty().sorted()
  val normalizedQuery = searchQuery.trim().lowercase()
  val filteredClusters =
      availableClusters.filter { clusterId ->
        if (normalizedQuery.isBlank()) {
          true
        } else {
          val clusterName = MatterConstants.ClustersMap[clusterId].orEmpty().lowercase()
          val clusterHex = formatExplorerId(clusterId).lowercase()
          clusterName.contains(normalizedQuery) || clusterHex.contains(normalizedQuery)
        }
      }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .padding(innerPadding)
              .padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small))) {
      items(endpointInfos, key = { it.endpoint }) { info ->
        val selected = info.endpoint == endpoint
        val firstDeviceType = info.types.firstOrNull()
        val endpointLabel =
            if (firstDeviceType == null) {
              stringResource(R.string.device_explorer_endpoint_chip_without_type, info.endpoint)
            } else {
              val typeName =
                  MatterConstants.DeviceTypesMap[firstDeviceType]
                      ?: stringResource(R.string.device_explorer_endpoint_type_unknown)
              stringResource(R.string.device_explorer_endpoint_chip, info.endpoint, typeName)
            }
        FilterChip(
            selected = selected,
            onClick = { onEndpointSelected(info.endpoint) },
            label = { Text(endpointLabel) },
        )
      }
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = { Text(stringResource(R.string.device_explorer_cluster_search)) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (filteredClusters.isEmpty()) {
      Text(
          text = stringResource(R.string.device_explorer_clusters_empty),
          style = MaterialTheme.typography.bodyMedium,
      )
      return@Column
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small)),
    ) {
      items(filteredClusters, key = { it }) { clusterId ->
        val clusterKey = ExplorerClusterKey(endpoint, clusterId)
        val isExpanded = expandedClusters.contains(clusterId)
        val details = clusterDetailsByKey[clusterKey]
        val selectedTab = selectedTabByCluster[clusterId] ?: ExplorerTab.ATTRIBUTES
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(
              modifier = Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.margin_normal)),
              verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_small)),
          ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth().clickable { onToggleCluster(endpoint, clusterId) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                  text =
                      stringResource(
                          R.string.device_explorer_cluster_title,
                          MatterConstants.ClustersMap[clusterId]
                              ?: stringResource(R.string.device_explorer_cluster_unknown),
                          formatExplorerId(clusterId),
                      ),
                  style = MaterialTheme.typography.titleMedium,
              )
              Icon(
                  imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                  contentDescription =
                      if (isExpanded) stringResource(R.string.device_explorer_collapse_cluster)
                      else stringResource(R.string.device_explorer_expand_cluster),
              )
            }
            if (isExpanded) {
              if (details == null) {
                Text(stringResource(R.string.device_explorer_loading_cluster))
              } else {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                  ExplorerTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(clusterId, tab) },
                        text = { Text(stringResource(tab.titleRes)) },
                    )
                  }
                }
                when (selectedTab) {
                  ExplorerTab.ATTRIBUTES -> {
                    if (details.attributes.isEmpty()) {
                      Text(stringResource(R.string.device_explorer_attributes_empty))
                    } else {
                      details.attributes.forEach { attribute ->
                        ExplorerRow(
                            label =
                                attribute.nameRes?.let { stringResource(it) }
                                    ?: stringResource(R.string.device_explorer_attribute_unknown),
                            value = formatExplorerId(attribute.id),
                            actionLabel = stringResource(R.string.device_explorer_open),
                            onClick = { onAttributeSelected(endpoint, clusterId, attribute) },
                        )
                      }
                    }
                  }
                  ExplorerTab.COMMANDS -> {
                    if (details.commands.isEmpty()) {
                      Text(stringResource(R.string.device_explorer_commands_empty))
                    } else {
                      details.commands.forEach { command ->
                        ExplorerRow(
                            label =
                                command.nameRes?.let { stringResource(it) }
                                    ?: stringResource(R.string.device_explorer_command_unknown),
                            value = formatExplorerId(command.id),
                            actionLabel = stringResource(R.string.device_explorer_invoke),
                            onClick = {
                              if (command.arguments.isEmpty()) {
                                onInvokeCommand(nodeId, endpoint, clusterId, command.id, emptyMap())
                              } else {
                                onCommandSelected(endpoint, clusterId, command)
                              }
                            },
                        )
                      }
                    }
                  }
                  ExplorerTab.EVENTS -> {
                    if (details.events.isEmpty()) {
                      Text(stringResource(R.string.device_explorer_events_empty))
                    } else {
                      details.events.forEach { event ->
                        Text(
                            text =
                                stringResource(
                                    R.string.device_explorer_event_title,
                                    event.nameRes?.let { stringResource(it) }
                                        ?: stringResource(R.string.device_explorer_event_unknown),
                                    formatExplorerId(event.id),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ExplorerRow(
    label: String,
    value: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = MaterialTheme.typography.bodyLarge)
      Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = onClick) { Text(actionLabel) }
  }
}

private fun formatExplorerId(id: Long): String = String.format("0x%04X", id)
