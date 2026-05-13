// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.aether.android.R
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.chip.MatterConstants
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
  val clusterDetailsByKey by viewModel.clusterDetailsByKey.collectAsState()
  val attributeValueByKey by viewModel.attributeValueByKey.collectAsState()
  val msgDialogInfo by viewModel.msgDialogInfo.collectAsState()

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
                    contentDescription = stringResource(R.string.device_explorer_toggle_search),
                )
              }
            },
        )
      }
  ) { innerPadding ->
    MsgAlertDialog(msgDialogInfo, viewModel::dismissMsgDialog)
    if (deviceMatterInfoList == null) {
      LoadingIndicator(stringResource(R.string.device_explorer_loading), innerPadding)
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
              details = clusterDetailsByKey[key],
              showSearch = showSearch,
              attributeSearchQuery = attributeSearchQuery,
              onAttributeSearchQueryChange = viewModel::onAttributeSearchQueryChange,
              onTabSelected = { tab ->
                viewModel.setClusterDetailTab(level.endpoint, level.clusterId, tab)
              },
              onAttributeSelected = { attribute ->
                viewModel.openAttributeDetail(level.endpoint, level.clusterId, attribute)
              },
              onCommandSelected = { command ->
                if (command.arguments.isEmpty()) {
                  viewModel.invokeCommand(
                      nodeId,
                      level.endpoint,
                      level.clusterId,
                      command.id,
                      emptyMap(),
                  )
                } else {
                  viewModel.openCommandInvoke(level.endpoint, level.clusterId, command)
                }
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

@Composable
private fun BreadcrumbBar(
    navStack: List<ExplorerLevel>,
    deviceMatterInfoList: List<DeviceMatterInfo>,
    onPopToIndex: (Int) -> Unit,
) {
  if (navStack.size <= 1) return

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(
                  horizontal = dimensionResource(R.dimen.margin_normal),
                  vertical = 6.dp,
              ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    navStack.forEachIndexed { index, level ->
      if (index == 0) return@forEachIndexed
      if (index > 1) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      val label = breadcrumbLabelFor(level, deviceMatterInfoList)
      val isLast = index == navStack.size - 1
      if (isLast) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
      } else {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onPopToIndex(index) },
        )
      }
    }
  }
  HorizontalDivider()
}

@Composable
private fun breadcrumbLabelFor(
    level: ExplorerLevel,
    deviceMatterInfoList: List<DeviceMatterInfo>,
): String =
    when (level) {
      ExplorerLevel.EndpointList -> stringResource(R.string.device_settings_admin_explorer)
      is ExplorerLevel.ClusterList -> {
        val info = deviceMatterInfoList.firstOrNull { it.endpoint == level.endpoint }
        val firstType = info?.types?.firstOrNull()
        if (firstType == null) {
          stringResource(R.string.device_explorer_endpoint_chip_without_type, level.endpoint)
        } else {
          val typeName =
              MatterConstants.DeviceTypesMap[firstType]
                  ?: stringResource(R.string.device_explorer_endpoint_type_unknown)
          stringResource(R.string.device_explorer_endpoint_chip, level.endpoint, typeName)
        }
      }
      is ExplorerLevel.ClusterDetail ->
          MatterConstants.ClustersMap[level.clusterId]
              ?: stringResource(R.string.device_explorer_cluster_unknown)
      is ExplorerLevel.AttributeDetail ->
          level.attribute.name ?: formatExplorerId(level.attribute.id)
      is ExplorerLevel.CommandInvoke -> level.command.name ?: formatExplorerId(level.command.id)
    }

