// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aether.android.R

@Composable
internal fun ExplorerRow(
    text: String,
    secondaryText: String? = null,
    isDimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
  val primaryColor =
      if (isDimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
      else MaterialTheme.colorScheme.onSurface
  val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(enabled = onClick != null) { onClick?.invoke() }
              .padding(horizontal = 8.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = text, style = MaterialTheme.typography.bodyLarge, color = primaryColor)
      if (!secondaryText.isNullOrBlank()) {
        Text(
            text = secondaryText,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor,
        )
      }
    }
  }
}

internal fun formatEndpointLabel(endpoint: UInt, name: String?): String =
    if (name.isNullOrBlank()) {
      "[${formatEndpointId(endpoint)}]"
    } else {
      "[${formatEndpointId(endpoint)}] $name"
    }

internal fun formatIdAndName(id: UInt, name: String?): String {
  val idText = formatExplorerId(id)
  return if (name.isNullOrBlank()) {
    "[$idText]"
  } else {
    "[$idText] $name"
  }
}

internal fun formatExplorerId(id: UInt): String =
    if (id <= 0xFFFFu) String.format("0x%04X", id.toLong())
    else String.format("0x%08X", id.toLong())

internal fun formatEndpointId(endpoint: UInt): String =
    if (endpoint <= 0xFFu) String.format("0x%02X", endpoint.toLong())
    else String.format("0x%04X", endpoint.toLong())

internal fun matchesExplorerQuery(query: String, vararg fields: String): Boolean {
  val normalizedQuery = query.trim().lowercase()
  if (normalizedQuery.isBlank()) {
    return true
  }
  return fields.any { it.lowercase().contains(normalizedQuery) }
}

@Composable
internal fun explorerSupportStatusText(baseText: String, isSupported: Boolean): String =
    if (isSupported) {
      baseText
    } else {
      "$baseText\n${stringResource(R.string.device_explorer_not_supported)}"
    }
