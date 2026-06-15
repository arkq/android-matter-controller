// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.data.models.GeneralDiagnosticsData

private fun formatUpTimeDuration(seconds: Long): String {
  val days = seconds / 86400
  val hours = (seconds % 86400) / 3600
  val minutes = (seconds % 3600) / 60
  val secs = seconds % 60
  return if (days > 0) {
    "${days}d ${hours}h ${minutes}m"
  } else if (hours > 0) {
    "${hours}h ${minutes}m ${secs}s"
  } else if (minutes > 0) {
    "${minutes}m ${secs}s"
  } else {
    "${secs}s"
  }
}

private fun formatTotalOperationalTime(hours: Int): String {
  val days = hours / 24
  val remainingHours = hours % 24
  return if (days > 0) {
    "${days}d ${remainingHours}h"
  } else {
    "${remainingHours}h"
  }
}

@Composable
fun GeneralDiagnosticsCard(data: GeneralDiagnosticsData) {
  DiagnosticsSection(title = stringResource(R.string.device_diagnostics_card_general)) {
    DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_uptime)) {
      Text(data.upTime?.let { formatUpTimeDuration(it) } ?: "N/A")
    }
    DiagnosticsInfoRow(
        label = stringResource(R.string.device_diagnostics_label_total_operational_time)
    ) {
      Text(data.totalOperationalHours?.let { formatTotalOperationalTime(it) } ?: "N/A")
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
        Text(data.activeHardwareFaults.joinToString(", ") { it.name })
      }
    }
    if (data.activeRadioFaults.isNotEmpty()) {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_active_radio_faults)
      ) {
        Text(data.activeRadioFaults.joinToString(", ") { it.name })
      }
    }
    if (data.activeNetworkFaults.isNotEmpty()) {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_active_network_faults)
      ) {
        Text(data.activeNetworkFaults.joinToString(", ") { it.name })
      }
    }
  }
  DiagnosticsSection(title = stringResource(R.string.device_diagnostics_card_interfaces)) {
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
                it.ipv4Addresses.mapNotNull { it.hostAddress }.joinToString(", "),
            )
        )
        Text(
            stringResource(
                R.string.device_diagnostics_interface_ipv6_address,
                it.ipv6Addresses.mapNotNull { it.hostAddress }.joinToString(", "),
            )
        )
      }
    }
  }
}
