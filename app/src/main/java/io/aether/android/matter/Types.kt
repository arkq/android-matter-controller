// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.matter

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R

data class ClusterInfo(
    val name: String,
    val attributes: Map<UInt, AttributeInfo>,
    val commandsIncoming: Map<UInt, CommandInfo>,
    val commandsOutgoing: Map<UInt, CommandInfo>,
    val events: Map<UInt, EventInfo>,
)

data class AttributeInfo(
    val name: String,
    val type: DataType,
    val readPrivilege: Privilege = Privilege.NONE,
    val writePrivilege: Privilege = Privilege.NONE,
)

data class ParameterInfo(
    val name: String,
    val type: DataType,
)

data class CommandInfo(
    val name: String,
    val parameters: Map<UInt, ParameterInfo>,
    val privilege: Privilege = Privilege.NONE,
)

data class EventInfo(
    val name: String,
)

enum class Privilege(val label: UInt) {
  NONE(0u),
  VIEW(1u),
  OPERATE(3u),
  MANAGE(4u),
  ADMINISTER(5u);

  @Composable
  fun toLabel(): String {
    return when (this) {
      NONE -> stringResource(R.string.attr_access_privilege_none)
      VIEW -> stringResource(R.string.attr_access_privilege_view)
      OPERATE -> stringResource(R.string.attr_access_privilege_operate)
      MANAGE -> stringResource(R.string.attr_access_privilege_manage)
      ADMINISTER -> stringResource(R.string.attr_access_privilege_administer)
    }
  }
}
