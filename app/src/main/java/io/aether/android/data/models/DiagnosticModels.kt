// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data.models

import io.aether.android.matter.Enums
import java.net.Inet6Address
import java.net.InetAddress

data class NetworkInterface(
    val name: String,
    val isOperational: Boolean,
    val offPremiseServicesReachableIPv4: Boolean?,
    val offPremiseServicesReachableIPv6: Boolean?,
    val hardwareAddress: ByteArray,
    val ipv4Addresses: List<InetAddress>,
    val ipv6Addresses: List<Inet6Address>,
    val type: Enums.DiagnosticsGeneralClusterInterfaceType,
)

data class GeneralDiagnosticsData(
    val networkInterfaces: List<NetworkInterface>,
    val rebootCount: Int,
    val upTime: Long? = null,
    val totalOperationalHours: Int? = null,
    val bootReason: Enums.DiagnosticsGeneralClusterBootReason? = null,
    val activeHardwareFaults: List<Enums.DiagnosticsGeneralClusterHardwareFault> = emptyList(),
    val activeRadioFaults: List<Enums.DiagnosticsGeneralClusterRadioFault> = emptyList(),
    val activeNetworkFaults: List<Enums.DiagnosticsGeneralClusterNetworkFault> = emptyList(),
    val testEventTriggersEnabled: Boolean? = null,
    val deviceLoadStatus: String? = null,
)

data class ThreadMetrics(
    val id: Long,
    val name: String?,
    val stackFreeCurrent: Int?,
    val stackFreeMinimum: Int?,
    val stackSize: Int?,
)

data class SoftwareDiagnosticsData(
    val threadMetrics: List<ThreadMetrics>,
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
