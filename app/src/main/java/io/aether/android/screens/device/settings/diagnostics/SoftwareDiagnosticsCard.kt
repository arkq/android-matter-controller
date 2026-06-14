// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.data.models.SoftwareDiagnosticsData
import io.aether.android.spacing

@Composable
fun SoftwareDiagnosticsCard(data: SoftwareDiagnosticsData) {

  val used = data.currentHeapUsed ?: 0L
  val free = data.currentHeapFree ?: 0L
  val total = used + free

  if (total > 0) {
    DiagnosticsInfoRow(
        label = stringResource(R.string.device_diagnostics_label_current_heap_usage)
    ) {
      val progress = used.toFloat() / total.toFloat()
      LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.paddingTiny),
          color =
              if (progress > 0.9f) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.device_diagnostics_current_heap_used, used / 1024))
        Text(stringResource(R.string.device_diagnostics_current_heap_free, free / 1024))
      }
    }
  }

  data.currentHeapHighWatermark?.let {
    DiagnosticsInfoRow(
        label = stringResource(R.string.device_diagnostics_label_current_heap_high_watermark)
    ) {
      Text(stringResource(R.string.device_diagnostics_current_heap_high_watermark, it / 1024))
    }
  }

  data.threadMetrics?.let {
    DiagnosticsInfoRow(label = stringResource(R.string.device_diagnostics_label_thread_metrics)) {
      Text(it)
    }
  }
}
