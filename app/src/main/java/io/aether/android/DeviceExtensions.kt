// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import io.aether.android.chip.DEVICE_TYPE_COLOR_TEMPERATURE_LIGHT
import io.aether.android.chip.DEVICE_TYPE_DIMMABLE_LIGHT
import io.aether.android.chip.DEVICE_TYPE_EXTENDED_COLOR_LIGHT

/** Returns the Matter endpoint number for [device], defaulting to 1 for legacy records. */
fun endpointFor(device: MatterEndpoint): Int = if (device.endpointId != 0) device.endpointId else 1

/** Returns true if the device exposes a Level Control (dimmable) cluster. */
fun supportsLevelControl(device: MatterEndpoint): Boolean {
  val primaryType = device.deviceTypesList.firstOrNull()?.toLong() ?: 0L
  return device.supportsLevelControl ||
      primaryType == DEVICE_TYPE_DIMMABLE_LIGHT ||
      primaryType == DEVICE_TYPE_COLOR_TEMPERATURE_LIGHT ||
      primaryType == DEVICE_TYPE_EXTENDED_COLOR_LIGHT
}

/**
 * Returns true if the device exposes the Color Temperature attribute of the Color Control cluster.
 * The flag [MatterEndpoint.supportsColorTemperature] is set at commissioning time only when the
 * Color Control cluster's AttributeList confirms that the color temperature attribute (id 7) is
 * present. A device type fallback is also kept for legacy commissioned devices.
 */
fun supportsColorTemperature(device: MatterEndpoint): Boolean {
  val primaryType = device.deviceTypesList.firstOrNull()?.toLong() ?: 0L
  return device.supportsColorTemperature ||
      primaryType == DEVICE_TYPE_COLOR_TEMPERATURE_LIGHT ||
      primaryType == DEVICE_TYPE_EXTENDED_COLOR_LIGHT
}
