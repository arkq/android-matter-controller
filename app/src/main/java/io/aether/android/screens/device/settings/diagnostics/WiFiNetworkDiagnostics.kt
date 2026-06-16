// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aether.android.R
import io.aether.android.data.models.WiFiNetworkDiagnosticsData

@Composable
fun WiFiNetworkDiagnostics(data: WiFiNetworkDiagnosticsData) {
  DiagnosticsSection(title = stringResource(R.string.device_diagnostics_section_wifi_network)) {

    // 1. NETWORK IDENTITY
    if (data.bssid != null || data.securityType != null) {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_network_identity)
      ) {
        Column {
          data.bssid?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
          data.securityType?.let {
            Text(
                text = it.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }
    }

    // 2. SIGNAL & PERFORMANCE (With RSSI Progress Bar)
    if (
        data.rssi != null ||
            data.wifiVersion != null ||
            data.channelNumber != null ||
            data.currentMaxRate != null
    ) {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_signal_performance)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          // Network Info Badges
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.wifiVersion?.let { Text(it.name) }
            data.channelNumber?.let { Text("Ch: $it") }
            data.currentMaxRate?.let { Text("$it Mb/s") }
          }

          // RSSI Visual Progress Bar
          data.rssi?.let { rssiVal ->
            // Normalize RSSI (-100 to -30 dBm) to a 0.0f to 1.0f float range
            val clampedRssi = rssiVal.coerceIn(-100, -30)
            val progress = (clampedRssi + 100) / 70f

            val barColor =
                when {
                  progress > 0.7f -> Color.Green
                  progress > 0.4f -> Color.Yellow
                  else -> Color.Red
                }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              LinearProgressIndicator(
                  progress = { progress },
                  modifier = Modifier.fillMaxWidth().height(6.dp),
                  color = barColor,
                  trackColor = MaterialTheme.colorScheme.surfaceVariant,
              )
              Text(
                  text = "Signal Strength: $rssiVal dBm",
                  style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
      }
    }

    // 3. UNICAST TRAFFIC
    if (data.packetUnicastRxCount != null || data.packetUnicastTxCount != null) {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_unicast_traffic)
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("⬇ Rx: ${data.packetUnicastRxCount ?: 0}")
          Text("⬆ Tx: ${data.packetUnicastTxCount ?: 0}")
        }
      }
    }

    // 4. MULTICAST TRAFFIC
    if (data.packetMulticastRxCount != null || data.packetMulticastTxCount != null) {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_multicast_traffic)
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("⬇ Rx: ${data.packetMulticastRxCount ?: 0}")
          Text("⬆ Tx: ${data.packetMulticastTxCount ?: 0}")
        }
      }
    }

    // 5. LINK QUALITY & DROPPED DATA
    if (data.beaconLostCount != null || data.beaconRxCount != null || data.overrunCount != null) {
      val lostBeacons = data.beaconLostCount ?: 0u
      val totalBeacons = (data.beaconRxCount ?: 0u) + lostBeacons

      // Calculate health bar (high lost beacons or overruns turn it red)
      val hasIssues = lostBeacons > 5u || (data.overrunCount ?: 0u) > 0u

      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_network_link_quality)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .height(6.dp)
                      .background(
                          color = if (hasIssues) Color.Red else Color.Green,
                          shape = RoundedCornerShape(3.dp),
                      )
          )
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("Lost Beacons: $lostBeacons", style = MaterialTheme.typography.bodySmall)
            Text("Overruns: ${data.overrunCount ?: 0}", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }

    data.bssid?.let {
      DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_bssid)) {
        Text(it)
      }
    }
    data.securityType?.let {
      DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_security_type)) {
        Text(it.name)
      }
    }
    data.wifiVersion?.let {
      DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_wifi_version)) {
        Text(it.name)
      }
    }
    data.channelNumber?.let {
      DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_channel_number)) {
        Text(it.toString())
      }
    }
    data.rssi?.let {
      DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_rssi)) {
        Text(it.toString())
      }
    }
    data.beaconLostCount?.let {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_beacon_lost_count)
      ) {
        Text(it.toString())
      }
    }
    data.beaconRxCount?.let {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_beacon_rx_count)
      ) {
        Text(it.toString())
      }
    }
    data.packetMulticastRxCount?.let {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_packet_multicast_rx_count)
      ) {
        Text(it.toString())
      }
    }
    data.packetMulticastTxCount?.let {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_packet_multicast_tx_count)
      ) {
        Text(it.toString())
      }
    }
    data.packetUnicastRxCount?.let {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_packet_unicast_rx_count)
      ) {
        Text(it.toString())
      }
    }
    data.packetUnicastTxCount?.let {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_packet_unicast_tx_count)
      ) {
        Text(it.toString())
      }
    }
    data.currentMaxRate?.let {
      DiagnosticsInfoRow(
          label = stringResource(R.string.device_diagnostics_label_current_max_rate)
      ) {
        Text(it.toString())
      }
    }
    data.overrunCount?.let {
      DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_overrun_count)) {
        Text(it.toString())
      }
    }
  }
}
