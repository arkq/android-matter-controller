// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Extended theme colors not covered by the Material3 color scheme. */
data class AetherExtendedColors(
    /** Color used to indicate a successful operation (e.g. read / write / invoke feedback). */
    val success: Color,
)

val LocalAetherExtendedColors = staticCompositionLocalOf {
  AetherExtendedColors(success = Color(0xFF4CAF50))
}

@Composable
fun AetherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme =
      remember(context, darkTheme) {
        when {
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
              if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
          darkTheme -> darkColorScheme()
          else -> lightColorScheme()
        }
      }
  val extendedColors =
      if (darkTheme) AetherExtendedColors(success = Color(0xFF81C784))
      else AetherExtendedColors(success = Color(0xFF4CAF50))
  CompositionLocalProvider(LocalAetherExtendedColors provides extendedColors) {
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
  }
}
