// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.aether.android.DeviceState
import io.aether.android.DevicesState
import io.aether.android.getTimestampForNow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Singleton repository that updates the dynamic state of the devices on the homesampleapp fabric.
 */
@Singleton
class DevicesStateRepository @Inject constructor(@ApplicationContext context: Context) {

  // The datastore managed by DevicesStateRepository.
  private val devicesStateDataStore = context.devicesStateDataStore

  // The Flow to read data from the DataStore.
  val devicesStateFlow: Flow<DevicesState> =
      devicesStateDataStore.data.catch { exception ->
        // dataStore.data throws an IOException when an error is encountered when reading data
        if (exception is IOException) {
          Timber.e(exception, "Error reading devicesState.")
          emit(DevicesState.getDefaultInstance())
        } else {
          throw exception
        }
      }

  /** The latest device state update */
  private val _lastUpdatedDeviceState = MutableLiveData(DeviceState.getDefaultInstance())
  val lastUpdatedDeviceState: LiveData<DeviceState>
    get() = _lastUpdatedDeviceState

  suspend fun addDeviceState(deviceId: Long, isOnline: Boolean, isOn: Boolean, level: Int, colorTemperature: Int) {
    val newDeviceState =
        DeviceState.newBuilder()
            .setDeviceId(deviceId)
            .setDateCaptured(getTimestampForNow())
            .setOnline(isOnline)
            .setOn(isOn)
            .setLevel(level)
            .setColorTemperature(colorTemperature)
            .build()

    devicesStateDataStore.updateData { devicesState ->
      devicesState.toBuilder().addDevicesState(newDeviceState).build()
    }
    _lastUpdatedDeviceState.value = newDeviceState
  }

  suspend fun updateDeviceState(deviceId: Long, isOnline: Boolean, isOn: Boolean, level: Int, colorTemperature: Int) {
    val newDeviceState =
        DeviceState.newBuilder()
            .setDeviceId(deviceId)
            .setDateCaptured(getTimestampForNow())
            .setOnline(isOnline)
            .setOn(isOn)
            .setLevel(level)
            .setColorTemperature(colorTemperature)
            .build()

    val devicesState = devicesStateFlow.first()
    val devicesStateCount = devicesState.devicesStateCount
    var updateDone = false
    for (index in 0 until devicesStateCount) {
      val deviceState = devicesState.getDevicesState(index)
      if (deviceId == deviceState.deviceId) {
        devicesStateDataStore.updateData { devicesStateList ->
          devicesStateList.toBuilder().setDevicesState(index, newDeviceState).build()
        }
        _lastUpdatedDeviceState.value = newDeviceState
        updateDone = true
        break
      }
    }
    if (!updateDone) {
      Timber.w(
          "We did not find device [${deviceId}] in devicesStateRepository; it should have been there???")
      addDeviceState(deviceId, isOnline = isOnline, isOn = isOn, level = level, colorTemperature = colorTemperature)
    }
  }

  suspend fun loadDeviceState(deviceId: Long) : DeviceState? {
    val devicesState = devicesStateFlow.first()
    val devicesStateCount = devicesState.devicesStateCount
    var updateDone = false
    for (index in 0 until devicesStateCount) {
      val deviceState = devicesState.getDevicesState(index)
      if (deviceId == deviceState.deviceId) {
        return deviceState
      }
    }
    return null
  }

  suspend fun getAllDevicesState(): DevicesState {
    return devicesStateFlow.first()
  }

  suspend fun clearAllData() {
    devicesStateDataStore.updateData { devicesStateList ->
      devicesStateList.toBuilder().clear().build()
    }
  }
}
