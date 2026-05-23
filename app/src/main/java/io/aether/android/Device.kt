// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

/** App-level device model used by UI/viewmodels, backed by node+endpoint proto state. */
data class Device(
    val nodeId: Long = 0L,
    val endpoint: Int = 1,
    val name: String = "",
    val vendorId: String = "",
    val vendorName: String = "",
    val productId: String = "",
    val productName: String = "",
    val deviceType: DeviceType = DeviceType.TYPE_UNKNOWN,
    val supportsLevelControl: Boolean = false,
    val supportsColorTemperature: Boolean = false,
    val on: Boolean = false,
    val level: Int = 0,
    val colorTemperature: Int = 0,
) {
  enum class DeviceType {
    TYPE_UNSPECIFIED,
    TYPE_LIGHT,
    TYPE_OUTLET,
    TYPE_DIMMABLE_LIGHT,
    TYPE_COLOR_TEMPERATURE_LIGHT,
    TYPE_EXTENDED_COLOR_LIGHT,
    TYPE_LIGHT_SWITCH,
    TYPE_UNKNOWN,
    UNRECOGNIZED,
  }

  fun toBuilder(): Builder = Builder(this)

  class Builder internal constructor(private var device: Device = Device()) {
    fun setNodeId(nodeId: Long) = apply { device = device.copy(nodeId = nodeId) }

    fun setEndpoint(endpoint: Int) = apply { device = device.copy(endpoint = endpoint) }

    fun setName(name: String) = apply { device = device.copy(name = name) }

    fun setVendorId(vendorId: String) = apply { device = device.copy(vendorId = vendorId) }

    fun setVendorName(vendorName: String) = apply { device = device.copy(vendorName = vendorName) }

    fun setProductId(productId: String) = apply { device = device.copy(productId = productId) }

    fun setProductName(productName: String) = apply {
      device = device.copy(productName = productName)
    }

    fun setDeviceType(deviceType: DeviceType) = apply {
      device = device.copy(deviceType = deviceType)
    }

    fun setSupportsLevelControl(supportsLevelControl: Boolean) = apply {
      device = device.copy(supportsLevelControl = supportsLevelControl)
    }

    fun setSupportsColorTemperature(supportsColorTemperature: Boolean) = apply {
      device = device.copy(supportsColorTemperature = supportsColorTemperature)
    }

    fun setOn(on: Boolean) = apply { device = device.copy(on = on) }

    fun setLevel(level: Int) = apply { device = device.copy(level = level) }

    fun setColorTemperature(colorTemperature: Int) = apply {
      device = device.copy(colorTemperature = colorTemperature)
    }

    fun build(): Device = device
  }

  companion object {
    fun newBuilder(): Builder = Builder()

    fun newBuilder(device: Device): Builder = Builder(device)
  }
}
