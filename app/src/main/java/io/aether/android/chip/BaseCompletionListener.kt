// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import chip.devicecontroller.ChipDeviceController
import timber.log.Timber

/**
 * ChipDeviceController uses a CompletionListener for callbacks. This is a "base" default
 * implementation for that CompletionListener.
 */
abstract class BaseCompletionListener : ChipDeviceController.CompletionListener {
  override fun onConnectDeviceComplete() {
    Timber.d("BaseCompletionListener onConnectDeviceComplete()")
  }

  override fun onStatusUpdate(status: Int) {
    Timber.d("BaseCompletionListener onStatusUpdate(): status [${status}]")
  }

  override fun onPairingComplete(code: Int) {
    Timber.d("BaseCompletionListener onPairingComplete(): code [${code}]")
  }

  override fun onPairingDeleted(code: Int) {
    Timber.d("BaseCompletionListener onPairingDeleted(): code [${code}]")
  }

  override fun onCommissioningComplete(nodeId: Long, errorCode: Int) {
    Timber.d(
        "BaseCompletionListener onCommissioningComplete(): nodeId [${nodeId}] errorCode [${errorCode}]")
  }

  override fun onNotifyChipConnectionClosed() {
    Timber.d("BaseCompletionListener onNotifyChipConnectionClosed()")
  }

  override fun onCloseBleComplete() {
    Timber.d("BaseCompletionListener onCloseBleComplete()")
  }

  override fun onError(error: Throwable) {
    Timber.e(error, "BaseCompletionListener onError()")
  }

  override fun onOpCSRGenerationComplete(csr: ByteArray) {
    Timber.d("BaseCompletionListener onOpCSRGenerationComplete() csr [${csr}]")
  }

  override fun onReadCommissioningInfo(
      vendorId: Int,
      productId: Int,
      wifiEndpointId: Int,
      threadEndpointId: Int
  ) {
    Timber.d(
        "onReadCommissioningInfo: vendorId [${vendorId}]  productId [${productId}]  wifiEndpointId [${wifiEndpointId}] threadEndpointId [${threadEndpointId}]")
  }

  override fun onCommissioningStatusUpdate(nodeId: Long, stage: String?, errorCode: Int) {
    Timber.d(
        "onCommissioningStatusUpdate nodeId [${nodeId}]  stage [${stage}]  errorCode [${errorCode}]")
  }
}
