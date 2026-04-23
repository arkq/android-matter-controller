// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

/** Returns the Matter node ID for [device], falling back to [Device.deviceId] for legacy records. */
fun nodeIdFor(device: Device): Long = if (device.nodeId != 0L) device.nodeId else device.deviceId

/** Returns the Matter endpoint number for [device], defaulting to 1 for legacy records. */
fun endpointFor(device: Device): Int = if (device.endpoint != 0) device.endpoint else 1

/** Returns true if the device exposes a Level Control (dimmable) cluster. */
fun supportsLevelControl(device: Device): Boolean {
  return device.supportsLevelControl ||
    device.deviceType == Device.DeviceType.TYPE_DIMMABLE_LIGHT ||
    device.deviceType == Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT ||
    device.deviceType == Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT
}

/**
 * Returns true if the device exposes the Color Temperature attribute of the Color Control cluster.
 * The flag [Device.supportsColorTemperature] is set at commissioning time only when the Color
 * Control cluster's AttributeList confirms that the color temperature attribute (id 7) is present.
 * A device type fallback is also kept for legacy commissioned devices.
 */
fun supportsColorTemperature(device: Device): Boolean {
  return device.supportsColorTemperature ||
    device.deviceType == Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT ||
    device.deviceType == Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT
}
