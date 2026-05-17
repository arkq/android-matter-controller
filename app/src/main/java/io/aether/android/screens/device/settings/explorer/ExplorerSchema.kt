// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import io.aether.android.chip.DataModelLoader
import io.aether.android.matter.MatterDataModel
import io.aether.android.matter.MatterPrivilege
import io.aether.android.matter.MatterType
import java.util.Locale

data class ExplorerAttributeDefinition(
    val id: Long,
    val type: MatterType = MatterType.TYPE_UNKNOWN,
    val name: String,
    val readPrivilegeLabel: String? = null,
    val writePrivilegeLabel: String? = null,
)

data class ExplorerCommandArgumentDefinition(
    val key: String,
    val name: String,
    val type: MatterType = MatterType.TYPE_UNKNOWN,
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
  fun buildKnownClustersById(
      model: MatterDataModel,
      genericAttributes: List<DataModelLoader.GenericAttributeDefinition>,
  ): Map<Long, ExplorerClusterDefinition> =
      model.clustersList.associate { cluster ->
        cluster.id.toLong() to
            ExplorerClusterDefinition(
                clusterId = cluster.id.toLong(),
                attributes =
                    (cluster.attributesList.map { attr ->
                          ExplorerAttributeDefinition(
                              id = attr.id.toLong(),
                              type = attr.type,
                              name = attr.name,
                              readPrivilegeLabel = privilegeLabel(attr.readPrivilege),
                              writePrivilegeLabel = privilegeLabel(attr.writePrivilege),
                          )
                        } +
                            genericAttributes.map {
                              ExplorerAttributeDefinition(
                                  id = it.id,
                                  name = it.name,
                                  type = it.typeValue,
                                  readPrivilegeLabel = privilegeLabel(it.readPrivilege),
                                  writePrivilegeLabel = privilegeLabel(it.writePrivilege),
                              )
                            })
                        .associateBy { it.id }
                        .values
                        .sortedBy { it.id },
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
                                    type = arg.type,
                                    minValue = knownRange?.first,
                                    maxValue = knownRange?.second,
                                )
                              },
                      )
                    },
            )
      }

  private fun knownRangeForArgument(name: String): Pair<Int, Int>? =
      when (name.lowercase(Locale.ROOT)) {
        "level" -> 0 to 254
        "transitiontime" -> 0 to 65535
        else -> null
      }

  private fun privilegeLabel(privilege: MatterPrivilege?): String? =
      when (val resolvedPrivilege = privilege ?: MatterPrivilege.PRIVILEGE_UNKNOWN) {
        MatterPrivilege.PRIVILEGE_UNKNOWN -> null
        MatterPrivilege.PRIVILEGE_ADMIN -> "Administer"
        MatterPrivilege.PRIVILEGE_MANAGE -> "Manage"
        MatterPrivilege.PRIVILEGE_OPERATE -> "Operate"
        MatterPrivilege.PRIVILEGE_VIEW -> "View"
        else -> "Privilege ${resolvedPrivilege.name}"
      }
}
