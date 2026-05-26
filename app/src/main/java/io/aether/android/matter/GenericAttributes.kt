// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.matter

object GenericAttributes {
  object GeneratedCommandList {
    const val ID = 0xFFF8
  }

  object AcceptedCommandList {
    const val ID = 0xFFF9
  }

  object EventList {
    const val ID = 0xFFFA
  }

  object AttributeList {
    const val ID = 0xFFFB
  }

  object FeatureMap {
    const val ID = 0xFFFC
  }

  object ClusterRevision {
    const val ID = 0xFFFD
  }
}

val GENERIC_ATTRIBUTES =
    mapOf<Int, AttributeInfo>(
        GenericAttributes.GeneratedCommandList.ID to
            AttributeInfo(
                name = "GeneratedCommandList",
                type = DataType.LIST_UINT32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.AcceptedCommandList.ID to
            AttributeInfo(
                name = "AcceptedCommandList",
                type = DataType.LIST_UINT32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.EventList.ID to
            AttributeInfo(
                name = "EventList",
                type = DataType.LIST_UINT32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.AttributeList.ID to
            AttributeInfo(
                name = "AttributeList",
                type = DataType.LIST_UINT32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.FeatureMap.ID to
            AttributeInfo(
                name = "FeatureMap",
                type = DataType.UINT32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.ClusterRevision.ID to
            AttributeInfo(
                name = "ClusterRevision",
                type = DataType.UINT16,
                readPrivilege = Privilege.VIEW,
            ),
    )
