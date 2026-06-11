// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aether.android.R
import io.aether.android.data.models.GeneralDiagnosticsData
import io.aether.android.data.models.NetworkInterface

private fun formatDuration(seconds: Long): String {
  val days = seconds / 86400
  val hours = (seconds % 86400) / 3600
  val minutes = (seconds % 3600) / 60
  val secs = seconds % 60
  if (days > 0) {
    return "${days}d ${hours}h ${minutes}m"
  } else if (hours > 0) {
    return "${hours}h ${minutes}m ${secs}s"
  } else if (minutes > 0) {
    return "${minutes}m ${secs}s"
  } else {
    return "${secs}s"
  }
}

@Composable
fun GeneralDiagnosticsCard(data: GeneralDiagnosticsData) {

  DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_uptime)) {
    Text(data.upTime?.let { formatDuration(it) } ?: "N/A")
  }
  DiagnosticsInfoRow(
      label = stringResource(R.string.device_diagnostics_label_total_operational_time)
  ) {
    Text(data.totalOperationalHours?.let { "$it hours" } ?: "N/A")
  }
  DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_reboot_count)) {
    Text(data.rebootCount.toString())
  }
  DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_boot_reason)) {
    Text(data.bootReason ?: "N/A")
  }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

    // Interfaces Section
    Text(
        text = stringResource(R.string.device_diagnostics_label_interfaces),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    data.networkInterfaces.forEach { iface ->
      NetworkInterfaceCard(iface)
    }
  }
}

@Composable
private fun NetworkInterfaceCard(iface: NetworkInterface) {
  OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        val color = if (iface.isOperational) Color.Green else Color.Gray
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = iface.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
      }
      Text("MAC: ${iface.hardwareAddress}", style = MaterialTheme.typography.labelSmall)

      if (iface.ipv4Addresses.isNotEmpty()) {
        Text(
            "IPv4",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        iface.ipv4Addresses.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
      }
      if (iface.ipv6Addresses.isNotEmpty()) {
        Text(
            "IPv6",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        iface.ipv6Addresses.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
    }
  }
}
