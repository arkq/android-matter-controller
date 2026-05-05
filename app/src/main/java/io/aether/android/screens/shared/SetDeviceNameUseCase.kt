// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.shared

import io.aether.android.chip.ClustersHelper
import io.aether.android.data.DevicesRepository
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Persists a device name in the local DataStore and writes it to the device's BasicInformation
 * NodeLabel attribute. Used both during commissioning (HomeViewModel) and when renaming an
 * already-commissioned device (DeviceViewModel) so both code paths share the same logic.
 */
@Singleton
class SetDeviceNameUseCase
@Inject
constructor(
  private val devicesRepository: DevicesRepository,
  private val clustersHelper: ClustersHelper,
) {
  /**
   * Updates the device name in the local DataStore and writes it to the on-device BasicInformation
   * NodeLabel attribute.
   *
   * [onLocalPersisted] is invoked after the local DataStore update succeeds but before the
   * on-device write, so callers can update UI state immediately without waiting for the slower
   * network operation.
   *
   * @param deviceId the device to update
   * @param name the new name
   * @param onLocalPersisted optional callback invoked after local persistence succeeds
   * @return null on full success, or the [Exception] from either the DataStore update or the
   *   on-device NodeLabel write
   */
  suspend fun execute(
    deviceId: Long,
    name: String,
    onLocalPersisted: suspend () -> Unit = {},
  ): Exception? {
    Timber.d("SetDeviceNameUseCase: deviceId [$deviceId] name [$name]")
    try {
      val device = devicesRepository.getDevice(deviceId)
      devicesRepository.updateDevice(device.toBuilder().setName(name).build())
    } catch (e: Exception) {
      Timber.e(e, "SetDeviceNameUseCase: failed to persist name locally")
      return e
    }
    onLocalPersisted()
    return try {
      clustersHelper.writeBasicClusterNodeLabelAttribute(deviceId, name)
      null
    } catch (e: Exception) {
      Timber.e(e, "SetDeviceNameUseCase: failed to write NodeLabel")
      e
    }
  }
}
