// SPDX-FileCopyrightText: 2020 The Android Open Source Project
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.Device
import io.aether.android.Devices
import io.aether.android.formatNodeId
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Singleton repository that updates and persists the set of devices in the homesampleapp fabric.
 */
@Singleton
class DevicesRepository @Inject constructor(@ApplicationContext context: Context) {

  // The datastore managed by DevicesRepository.
  private val devicesDataStore = context.devicesDataStore

  // The Flow to read data from the DataStore.
  val devicesFlow: Flow<Devices> =
      devicesDataStore.data.catch { exception ->
        // dataStore.data throws an IOException when an error is encountered when reading data
        if (exception is IOException) {
          Timber.e(exception, "Error reading devices.")
          emit(Devices.getDefaultInstance())
        } else {
          throw exception
        }
      }

  suspend fun addDevice(device: Device) {
    Timber.d("addDevice: device [${device}]")
    devicesDataStore.updateData { devices -> devices.toBuilder().addDevices(device).build() }
  }

  suspend fun updateDevice(device: Device) {
    Timber.d("updateDevice: device [${device}]")
    val index = getIndex(device.nodeId)
    devicesDataStore.updateData { devices -> devices.toBuilder().setDevices(index, device).build() }
  }

  suspend fun updateDeviceType(nodeId: Long, deviceType: Device.DeviceType) {
    Timber.d("updateDeviceType: nodeId [${formatNodeId(nodeId)}] deviceType [${deviceType}]")
    val (index, device) = getIndexAndDevice(nodeId)
    if (index == null) {
      Timber.e(
          "Unable to get device information to update its type: nodeId [${formatNodeId(nodeId)}] deviceType [${deviceType}]"
      )
      return
    }
    val deviceBuilder = Device.newBuilder(device)
    deviceBuilder.deviceType = deviceType
    devicesDataStore.updateData { devices ->
      devices.toBuilder().setDevices(index, deviceBuilder.build()).build()
    }
  }

  suspend fun removeDevice(nodeId: Long) {
    Timber.d("removeDevice: nodeId [${formatNodeId(nodeId)}]")
    val index = getIndex(nodeId)
    if (index == -1) {
      throw Exception("Device not found: ${formatNodeId(nodeId)}")
    }
    devicesDataStore.updateData { devicesList ->
      devicesList.toBuilder().removeDevices(index).build()
    }
  }

  suspend fun getDevice(nodeId: Long): Device = getDeviceByNodeId(nodeId)

  suspend fun getDeviceByNodeId(nodeId: Long): Device {
    return getAllDevices().devicesList.firstOrNull { it.nodeId == nodeId }
        ?: throw Exception("Device not found for nodeId: ${formatNodeId(nodeId)}")
  }

  suspend fun getDevicesByNodeId(nodeId: Long): List<Device> {
    return getAllDevices().devicesList.filter { it.nodeId == nodeId }
  }

  suspend fun getAllDevices(): Devices {
    return devicesFlow.first()
  }

  suspend fun clearAllData() {
    devicesDataStore.updateData { devicesList -> devicesList.toBuilder().clear().build() }
  }

  private suspend fun getIndex(nodeId: Long): Int {
    val devices = devicesFlow.first()
    return getIndex(devices, nodeId)
  }

  private fun getIndex(devices: Devices, nodeId: Long): Int {
    val devicesCount = devices.devicesCount
    for (index in 0 until devicesCount) {
      val device = devices.getDevices(index)
      if (device.nodeId == nodeId) {
        return index
      }
    }
    return -1
  }

  private suspend fun getIndexAndDevice(nodeId: Long): Pair<Int?, Device?> {
    val devices = devicesFlow.first()
    return getIndexAndDevice(devices, nodeId)
  }

  private fun getIndexAndDevice(devices: Devices, nodeId: Long): Pair<Int?, Device?> {
    val devicesCount = devices.devicesCount
    for (index in 0 until devicesCount) {
      val device = devices.getDevices(index)
      if (device.nodeId == nodeId) {
        return index to device
      }
    }
    return null to null
  }
}
