// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data.models

data class NetworkInterface(
    val name: String,
    val isOperational: Boolean,
    val hardwareAddress: String,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
)

data class GeneralDiagnosticsData(
    val networkInterfaces: List<NetworkInterface>,
    val rebootCount: Int,
    val upTime: Long? = null,
    val totalOperationalHours: Int? = null,
    val bootReason: String? = null,
    var activeHardwareFaults: List<String>? = null,
    var activeRadioFaults: List<String>? = null,
    var activeNetworkFaults: List<String>? = null,
    var testEventTriggersEnabled: Boolean? = null,
    var deviceLoadStatus: String? = null,
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
