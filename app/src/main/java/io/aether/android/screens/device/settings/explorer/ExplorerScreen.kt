// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.aether.android.R
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.chip.labelRes
import io.aether.android.matter.MatterType
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.common.SearchTextField

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
  val msgDialogInfo by viewModel.msgDialogInfo.collectAsState()
  val clustersMap = viewModel.clustersMap
  val devicesMap = viewModel.devicesMap
  val knownClustersById = viewModel.knownClustersById

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
          clustersMap = clustersMap,
          devicesMap = devicesMap,
          onPopToIndex = viewModel::popToIndex,
      )

      when (val level = navStack.last()) {
        ExplorerLevel.EndpointList ->
            EndpointListContent(
                infos = infos,
                devicesMap = devicesMap,
                showSearch = showSearch,
                searchQuery = endpointSearchQuery,
                onSearchQueryChange = viewModel::onEndpointSearchQueryChange,
                onSelectEndpoint = viewModel::selectEndpoint,
            )
        is ExplorerLevel.ClusterList ->
            ClusterListContent(
                endpoint = level.endpoint,
                infos = infos,
                clustersMap = clustersMap,
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
              typeLabelFor = viewModel::shortTypeLabel,
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
              typeLabelFor = viewModel::shortTypeLabel,
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
                typeLabelFor = viewModel::shortTypeLabel,
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
    clustersMap: Map<Long, String>,
    devicesMap: Map<Long, String>,
    onPopToIndex: (Int) -> Unit,
) {
  val scrollState = rememberScrollState()
  LaunchedEffect(navStack.size) { scrollState.animateScrollTo(scrollState.maxValue) }

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .horizontalScroll(scrollState)
              .padding(
                  horizontal = dimensionResource(R.dimen.margin_normal),
                  vertical = 6.dp,
              ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    navStack.forEachIndexed { index, level ->
      if (index > 0) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Spacer(modifier = Modifier.size(24.dp))
      }
      val label = breadcrumbLabelFor(level, deviceMatterInfoList, clustersMap, devicesMap)
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
    clustersMap: Map<Long, String>,
    devicesMap: Map<Long, String>,
): String =
    when (level) {
      ExplorerLevel.EndpointList -> stringResource(R.string.device_explorer_root)
      is ExplorerLevel.ClusterList -> {
        val info = deviceMatterInfoList.firstOrNull { it.endpoint == level.endpoint }
        val endpointName =
            info
                ?.types
                .orEmpty()
                .map { typeId ->
                  devicesMap[typeId]
                      ?: stringResource(R.string.device_explorer_endpoint_type_unknown)
                }
                .joinToString(" & ")
        formatEndpointLabel(level.endpoint, endpointName)
      }
      is ExplorerLevel.ClusterDetail -> {
        val name =
            clustersMap[level.clusterId] ?: stringResource(R.string.device_explorer_cluster_unknown)
        formatIdAndName(level.clusterId, name)
      }
      is ExplorerLevel.AttributeDetail -> formatIdAndName(level.attribute.id, level.attribute.name)
      is ExplorerLevel.CommandInvoke -> formatIdAndName(level.command.id, level.command.name)
    }

@Composable
private fun EndpointListContent(
    infos: List<DeviceMatterInfo>,
    devicesMap: Map<Long, String>,
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
      val endpointText = formatEndpointId(info.endpoint).lowercase()
      val typeNames =
          info.types.joinToString(" ") { typeId -> devicesMap[typeId].orEmpty().lowercase() }
      endpointText.contains(normalizedQuery) || typeNames.contains(normalizedQuery)
    }
  }

  Column(
      modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    if (showSearch) {
      SearchTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          label = { Text(stringResource(R.string.device_explorer_endpoint_search)) },
      )
    }

    if (filteredInfos.isEmpty()) {
      Text(
          text = stringResource(R.string.device_explorer_endpoints_empty),
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
                          R.string.device_explorer_server_clusters_count,
                          info.serverClusters.size,
                      )
                  )
                  append(" • ")
                  append(
                      stringResource(
                          R.string.device_explorer_client_clusters_count,
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

@Composable
private fun ClusterListContent(
    endpoint: Int,
    infos: List<DeviceMatterInfo>,
    clustersMap: Map<Long, String>,
    knownClustersById: Map<Long, ExplorerClusterDefinition>,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectCluster: (Long) -> Unit,
) {
  val endpointInfo = infos.firstOrNull { it.endpoint == endpoint }
  val serverClusters = endpointInfo?.serverClusters.orEmpty().sorted()
  val clientClusters = endpointInfo?.clientClusters.orEmpty().sorted()
  val normalizedQuery = searchQuery.trim().lowercase()
  val clusterMatchesQuery: (Long) -> Boolean = { clusterId ->
    if (normalizedQuery.isBlank()) {
      true
    } else {
      val name = clustersMap[clusterId].orEmpty().lowercase()
      val hex = formatExplorerId(clusterId).lowercase()
      name.contains(normalizedQuery) || hex.contains(normalizedQuery)
    }
  }
  val filteredServerClusters = serverClusters.filter(clusterMatchesQuery)
  val filteredClientClusters = clientClusters.filter(clusterMatchesQuery)

  Column(
      modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    if (showSearch) {
      SearchTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          label = { Text(stringResource(R.string.device_explorer_cluster_search)) },
      )
    }

    if (filteredServerClusters.isEmpty() && filteredClientClusters.isEmpty()) {
      Text(
          text = stringResource(R.string.device_explorer_clusters_empty),
          style = MaterialTheme.typography.bodyMedium,
      )
      return@Column
    }

    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      item(key = "server-title") {
        Text(
            text = stringResource(R.string.device_explorer_server_clusters_section),
            style = MaterialTheme.typography.titleSmall,
        )
      }
      if (filteredServerClusters.isEmpty()) {
        item(key = "server-empty") {
          Text(
              text = stringResource(R.string.device_explorer_clusters_section_empty),
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(horizontal = 8.dp),
          )
        }
      } else {
        items(filteredServerClusters, key = { "s-$it" }) { clusterId ->
          val name =
              clustersMap[clusterId] ?: stringResource(R.string.device_explorer_cluster_unknown)
          val known = knownClustersById[clusterId]
          ExplorerRow(
              text = formatIdAndName(clusterId, name),
              secondaryText =
                  stringResource(
                      R.string.device_explorer_cluster_counts,
                      known?.attributes?.size ?: 0,
                      known?.commands?.size ?: 0,
                      known?.events?.size ?: 0,
                  ),
              onClick = { onSelectCluster(clusterId) },
          )
        }
      }

      item(key = "client-title") {
        Text(
            text = stringResource(R.string.device_explorer_client_clusters_section),
            style = MaterialTheme.typography.titleSmall,
        )
      }
      if (filteredClientClusters.isEmpty()) {
        item(key = "client-empty") {
          Text(
              text = stringResource(R.string.device_explorer_clusters_section_empty),
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(horizontal = 8.dp),
          )
        }
      } else {
        items(filteredClientClusters, key = { "c-$it" }) { clusterId ->
          val name =
              clustersMap[clusterId] ?: stringResource(R.string.device_explorer_cluster_unknown)
          val known = knownClustersById[clusterId]
          ExplorerRow(
              text = formatIdAndName(clusterId, name),
              secondaryText =
                  stringResource(
                      R.string.device_explorer_cluster_counts,
                      known?.attributes?.size ?: 0,
                      known?.commands?.size ?: 0,
                      known?.events?.size ?: 0,
                  ),
              onClick = { onSelectCluster(clusterId) },
          )
        }
      }
    }
  }
}

@Composable
private fun ClusterDetailContent(
    tab: ExplorerTab,
    isLoading: Boolean,
    details: ExplorerClusterDetails?,
    typeLabelFor: (MatterType) -> String,
    showSearch: Boolean,
    attributeSearchQuery: String,
    commandSearchQuery: String,
    eventSearchQuery: String,
    onAttributeSearchQueryChange: (String) -> Unit,
    onCommandSearchQueryChange: (String) -> Unit,
    onEventSearchQueryChange: (String) -> Unit,
    onTabSelected: (ExplorerTab) -> Unit,
    onAttributeSelected: (ExplorerAttributeUiItem) -> Unit,
    onCommandSelected: (ExplorerCommandUiItem) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    PrimaryTabRow(selectedTabIndex = tab.ordinal) {
      ExplorerTab.entries.forEach { t ->
        Tab(
            selected = tab == t,
            onClick = { onTabSelected(t) },
            text = { Text(stringResource(t.titleRes)) },
        )
      }
    }

    if (isLoading || details == null) {
      LoadingIndicator(stringResource(R.string.device_explorer_loading_cluster))
      return@Column
    }

    when (tab) {
      ExplorerTab.ATTRIBUTES -> {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
        ) {
          if (showSearch) {
            SearchTextField(
                value = attributeSearchQuery,
                onValueChange = onAttributeSearchQueryChange,
                label = { Text(stringResource(R.string.device_explorer_attribute_search)) },
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filtered, key = { it.id }) { attribute ->
                ExplorerRow(
                    text =
                        formatIdAndName(
                            attribute.id,
                            attribute.name
                                ?: stringResource(R.string.device_explorer_attribute_unknown),
                        ),
                    secondaryText =
                        stringResource(
                            R.string.device_explorer_attribute_metadata,
                            stringResource(attribute.readPrivilege.labelRes()),
                            stringResource(attribute.writePrivilege.labelRes()),
                            typeLabelFor(attribute.type),
                        ),
                    onClick = { onAttributeSelected(attribute) },
                )
              }
            }
          }
        }
      }
      ExplorerTab.COMMANDS -> {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
        ) {
          if (showSearch) {
            SearchTextField(
                value = commandSearchQuery,
                onValueChange = onCommandSearchQueryChange,
                label = { Text(stringResource(R.string.device_explorer_command_search)) },
            )
          }

          val normalizedQuery = commandSearchQuery.trim().lowercase()
          val filteredCommands =
              details.commands.filter { command ->
                if (normalizedQuery.isBlank()) {
                  true
                } else {
                  val name = command.name.orEmpty().lowercase()
                  val hex = formatExplorerId(command.id).lowercase()
                  name.contains(normalizedQuery) || hex.contains(normalizedQuery)
                }
              }

          if (filteredCommands.isEmpty()) {
            Text(
                text = stringResource(R.string.device_explorer_commands_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filteredCommands, key = { it.id }) { command ->
                ExplorerRow(
                    text =
                        formatIdAndName(
                            command.id,
                            command.name
                                ?: stringResource(R.string.device_explorer_command_unknown),
                        ),
                    secondaryText =
                        stringResource(
                            R.string.device_explorer_command_arguments_count,
                            command.arguments.size,
                        ),
                    onClick = { onCommandSelected(command) },
                )
              }
            }
          }
        }
      }
      ExplorerTab.EVENTS -> {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimensionResource(R.dimen.margin_normal)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
        ) {
          if (showSearch) {
            SearchTextField(
                value = eventSearchQuery,
                onValueChange = onEventSearchQueryChange,
                label = { Text(stringResource(R.string.device_explorer_event_search)) },
            )
          }

          val normalizedQuery = eventSearchQuery.trim().lowercase()
          val filteredEvents =
              details.events.filter { event ->
                if (normalizedQuery.isBlank()) {
                  true
                } else {
                  val name = event.name.orEmpty().lowercase()
                  val hex = formatExplorerId(event.id).lowercase()
                  name.contains(normalizedQuery) || hex.contains(normalizedQuery)
                }
              }

          if (filteredEvents.isEmpty()) {
            Text(
                text = stringResource(R.string.device_explorer_events_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filteredEvents, key = { it.id }) { event ->
                ExplorerRow(
                    text =
                        formatIdAndName(
                            event.id,
                            event.name ?: stringResource(R.string.device_explorer_event_unknown),
                        ),
                )
              }
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
    typeLabelFor: (MatterType) -> String,
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
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(dimensionResource(R.dimen.margin_normal)),
      verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal)),
  ) {
    Text(
        text = formatIdAndName(attribute.id, attribute.name),
        style = MaterialTheme.typography.titleMedium,
    )

    Text(
        text =
            stringResource(
                R.string.device_explorer_attribute_type,
                typeLabelFor(attribute.type),
            ),
        style = MaterialTheme.typography.bodyMedium,
    )

    Text(
        text =
            stringResource(
                R.string.device_explorer_attribute_read_privilege,
                stringResource(attribute.readPrivilege.labelRes()),
            ),
        style = MaterialTheme.typography.bodyMedium,
    )

    Text(
        text =
            stringResource(
                R.string.device_explorer_attribute_write_privilege,
                stringResource(attribute.writePrivilege.labelRes()),
            ),
        style = MaterialTheme.typography.bodyMedium,
    )

    OutlinedTextField(
        value = editValue,
        onValueChange = { editValue = it },
        label = { Text(stringResource(R.string.device_explorer_value)) },
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val focusManager = LocalFocusManager.current
      Button(onClick = onRead) { Text(stringResource(R.string.device_explorer_read)) }
      Button(
          onClick = {
            onWrite(editValue)
            focusManager.clearFocus()
          }
      ) {
        Text(stringResource(R.string.device_explorer_write))
      }
    }
  }
}

