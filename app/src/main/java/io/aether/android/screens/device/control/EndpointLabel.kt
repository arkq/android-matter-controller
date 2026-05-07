// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.control

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Renders a small endpoint label (e.g. "Endpoint 2") when [label] is non-null. */
@Composable
internal fun EndpointLabel(label: String?) {
  if (label != null) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      modifier = Modifier.padding(bottom = 4.dp),
    )
  }
}
