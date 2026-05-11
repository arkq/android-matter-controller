// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.aether.android.Device
import io.aether.android.R
import io.aether.android.getDeviceTypeDisplayStringId

@Composable
internal fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  var inputText by remember(currentName) { mutableStateOf(currentName) }
  AlertDialog(
      title = { Text(stringResource(R.string.rename_device)) },
      text = {
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text(stringResource(R.string.rename_device_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
      },
      confirmButton = {
        Button(
            onClick = { onConfirm(inputText.trim()) },
            enabled = inputText.trim().isNotBlank(),
        ) {
          Text(stringResource(R.string.ok))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
      onDismissRequest = onDismiss,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceTypeDialog(
    currentType: Device.DeviceType,
    onConfirm: (Device.DeviceType) -> Unit,
    onDismiss: () -> Unit,
) {
  val types =
      listOf(
          Device.DeviceType.TYPE_LIGHT,
          Device.DeviceType.TYPE_DIMMABLE_LIGHT,
          Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT,
          Device.DeviceType.TYPE_EXTENDED_COLOR_LIGHT,
          Device.DeviceType.TYPE_LIGHT_SWITCH,
          Device.DeviceType.TYPE_OUTLET,
          Device.DeviceType.TYPE_UNKNOWN,
      )
  var expanded by remember { mutableStateOf(false) }
  var selectedType by remember(currentType) { mutableStateOf(currentType) }

  AlertDialog(
      title = { Text(stringResource(R.string.device_type_dialog_title)) },
      text = {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
          OutlinedTextField(
              value = stringResource(getDeviceTypeDisplayStringId(selectedType)),
              onValueChange = {},
              readOnly = true,
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false },
          ) {
            types.forEach { type ->
              DropdownMenuItem(
                  text = { Text(stringResource(getDeviceTypeDisplayStringId(type))) },
                  onClick = {
                    selectedType = type
                    expanded = false
                  },
              )
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { onConfirm(selectedType) }) { Text(stringResource(R.string.ok)) }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
      onDismissRequest = onDismiss,
  )
}
