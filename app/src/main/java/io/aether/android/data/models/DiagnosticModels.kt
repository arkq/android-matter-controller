// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data.models

import chip.devicecontroller.ChipStructs

data class GeneralDiagnosticsData(
    val networkInterfaces: List<ChipStructs.GeneralDiagnosticsClusterNetworkInterface>,
    val rebootCount: Int,
    val upTime: Long? = null,
    val totalOperationalHours: Int? = null,
    val bootReason: Int? = null,
    val activeHardwareFaults: List<String>? = null,
    val activeRadioFaults: List<String>? = null,
    val activeNetworkFaults: List<String>? = null,
    val testEventTriggersEnabled: Boolean? = null,
    val deviceLoadStatus: String? = null,
)

data class SoftwareDiagnosticsData(
    val threadMetrics: String? = null,
    val currentHeapFree: Long? = null,
    val currentHeapUsed: Long? = null,
    val currentHeapHighWatermark: Long? = null,
)

data class WiFiDiagnosticsData(
    val bssid: String? = null,
    val securityType: String? = null,
    val wifiVersion: String? = null,
    val channelNumber: Int? = null,
    val rssi: Int? = null,
)
