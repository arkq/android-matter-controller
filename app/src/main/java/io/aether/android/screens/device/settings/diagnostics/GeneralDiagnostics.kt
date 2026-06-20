// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.data.models.GeneralDiagnosticsData
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Composable
fun GeneralDiagnostics(data: GeneralDiagnosticsData) {
  DiagnosticsSection(title = stringResource(R.string.device_diagnostics_section_general)) {
    DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_uptime)) {
      Text(data.upTime?.let { it.toLong().seconds.toString() } ?: "N/A")
    }
    DiagnosticsInfoRow(
        label = stringResource(R.string.device_diagnostics_label_total_operational_time)
    ) {
      Text(data.totalOperationalHours?.let { it.toInt().hours.toString() } ?: "N/A")
    }
    DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_reboot_count)) {
      Text(data.rebootCount.toString())
    }
    DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_boot_reason)) {
      Text(data.bootReason?.name ?: "N/A")
    }
    if (data.activeHardwareFaults.isNotEmpty()) {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_active_hardware_faults)
      ) {
        Text(data.activeHardwareFaults.joinToString(" • ") { it.name })
      }
    }
    if (data.activeRadioFaults.isNotEmpty()) {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_active_radio_faults)
      ) {
        Text(data.activeRadioFaults.joinToString(" • ") { it.name })
      }
    }
    if (data.activeNetworkFaults.isNotEmpty()) {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_active_network_faults)
      ) {
        Text(data.activeNetworkFaults.joinToString(" • ") { it.name })
      }
    }
  }
  DiagnosticsSection(title = stringResource(R.string.device_diagnostics_section_interfaces)) {
    data.networkInterfaces.forEach {
      DiagnosticsInfoRow(
          label = "${it.name} (${it.type.name}) ${if (it.isOperational) "[UP]" else "[DOWN]"}"
      ) {
        Text(
            stringResource(
                R.string.device_diagnostics_interface_hw_address,
                it.hardwareAddress.joinToString(separator = ":") { "%02X".format(it) },
            )
        )
        Text(
            stringResource(
                R.string.device_diagnostics_interface_ipv4_address,
                it.ipv4Addresses.mapNotNull { it.hostAddress }.joinToString(" • "),
            )
        )
        Text(
            stringResource(
                R.string.device_diagnostics_interface_ipv6_address,
                it.ipv6Addresses.mapNotNull { it.hostAddress }.joinToString(" • "),
            )
        )
      }
    }
  }
}
