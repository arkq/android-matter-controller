// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.protobuf.Timestamp
import io.aether.android.Device
import io.aether.android.DeviceState
import io.aether.android.R
import io.aether.android.screens.device.cluster.LEVEL_MAX
import io.aether.android.screens.device.cluster.LevelClusterControl
import io.aether.android.screens.device.cluster.OnOffClusterControl
import io.aether.android.screens.home.DeviceUiModel
import timber.log.Timber

/**
 * Device-type control for endpoints that expose the **OnOff** and **Level Control** clusters
 * (e.g. dimmable lights). Manages local state and delegates rendering to [OnOffClusterControl]
 * and [LevelClusterControl].
 *
 * @param endpointModel the endpoint whose state is shown
 * @param lastUpdatedDeviceState the most recent state broadcast from the repository
 * @param onOnOffClick called when the user toggles the switch
 * @param onBrightnessChange called when the user finishes moving the brightness slider; receives
 *   the raw cluster value in the range `0..254`
 */
@Composable
internal fun DimmableDeviceControl(
  endpointModel: DeviceUiModel,
  lastUpdatedDeviceState: DeviceState?,
  onOnOffClick: (Boolean) -> Unit,
  onBrightnessChange: (Int) -> Unit,
) {
  var isOnline by remember(endpointModel) { mutableStateOf(endpointModel.isOnline) }
  var isOn by remember(endpointModel) { mutableStateOf(endpointModel.isOn) }
  var brightness by remember(endpointModel) { mutableFloatStateOf(endpointModel.level / LEVEL_MAX) }

  LaunchedEffect(endpointModel, lastUpdatedDeviceState) {
    when {
      lastUpdatedDeviceState?.deviceId == endpointModel.device.deviceId -> {
        isOnline = lastUpdatedDeviceState.online
        isOn = lastUpdatedDeviceState.on
        brightness = lastUpdatedDeviceState.level / LEVEL_MAX
      }
      lastUpdatedDeviceState == null -> {
        isOnline = endpointModel.isOnline
        isOn = endpointModel.isOn
        brightness = endpointModel.level / LEVEL_MAX
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
    LevelClusterControl(
      title = stringResource(R.string.brightness),
      isOnline = isOnline,
      isOn = isOn,
      level = brightness,
      onLevelChange = { brightness = it },
      onLevelChangeFinished = { onBrightnessChange((brightness * LEVEL_MAX).toInt()) },
    )
  }
}

// -----------------------------------------------------------------------------------------------
// Compose Previews

@Preview(widthDp = 300)
@Composable
private fun DimmableDeviceControl_Online() {
  val model = DeviceUiModel(
    device = Device.newBuilder()
      .setDeviceId(1L)
      .setDeviceType(Device.DeviceType.TYPE_DIMMABLE_LIGHT)
      .setDateCommissioned(Timestamp.getDefaultInstance())
      .setName("DimmableLight")
      .build(),
    isOnline = true,
    isOn = true,
    level = 127,
  )
  MaterialTheme {
    DimmableDeviceControl(
      endpointModel = model,
      lastUpdatedDeviceState = null,
      onOnOffClick = { Timber.d("onOff: $it") },
      onBrightnessChange = { Timber.d("brightness: $it") },
    )
  }
}
