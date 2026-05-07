// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

 package io.aether.android.screens.device.cluster

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aether.android.R
import timber.log.Timber

/**
 * Displays a labeled slider that controls a single Level-type cluster attribute (brightness or
 * colour temperature). [level] is normalised to `0f..1f`; the caller converts to/from raw cluster
 * values.
 */
@Composable
internal fun LevelClusterControl(
  title: String,
  isOnline: Boolean,
  isOn: Boolean,
  level: Float,
  onLevelChange: (Float) -> Unit,
  onLevelChangeFinished: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Column(modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content))) {
      Text(text = title)
      Slider(
        enabled = isOnline && isOn,
        value = level,
        onValueChange = onLevelChange,
        onValueChangeFinished = onLevelChangeFinished,
        valueRange = 0f..1f,
      )
      Text(
        text = (level * 100).toInt().toString(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Compose Previews

@Preview(widthDp = 300)
@Composable
private fun LevelClusterControl_Brightness() {
  MaterialTheme {
    LevelClusterControl(
      title = stringResource(R.string.brightness),
      isOnline = true,
      isOn = true,
      level = 0.45f,
      onLevelChange = { Timber.d("level: $it") },
      onLevelChangeFinished = {},
    )
  }
}

@Preview(widthDp = 300)
@Composable
private fun LevelClusterControl_ColorTemperature() {
  MaterialTheme {
    LevelClusterControl(
      title = stringResource(R.string.color_temperature),
      isOnline = true,
      isOn = true,
      level = 0.7f,
      onLevelChange = { Timber.d("colorTemp: $it") },
      onLevelChangeFinished = {},
    )
  }
}
