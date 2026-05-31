// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.matter.AttributeId
import io.aether.android.matter.ClusterId
import io.aether.android.matter.CommandId
import io.aether.android.matter.DataType
import io.aether.android.matter.EventId
import io.aether.android.matter.NodeId
import io.aether.android.matter.Privilege
import io.aether.android.screens.common.DialogInfo
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class ExplorerTab(@field:StringRes @param:StringRes val titleRes: Int) {
  ATTRIBUTES(R.string.device_explorer_tab_attributes),
  COMMANDS(R.string.device_explorer_tab_commands),
  EVENTS(R.string.device_explorer_tab_events),
}

data class ExplorerClusterKey(val endpoint: Int, val clusterId: ClusterId)

data class ExplorerAttributeUiItem(
    val id: AttributeId,
    val name: String? = null,
    val type: DataType = DataType.UNKNOWN,
    val readPrivilege: Privilege = Privilege.NONE,
    val writePrivilege: Privilege = Privilege.NONE,
    val isSupported: Boolean = true,
)

data class ExplorerCommandUiItem(
    val id: CommandId,
    val name: String? = null,
    val arguments: List<ExplorerCommandArgumentDefinition> = emptyList(),
    val isSupported: Boolean = true,
)

data class ExplorerEventUiItem(
    val id: EventId,
    val name: String? = null,
)

data class ExplorerClusterDetails(
    val attributes: List<ExplorerAttributeUiItem> = emptyList(),
    val commands: List<ExplorerCommandUiItem> = emptyList(),
    val events: List<ExplorerEventUiItem> = emptyList(),
)

sealed class ExplorerLevel {
  object EndpointList : ExplorerLevel()

  data class ClusterList(val endpoint: UInt) : ExplorerLevel()

  data class ClusterDetail(
      val endpoint: Int,
      val clusterId: ClusterId,
      val tab: ExplorerTab = ExplorerTab.ATTRIBUTES,
  ) : ExplorerLevel()

  data class AttributeDetail(
      val endpoint: Int,
      val clusterId: ClusterId,
      val attribute: ExplorerAttributeUiItem,
  ) : ExplorerLevel()

  data class CommandInvoke(
      val endpoint: Int,
      val clusterId: ClusterId,
      val command: ExplorerCommandUiItem,
  ) : ExplorerLevel()
}

