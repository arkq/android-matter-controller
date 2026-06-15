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
  data.activeHardwareFaults?.let { faults ->
    DiagnosticsInfoRow(
        label = stringResource(R.string.device_diagnostics_label_active_hardware_faults)
    ) {
      Text(faults.joinToString(", ") { it.name })
    }
  }
  data.activeRadioFaults?.let { faults ->
    DiagnosticsInfoRow(
        label = stringResource(R.string.device_diagnostics_label_active_radio_faults)
    ) {
      Text(faults.joinToString(", ") { it.name })
    }
  }
  data.activeNetworkFaults?.let { faults ->
    DiagnosticsInfoRow(
        label = stringResource(R.string.device_diagnostics_label_active_network_faults)
    ) {
      Text(faults.joinToString(", ") { it.name })
    }
  }
}

@Composable
fun GeneralDiagnosticsInterfacesCard(data: GeneralDiagnosticsData) {
  data.networkInterfaces.forEach { iface ->
    DiagnosticsInfoRow(label = iface.name) {
      Text("MAC: ${iface.hardwareAddress.joinToString(separator = ":") { "%02X".format(it) }}")
    }
  }
  // OutlinedCard(modifier = Modifier.fillMaxWidth()) {
  //   Column(modifier = Modifier.padding(12.dp)) {
  //     Row(verticalAlignment = Alignment.CenterVertically) {
  //       val color = if (iface.isOperational) Color.Green else Color.Gray
  //       Box(Modifier.size(8.dp).background(color, CircleShape))
  //       Spacer(Modifier.width(8.dp))
  //       Text(
  //           text = iface.name,
  //           style = MaterialTheme.typography.bodyLarge,
  //           fontWeight = FontWeight.Bold,
  //       )
  //     }
  //     Text("MAC: ${iface.hardwareAddress}", style = MaterialTheme.typography.labelSmall)

  //     if (iface.ipv4Addresses.isNotEmpty()) {
  //       Text(
  //           "IPv4",
  //           style = MaterialTheme.typography.labelMedium,
  //           color = MaterialTheme.colorScheme.primary,
  //           modifier = Modifier.padding(top = 8.dp),
  //       )
  //       iface.ipv4Addresses.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
  //     }
  //     if (iface.ipv6Addresses.isNotEmpty()) {
  //       Text(
  //           "IPv6",
  //           style = MaterialTheme.typography.labelMedium,
  //           color = MaterialTheme.colorScheme.primary,
  //           modifier = Modifier.padding(top = 8.dp),
  //       )
  //       iface.ipv6Addresses.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
  //     }
  //   }
  // }
}