@Composable
private fun CommandInvokeContent(
    command: ExplorerCommandUiItem,
    typeLabelFor: (MatterType) -> String,
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
        text = formatIdAndName(command.id, command.name),
        style = MaterialTheme.typography.titleMedium,
    )

    if (command.arguments.isNotEmpty()) {
      command.arguments.forEach { argument ->
        OutlinedTextField(
            value = commandArguments[argument.key].orEmpty(),
            onValueChange = { commandArguments[argument.key] = it },
            label = {
              val label = buildCommandArgumentLabel(argument, typeLabelFor)
              Text(label)
            },
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))
    Button(
        onClick = {
          onInvoke(commandArguments.toMap())
          commandArguments.keys.forEach { commandArguments[it] = "" }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(stringResource(R.string.device_explorer_invoke))
    }
  }
}

@Composable
private fun ExplorerRow(
    text: String,
    secondaryText: String? = null,
    onClick: (() -> Unit)? = null,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(enabled = onClick != null) { onClick?.invoke() }
              .padding(horizontal = 8.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = text, style = MaterialTheme.typography.bodyLarge)
      if (!secondaryText.isNullOrBlank()) {
        Text(text = secondaryText, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun buildCommandArgumentLabel(
    argument: ExplorerCommandArgumentDefinition,
    typeLabelFor: (MatterType) -> String,
): String {
  val typeLabel = typeLabelFor(argument.type)
  return stringResource(
      R.string.device_explorer_argument_with_type,
      argument.name,
      typeLabel,
  )
}

private fun formatEndpointLabel(endpoint: Int, name: String?): String =
    if (name.isNullOrBlank()) {
      "[${formatEndpointId(endpoint)}]"
    } else {
      "[${formatEndpointId(endpoint)}] $name"
    }

private fun formatIdAndName(id: Long, name: String?): String {
  val idText = formatExplorerId(id)
  return if (name.isNullOrBlank()) {
    "[$idText]"
  } else {
    "[$idText] $name"
  }
}

private fun formatExplorerId(id: Long): String =
    if (id <= 0xFFFF) String.format("0x%04X", id) else String.format("0x%08X", id)

private fun formatEndpointId(endpoint: Int): String =
    if (endpoint <= 0xFF) String.format("0x%02X", endpoint) else String.format("0x%04X", endpoint)
