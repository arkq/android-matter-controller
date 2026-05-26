// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.dimensionResource
import io.aether.android.R
import io.aether.android.data.DevicesStateRepository
import io.aether.android.endpointFor
import io.aether.android.screens.device.cluster.OnOffClusterControl
import io.aether.android.screens.home.DeviceUiModel

/**
 * Device-type control for endpoints that expose only the **OnOff** cluster (e.g. smart outlets,
 * on/off lights). Manages local state and delegates rendering to [OnOffClusterControl].
 *
 * @param endpointModel the endpoint whose state is shown
 * @param lastUpdatedDeviceState the most recent state broadcast from the repository; used to keep
 *   local state in sync without a full model reload
 * @param onOnOffClick called when the user toggles the switch; receives the new on/off value
 */
@Composable
internal fun OnOffDeviceControl(
    endpointModel: DeviceUiModel,
    lastUpdatedDeviceState: DevicesStateRepository.EndpointStateSnapshot?,
    onOnOffClick: (Boolean) -> Unit,
) {
  var isOnline by remember(endpointModel) { mutableStateOf(endpointModel.isOnline) }
  var isOn by remember(endpointModel) { mutableStateOf(endpointModel.isOn) }

  LaunchedEffect(endpointModel, lastUpdatedDeviceState) {
    when {
      lastUpdatedDeviceState?.nodeId == endpointModel.node.nodeId &&
          lastUpdatedDeviceState.endpointId == endpointFor(endpointModel.endpoint) -> {
        isOnline = lastUpdatedDeviceState.online
        isOn = lastUpdatedDeviceState.on
      }
      lastUpdatedDeviceState == null -> {
        isOnline = endpointModel.isOnline
        isOn = endpointModel.isOn
      }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_normal))) {
    OnOffClusterControl(
        isOnline = isOnline,
        isOn = isOn,
        onToggle = { value ->
          isOn = value
          onOnOffClick(value)
        },
    )
  }
}
