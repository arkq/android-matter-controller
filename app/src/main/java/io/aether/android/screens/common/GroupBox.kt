// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aether.android.spacing

@Composable
fun GroupBox(
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MaterialTheme.spacing.roundedCorner),
    content: @Composable ColumnScope.() -> Unit,
) {
  val surfacePadding = MaterialTheme.spacing.paddingSurfaceContent
  Box(
      modifier =
          modifier.padding(top = surfacePadding / 2).semantics(mergeDescendants = true) {
            contentDescription = title
          }
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                )
                .padding(MaterialTheme.spacing.paddingNormal)
    ) {
      content()
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.align(Alignment.TopStart)
                .offset(
                    x = surfacePadding,
                    y = -MaterialTheme.typography.labelMedium.fontSize.value.dp / 2,
                )
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp),
    )
  }
}
