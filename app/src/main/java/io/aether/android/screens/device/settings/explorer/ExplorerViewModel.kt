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
import io.aether.android.chip.MatterConstants
import io.aether.android.screens.common.DialogInfo
import javax.inject.Inject
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

data class ExplorerClusterKey(val endpoint: Int, val clusterId: Long)

data class ExplorerAttributeUiItem(
    val id: Long,
    val name: String? = null,
    val writable: Boolean = false,
)

data class ExplorerCommandUiItem(
    val id: Long,
    val name: String? = null,
    val arguments: List<ExplorerCommandArgumentDefinition> = emptyList(),
)

data class ExplorerEventUiItem(
    val id: Long,
    val name: String? = null,
)

data class ExplorerClusterDetails(
    val attributes: List<ExplorerAttributeUiItem> = emptyList(),
    val commands: List<ExplorerCommandUiItem> = emptyList(),
    val events: List<ExplorerEventUiItem> = emptyList(),
)

sealed class ExplorerLevel {
  object EndpointList : ExplorerLevel()

  data class ClusterList(val endpoint: Int) : ExplorerLevel()

  data class ClusterDetail(
      val endpoint: Int,
      val clusterId: Long,
      val tab: ExplorerTab = ExplorerTab.ATTRIBUTES,
  ) : ExplorerLevel()

  data class AttributeDetail(
      val endpoint: Int,
      val clusterId: Long,
      val attribute: ExplorerAttributeUiItem,
  ) : ExplorerLevel()

  data class CommandInvoke(
      val endpoint: Int,
      val clusterId: Long,
      val command: ExplorerCommandUiItem,
  ) : ExplorerLevel()
}

