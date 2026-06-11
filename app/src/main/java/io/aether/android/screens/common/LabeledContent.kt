// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aether.android.spacing

@Composable
fun LabeledContent(
    label: String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    spacing: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
  Column(
      modifier = modifier.semantics(mergeDescendants = true) {},
      verticalArrangement = Arrangement.spacedBy(spacing),
  ) {
    Text(
        text = label,
        style = labelStyle,
        color = labelColor,
    )
    content()
  }
}
