// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.annotation.StringRes
import io.aether.android.R

enum class ExplorerValueType {
  BOOLEAN,
  STRING,
  UINT8,
  UINT16,
}

data class ExplorerAttributeDefinition(
    val id: Long,
    @field:StringRes @param:StringRes val nameRes: Int,
    val writable: Boolean = false,
)

data class ExplorerCommandArgumentDefinition(
    val key: String,
    @field:StringRes @param:StringRes val nameRes: Int,
    val type: ExplorerValueType,
    val minValue: Int? = null,
    val maxValue: Int? = null,
)

data class ExplorerCommandDefinition(
    val id: Long,
    @field:StringRes @param:StringRes val nameRes: Int,
    val arguments: List<ExplorerCommandArgumentDefinition> = emptyList(),
)

data class ExplorerEventDefinition(
    val id: Long,
    @field:StringRes @param:StringRes val nameRes: Int,
)

data class ExplorerClusterDefinition(
    val clusterId: Long,
    val attributes: List<ExplorerAttributeDefinition> = emptyList(),
    val commands: List<ExplorerCommandDefinition> = emptyList(),
    val events: List<ExplorerEventDefinition> = emptyList(),
)

/**
 * Schema definitions sourced from Matter specification cluster definitions.
 *
 * This is used to provide readable names and argument constraints for known commands/attributes.
 */
object ExplorerSchema {
  private val knownClusters =
      listOf(
          ExplorerClusterDefinition(
              clusterId = 0x0028,
              attributes =
                  listOf(
                      ExplorerAttributeDefinition(
                          id = 0x0005,
                          nameRes = R.string.device_explorer_attribute_node_label,
                          writable = true,
                      )
                  ),
          ),
          ExplorerClusterDefinition(
              clusterId = 0x0006,
              attributes =
                  listOf(
                      ExplorerAttributeDefinition(
                          id = 0x0000,
                          nameRes = R.string.device_explorer_attribute_on_off,
                      )
                  ),
              commands =
                  listOf(
                      ExplorerCommandDefinition(
                          id = 0x0000,
                          nameRes = R.string.device_explorer_command_off,
                      ),
                      ExplorerCommandDefinition(
                          id = 0x0001,
                          nameRes = R.string.device_explorer_command_on,
                      ),
                      ExplorerCommandDefinition(
                          id = 0x0002,
                          nameRes = R.string.device_explorer_command_toggle,
                      ),
                  ),
          ),
          ExplorerClusterDefinition(
              clusterId = 0x0008,
              attributes =
                  listOf(
                      ExplorerAttributeDefinition(
                          id = 0x0000,
                          nameRes = R.string.device_explorer_attribute_current_level,
                      )
                  ),
              commands =
                  listOf(
                      ExplorerCommandDefinition(
                          id = 0x0000,
                          nameRes = R.string.device_explorer_command_move_to_level,
                          arguments =
                              listOf(
                                  ExplorerCommandArgumentDefinition(
                                      key = "level",
                                      nameRes = R.string.device_explorer_argument_level,
                                      type = ExplorerValueType.UINT8,
                                      minValue = 0,
                                      maxValue = 254,
                                  ),
                                  ExplorerCommandArgumentDefinition(
                                      key = "transitionTime",
                                      nameRes = R.string.device_explorer_argument_transition_time,
                                      type = ExplorerValueType.UINT16,
                                      minValue = 0,
                                      maxValue = 65535,
                                  ),
                              ),
                      )
                  ),
          ),
      )

  private val knownClustersById = knownClusters.associateBy { it.clusterId }

  fun findCluster(clusterId: Long): ExplorerClusterDefinition? = knownClustersById[clusterId]
}
