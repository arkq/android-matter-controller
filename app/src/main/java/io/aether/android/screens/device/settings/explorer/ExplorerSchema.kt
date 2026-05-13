// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import io.aether.android.chip.MatterConstants

enum class ExplorerValueType {
  BOOLEAN,
  STRING,
  UINT8,
  UINT16,
}

data class ExplorerAttributeDefinition(
    val id: Long,
    val name: String,
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
  private val knownClustersById: Map<Long, ExplorerClusterDefinition> =
      MatterConstants.ExplorerKnownClustersById.mapValues { (_, cluster) ->
        ExplorerClusterDefinition(
            clusterId = cluster.clusterId,
            attributes =
                cluster.attributes.map { attr ->
                  ExplorerAttributeDefinition(
                      id = attr.id,
                      name = attr.name,
                      writable = attr.writable,
                  )
                },
            commands =
                cluster.commands.map { command ->
                  ExplorerCommandDefinition(
                      id = command.id,
                      name = command.name,
                      arguments =
                          command.arguments.map { arg ->
                            ExplorerCommandArgumentDefinition(
                                key = arg.key,
                                name = arg.name,
                                type =
                                    when (arg.key) {
                                      "level" -> ExplorerValueType.UINT8
                                      "transitionTime" -> ExplorerValueType.UINT16
                                      else -> ExplorerValueType.STRING
                                    },
                                minValue = arg.minValue,
                                maxValue = arg.maxValue,
                            )
                          },
                  )
                },
            events =
                cluster.events.map { event ->
                  ExplorerEventDefinition(
                      id = event.id,
                      name = event.name,
                  )
                },
        )
      }

  fun findCluster(clusterId: Long): ExplorerClusterDefinition? = knownClustersById[clusterId]
}
