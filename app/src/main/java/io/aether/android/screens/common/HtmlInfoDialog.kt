// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import android.text.method.LinkMovementMethod
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.google.android.material.textview.MaterialTextView
import io.aether.android.R

@Composable
fun HtmlInfoDialog(title: String, htmlInfo: String, onClick: () -> Unit) {
  val htmlText = HtmlCompat.fromHtml(htmlInfo, HtmlCompat.FROM_HTML_MODE_LEGACY)
  AlertDialog(
      title = { Text(text = title) },
      text = {
        // See https://developer.android.com/codelabs/jetpack-compose-migration
        AndroidView(
            update = { it.text = htmlText },
            factory = {
              MaterialTextView(it).apply { movementMethod = LinkMovementMethod.getInstance() }
            },
        )
      },
      confirmButton = { TextButton(onClick = onClick) { Text(stringResource(R.string.ok)) } },
      onDismissRequest = {},
      dismissButton = {},
  )
}
