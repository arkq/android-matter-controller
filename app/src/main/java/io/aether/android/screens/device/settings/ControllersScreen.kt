// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.aether.android.R
import io.aether.android.formatFabricId
import io.aether.android.formatNodeId
import io.aether.android.chip.vendorLabel

/** Route composable for the Controllers screen. */
@Composable
fun ControllersRoute(
    innerPadding: PaddingValues,
    updateTitle: (String) -> Unit,
    deviceId: Long,
    viewModel: ControllersViewModel = hiltViewModel(),
) {
  val title = stringResource(R.string.device_settings_manage_controllers)
  LaunchedEffect(title) { updateTitle(title) }
  LaunchedEffect(deviceId) { viewModel.loadControllers(deviceId) }

  val uiState by viewModel.uiState.collectAsState()

  ControllersScreen(
      innerPadding = innerPadding,
      uiState = uiState,
      onRemoveController = { fabricIndex -> viewModel.removeController(deviceId, fabricIndex) },
  )
}

@Composable
private fun ControllersScreen(
    innerPadding: PaddingValues,
    uiState: ControllersViewModel.UiState,
    onRemoveController: (fabricIndex: Int) -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    when (uiState) {
      is ControllersViewModel.UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.controllers_loading),
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }

      is ControllersViewModel.UiState.Error -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
              text = uiState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(16.dp),
          )
        }
      }

      is ControllersViewModel.UiState.Loaded -> {
        if (uiState.fabrics.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.controllers_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        } else {
          LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(dimensionResource(R.dimen.margin_normal)),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(uiState.fabrics) { fabric ->
              ControllerItem(fabric = fabric, onRemove = { onRemoveController(fabric.fabricIndex) })
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ControllerItem(
  fabric: ManagedFabric,
    onRemove: () -> Unit,
) {
  var showConfirmDialog by remember { mutableStateOf(false) }

  if (showConfirmDialog) {
    AlertDialog(
        title = { Text(stringResource(R.string.controller_remove_confirm_title)) },
        text = { Text(stringResource(R.string.controller_remove_confirm_body)) },
        confirmButton = {
          Button(
              onClick = {
                showConfirmDialog = false
                onRemove()
              },
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.error,
                      contentColor = MaterialTheme.colorScheme.onError,
                  ),
          ) {
            Text(stringResource(R.string.remove_controller))
          }
        },
        dismissButton = {
          TextButton(onClick = { showConfirmDialog = false }) {
            Text(stringResource(R.string.cancel))
          }
        },
        onDismissRequest = { showConfirmDialog = false },
    )
  }

  Surface(
      modifier = Modifier.fillMaxWidth(),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(dimensionResource(R.dimen.rounded_corner)),
  ) {
    Row(
        modifier = Modifier.padding(dimensionResource(R.dimen.padding_surface_content)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        val label =
            fabric.label?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.controller_default_label, fabric.fabricIndex)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        fabric.vendorID?.let { vendorId ->
          Text(
              text = stringResource(R.string.controller_vendor_id, vendorLabel(vendorId)),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        fabric.fabricID?.let { fabricId ->
          Text(
              text = stringResource(R.string.controller_fabric_id, formatFabricId(fabricId)),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        fabric.nodeID?.let { nodeId ->
          Text(
              text = stringResource(R.string.controller_node_id, formatNodeId(nodeId)),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      IconButton(onClick = { showConfirmDialog = true }) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.remove_controller),
            tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}
