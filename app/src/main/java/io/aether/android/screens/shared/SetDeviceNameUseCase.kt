// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.shared

import io.aether.android.chip.ClustersHelper
import io.aether.android.data.DevicesRepository
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** Result of [SetDeviceNameUseCase.execute]. */
sealed class SetDeviceNameResult {
  /** Both the local DataStore update and the on-device NodeLabel write succeeded. */
  data object Success : SetDeviceNameResult()

  /** The local DataStore update failed; the on-device write was not attempted. */
  data class LocalError(val exception: Exception) : SetDeviceNameResult()

  /** The local DataStore update succeeded but the on-device NodeLabel write failed. */
  data class NodeLabelError(val exception: Exception) : SetDeviceNameResult()
}

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
   * network operation. Any exception thrown by [onLocalPersisted] propagates to the caller.
   *
   * @param deviceId the device to update
   * @param name the new name
   * @param onLocalPersisted optional callback invoked after local persistence succeeds
   * @return [SetDeviceNameResult.Success] on full success, [SetDeviceNameResult.LocalError] if the
   *   DataStore update fails, or [SetDeviceNameResult.NodeLabelError] if the on-device write fails
   */
  suspend fun execute(
    deviceId: Long,
    name: String,
    onLocalPersisted: suspend () -> Unit = {},
  ): SetDeviceNameResult {
    Timber.d("SetDeviceNameUseCase: deviceId [$deviceId] name [$name]")
    try {
      val device = devicesRepository.getDevice(deviceId)
      devicesRepository.updateDevice(device.toBuilder().setName(name).build())
    } catch (e: Exception) {
      Timber.e(e, "SetDeviceNameUseCase: failed to persist name locally")
      return SetDeviceNameResult.LocalError(e)
    }
    onLocalPersisted()
    return try {
      clustersHelper.writeBasicClusterNodeLabelAttribute(deviceId, name)
      SetDeviceNameResult.Success
    } catch (e: Exception) {
      Timber.e(e, "SetDeviceNameUseCase: failed to write NodeLabel")
      SetDeviceNameResult.NodeLabelError(e)
    }
  }
}