@Composable
private fun EndpointListContent(
    infos: List<DeviceMatterInfo>,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectEndpoint: (Int) -> Unit,
) {
  val normalizedQuery = searchQuery.trim().lowercase()
  val filteredInfos = infos.filter { info ->
    if (normalizedQuery.isBlank()) {
      true
    } else {
      val endpointText = "ep${info.endpoint}".lowercase()
      val firstType = info.types.firstOrNull()
      val typeName = MatterConstants.DeviceTypesMap[firstType].orEmpty().lowercase()
      endpointText.contains(normalizedQuery) || typeName.contains(normalizedQuery)
    }
  }

  Column(
      modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    if (showSearch) {
      OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          label = { Text(stringResource(R.string.device_explorer_endpoint_search)) },
          modifier = Modifier.fillMaxWidth(),
      )
    }

    if (filteredInfos.isEmpty()) {
      Text(
          text = stringResource(R.string.device_explorer_endpoints_empty),
          style = MaterialTheme.typography.bodyMedium,
      )
      return@Column
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(filteredInfos, key = { it.endpoint }) { info ->
        val firstType = info.types.firstOrNull()
        val label =
            if (firstType == null) {
              stringResource(R.string.device_explorer_endpoint_chip_without_type, info.endpoint)
            } else {
              val typeName =
                  MatterConstants.DeviceTypesMap[firstType]
                      ?: stringResource(R.string.device_explorer_endpoint_type_unknown)
              stringResource(R.string.device_explorer_endpoint_chip, info.endpoint, typeName)
            }
        ExplorerRow(
            label = label,
            value =
                stringResource(
                    R.string.device_explorer_server_clusters_count,
                    info.serverClusters.size,
                ),
            onClick = { onSelectEndpoint(info.endpoint) },
        )
      }
    }
  }
}

@Composable
private fun ClusterListContent(
    endpoint: Int,
    infos: List<DeviceMatterInfo>,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectCluster: (Long) -> Unit,
) {
  val endpointInfo = infos.firstOrNull { it.endpoint == endpoint }
  val availableClusters = endpointInfo?.serverClusters.orEmpty().sorted()
  val normalizedQuery = searchQuery.trim().lowercase()
  val filteredClusters = availableClusters.filter { clusterId ->
    if (normalizedQuery.isBlank()) {
      true
    } else {
      val name = MatterConstants.ClustersMap[clusterId].orEmpty().lowercase()
      val hex = formatExplorerId(clusterId).lowercase()
      name.contains(normalizedQuery) || hex.contains(normalizedQuery)
    }
  }

  Column(
      modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    if (showSearch) {
      OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          label = { Text(stringResource(R.string.device_explorer_cluster_search)) },
          modifier = Modifier.fillMaxWidth(),
      )
    }

    if (filteredClusters.isEmpty()) {
      Text(
          text = stringResource(R.string.device_explorer_clusters_empty),
          style = MaterialTheme.typography.bodyMedium,
      )
      return@Column
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(filteredClusters, key = { it }) { clusterId ->
        ExplorerRow(
            label =
                MatterConstants.ClustersMap[clusterId]
                    ?: stringResource(R.string.device_explorer_cluster_unknown),
            value = formatExplorerId(clusterId),
            onClick = { onSelectCluster(clusterId) },
        )
      }
    }
  }
}

