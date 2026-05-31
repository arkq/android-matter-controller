// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import io.aether.android.matter.Devices
import io.aether.android.matter.EndpointId
import io.aether.android.matter.toEndpointId

/** Returns [MatterEndpoint.endpointId] as the strongly typed [EndpointId]. */
fun MatterEndpoint.endpointIdTyped(): EndpointId = endpointId.toEndpointId()

/** Returns true if the device exposes a Level Control (dimmable) cluster. */
fun supportsLevelControl(device: MatterEndpoint): Boolean {
  val primaryType = device.deviceTypesList.firstOrNull()?.toLong() ?: 0L
  return device.supportsLevelControl ||
      primaryType == Devices.DimmableLight.ID.toLong() ||
      primaryType == Devices.ColorTemperatureLight.ID.toLong() ||
      primaryType == Devices.ExtendedColorLight.ID.toLong()
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
      primaryType == Devices.ColorTemperatureLight.ID.toLong() ||
      primaryType == Devices.ExtendedColorLight.ID.toLong()
}
