// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R

data class DialogInfo(
    @field:StringRes @param:StringRes val titleRes: Int? = null,
    val title: String? = null,
    @field:StringRes @param:StringRes val messageRes: Int? = null,
    val message: String? = null,
    val showConfirmButton: Boolean = true,
)

// Useful dialog that can display title, message, and confirm button.
@Composable
fun MsgAlertDialog(dialogInfo: DialogInfo, onDismissMsgAlertDialog: () -> Unit) {
  val resolvedTitle = dialogInfo.titleRes?.let { stringResource(it) } ?: dialogInfo.title
  val resolvedMessage = dialogInfo.messageRes?.let { stringResource(it) } ?: dialogInfo.message
  AlertDialog(
      title = {
        if (!resolvedTitle.isNullOrEmpty()) {
          Text(resolvedTitle)
        }
      },
      text = {
        if (!resolvedMessage.isNullOrEmpty()) {
          Text(resolvedMessage)
        }
      },
      confirmButton = {
        if (dialogInfo.showConfirmButton) {
          TextButton(onClick = onDismissMsgAlertDialog) { Text(stringResource(R.string.ok)) }
        }
      },
      onDismissRequest = {},
      dismissButton = {},
  )
}
