// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import io.aether.android.chip.DataModelLoader
import io.aether.android.matter.AttributeId
import io.aether.android.matter.ClusterId
import io.aether.android.matter.CommandId
import io.aether.android.matter.EventId
import io.aether.android.matter.MatterDataModel
import io.aether.android.matter.MatterPrivilege
import io.aether.android.matter.MatterType
import io.aether.android.matter.toAttributeId
import io.aether.android.matter.toClusterId
import io.aether.android.matter.toCommandId

data class ExplorerAttributeDefinition(
    val id: AttributeId,
    val type: MatterType = MatterType.TYPE_UNKNOWN,
    val name: String,
    val readPrivilege: MatterPrivilege = MatterPrivilege.PRIVILEGE_UNKNOWN,
    val writePrivilege: MatterPrivilege = MatterPrivilege.PRIVILEGE_UNKNOWN,
)

data class ExplorerCommandArgumentDefinition(
    val key: String,
    val name: String,
    val type: MatterType = MatterType.TYPE_UNKNOWN,
)

data class ExplorerCommandDefinition(
    val id: CommandId,
    val name: String,
    val arguments: List<ExplorerCommandArgumentDefinition> = emptyList(),
)

data class ExplorerEventDefinition(
    val id: EventId,
    val name: String,
)

data class ExplorerClusterDefinition(
    val clusterId: ClusterId,
    val attributes: List<ExplorerAttributeDefinition> = emptyList(),
    val commands: List<ExplorerCommandDefinition> = emptyList(),
    val events: List<ExplorerEventDefinition> = emptyList(),
)

object ExplorerSchema {
  fun buildKnownClustersById(
      model: MatterDataModel,
      genericAttributes: List<DataModelLoader.GenericAttributeDefinition>,
  ): Map<ClusterId, ExplorerClusterDefinition> =
      model.clustersList.associate { cluster ->
        cluster.id.toLong().toClusterId() to
            ExplorerClusterDefinition(
                clusterId = cluster.id.toLong().toClusterId(),
                attributes =
                    (cluster.attributesList.map { attr ->
                          ExplorerAttributeDefinition(
                              id = attr.id.toLong().toAttributeId(),
                              type = attr.type,
                              name = attr.name,
                              readPrivilege = attr.readPrivilege,
                              writePrivilege = attr.writePrivilege,
                          )
                        } +
                            genericAttributes.map {
                              ExplorerAttributeDefinition(
                                  id = it.id,
                                  name = it.name,
                                  type = it.typeValue,
                                  readPrivilege = it.readPrivilege,
                                  writePrivilege = it.writePrivilege,
                              )
                            })
                        .associateBy { it.id }
                        .values
                        .sortedBy { it.id },
                commands =
                    cluster.commandsList.map { command ->
                      ExplorerCommandDefinition(
                          id = command.id.toLong().toCommandId(),
                          name = command.name,
                          arguments =
                              command.parametersList.map { arg ->
                                ExplorerCommandArgumentDefinition(
                                    key = arg.name,
                                    name = arg.name,
                                    type = arg.type,
                                )
                              },
                      )
                    },
            )
      }
}
