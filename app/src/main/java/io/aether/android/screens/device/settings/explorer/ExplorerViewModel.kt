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
    @field:StringRes @param:StringRes val nameRes: Int? = null,
    val writable: Boolean = false,
)

data class ExplorerCommandUiItem(
    val id: Long,
    @field:StringRes @param:StringRes val nameRes: Int? = null,
    val arguments: List<ExplorerCommandArgumentDefinition> = emptyList(),
)

data class ExplorerEventUiItem(
    val id: Long,
    @field:StringRes @param:StringRes val nameRes: Int? = null,
)

data class ExplorerClusterDetails(
    val attributes: List<ExplorerAttributeUiItem> = emptyList(),
    val commands: List<ExplorerCommandUiItem> = emptyList(),
    val events: List<ExplorerEventUiItem> = emptyList(),
)

@HiltViewModel
class ExplorerViewModel @Inject constructor(private val clustersHelper: ClustersHelper) :
    ViewModel() {
  companion object {
    private const val BASIC_INFORMATION_CLUSTER_ID = 0x0028L
    private const val BASIC_INFORMATION_NODE_LABEL_ATTRIBUTE_ID = 0x0005L
    private const val ROOT_ENDPOINT = 0
  }

  private var _deviceMatterInfoList = MutableStateFlow<List<DeviceMatterInfo>?>(null)
  val deviceMatterInfoList: StateFlow<List<DeviceMatterInfo>?> = _deviceMatterInfoList.asStateFlow()

  private var _selectedEndpoint = MutableStateFlow<Int?>(null)
  val selectedEndpoint: StateFlow<Int?> = _selectedEndpoint.asStateFlow()

  private var _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private var _expandedClusters = MutableStateFlow<Set<Long>>(emptySet())
  val expandedClusters: StateFlow<Set<Long>> = _expandedClusters.asStateFlow()

  private var _selectedTabByCluster = MutableStateFlow<Map<Long, ExplorerTab>>(emptyMap())
  val selectedTabByCluster: StateFlow<Map<Long, ExplorerTab>> = _selectedTabByCluster.asStateFlow()

  private var _clusterDetailsByKey =
      MutableStateFlow<Map<ExplorerClusterKey, ExplorerClusterDetails>>(emptyMap())
  val clusterDetailsByKey: StateFlow<Map<ExplorerClusterKey, ExplorerClusterDetails>> =
      _clusterDetailsByKey.asStateFlow()

  private var _attributeValueByKey = MutableStateFlow<Map<String, String>>(emptyMap())
  val attributeValueByKey: StateFlow<Map<String, String>> = _attributeValueByKey.asStateFlow()

  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  fun loadExplorer(nodeId: Long) {
    viewModelScope.launch {
      val isInitialLoad = _deviceMatterInfoList.value == null
      try {
        val infos = clustersHelper.fetchDeviceMatterInfo(nodeId).sortedBy { it.endpoint }
        _deviceMatterInfoList.value = infos
        if (_selectedEndpoint.value == null) {
          _selectedEndpoint.value = infos.firstOrNull()?.endpoint
        }
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

  fun selectEndpoint(endpoint: Int) {
    _selectedEndpoint.value = endpoint
    _expandedClusters.value = emptySet()
    _selectedTabByCluster.value = emptyMap()
  }

  fun onSearchQueryChange(query: String) {
    _searchQuery.value = query
  }

  fun toggleCluster(nodeId: Long, endpoint: Int, clusterId: Long) {
    val currentlyExpanded = _expandedClusters.value.contains(clusterId)
    _expandedClusters.update { expanded ->
      if (currentlyExpanded) expanded - clusterId else expanded + clusterId
    }
    if (!currentlyExpanded) {
      ensureClusterDetails(nodeId, endpoint, clusterId)
    }
  }

  fun setClusterTab(clusterId: Long, tab: ExplorerTab) {
    _selectedTabByCluster.update { it + (clusterId to tab) }
  }

  fun ensureClusterDetails(nodeId: Long, endpoint: Int, clusterId: Long) {
    val key = ExplorerClusterKey(endpoint, clusterId)
    if (_clusterDetailsByKey.value.containsKey(key)) {
      return
    }
    viewModelScope.launch {
      try {
        val knownSchema = ExplorerSchema.findCluster(clusterId)
        val attributesFromDevice =
            clustersHelper.readClusterAttributeList(nodeId, endpoint, clusterId)
        val commandsFromDevice =
            clustersHelper.readClusterAcceptedCommandList(nodeId, endpoint, clusterId)
        val eventsFromDevice = clustersHelper.readClusterEventList(nodeId, endpoint, clusterId)

        val knownAttributes = knownSchema?.attributes.orEmpty().associateBy { it.id }
        val knownCommands = knownSchema?.commands.orEmpty().associateBy { it.id }
        val knownEvents = knownSchema?.events.orEmpty().associateBy { it.id }

        val attributes =
            (attributesFromDevice + knownAttributes.keys).toSet().sorted().map { id ->
              val known = knownAttributes[id]
              ExplorerAttributeUiItem(
                  id = id,
                  nameRes = known?.nameRes,
                  writable = known?.writable == true,
              )
            }
        val commands =
            (commandsFromDevice + knownCommands.keys).toSet().sorted().map { id ->
              val known = knownCommands[id]
              ExplorerCommandUiItem(
                  id = id,
                  nameRes = known?.nameRes,
                  arguments = known?.arguments.orEmpty(),
              )
            }
        val events =
            (eventsFromDevice + knownEvents.keys).toSet().sorted().map { id ->
              val known = knownEvents[id]
              ExplorerEventUiItem(id = id, nameRes = known?.nameRes)
            }
        _clusterDetailsByKey.update {
          it + (key to ExplorerClusterDetails(attributes, commands, events))
        }
      } catch (e: Exception) {
        Timber.e(e, "ensureClusterDetails failed")
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_action_failed,
        )
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
      } catch (e: ExplorerValidationException) {
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
    val parsedValue = value?.trim()?.toIntOrNull()
    if (parsedValue == null) {
      throw ExplorerValidationException(invalidNumberMessageRes)
    }
    if (parsedValue < min || parsedValue > max) {
      throw ExplorerValidationException(outOfRangeMessageRes)
    }
    return parsedValue
  }

  fun attributeValue(endpoint: Int, clusterId: Long, attributeId: Long): String? {
    return _attributeValueByKey.value[attributeKey(endpoint, clusterId, attributeId)]
  }

  private fun attributeKey(endpoint: Int, clusterId: Long, attributeId: Long): String {
    return "$endpoint-$clusterId-$attributeId"
  }

  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }

  private fun showMsgDialog(@StringRes titleRes: Int, message: String?) {
    _msgDialogInfo.value = DialogInfo(titleRes = titleRes, message = message)
  }

  private fun showMsgDialog(@StringRes titleRes: Int, @StringRes messageRes: Int) {
    _msgDialogInfo.value = DialogInfo(titleRes = titleRes, messageRes = messageRes)
  }

  private class ExplorerValidationException(@field:StringRes @param:StringRes val messageRes: Int) :
      IllegalArgumentException()
}
