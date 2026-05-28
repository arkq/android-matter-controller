// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.matter

object GenericAttributes {
  object GeneratedCommandList {
    const val ID = 0xFFF8u
  }

  object AcceptedCommandList {
    const val ID = 0xFFF9u
  }

  object EventList {
    const val ID = 0xFFFAu
  }

  object AttributeList {
    const val ID = 0xFFFBu
  }

  object FeatureMap {
    const val ID = 0xFFFCu
  }

  object ClusterRevision {
    const val ID = 0xFFFDu
  }
}

val GENERIC_ATTRIBUTES =
    mapOf<UInt, AttributeInfo>(
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
