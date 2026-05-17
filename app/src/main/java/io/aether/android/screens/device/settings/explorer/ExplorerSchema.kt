// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import io.aether.android.matter.MatterDataModel
import io.aether.android.matter.MatterType
import java.util.Locale

enum class ExplorerValueType {
  UNKNOWN,
  BOOLEAN,
  STRING,
  UINT8,
  UINT16,
  UINT32,
  UINT64,
  INT8,
  INT16,
  INT32,
  INT64,
}

data class ExplorerAttributeDefinition(
    val id: Long,
    val name: String,
    val type: ExplorerValueType = ExplorerValueType.UNKNOWN,
    val readPrivilegeLabel: String? = null,
    val writePrivilegeLabel: String? = null,
    val writable: Boolean = false,
)

data class ExplorerCommandArgumentDefinition(
    val key: String,
    val name: String,
    val type: ExplorerValueType,
    val minValue: Int? = null,
    val maxValue: Int? = null,
)

data class ExplorerCommandDefinition(
    val id: Long,
    val name: String,
    val arguments: List<ExplorerCommandArgumentDefinition> = emptyList(),
)

data class ExplorerEventDefinition(
    val id: Long,
    val name: String,
)

data class ExplorerClusterDefinition(
    val clusterId: Long,
    val attributes: List<ExplorerAttributeDefinition> = emptyList(),
    val commands: List<ExplorerCommandDefinition> = emptyList(),
    val events: List<ExplorerEventDefinition> = emptyList(),
)

object ExplorerSchema {
  fun buildKnownClustersById(model: MatterDataModel): Map<Long, ExplorerClusterDefinition> =
      model.clustersList.associate { cluster ->
        cluster.id.toLong() to
            ExplorerClusterDefinition(
                clusterId = cluster.id.toLong(),
                attributes =
                    cluster.attributesList.map { attr ->
                      ExplorerAttributeDefinition(
                          id = attr.id.toLong(),
                          name = attr.name,
                          type = valueTypeForMatterType(attr.typeValue),
                          readPrivilegeLabel = privilegeLabel(attr.readPrivilegeValue),
                          writePrivilegeLabel = privilegeLabel(attr.writePrivilegeValue),
                          writable = attr.writePrivilegeValue != 0,
                      )
                    },
                commands =
                    cluster.commandsList.map { command ->
                      ExplorerCommandDefinition(
                          id = command.id.toLong(),
                          name = command.name,
                          arguments =
                              command.parametersList.map { arg ->
                                val knownRange = knownRangeForArgument(arg.name)
                                ExplorerCommandArgumentDefinition(
                                    key = arg.name,
                                    name = arg.name,
                                    type = valueTypeForMatterType(arg.typeValue),
                                    minValue = knownRange?.first,
                                    maxValue = knownRange?.second,
                                )
                              },
                      )
                    },
            )
      }

  private fun valueTypeForMatterType(typeValue: Int): ExplorerValueType {
    return when (MatterType.forNumber(typeValue) ?: MatterType.TYPE_UNKNOWN) {
      MatterType.TYPE_BOOLEAN -> ExplorerValueType.BOOLEAN
      MatterType.TYPE_CHAR_STRING,
      MatterType.TYPE_OCTET_STRING,
      MatterType.TYPE_IPV4ADR,
      MatterType.TYPE_IPV6ADR,
      MatterType.TYPE_NAMESPACE -> ExplorerValueType.STRING
      MatterType.TYPE_INT8U,
      MatterType.TYPE_BITMAP8,
      MatterType.TYPE_ENUM8 -> ExplorerValueType.UINT8
      MatterType.TYPE_INT16U,
      MatterType.TYPE_BITMAP16,
      MatterType.TYPE_ENUM16 -> ExplorerValueType.UINT16
      MatterType.TYPE_INT24U,
      MatterType.TYPE_INT32U,
      MatterType.TYPE_BITMAP32,
      MatterType.TYPE_ACTION_ID,
      MatterType.TYPE_ATTR_ID,
      MatterType.TYPE_CLUSTER_ID,
      MatterType.TYPE_CMD_ID,
      MatterType.TYPE_DEVTYPE_ID,
      MatterType.TYPE_EVENT_ID,
      MatterType.TYPE_FABRIC_IDX,
      MatterType.TYPE_GROUP_ID -> ExplorerValueType.UINT32
      MatterType.TYPE_INT40U,
      MatterType.TYPE_INT48U,
      MatterType.TYPE_INT56U,
      MatterType.TYPE_INT64U,
      MatterType.TYPE_BITMAP64,
      MatterType.TYPE_FABRIC_ID,
      MatterType.TYPE_NODE_ID,
      MatterType.TYPE_EPOCH_S,
      MatterType.TYPE_EPOCH_US -> ExplorerValueType.UINT64
      MatterType.TYPE_INT8S -> ExplorerValueType.INT8
      MatterType.TYPE_INT16S -> ExplorerValueType.INT16
      MatterType.TYPE_INT24S,
      MatterType.TYPE_INT32S -> ExplorerValueType.INT32
      MatterType.TYPE_INT40S,
      MatterType.TYPE_INT48S,
      MatterType.TYPE_INT56S,
      MatterType.TYPE_INT64S -> ExplorerValueType.INT64
      else -> ExplorerValueType.UNKNOWN
    }
  }

  private fun knownRangeForArgument(name: String): Pair<Int, Int>? =
      when (name.lowercase(Locale.ROOT)) {
        "level" -> 0 to 254
        "transitiontime" -> 0 to 65535
        else -> null
      }

  private fun privilegeLabel(privilege: Int): String? =
      when (privilege) {
        0 -> null
        1 -> "Administer"
        2 -> "Manage"
        3 -> "Operate"
        4 -> "View"
        else -> "Privilege $privilege"
      }
}
