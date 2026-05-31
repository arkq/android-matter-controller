// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

/** App-level container for commissioned devices. */
data class Devices(val devicesList: List<Device> = emptyList()) {
  val devicesCount: Int
    get() = devicesList.size

  class Builder internal constructor(private val devices: MutableList<Device> = mutableListOf()) {
    fun addDevices(device: Device) = apply { devices.add(device) }

    fun addAllDevices(newDevices: Iterable<Device>) = apply { devices.addAll(newDevices) }

    fun setDevices(index: Int, device: Device) = apply { devices[index] = device }

    fun removeDevices(index: Int) = apply { devices.removeAt(index) }

    fun clearDevices() = apply { devices.clear() }

    fun build(): Devices = Devices(devices.toList())
  }

  companion object {
    fun newBuilder(): Builder = Builder()

    fun getDefaultInstance(): Devices = Devices()
  }
}
