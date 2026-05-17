// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.DataModelLoader
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.matter.MatterPrivilege
import io.aether.android.matter.MatterType
import io.aether.android.screens.common.DialogInfo
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
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
    val type: MatterType = MatterType.TYPE_UNKNOWN,
    val readPrivilege: MatterPrivilege = MatterPrivilege.PRIVILEGE_UNKNOWN,
    val writePrivilege: MatterPrivilege = MatterPrivilege.PRIVILEGE_UNKNOWN,
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
class ExplorerViewModel
@Inject
constructor(
    private val clustersHelper: ClustersHelper,
    private val dataModelLoader: DataModelLoader,
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

  val clustersMap: Map<Long, String> = dataModelLoader.clustersMap
  val devicesMap: Map<Long, String> = dataModelLoader.devicesMap

  val knownClustersById: Map<Long, ExplorerClusterDefinition> by lazy {
    ExplorerSchema.buildKnownClustersById(dataModelLoader.load(), dataModelLoader.genericAttributes)
  }

  fun shortTypeLabel(type: MatterType): String = dataModelLoader.shortTypeLabel(type)

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
    clearSearchForLevel(newStack.last())
  }

  fun popToIndex(index: Int) {
    val stack = _navStack.value
    if (index < 0 || index >= stack.size) return
    val newStack = stack.subList(0, index + 1)
    _navStack.value = newStack
    clearSearchForLevel(newStack.last())
  }

  fun selectEndpoint(endpoint: Int) {
    _clusterSearchQuery.value = ""
    _navStack.update { it + ExplorerLevel.ClusterList(endpoint) }
  }

  fun selectCluster(nodeId: Long, endpoint: Int, clusterId: Long) {
    _attributeSearchQuery.value = ""
    _commandSearchQuery.value = ""
    _eventSearchQuery.value = ""
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

  fun onCommandSearchQueryChange(query: String) {
    _commandSearchQuery.value = query
  }

  fun onEventSearchQueryChange(query: String) {
    _eventSearchQuery.value = query
  }

  fun openAttributeDetail(endpoint: Int, clusterId: Long, attribute: ExplorerAttributeUiItem) {
    _navStack.update { it + ExplorerLevel.AttributeDetail(endpoint, clusterId, attribute) }
  }

  fun openCommandInvoke(endpoint: Int, clusterId: Long, command: ExplorerCommandUiItem) {
    _navStack.update { it + ExplorerLevel.CommandInvoke(endpoint, clusterId, command) }
  }

  private fun ensureClusterDetails(nodeId: Long, endpoint: Int, clusterId: Long) {
    val key = ExplorerClusterKey(endpoint, clusterId)
    if (_clusterDetailsByKey.value.containsKey(key) || _loadingClusterKeys.value.contains(key)) {
      return
    }

    _loadingClusterKeys.update { it + key }
    viewModelScope.launch {
      try {
        val knownSchema = knownClustersById[clusterId]

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
            runCatching {
                  clustersHelper.readClusterAcceptedCommandList(nodeId, endpoint, clusterId)
                }
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

        val attributes =
            (attributesFromDevice + knownAttributes.keys).toSet().sorted().map { id ->
              val known = knownAttributes[id]
              ExplorerAttributeUiItem(
                  id = id,
                  name = known?.name,
                  type = known?.type ?: MatterType.TYPE_UNKNOWN,
                  readPrivilege = known?.readPrivilege ?: MatterPrivilege.PRIVILEGE_UNKNOWN,
                  writePrivilege = known?.writePrivilege ?: MatterPrivilege.PRIVILEGE_UNKNOWN,
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

        _clusterDetailsByKey.update { current ->
          current + (key to ExplorerClusterDetails(attributes, commands, events))
        }
      } finally {
        _loadingClusterKeys.update { it - key }
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
      nodeId: Long,
      endpoint: Int,
      clusterId: Long,
      attributeId: Long,
      value: String,
  ) {
    viewModelScope.launch {
      try {
        val attributeType =
            knownClustersById[clusterId]?.attributes?.firstOrNull { it.id == attributeId }?.type
                ?: MatterType.TYPE_UNKNOWN
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

        clustersHelper.writeGenericAttribute(nodeId, endpoint, clusterId, attributeId, payload)
        _attributeValueByKey.update {
          it + (attributeKey(endpoint, clusterId, attributeId) to value)
        }
        _attributeWriteSuccessCount.update { it + 1 }
      } catch (e: ExplorerInputValidationException) {
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_unsupported_attribute_write,
        )
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
        val arguments =
            knownClustersById[clusterId]
                ?.commands
                ?.firstOrNull { it.id == commandId }
                ?.arguments
                .orEmpty()
        val payload = ExplorerTlvCodec.encodeCommandPayload(arguments, argumentValues)
        clustersHelper.invokeGenericCommand(nodeId, endpoint, clusterId, commandId, payload)
        _commandInvokeSuccessCount.update { it + 1 }
      } catch (e: ExplorerInputValidationException) {
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_unsupported_command,
        )
      } catch (e: Exception) {
        Timber.e(e, "invokeCommand failed")
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_action_failed,
        )
      }
    }
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

  private class ExplorerInputValidationException(
      @field:StringRes @param:StringRes val messageRes: Int,
  ) : IllegalArgumentException()

  private class ExplorerUnsupportedValueException : IllegalArgumentException()

  private object ExplorerTlvCodec {
    private const val TLV_STRUCTURE_START = 0x15
    private const val TLV_CONTAINER_END = 0x18
    private const val TLV_CONTEXT_SIGNED_1 = 0x20
    private const val TLV_CONTEXT_SIGNED_2 = 0x21
    private const val TLV_CONTEXT_SIGNED_4 = 0x22
    private const val TLV_CONTEXT_SIGNED_8 = 0x23
    private const val TLV_CONTEXT_UNSIGNED_1 = 0x24
    private const val TLV_CONTEXT_UNSIGNED_2 = 0x25
    private const val TLV_CONTEXT_UNSIGNED_4 = 0x26
    private const val TLV_CONTEXT_UNSIGNED_8 = 0x27
    private const val TLV_CONTEXT_BOOL_FALSE = 0x28
    private const val TLV_CONTEXT_BOOL_TRUE = 0x29
    private const val TLV_CONTEXT_STRING_1 = 0x2C

    private const val TLV_ANON_SIGNED_1 = 0x00
    private const val TLV_ANON_SIGNED_2 = 0x01
    private const val TLV_ANON_SIGNED_4 = 0x02
    private const val TLV_ANON_SIGNED_8 = 0x03
    private const val TLV_ANON_UNSIGNED_1 = 0x04
    private const val TLV_ANON_UNSIGNED_2 = 0x05
    private const val TLV_ANON_UNSIGNED_4 = 0x06
    private const val TLV_ANON_UNSIGNED_8 = 0x07
    private const val TLV_ANON_BOOL_FALSE = 0x08
    private const val TLV_ANON_BOOL_TRUE = 0x09
    private const val TLV_ANON_STRING_1 = 0x0C

    fun encodeCommandPayload(
        definitions: List<ExplorerCommandArgumentDefinition>,
        argumentValues: Map<String, String>,
    ): ByteArray {
      val out = ByteArrayOutputStream()
      out.write(TLV_STRUCTURE_START)
      definitions.forEachIndexed { index, definition ->
        encodeContextValue(out, index, definition, argumentValues[definition.key])
      }
      out.write(TLV_CONTAINER_END)
      return out.toByteArray()
    }

    fun encodeAnonymousValue(
        type: MatterType,
        rawValue: String,
        @StringRes invalidNumberMessageRes: Int,
    ): ByteArray? {
      if (type == MatterType.TYPE_UNKNOWN || type == MatterType.UNRECOGNIZED) {
        return null
      }
      val out = ByteArrayOutputStream()
      when (type) {
        MatterType.TYPE_BOOL -> {
          val parsed = rawValue.trim().toBooleanStrictOrNull()
          when (parsed) {
            true -> out.write(TLV_ANON_BOOL_TRUE)
            false -> out.write(TLV_ANON_BOOL_FALSE)
            null ->
                throw ExplorerInputValidationException(
                    R.string.device_explorer_error_invalid_boolean
                )
          }
        }
        MatterType.TYPE_STRING,
        MatterType.TYPE_OCTSTR,
        MatterType.TYPE_IPV4ADR,
        MatterType.TYPE_IPV6ADR,
        MatterType.TYPE_IPV6PRE,
        MatterType.TYPE_HWADR -> {
          val bytes = rawValue.toByteArray(StandardCharsets.UTF_8)
          requireStringLength(bytes)
          out.write(TLV_ANON_STRING_1)
          out.write(bytes.size)
          out.write(bytes)
        }
        MatterType.TYPE_UINT8,
        MatterType.TYPE_ENUM8,
        MatterType.TYPE_MAP8 ->
            writeAnonymousUnsigned(
                out,
                TLV_ANON_UNSIGNED_1,
                parseUnsigned(rawValue, 0xFF, invalidNumberMessageRes),
                1,
            )
        MatterType.TYPE_UINT16,
        MatterType.TYPE_ENUM16,
        MatterType.TYPE_MAP16 ->
            writeAnonymousUnsigned(
                out,
                TLV_ANON_UNSIGNED_2,
                parseUnsigned(rawValue, 0xFFFF, invalidNumberMessageRes),
                2,
            )
        MatterType.TYPE_UINT24,
        MatterType.TYPE_UINT32,
        MatterType.TYPE_CLUSTER_ID,
        MatterType.TYPE_ATTRIBUTE_ID,
        MatterType.TYPE_ENDPOINT_NO,
        MatterType.TYPE_DEVTYPE_ID,
        MatterType.TYPE_GROUP_ID,
        MatterType.TYPE_VENDOR_ID,
        MatterType.TYPE_MESSAGE_ID,
        MatterType.TYPE_SNAPSHOT_STREAM_ID,
        MatterType.TYPE_TLS_ENDPOINT_ID ->
            writeAnonymousUnsigned(
                out,
                TLV_ANON_UNSIGNED_4,
                parseUnsigned(rawValue, 0xFFFFFFFFL, invalidNumberMessageRes),
                4,
            )
        MatterType.TYPE_UINT64,
        MatterType.TYPE_EPOCH_S,
        MatterType.TYPE_EPOCH_US,
        MatterType.TYPE_FABRIC_IDX,
        MatterType.TYPE_NODE_ID,
        MatterType.TYPE_SUBJECT_ID,
        MatterType.TYPE_TLSCAID,
        MatterType.TYPE_TLSCCDID ->
            writeAnonymousUnsigned(
                out,
                TLV_ANON_UNSIGNED_8,
                parseUnsigned(rawValue, Long.MAX_VALUE, invalidNumberMessageRes),
                8,
            )
        MatterType.TYPE_INT8 ->
            writeAnonymousSigned(
                out,
                TLV_ANON_SIGNED_1,
                parseSigned(rawValue, -128, 127, invalidNumberMessageRes),
                1,
            )
        MatterType.TYPE_INT16 ->
            writeAnonymousSigned(
                out,
                TLV_ANON_SIGNED_2,
                parseSigned(rawValue, -32768, 32767, invalidNumberMessageRes),
                2,
            )
        MatterType.TYPE_INT32 ->
            writeAnonymousSigned(
                out,
                TLV_ANON_SIGNED_4,
                parseSigned(
                    rawValue,
                    Int.MIN_VALUE.toLong(),
                    Int.MAX_VALUE.toLong(),
                    invalidNumberMessageRes,
                ),
                4,
            )
        MatterType.TYPE_INT64 ->
            writeAnonymousSigned(
                out,
                TLV_ANON_SIGNED_8,
                parseSigned(rawValue, Long.MIN_VALUE, Long.MAX_VALUE, invalidNumberMessageRes),
                8,
            )
        else -> return null
      }
      return out.toByteArray()
    }

    private fun encodeContextValue(
        out: ByteArrayOutputStream,
        tag: Int,
        definition: ExplorerCommandArgumentDefinition,
        rawValue: String?,
    ) {
      val requiredValue = rawValue?.trim().orEmpty()
      when (definition.type) {
        MatterType.TYPE_BOOL -> {
          val parsed = requiredValue.toBooleanStrictOrNull()
          when (parsed) {
            true -> out.write(TLV_CONTEXT_BOOL_TRUE)
            false -> out.write(TLV_CONTEXT_BOOL_FALSE)
            null ->
                throw ExplorerInputValidationException(
                    R.string.device_explorer_error_invalid_boolean
                )
          }
          out.write(tag)
        }
        MatterType.TYPE_STRING,
        MatterType.TYPE_OCTSTR,
        MatterType.TYPE_IPV4ADR,
        MatterType.TYPE_IPV6ADR,
        MatterType.TYPE_IPV6PRE,
        MatterType.TYPE_HWADR -> {
          val bytes = requiredValue.toByteArray(StandardCharsets.UTF_8)
          requireStringLength(bytes)
          out.write(TLV_CONTEXT_STRING_1)
          out.write(tag)
          out.write(bytes.size)
          out.write(bytes)
        }
        MatterType.TYPE_UINT8,
        MatterType.TYPE_ENUM8,
        MatterType.TYPE_MAP8 ->
            writeContextUnsigned(
                out,
                tag,
                TLV_CONTEXT_UNSIGNED_1,
                parseUnsigned(requiredValue, 0xFF, R.string.device_explorer_error_invalid_number),
                1,
            )
        MatterType.TYPE_UINT16,
        MatterType.TYPE_ENUM16,
        MatterType.TYPE_MAP16 ->
            writeContextUnsigned(
                out,
                tag,
                TLV_CONTEXT_UNSIGNED_2,
                parseUnsigned(requiredValue, 0xFFFF, R.string.device_explorer_error_invalid_number),
                2,
            )
        MatterType.TYPE_UINT24,
        MatterType.TYPE_UINT32,
        MatterType.TYPE_CLUSTER_ID,
        MatterType.TYPE_ATTRIBUTE_ID,
        MatterType.TYPE_ENDPOINT_NO,
        MatterType.TYPE_DEVTYPE_ID,
        MatterType.TYPE_GROUP_ID,
        MatterType.TYPE_VENDOR_ID,
        MatterType.TYPE_MESSAGE_ID,
        MatterType.TYPE_SNAPSHOT_STREAM_ID,
        MatterType.TYPE_TLS_ENDPOINT_ID ->
            writeContextUnsigned(
                out,
                tag,
                TLV_CONTEXT_UNSIGNED_4,
                parseUnsigned(
                    requiredValue,
                    0xFFFFFFFFL,
                    R.string.device_explorer_error_invalid_number,
                ),
                4,
            )
        MatterType.TYPE_UINT64,
        MatterType.TYPE_EPOCH_S,
        MatterType.TYPE_EPOCH_US,
        MatterType.TYPE_FABRIC_IDX,
        MatterType.TYPE_NODE_ID,
        MatterType.TYPE_SUBJECT_ID,
        MatterType.TYPE_TLSCAID,
        MatterType.TYPE_TLSCCDID ->
            writeContextUnsigned(
                out,
                tag,
                TLV_CONTEXT_UNSIGNED_8,
                parseUnsigned(
                    requiredValue,
                    Long.MAX_VALUE,
                    R.string.device_explorer_error_invalid_number,
                ),
                8,
            )
        MatterType.TYPE_INT8 ->
            writeContextSigned(
                out,
                tag,
                TLV_CONTEXT_SIGNED_1,
                parseSigned(
                    requiredValue,
                    -128,
                    127,
                    R.string.device_explorer_error_invalid_number,
                ),
                1,
            )
        MatterType.TYPE_INT16 ->
            writeContextSigned(
                out,
                tag,
                TLV_CONTEXT_SIGNED_2,
                parseSigned(
                    requiredValue,
                    -32768,
                    32767,
                    R.string.device_explorer_error_invalid_number,
                ),
                2,
            )
        MatterType.TYPE_INT32 ->
            writeContextSigned(
                out,
                tag,
                TLV_CONTEXT_SIGNED_4,
                parseSigned(
                    requiredValue,
                    Int.MIN_VALUE.toLong(),
                    Int.MAX_VALUE.toLong(),
                    R.string.device_explorer_error_invalid_number,
                ),
                4,
            )
        MatterType.TYPE_INT64 ->
            writeContextSigned(
                out,
                tag,
                TLV_CONTEXT_SIGNED_8,
                parseSigned(
                    requiredValue,
                    Long.MIN_VALUE,
                    Long.MAX_VALUE,
                    R.string.device_explorer_error_invalid_number,
                ),
                8,
            )
        else -> throw ExplorerUnsupportedValueException()
      }
    }

    private fun writeContextUnsigned(
        out: ByteArrayOutputStream,
        tag: Int,
        controlByte: Int,
        value: Long,
        sizeBytes: Int,
    ) {
      out.write(controlByte)
      out.write(tag)
      writeLittleEndian(out, value, sizeBytes)
    }

    private fun writeContextSigned(
        out: ByteArrayOutputStream,
        tag: Int,
        controlByte: Int,
        value: Long,
        sizeBytes: Int,
    ) {
      out.write(controlByte)
      out.write(tag)
      writeLittleEndian(out, value, sizeBytes)
    }

    private fun writeAnonymousUnsigned(
        out: ByteArrayOutputStream,
        controlByte: Int,
        value: Long,
        sizeBytes: Int,
    ) {
      out.write(controlByte)
      writeLittleEndian(out, value, sizeBytes)
    }

    private fun writeAnonymousSigned(
        out: ByteArrayOutputStream,
        controlByte: Int,
        value: Long,
        sizeBytes: Int,
    ) {
      out.write(controlByte)
      writeLittleEndian(out, value, sizeBytes)
    }

    private fun writeLittleEndian(out: ByteArrayOutputStream, value: Long, sizeBytes: Int) {
      repeat(sizeBytes) { shiftByte -> out.write(((value ushr (shiftByte * 8)) and 0xFF).toInt()) }
    }

    private fun parseUnsigned(
        value: String,
        max: Long,
        @StringRes invalidNumberMessageRes: Int,
    ): Long {
      val parsed = parseFlexibleLong(value, invalidNumberMessageRes)
      if (parsed < 0 || parsed > max) {
        throw ExplorerInputValidationException(invalidNumberMessageRes)
      }
      return parsed
    }

    private fun parseSigned(
        value: String,
        min: Long,
        max: Long,
        @StringRes invalidNumberMessageRes: Int,
    ): Long {
      val parsed = parseFlexibleLong(value, invalidNumberMessageRes)
      if (parsed < min || parsed > max) {
        throw ExplorerInputValidationException(invalidNumberMessageRes)
      }
      return parsed
    }

    private fun parseFlexibleLong(value: String, @StringRes invalidNumberMessageRes: Int): Long {
      val normalized = value.trim()
      if (normalized.isBlank()) {
        throw ExplorerInputValidationException(invalidNumberMessageRes)
      }
      return when {
        normalized.startsWith("0x", ignoreCase = true) ->
            normalized.substring(2).toLongOrNull(16)
                ?: throw ExplorerInputValidationException(invalidNumberMessageRes)
        normalized.startsWith("-0x", ignoreCase = true) ->
            -(normalized.substring(3).toLongOrNull(16)
                ?: throw ExplorerInputValidationException(invalidNumberMessageRes))
        else ->
            normalized.toLongOrNull()
                ?: throw ExplorerInputValidationException(invalidNumberMessageRes)
      }
    }

    private fun requireStringLength(bytes: ByteArray) {
      if (bytes.size > 0xFF) {
        throw ExplorerUnsupportedValueException()
      }
    }
  }
}
