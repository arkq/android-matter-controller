// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF6750A4),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFEADDFF),
  onPrimaryContainer = Color(0xFF21005D),
  secondary = Color(0xFF625B71),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFE8DEF8),
  onSecondaryContainer = Color(0xFF1D192B),
  background = Color(0xFFFFFBFE),
  onBackground = Color(0xFF1C1B1F),
  surface = Color(0xFFFFFBFE),
  onSurface = Color(0xFF1C1B1F),
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFFD0BCFF),
  onPrimary = Color(0xFF381E72),
  primaryContainer = Color(0xFF4F378B),
  onPrimaryContainer = Color(0xFFEADDFF),
  secondary = Color(0xFFCCC2DC),
  onSecondary = Color(0xFF332D41),
  secondaryContainer = Color(0xFF4A4458),
  onSecondaryContainer = Color(0xFFE8DEF8),
  background = Color(0xFF1C1B1F),
  onBackground = Color(0xFFE6E1E5),
  surface = Color(0xFF1C1B1F),
  onSurface = Color(0xFFE6E1E5),
)

@Composable
fun AetherTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    content = content,
  )
}
