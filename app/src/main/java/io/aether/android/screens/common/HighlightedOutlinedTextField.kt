// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

private enum class FieldHighlight {
  STANDARD,
  EDITED,
  SUCCESS,
}

@Composable
private fun fieldHighlightColors(highlight: FieldHighlight): TextFieldColors {
  val successColor = Color(0xFF4CAF50)
  val editedColor = MaterialTheme.colorScheme.error
  val standardFocused = MaterialTheme.colorScheme.primary
  val standardUnfocused = MaterialTheme.colorScheme.outline
  val durationMs = if (highlight == FieldHighlight.STANDARD) 500 else 150
  val animSpec = tween<Color>(durationMillis = durationMs)
  val targetFocused =
      when (highlight) {
        FieldHighlight.SUCCESS -> successColor
        FieldHighlight.EDITED -> editedColor
        FieldHighlight.STANDARD -> standardFocused
      }
  val targetUnfocused =
      when (highlight) {
        FieldHighlight.SUCCESS -> successColor
        FieldHighlight.EDITED -> editedColor
        FieldHighlight.STANDARD -> standardUnfocused
      }
  val focusedColor by
      animateColorAsState(
          targetValue = targetFocused,
          animationSpec = animSpec,
          label = "focusedBorderColor",
      )
  val unfocusedColor by
      animateColorAsState(
          targetValue = targetUnfocused,
          animationSpec = animSpec,
          label = "unfocusedBorderColor",
      )
  return OutlinedTextFieldDefaults.colors(
      focusedBorderColor = focusedColor,
      unfocusedBorderColor = unfocusedColor,
  )
}

/**
 * An [OutlinedTextField] that visually signals interaction results via border color transitions:
 * - Turns **red** while the user is editing.
 * - Turns **green** (then fades back to the standard color over 500 ms) when [successTrigger]
 *   increments, indicating that the last operation succeeded.
 *
 * @param successTrigger Increment this value to trigger the green-success animation.
 * @param resetKey When this value changes all internal highlight state is reset (e.g. when
 *   navigating to a different attribute or command).
 */
@Composable
fun HighlightedOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    successTrigger: Int,
    resetKey: Any,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
  var lastSeen by remember(resetKey) { mutableIntStateOf(successTrigger) }
  var highlight by remember(resetKey) { mutableStateOf(FieldHighlight.STANDARD) }

  LaunchedEffect(successTrigger) {
    if (successTrigger > lastSeen) {
      lastSeen = successTrigger
      highlight = FieldHighlight.SUCCESS
      delay(2000)
      if (highlight == FieldHighlight.SUCCESS) highlight = FieldHighlight.STANDARD
    }
  }

  OutlinedTextField(
      value = value,
      onValueChange = {
        onValueChange(it)
        highlight = FieldHighlight.EDITED
      },
      modifier = modifier,
      label = label,
      enabled = enabled,
      colors = fieldHighlightColors(highlight),
  )
}
