// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.aether.android.R
import timber.log.Timber

@Composable
internal fun RemoveDeviceSection(onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.error,
      contentColor = MaterialTheme.colorScheme.onError,
    ),
  ) {
    Icon(Icons.Outlined.Delete, contentDescription = null)
    Spacer(modifier = Modifier.width(8.dp))
    Text(stringResource(R.string.remove_device).uppercase())
  }
}

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
      Button(onClick = { onRemoveDeviceOutcome(true) }) {
        Text(stringResource(R.string.yes_remove_it))
      }
    },
    onDismissRequest = {},
    dismissButton = {
      Button(onClick = { onRemoveDeviceOutcome(false) }) { Text(stringResource(R.string.cancel)) }
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
      Button(onClick = { onConfirmDeviceRemovalOutcome(true) }) {
        Text(stringResource(R.string.yes_remove_it))
      }
    },
    onDismissRequest = {},
    dismissButton = {
      Button(onClick = { onConfirmDeviceRemovalOutcome(false) }) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@Preview(widthDp = 300)
@Composable
private fun RemoveDeviceSectionPreview() {
  MaterialTheme { RemoveDeviceSection({ Timber.d("preview", "button clicked") }) }
}