@HiltViewModel
class ExplorerViewModel @Inject constructor(private val clustersHelper: ClustersHelper) :
    ViewModel() {
  companion object {
    private const val BASIC_INFORMATION_CLUSTER_ID = 0x0028L
    private const val BASIC_INFORMATION_NODE_LABEL_ATTRIBUTE_ID = 0x0005L
    private const val ROOT_ENDPOINT = 0
  }

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

  private val _clusterDetailsByKey =
      MutableStateFlow<Map<ExplorerClusterKey, ExplorerClusterDetails>>(emptyMap())
  val clusterDetailsByKey: StateFlow<Map<ExplorerClusterKey, ExplorerClusterDetails>> =
      _clusterDetailsByKey.asStateFlow()

  private val _attributeValueByKey = MutableStateFlow<Map<String, String>>(emptyMap())
  val attributeValueByKey: StateFlow<Map<String, String>> = _attributeValueByKey.asStateFlow()

  private val _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  fun loadExplorer(nodeId: Long) {
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
    val newTop = newStack.last()
    if (newTop is ExplorerLevel.ClusterList || newTop is ExplorerLevel.EndpointList) {
      _attributeSearchQuery.value = ""
    }
    if (newTop is ExplorerLevel.EndpointList) {
      _clusterSearchQuery.value = ""
    }
  }

  fun popToIndex(index: Int) {
    val stack = _navStack.value
    if (index < 0 || index >= stack.size) return
    val newStack = stack.subList(0, index + 1)
    _navStack.value = newStack
    val newTop = newStack.last()
    if (newTop is ExplorerLevel.ClusterList || newTop is ExplorerLevel.EndpointList) {
      _attributeSearchQuery.value = ""
    }
    if (newTop is ExplorerLevel.EndpointList) {
      _clusterSearchQuery.value = ""
    }
  }

  fun selectEndpoint(endpoint: Int) {
    _clusterSearchQuery.value = ""
    _navStack.update { it + ExplorerLevel.ClusterList(endpoint) }
  }

  fun selectCluster(nodeId: Long, endpoint: Int, clusterId: Long) {
    _attributeSearchQuery.value = ""
    _navStack.update { it + ExplorerLevel.ClusterDetail(endpoint, clusterId) }
    ensureClusterDetails(nodeId, endpoint, clusterId)
  }

  fun setClusterDetailTab(endpoint: Int, clusterId: Long, tab: ExplorerTab) {
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

  fun openAttributeDetail(endpoint: Int, clusterId: Long, attribute: ExplorerAttributeUiItem) {
    _navStack.update { it + ExplorerLevel.AttributeDetail(endpoint, clusterId, attribute) }
  }

  fun openCommandInvoke(endpoint: Int, clusterId: Long, command: ExplorerCommandUiItem) {
    _navStack.update { it + ExplorerLevel.CommandInvoke(endpoint, clusterId, command) }
  }

  private fun ensureClusterDetails(nodeId: Long, endpoint: Int, clusterId: Long) {
    val key = ExplorerClusterKey(endpoint, clusterId)
    if (_clusterDetailsByKey.value.containsKey(key)) return

    viewModelScope.launch {
      val knownSchema = ExplorerSchema.findCluster(clusterId)

      val attributesFromDevice =
          runCatching { clustersHelper.readClusterAttributeList(nodeId, endpoint, clusterId) }
              .getOrElse {
                Timber.w(
                    it,
                    "readClusterAttributeList failed endpoint=%d cluster=0x%X",
                    endpoint,
                    clusterId,
                )
                emptyList()
              }
      val commandsFromDevice =
          runCatching { clustersHelper.readClusterAcceptedCommandList(nodeId, endpoint, clusterId) }
              .getOrElse {
                Timber.w(
                    it,
                    "readClusterAcceptedCommandList failed endpoint=%d cluster=0x%X",
                    endpoint,
                    clusterId,
                )
                emptyList()
              }
      val eventsFromDevice =
          runCatching { clustersHelper.readClusterEventList(nodeId, endpoint, clusterId) }
              .getOrElse {
                Timber.w(
                    it,
                    "readClusterEventList failed endpoint=%d cluster=0x%X",
                    endpoint,
                    clusterId,
                )
                emptyList()
              }

      val knownAttributes = knownSchema?.attributes.orEmpty().associateBy { it.id }
      val knownCommands = knownSchema?.commands.orEmpty().associateBy { it.id }
      val knownEvents = knownSchema?.events.orEmpty().associateBy { it.id }
      val globalKnownAttributes = MatterConstants.ExplorerGlobalAttributesById

      val attributes =
          (attributesFromDevice + knownAttributes.keys + globalKnownAttributes.keys)
              .toSet()
              .sorted()
              .map { id ->
                val known = knownAttributes[id]
                val globalKnown = globalKnownAttributes[id]
            ExplorerAttributeUiItem(
                id = id,
                name =
                    known?.name
                        ?: globalKnown?.name
                        ?: if (MatterConstants.isGlobalAttributeId(id)) {
                          "Global Attribute"
                        } else {
                          null
                        },
                writable = known?.writable == true,
            )
          }
      val commands =
          (commandsFromDevice + knownCommands.keys).toSet().sorted().map { id ->
            val known = knownCommands[id]
            ExplorerCommandUiItem(
                id = id,
                name = known?.name,
                arguments = known?.arguments.orEmpty(),
            )
          }
      val events =
          (eventsFromDevice + knownEvents.keys).toSet().sorted().map { id ->
            val known = knownEvents[id]
            ExplorerEventUiItem(id = id, name = known?.name)
          }
      _clusterDetailsByKey.update {
        it + (key to ExplorerClusterDetails(attributes, commands, events))
      }
    }
  }

  fun readAttribute(nodeId: Long, endpoint: Int, clusterId: Long, attributeId: Long) {
    viewModelScope.launch {
      try {
        val value =
            clustersHelper.readAttributeValue(nodeId, endpoint, clusterId, attributeId).orEmpty()
        _attributeValueByKey.update {
          it + (attributeKey(endpoint, clusterId, attributeId) to value)
        }
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
      nodeId: Long,
      endpoint: Int,
      clusterId: Long,
      attributeId: Long,
      value: String,
  ) {
    viewModelScope.launch {
      try {
        if (
            clusterId == BASIC_INFORMATION_CLUSTER_ID &&
                attributeId == BASIC_INFORMATION_NODE_LABEL_ATTRIBUTE_ID &&
                endpoint == ROOT_ENDPOINT
        ) {
          clustersHelper.writeBasicInformationNodeLabelAttribute(nodeId, value)
          _attributeValueByKey.update {
            it + (attributeKey(endpoint, clusterId, attributeId) to value)
          }
        } else {
          showMsgDialog(
              R.string.device_settings_admin_explorer,
              R.string.device_explorer_error_unsupported_attribute_write,
          )
        }
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
      nodeId: Long,
      endpoint: Int,
      clusterId: Long,
      commandId: Long,
      argumentValues: Map<String, String>,
  ) {
    viewModelScope.launch {
      try {
        when (clusterId) {
          0x0006L -> invokeOnOffCommand(nodeId, endpoint, commandId)
          0x0008L -> invokeLevelControlCommand(nodeId, endpoint, commandId, argumentValues)
          else ->
              showMsgDialog(
                  R.string.device_settings_admin_explorer,
                  R.string.device_explorer_error_unsupported_command,
              )
        }
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

  private suspend fun invokeOnOffCommand(nodeId: Long, endpoint: Int, commandId: Long) {
    when (commandId) {
      0x0000L -> clustersHelper.setOnOffDeviceStateOnOffCluster(nodeId, false, endpoint)
      0x0001L -> clustersHelper.setOnOffDeviceStateOnOffCluster(nodeId, true, endpoint)
      0x0002L -> clustersHelper.toggleDeviceStateOnOffCluster(nodeId, endpoint)
      else ->
          showMsgDialog(
              R.string.device_settings_admin_explorer,
              R.string.device_explorer_error_unsupported_command,
          )
    }
  }

  private suspend fun invokeLevelControlCommand(
      nodeId: Long,
      endpoint: Int,
      commandId: Long,
      argumentValues: Map<String, String>,
  ) {
    if (commandId != 0x0000L) {
      showMsgDialog(
          R.string.device_settings_admin_explorer,
          R.string.device_explorer_error_unsupported_command,
      )
      return
    }

    val level =
        parseBoundedInt(
            argumentValues["level"],
            0,
            254,
            invalidNumberMessageRes = R.string.device_explorer_error_invalid_level_number,
            outOfRangeMessageRes = R.string.device_explorer_error_level_out_of_range,
        )
    val transitionTime =
        parseBoundedInt(
            argumentValues["transitionTime"],
            0,
            65535,
            invalidNumberMessageRes = R.string.device_explorer_error_invalid_transition_time_number,
            outOfRangeMessageRes = R.string.device_explorer_error_transition_time_out_of_range,
        )
    clustersHelper.moveToLevelCommand(nodeId, endpoint, level, transitionTime)
  }

  private fun parseBoundedInt(
      value: String?,
      min: Int,
      max: Int,
      @StringRes invalidNumberMessageRes: Int,
      @StringRes outOfRangeMessageRes: Int,
  ): Int {
    val parsedValue =
        value?.trim()?.toIntOrNull()
            ?: throw ExplorerInputValidationException(invalidNumberMessageRes)
    if (parsedValue < min || parsedValue > max) {
      throw ExplorerInputValidationException(outOfRangeMessageRes)
    }
    return parsedValue
  }

  internal fun attributeKey(endpoint: Int, clusterId: Long, attributeId: Long): String =
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

  private class ExplorerInputValidationException(
      @field:StringRes @param:StringRes val messageRes: Int,
  ) : IllegalArgumentException()
}
