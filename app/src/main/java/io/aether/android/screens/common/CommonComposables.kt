// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import android.text.method.LinkMovementMethod
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.google.android.material.textview.MaterialTextView
import timber.log.Timber

// Information used for [MsgAlertDialog].
data class DialogInfo(
  val title: String? = null,
  val message: String? = null,
  val showConfirmButton: Boolean = true,
  @StringRes val titleRes: Int? = null,
)

// Useful dialog that can display title, message, and confirm button.
@Composable
fun MsgAlertDialog(dialogInfo: DialogInfo?, onDismissMsgAlertDialog: () -> Unit) {
  Timber.d("MsgAlertDialog [$dialogInfo]")
  if (dialogInfo == null) return

  val resolvedTitle =
    dialogInfo.titleRes?.let { stringResource(it) } ?: dialogInfo.title

  AlertDialog(
    title = {
      if (!resolvedTitle.isNullOrEmpty()) {
        Text(resolvedTitle)
      }
    },
    text = {
      if (!dialogInfo.message.isNullOrEmpty()) {
        Text(dialogInfo.message)
      }
    },
    confirmButton = {
      if (dialogInfo.showConfirmButton) {
        TextButton(onClick = onDismissMsgAlertDialog) { Text("OK") }
      }
    },
    onDismissRequest = {},
    dismissButton = {},
  )
}

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
    confirmButton = { TextButton(onClick = onClick) { Text("OK") } },
    onDismissRequest = {},
    dismissButton = {},
  )
}
