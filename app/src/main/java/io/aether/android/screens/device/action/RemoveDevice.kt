// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.action

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import timber.log.Timber

@Composable
internal fun RemoveDeviceAlertDialog(
    showRemoveDeviceAlertDialog: Boolean,
    onRemoveDeviceOutcome: (doIt: Boolean) -> Unit,
) {
  Timber.d("RemoveDeviceAlertDialog [$showRemoveDeviceAlertDialog]")
  if (!showRemoveDeviceAlertDialog) {
    return
  }

  AlertDialog(
      title = { Text(text = stringResource(R.string.remove_device_dialog_title)) },
      text = { Text(stringResource(R.string.remove_device_dialog_body)) },
      confirmButton = {
        Button(
            onClick = { onRemoveDeviceOutcome(true) },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
        ) {
          Text(stringResource(R.string.yes_remove_it))
        }
      },
      onDismissRequest = {},
      dismissButton = {
        TextButton(onClick = { onRemoveDeviceOutcome(false) }) {
          Text(stringResource(R.string.cancel))
        }
      },
  )
}

@Composable
internal fun ConfirmDeviceRemovalAlertDialog(
    showConfirmDeviceRemovalAlertDialog: Boolean,
    onConfirmDeviceRemovalOutcome: (doIt: Boolean) -> Unit,
) {
  if (!showConfirmDeviceRemovalAlertDialog) {
    return
  }

  AlertDialog(
      title = { Text(text = stringResource(R.string.confirm_remove_device_dialog_title)) },
      text = { Text(stringResource(R.string.confirm_remove_device_dialog_body)) },
      confirmButton = {
        Button(
            onClick = { onConfirmDeviceRemovalOutcome(true) },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
        ) {
          Text(stringResource(R.string.yes_remove_it))
        }
      },
      onDismissRequest = {},
      dismissButton = {
        TextButton(onClick = { onConfirmDeviceRemovalOutcome(false) }) {
          Text(stringResource(R.string.cancel))
        }
      },
  )
}
