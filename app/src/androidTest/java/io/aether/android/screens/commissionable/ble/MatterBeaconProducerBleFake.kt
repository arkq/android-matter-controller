// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.commissionable.ble

import android.content.Context
import io.aether.android.screens.commissionable.MatterBeacon
import io.aether.android.screens.commissionable.MatterBeaconProducer
import io.aether.android.screens.commissionable.Transport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/** [MatterBeaconProducer] which emits BLE beacons as they are discovered. */
class MatterBeaconProducerBleFake
@Inject
constructor(@ApplicationContext private val context: Context) : MatterBeaconProducer {

  private val EMIT_DELAY_MS = 1000L

  override fun getBeaconsFlow(): Flow<MatterBeacon> = callbackFlow {
    Timber.d("Starting BLE discovery -- NATIVE")

    var count = 0
    while (true) {
      val beacon =
          MatterBeacon(
              name = "BLE-test-${count}",
              vendorId = 1,
              productId = 1,
              discriminator = 1,
              Transport.Ble("1.1.1.1"))

      trySend(beacon)

      delay(EMIT_DELAY_MS)
      count++
    }

    awaitClose { Timber.d("awaitClose: Stop discovery.") }
  }
}
