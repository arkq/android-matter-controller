// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.matter

@OptIn(ExperimentalStdlibApi::class)
internal val hexFormat = HexFormat {
  upperCase = true
  number.prefix = "0x"
}

/**
 * A Matter Fabric ID (uint64 per spec). Formats as 16-digit uppercase hex, e.g. 0x0000000000000001.
 */
@JvmInline
value class FabricId(val value: ULong) : Comparable<FabricId> {
  override fun toString(): String = value.toHexString(hexFormat)

  override fun compareTo(other: FabricId): Int = value.compareTo(other.value)
}

/**
 * A Matter Node ID (uint64 per spec). Formats as 16-digit uppercase hex, e.g. 0x0000000000000001.
 */
@JvmInline
value class NodeId(val value: ULong) : Comparable<NodeId> {
  override fun toString(): String = value.toHexString(hexFormat)

  override fun compareTo(other: NodeId): Int = value.compareTo(other.value)
}

/** A Matter Cluster ID (uint32 per spec). Formats as 4-digit (≤0xFFFF) or 8-digit hex. */
@JvmInline
value class ClusterId(val value: UInt) : Comparable<ClusterId> {
  override fun toString(): String =
      when {
        value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
        else -> value.toHexString(hexFormat)
      }

  override fun compareTo(other: ClusterId): Int = value.compareTo(other.value)
}

/** A Matter Attribute ID (uint32 per spec). Formats as 4-digit (≤0xFFFF) or 8-digit hex. */
@JvmInline
value class AttributeId(val value: UInt) : Comparable<AttributeId> {
  override fun toString(): String =
      when {
        value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
        else -> value.toHexString(hexFormat)
      }

  override fun compareTo(other: AttributeId): Int = value.compareTo(other.value)
}

/** A Matter Command ID (uint32 per spec). Formats as 4-digit (≤0xFFFF) or 8-digit hex. */
@JvmInline
value class CommandId(val value: UInt) : Comparable<CommandId> {
  override fun toString(): String =
      when {
        value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
        else -> value.toHexString(hexFormat)
      }

  override fun compareTo(other: CommandId): Int = value.compareTo(other.value)
}

/** A Matter Event ID (uint32 per spec). Formats as 4-digit (≤0xFFFF) or 8-digit hex. */
@JvmInline
value class EventId(val value: UInt) : Comparable<EventId> {
  override fun toString(): String =
      when {
        value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
        else -> value.toHexString(hexFormat)
      }

  override fun compareTo(other: EventId): Int = value.compareTo(other.value)
}

/** A Matter Device Type ID (uint32 per spec). Formats as 4-digit (≤0xFFFF) or 8-digit hex. */
@JvmInline
value class DeviceTypeId(val value: UInt) : Comparable<DeviceTypeId> {
  override fun toString(): String =
      when {
        value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
        else -> value.toHexString(hexFormat)
      }

  override fun compareTo(other: DeviceTypeId): Int = value.compareTo(other.value)
}

/** A Matter Vendor ID (uint16 per spec). Formats as 4-digit uppercase hex, e.g. 0x1011. */
@JvmInline
value class VendorId(val value: UShort) : Comparable<VendorId> {
  override fun toString(): String = value.toHexString(hexFormat)

  override fun compareTo(other: VendorId): Int = value.compareTo(other.value)
}

/** A Matter Product ID (uint16 per spec). Formats as 4-digit uppercase hex, e.g. 0x8001. */
@JvmInline
value class ProductId(val value: UShort) : Comparable<ProductId> {
  override fun toString(): String = value.toHexString(hexFormat)

  override fun compareTo(other: ProductId): Int = value.compareTo(other.value)
}

// ---------------------------------------------------------------------------
// Conversion extensions from Long/Int (used at Chip SDK and navigation boundaries)
// ---------------------------------------------------------------------------

fun Long.toFabricId(): FabricId = FabricId(toULong())

fun Long.toNodeId(): NodeId = NodeId(toULong())

fun Long.toClusterId(): ClusterId = ClusterId(toUInt())

fun Long.toAttributeId(): AttributeId = AttributeId(toUInt())

fun Long.toCommandId(): CommandId = CommandId(toUInt())

fun Long.toEventId(): EventId = EventId(toUInt())

fun Long.toDeviceTypeId(): DeviceTypeId = DeviceTypeId(toUInt())

fun Int.toVendorId(): VendorId = VendorId(toUShort())

fun Int.toProductId(): ProductId = ProductId(toUShort())

// ---------------------------------------------------------------------------
// Conversion extensions back to Long/Int (used at Chip SDK boundary)
// ---------------------------------------------------------------------------

fun FabricId.toLong(): Long = value.toLong()

fun NodeId.toLong(): Long = value.toLong()

fun ClusterId.toLong(): Long = value.toLong()

fun AttributeId.toLong(): Long = value.toLong()

fun CommandId.toLong(): Long = value.toLong()

fun EventId.toLong(): Long = value.toLong()

fun DeviceTypeId.toLong(): Long = value.toLong()

fun VendorId.toInt(): Int = value.toInt()

fun ProductId.toInt(): Int = value.toInt()