@Composable
private fun ClusterDetailContent(
    tab: ExplorerTab,
    details: ExplorerClusterDetails?,
    showSearch: Boolean,
    attributeSearchQuery: String,
    onAttributeSearchQueryChange: (String) -> Unit,
    onTabSelected: (ExplorerTab) -> Unit,
    onAttributeSelected: (ExplorerAttributeUiItem) -> Unit,
    onCommandSelected: (ExplorerCommandUiItem) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = tab.ordinal) {
      ExplorerTab.entries.forEach { t ->
        Tab(
            selected = tab == t,
            onClick = { onTabSelected(t) },
            text = { Text(stringResource(t.titleRes)) },
        )
      }
    }

    if (details == null) {
      Column(modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal))) {
        Text(stringResource(R.string.device_explorer_loading_cluster))
      }
      return@Column
    }

    when (tab) {
      ExplorerTab.ATTRIBUTES -> {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
        ) {
          if (showSearch) {
            OutlinedTextField(
                value = attributeSearchQuery,
                onValueChange = onAttributeSearchQueryChange,
                label = { Text(stringResource(R.string.device_explorer_attribute_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
          }

          val normalizedQuery = attributeSearchQuery.trim().lowercase()
          val filtered =
              details.attributes.filter { attr ->
                if (normalizedQuery.isBlank()) {
                  true
                } else {
                  val name = attr.name.orEmpty().lowercase()
                  val hex = formatExplorerId(attr.id).lowercase()
                  name.contains(normalizedQuery) || hex.contains(normalizedQuery)
                }
              }

          if (filtered.isEmpty()) {
            Text(
                text = stringResource(R.string.device_explorer_attributes_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filtered, key = { it.id }) { attribute ->
                ExplorerRow(
                    label =
                        attribute.name
                            ?: stringResource(R.string.device_explorer_attribute_unknown),
                    value = formatExplorerId(attribute.id),
                    onClick = { onAttributeSelected(attribute) },
                )
              }
            }
          }
        }
      }
      ExplorerTab.COMMANDS -> {
        if (details.commands.isEmpty()) {
          Column(modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal))) {
            Text(
                text = stringResource(R.string.device_explorer_commands_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        } else {
          LazyColumn(
              contentPadding = PaddingValues(dimensionResource(R.dimen.margin_normal)),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(details.commands, key = { it.id }) { command ->
              ExplorerRow(
                  label = command.name ?: stringResource(R.string.device_explorer_command_unknown),
                  value = formatExplorerId(command.id),
                  onClick = { onCommandSelected(command) },
              )
            }
          }
        }
      }
      ExplorerTab.EVENTS -> {
        if (details.events.isEmpty()) {
          Column(modifier = Modifier.padding(dimensionResource(R.dimen.margin_normal))) {
            Text(
                text = stringResource(R.string.device_explorer_events_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        } else {
          LazyColumn(
              contentPadding = PaddingValues(dimensionResource(R.dimen.margin_normal)),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(details.events, key = { it.id }) { event ->
              ExplorerRow(
                  label = event.name ?: stringResource(R.string.device_explorer_event_unknown),
                  value = formatExplorerId(event.id),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AttributeDetailContent(
    attribute: ExplorerAttributeUiItem,
    currentValue: String?,
    onRead: () -> Unit,
    onWrite: (String) -> Unit,
) {
  LaunchedEffect(attribute.id) { onRead() }
  var editValue by remember(attribute.id) { mutableStateOf("") }
  LaunchedEffect(attribute.id, currentValue) {
    if (currentValue != null) {
      editValue = currentValue
    }
  }

  Column(
      modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    Text(
        text =
            stringResource(
                R.string.device_explorer_attribute_title,
                attribute.name ?: stringResource(R.string.device_explorer_attribute_unknown),
                formatExplorerId(attribute.id),
            ),
        style = MaterialTheme.typography.titleMedium,
    )

    Text(
        text =
            stringResource(
                R.string.device_explorer_current_value,
                currentValue ?: stringResource(R.string.device_explorer_value_not_read),
            ),
        style = MaterialTheme.typography.bodyMedium,
    )

    OutlinedTextField(
        value = editValue,
        onValueChange = { editValue = it },
        label = { Text(stringResource(R.string.device_explorer_edit_value)) },
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onRead) { Text(stringResource(R.string.device_explorer_read)) }
      Button(onClick = { onWrite(editValue) }) {
        Text(stringResource(R.string.device_explorer_write))
      }
    }
  }
}

@Composable
private fun CommandInvokeContent(
    command: ExplorerCommandUiItem,
    onInvoke: (Map<String, String>) -> Unit,
) {
  val commandArguments =
      remember(command.id) {
        mutableStateMapOf<String, String>().also { map ->
          command.arguments.forEach { map[it.key] = "" }
        }
      }

  Column(
      modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    Text(
        text =
            stringResource(
                R.string.device_explorer_command_title,
                command.name ?: stringResource(R.string.device_explorer_command_unknown),
                formatExplorerId(command.id),
            ),
        style = MaterialTheme.typography.titleMedium,
    )

    if (command.arguments.isEmpty()) {
      Text(
          text = stringResource(R.string.device_explorer_no_arguments),
          style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      command.arguments.forEach { argument ->
        OutlinedTextField(
            value = commandArguments[argument.key].orEmpty(),
            onValueChange = { commandArguments[argument.key] = it },
            label = {
              val label =
                  if (argument.minValue != null && argument.maxValue != null) {
                    stringResource(
                        R.string.device_explorer_argument_with_range,
                        argument.name,
                        argument.minValue,
                        argument.maxValue,
                    )
                  } else {
                    argument.name
                  }
              Text(label)
            },
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))
    Button(
        onClick = { onInvoke(commandArguments.toMap()) },
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(stringResource(R.string.device_explorer_invoke))
    }
  }
}

@Composable
private fun ExplorerRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(enabled = onClick != null) { onClick?.invoke() }
              .padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = MaterialTheme.typography.bodyLarge)
      Spacer(modifier = Modifier.height(2.dp))
      Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
  }
}

private fun formatExplorerId(id: Long): String =
    if (id <= 0xFFFF) String.format("0x%04X", id) else String.format("0x%08X", id)
