// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.commissionable

import kotlinx.coroutines.flow.Flow

/** Provides a [Flow] of [MatterBeacon]s that can be observed as they are discovered. */
fun interface MatterBeaconProducer {
  /** Returns a [Flow] of [MatterBeacon]s that emit devices as they are discovered. */
  fun getBeaconsFlow(): Flow<MatterBeacon>
}
