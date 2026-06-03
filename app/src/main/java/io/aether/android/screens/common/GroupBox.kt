// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.aether.android.spacing

@Composable
fun GroupBox(title: String, content: @Composable () -> Unit) {
  val surfacePadding = MaterialTheme.spacing.paddingSurfaceContent
  Box(modifier = Modifier.padding(top = surfacePadding)) {
    Column(
        modifier =
            Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(MaterialTheme.spacing.roundedCorner),
                )
                .padding(MaterialTheme.spacing.paddingNormal)
    ) {
      content()
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.offset(x = surfacePadding, y = -(surfacePadding / 2))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = surfacePadding / 2),
    )
  }
}
