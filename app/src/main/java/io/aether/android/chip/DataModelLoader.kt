// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import io.aether.android.matter.CLUSTERS
import io.aether.android.matter.DEVICES
import javax.inject.Inject
import javax.inject.Singleton

/** Exposes Matter data-model information sourced from generated Kotlin registry files. */
@Singleton
class DataModelLoader @Inject constructor() {

  /** Maps device ID -> device name using the generated data-model registry. */
  val devicesMap: Map<Long, String> by lazy { DEVICES.mapKeys { it.key.toLong() } }

  /** Maps cluster ID -> cluster name using the generated data-model registry. */
  val clustersMap: Map<Long, String> by lazy {
    CLUSTERS.mapKeys { it.key.toLong() }.mapValues { it.value.name }
  }
}
