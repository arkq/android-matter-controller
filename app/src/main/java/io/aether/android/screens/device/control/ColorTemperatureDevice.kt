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
import io.aether.android.R
import io.aether.android.data.DevicesStateRepository
import io.aether.android.endpointFor
import io.aether.android.screens.device.cluster.COLOR_TEMPERATURE_MAX
import io.aether.android.screens.device.cluster.LEVEL_MAX
import io.aether.android.screens.device.cluster.LevelClusterControl
import io.aether.android.screens.device.cluster.OnOffClusterControl
import io.aether.android.screens.home.DeviceUiModel
import timber.log.Timber

/**
 * Device-type control for endpoints that expose the **OnOff**, **Level Control**, and **Color
 * Control** (colour temperature) clusters (e.g. colour-temperature and extended-colour lights).
 * Manages local state and delegates rendering to [OnOffClusterControl] and [LevelClusterControl].
 *
 * @param endpointModel the endpoint whose state is shown
 * @param lastUpdatedDeviceState the most recent state broadcast from the repository
 * @param onOnOffClick called when the user toggles the switch
 * @param onBrightnessChange called when the brightness slider is released; raw value `0..254`
 * @param onColorTemperatureChange called when the colour-temperature slider is released; raw mireds
 *   value `0..1667`
 */
@Composable
internal fun ColorTemperatureDeviceControl(
    endpointModel: DeviceUiModel,
    lastUpdatedDeviceState: DevicesStateRepository.EndpointStateSnapshot?,
    onOnOffClick: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onColorTemperatureChange: (Int) -> Unit,
) {
  var isOnline by remember(endpointModel) { mutableStateOf(endpointModel.isOnline) }
  var isOn by remember(endpointModel) { mutableStateOf(endpointModel.isOn) }
  var brightness by remember(endpointModel) { mutableFloatStateOf(endpointModel.level / LEVEL_MAX) }
  var colorTemperature by
      remember(endpointModel) {
        mutableFloatStateOf(endpointModel.colorTemperature / COLOR_TEMPERATURE_MAX)
      }

  LaunchedEffect(endpointModel, lastUpdatedDeviceState) {
    when {
      lastUpdatedDeviceState?.nodeId == endpointModel.device.nodeId &&
          lastUpdatedDeviceState.endpointId == endpointFor(endpointModel.device) -> {
        isOnline = lastUpdatedDeviceState.online
        isOn = lastUpdatedDeviceState.on
        brightness = lastUpdatedDeviceState.level / LEVEL_MAX
        colorTemperature = lastUpdatedDeviceState.colorTemperature / COLOR_TEMPERATURE_MAX
      }
      lastUpdatedDeviceState == null -> {
        isOnline = endpointModel.isOnline
        isOn = endpointModel.isOn
        brightness = endpointModel.level / LEVEL_MAX
        colorTemperature = endpointModel.colorTemperature / COLOR_TEMPERATURE_MAX
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
    LevelClusterControl(
        title = stringResource(R.string.color_temperature),
        isOnline = isOnline,
        isOn = isOn,
        level = colorTemperature,
        onLevelChange = { colorTemperature = it },
        onLevelChangeFinished = {
          onColorTemperatureChange((colorTemperature * COLOR_TEMPERATURE_MAX).toInt())
        },
    )
  }
}

// -----------------------------------------------------------------------------------------------
// Compose Previews

@Preview(widthDp = 300)
@Composable
private fun ColorTemperatureDeviceControl_Online() {
  val model =
      DeviceUiModel(
          device =
              Device.newBuilder()
                  .setNodeId(1L)
                  .setDeviceType(Device.DeviceType.TYPE_COLOR_TEMPERATURE_LIGHT)
                  .setDateCommissioned(Timestamp.getDefaultInstance())
                  .setName("CTLight")
                  .setSupportsLevelControl(true)
                  .setSupportsColorTemperature(true)
                  .build(),
          isOnline = true,
          isOn = true,
          level = 127,
          colorTemperature = 833,
      )
  MaterialTheme {
    ColorTemperatureDeviceControl(
        endpointModel = model,
        lastUpdatedDeviceState = null,
        onOnOffClick = { Timber.d("onOff: $it") },
        onBrightnessChange = { Timber.d("brightness: $it") },
        onColorTemperatureChange = { Timber.d("colorTemp: $it") },
    )
  }
}
