// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.cluster

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aether.android.R
import io.aether.android.stateDisplayString
import timber.log.Timber

/** Displays the on/off state of a single OnOff cluster and lets the user toggle it. */
@Composable
internal fun OnOffClusterControl(
  isOnline: Boolean,
  isOn: Boolean,
  onToggle: (Boolean) -> Unit,
) {
  val bgColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface
  val contentColor =
    if (isOnline && isOn) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface
  Surface(
    modifier = Modifier.fillMaxWidth(),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    color = bgColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content)),
    ) {
      Text(text = stateDisplayString(isOnline, isOn), style = MaterialTheme.typography.bodyLarge)
      Spacer(Modifier.weight(1f))
      Switch(checked = isOn, onCheckedChange = onToggle)
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Compose Previews

@Preview(widthDp = 300)
@Composable
private fun OnOffClusterControl_OnlineOn() {
  MaterialTheme { OnOffClusterControl(isOnline = true, isOn = true) { Timber.d("toggle: $it") } }
}

@Preview(widthDp = 300)
@Composable
private fun OnOffClusterControl_Offline() {
  MaterialTheme { OnOffClusterControl(isOnline = false, isOn = false) { Timber.d("toggle: $it") } }
}
