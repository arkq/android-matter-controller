// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import io.aether.android.matter.MatterType

internal enum class ExplorerInputKind {
  TEXT,
  UNSIGNED_INTEGER,
  SIGNED_INTEGER,
  BITMAP,
}

/** Maps Matter value types to the explorer input category used for UI constraints. */
internal fun MatterType.inputKind(): ExplorerInputKind =
    when (this) {
      MatterType.TYPE_MAP8,
      MatterType.TYPE_MAP16 -> ExplorerInputKind.BITMAP
      MatterType.TYPE_UINT8,
      MatterType.TYPE_ENUM8,
      MatterType.TYPE_UINT16,
      MatterType.TYPE_ENUM16,
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
      MatterType.TYPE_TLS_ENDPOINT_ID,
      MatterType.TYPE_UINT64,
      MatterType.TYPE_EPOCH_S,
      MatterType.TYPE_EPOCH_US,
      MatterType.TYPE_FABRIC_IDX,
      MatterType.TYPE_NODE_ID,
      MatterType.TYPE_SUBJECT_ID,
      MatterType.TYPE_TLSCAID,
      MatterType.TYPE_TLSCCDID -> ExplorerInputKind.UNSIGNED_INTEGER
      MatterType.TYPE_INT8,
      MatterType.TYPE_INT16,
      MatterType.TYPE_INT32,
      MatterType.TYPE_INT64 -> ExplorerInputKind.SIGNED_INTEGER
      else -> ExplorerInputKind.TEXT
    }

/** Validates whether [value] is acceptable for [type] while the user is typing. */
internal fun isValidInputForType(type: MatterType, value: String): Boolean =
    when (type.inputKind()) {
      ExplorerInputKind.TEXT -> true
      ExplorerInputKind.UNSIGNED_INTEGER -> isPartialUnsigned(value)
      ExplorerInputKind.SIGNED_INTEGER -> isPartialSigned(value)
      ExplorerInputKind.BITMAP -> isBitmapBinaryValue(value, allowEmpty = true)
    }

private fun isPartialUnsigned(value: String): Boolean {
  if (value.isEmpty()) {
    return true
  }
  return if (value.startsWith("0x") || value.startsWith("0X")) {
    value.length == 2 || value.substring(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
  } else {
    value.all(Char::isDigit)
  }
}

private fun isPartialSigned(value: String): Boolean {
  if (value.isEmpty() || value == "-") {
    return true
  }
  if (value.startsWith("-0x") || value.startsWith("-0X")) {
    return value.length == 3 ||
        value.substring(3).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
  }
  if (value.startsWith("0x") || value.startsWith("0X")) {
    return value.length == 2 ||
        value.substring(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
  }
  val digits = if (value.startsWith("-")) value.substring(1) else value
  return digits.isNotEmpty() && digits.all(Char::isDigit)
}

/**
 * Returns `true` if [value] contains only binary digits (`0` or `1`).
 *
 * Use [allowEmpty] for input-in-progress states where an empty field is temporarily valid.
 */
internal fun isBitmapBinaryValue(value: String, allowEmpty: Boolean = false): Boolean {
  if (value.isEmpty()) {
    return allowEmpty
  }
  return value.all { it == '0' || it == '1' }
}