@HiltViewModel
class ExplorerViewModel
@Inject
constructor(
    private val clustersHelper: ClustersHelper,
) : ViewModel() {

  private val _deviceMatterInfoList = MutableStateFlow<List<DeviceMatterInfo>?>(null)
  val deviceMatterInfoList: StateFlow<List<DeviceMatterInfo>?> = _deviceMatterInfoList.asStateFlow()

  private val _navStack = MutableStateFlow<List<ExplorerLevel>>(listOf(ExplorerLevel.EndpointList))
  val navStack: StateFlow<List<ExplorerLevel>> = _navStack.asStateFlow()

  private val _endpointSearchQuery = MutableStateFlow("")
  val endpointSearchQuery: StateFlow<String> = _endpointSearchQuery.asStateFlow()

  private val _clusterSearchQuery = MutableStateFlow("")
  val clusterSearchQuery: StateFlow<String> = _clusterSearchQuery.asStateFlow()

  private val _attributeSearchQuery = MutableStateFlow("")
  val attributeSearchQuery: StateFlow<String> = _attributeSearchQuery.asStateFlow()

  private val _commandSearchQuery = MutableStateFlow("")
  val commandSearchQuery: StateFlow<String> = _commandSearchQuery.asStateFlow()

  private val _eventSearchQuery = MutableStateFlow("")
  val eventSearchQuery: StateFlow<String> = _eventSearchQuery.asStateFlow()

  private val _loadingClusterKeys = MutableStateFlow<Set<ExplorerClusterKey>>(emptySet())
  val loadingClusterKeys: StateFlow<Set<ExplorerClusterKey>> = _loadingClusterKeys.asStateFlow()

  private val _clusterDetailsByKey =
      MutableStateFlow<Map<ExplorerClusterKey, ExplorerClusterDetails>>(emptyMap())
  val clusterDetailsByKey: StateFlow<Map<ExplorerClusterKey, ExplorerClusterDetails>> =
      _clusterDetailsByKey.asStateFlow()

  private val _attributeValueByKey = MutableStateFlow<Map<String, String>>(emptyMap())
  val attributeValueByKey: StateFlow<Map<String, String>> = _attributeValueByKey.asStateFlow()

  private val _attributeReadSuccessCount = MutableStateFlow(0)
  val attributeReadSuccessCount: StateFlow<Int> = _attributeReadSuccessCount.asStateFlow()

  private val _attributeWriteSuccessCount = MutableStateFlow(0)
  val attributeWriteSuccessCount: StateFlow<Int> = _attributeWriteSuccessCount.asStateFlow()

  private val _commandInvokeSuccessCount = MutableStateFlow(0)
  val commandInvokeSuccessCount: StateFlow<Int> = _commandInvokeSuccessCount.asStateFlow()

  private val _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  private val _knownClustersById =
      MutableStateFlow<Map<ClusterId, ExplorerClusterDefinition>>(emptyMap())
  val knownClustersById: StateFlow<Map<ClusterId, ExplorerClusterDefinition>> =
      _knownClustersById.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      _knownClustersById.value = ExplorerSchema.buildKnownClustersById()
    }
  }

  fun loadExplorer(nodeId: NodeId) {
    viewModelScope.launch {
      val isInitialLoad = _deviceMatterInfoList.value == null
      try {
        val infos = clustersHelper.fetchDeviceMatterInfo(nodeId).sortedBy { it.endpoint }
        _deviceMatterInfoList.value = infos
      } catch (e: Exception) {
        Timber.e(e, "loadExplorer failed")
        if (isInitialLoad) {
          _deviceMatterInfoList.value = emptyList()
        }
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_action_failed,
        )
      }
    }
  }

  fun navigateBack() {
    val stack = _navStack.value
    if (stack.size <= 1) return
    val newStack = stack.dropLast(1)
    _navStack.value = newStack
    clearSearchForLevel(newStack.last())
  }

  fun popToIndex(index: Int) {
    val stack = _navStack.value
    if (index < 0 || index >= stack.size) return
    val newStack = stack.subList(0, index + 1)
    _navStack.value = newStack
    clearSearchForLevel(newStack.last())
  }

  fun selectEndpoint(endpoint: UInt) {
    _clusterSearchQuery.value = ""
    _navStack.update { it + ExplorerLevel.ClusterList(endpoint) }
  }

  fun selectCluster(nodeId: NodeId, endpoint: Int, clusterId: ClusterId) {
    _attributeSearchQuery.value = ""
    _commandSearchQuery.value = ""
    _eventSearchQuery.value = ""
    _navStack.update { it + ExplorerLevel.ClusterDetail(endpoint, clusterId) }
    ensureClusterDetails(nodeId, endpoint, clusterId)
  }

  fun setClusterDetailTab(endpoint: Int, clusterId: ClusterId, tab: ExplorerTab) {
    _navStack.update { stack ->
      stack.map { level ->
        if (
            level is ExplorerLevel.ClusterDetail &&
                level.endpoint == endpoint &&
                level.clusterId == clusterId
        ) {
          level.copy(tab = tab)
        } else {
          level
        }
      }
    }
  }

  fun onEndpointSearchQueryChange(query: String) {
    _endpointSearchQuery.value = query
  }

  fun onClusterSearchQueryChange(query: String) {
    _clusterSearchQuery.value = query
  }

  fun onAttributeSearchQueryChange(query: String) {
    _attributeSearchQuery.value = query
  }

  fun onCommandSearchQueryChange(query: String) {
    _commandSearchQuery.value = query
  }

  fun onEventSearchQueryChange(query: String) {
    _eventSearchQuery.value = query
  }

  fun openAttributeDetail(endpoint: Int, clusterId: ClusterId, attribute: ExplorerAttributeUiItem) {
    _navStack.update { it + ExplorerLevel.AttributeDetail(endpoint, clusterId, attribute) }
  }

  fun openCommandInvoke(endpoint: Int, clusterId: ClusterId, command: ExplorerCommandUiItem) {
    _navStack.update { it + ExplorerLevel.CommandInvoke(endpoint, clusterId, command) }
  }

  private fun ensureClusterDetails(nodeId: NodeId, endpoint: Int, clusterId: ClusterId) {
    val key = ExplorerClusterKey(endpoint, clusterId)
    if (_clusterDetailsByKey.value.containsKey(key) || _loadingClusterKeys.value.contains(key)) {
      return
    }

    _loadingClusterKeys.update { it + key }
    viewModelScope.launch {
      try {
        val knownSchema = _knownClustersById.value[clusterId]

        val attributesFromDevice =
            runCatching {
                  clustersHelper.readClusterAttributeList(nodeId, endpoint, clusterId)
                }
                .getOrElse {
                  Timber.w(
                      it,
                      "readClusterAttributeList failed endpoint=%d cluster=%s",
                      endpoint,
                      clusterId,
                  )
                  emptyList()
                }
        val commandsFromDevice =
            runCatching {
                  clustersHelper.readClusterAcceptedCommandList(
                  nodeId,
                      endpoint,
                      clusterId,
                  )
                }
                .getOrElse {
                  Timber.w(
                      it,
                      "readClusterAcceptedCommandList failed endpoint=%d cluster=%s",
                      endpoint,
                      clusterId,
                  )
                  emptyList()
                }
        val generatedCommandsFromDevice =
            runCatching {
                  clustersHelper.readClusterGeneratedCommandList(
                  nodeId,
                      endpoint,
                      clusterId,
                  )
                }
                .getOrElse {
                  Timber.w(
                      it,
                      "readClusterGeneratedCommandList failed endpoint=%d cluster=%s",
                      endpoint,
                      clusterId,
                  )
                  emptyList()
                }
        val eventsFromDevice =
            runCatching {
                  clustersHelper.readClusterEventList(nodeId, endpoint, clusterId)
                }
                .getOrElse {
                  Timber.w(
                      it,
                      "readClusterEventList failed endpoint=%d cluster=%s",
                      endpoint,
                      clusterId,
                  )
                  emptyList()
                }

        val knownAttributes = knownSchema?.attributes.orEmpty().associateBy { it.id }
        val knownCommands = knownSchema?.commands.orEmpty().associateBy { it.id }
        val knownEvents = knownSchema?.events.orEmpty().associateBy { it.id }
        val supportedAttributeIds = attributesFromDevice.toSet()
        val supportedCommandIds = (commandsFromDevice + generatedCommandsFromDevice).toSet()

        val attributes =
            (attributesFromDevice + knownAttributes.keys).toSet().sorted().map { id ->
              val known = knownAttributes[id]
              ExplorerAttributeUiItem(
                  id = id,
                  name = known?.name,
                  type = known?.type ?: DataType.UNKNOWN,
                  readPrivilege = known?.readPrivilege ?: Privilege.NONE,
                  writePrivilege = known?.writePrivilege ?: Privilege.NONE,
                  isSupported = id in supportedAttributeIds,
              )
            }
        val commands =
            (supportedCommandIds + knownCommands.keys).toList().sorted().map { id ->
              val known = knownCommands[id]
              ExplorerCommandUiItem(
                  id = id,
                  name = known?.name,
                  arguments = known?.arguments.orEmpty(),
                  isSupported = id in supportedCommandIds,
              )
            }
        val events =
            (eventsFromDevice + knownEvents.keys).toSet().sorted().map { id ->
              val known = knownEvents[id]
              ExplorerEventUiItem(id = id, name = known?.name)
            }

        _clusterDetailsByKey.update { current ->
          current + (key to ExplorerClusterDetails(attributes, commands, events))
        }
      } finally {
        _loadingClusterKeys.update { it - key }
      }
    }
  }

  fun readAttribute(
      nodeId: NodeId,
      endpoint: Int,
      clusterId: ClusterId,
      attributeId: AttributeId,
  ) {
    viewModelScope.launch {
      try {
        val value =
          clustersHelper.readAttributeValue(nodeId, endpoint, clusterId, attributeId)
        _attributeValueByKey.update {
          it + (attributeKey(endpoint, clusterId, attributeId) to value)
        }
        _attributeReadSuccessCount.update { it + 1 }
      } catch (e: Exception) {
        Timber.e(e, "readAttribute failed")
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_action_failed,
        )
      }
    }
  }

  fun writeAttribute(
      nodeId: NodeId,
      endpoint: Int,
      clusterId: ClusterId,
      attributeId: AttributeId,
      value: String,
  ) {
    viewModelScope.launch {
      try {
        val attributeType =
            _knownClustersById.value[clusterId]
                ?.attributes
                ?.firstOrNull { it.id == attributeId }
                ?.type ?: DataType.UNKNOWN
        val payload =
            ExplorerTlvCodec.encodeAnonymousValue(
                type = attributeType,
                rawValue = value,
                invalidNumberMessageRes = R.string.device_explorer_error_invalid_number,
            )
                ?: run {
                  showMsgDialog(
                      R.string.device_settings_admin_explorer,
                      R.string.device_explorer_error_unsupported_attribute_write,
                  )
                  return@launch
                }

        clustersHelper.writeGenericAttribute(
          nodeId,
            endpoint,
            clusterId,
            attributeId,
            payload,
        )
        _attributeValueByKey.update {
          it + (attributeKey(endpoint, clusterId, attributeId) to value)
        }
        _attributeWriteSuccessCount.update { it + 1 }
      } catch (e: ExplorerInputValidationException) {
        showMsgDialog(R.string.device_settings_admin_explorer, e.messageRes)
      } catch (e: Exception) {
        Timber.e(e, "writeAttribute failed")
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_action_failed,
        )
      }
    }
  }

  fun invokeCommand(
      nodeId: NodeId,
      endpoint: Int,
      clusterId: ClusterId,
      commandId: CommandId,
      argumentValues: Map<String, String>,
  ) {
    viewModelScope.launch {
      try {
        val arguments =
            _knownClustersById.value[clusterId]
                ?.commands
                ?.firstOrNull { it.id == commandId }
                ?.arguments
                .orEmpty()
        val payload = ExplorerTlvCodec.encodeCommandPayload(arguments, argumentValues)
        clustersHelper.invokeGenericCommand(
          nodeId,
            endpoint,
            clusterId,
            commandId,
            payload,
        )
        _commandInvokeSuccessCount.update { it + 1 }
      } catch (e: ExplorerInputValidationException) {
        showMsgDialog(R.string.device_settings_admin_explorer, e.messageRes)
      } catch (e: Exception) {
        Timber.e(e, "invokeCommand failed")
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_action_failed,
        )
      }
    }
  }

  internal fun attributeKey(endpoint: Int, clusterId: ClusterId, attributeId: AttributeId): String =
      "$endpoint-$clusterId-$attributeId"

  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }

  private fun showMsgDialog(@StringRes titleRes: Int, message: String?) {
    _msgDialogInfo.value = DialogInfo(titleRes = titleRes, message = message)
  }

  private fun showMsgDialog(@StringRes titleRes: Int, @StringRes messageRes: Int) {
    _msgDialogInfo.value = DialogInfo(titleRes = titleRes, messageRes = messageRes)
  }

  private fun clearSearchForLevel(level: ExplorerLevel) {
    if (level is ExplorerLevel.ClusterList || level is ExplorerLevel.EndpointList) {
      _attributeSearchQuery.value = ""
      _commandSearchQuery.value = ""
      _eventSearchQuery.value = ""
    }
    if (level is ExplorerLevel.EndpointList) {
      _clusterSearchQuery.value = ""
    }
  }
}
