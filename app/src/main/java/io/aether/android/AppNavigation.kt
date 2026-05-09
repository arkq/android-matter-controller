// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.aether.android.screens.commissionable.CommissionableRoute
import io.aether.android.screens.device.DeviceRoute
import io.aether.android.screens.device.settings.ControllersRoute
import io.aether.android.screens.device.settings.DeviceSettingsRoute
import io.aether.android.screens.device.settings.InspectRoute
import io.aether.android.screens.home.HomeRoute
import io.aether.android.screens.thread.ThreadRoute

// Constants for Navigation destinations
const val DEST_HOME = "home"
const val DEST_DEVICE = "device"
const val DEST_INSPECT = "inspect"
const val DEST_DEVICE_SETTINGS = "device_settings"
const val DEST_CONTROLLERS = "controllers"
const val DEST_COMMISSIONABLE_DEVICES = "commissionable_devices"
const val DEST_THREAD = "thread"

@Composable
fun AppNavigation(
    navController: NavHostController,
    innerPadding: PaddingValues,
    updateTitle: (title: String) -> Unit,
    updateTitleContent: (@Composable () -> Unit) -> Unit,
    updateActions: (@Composable RowScope.() -> Unit) -> Unit,
) {
  // Lambdas to all destinations needed in our various routes.
  // [Top level Route Composables should not be passed the navController explicitly,
  // as NavController is an unstable type. Indirection like a lambda should be used
  // as the compiler considers lambdas stable.]
  val navigateToHome: () -> Unit = remember { { navController.navigate(DEST_HOME) } }
  val navigateToDevice: (deviceId: Long, deviceName: String) -> Unit = remember {
    { deviceId, deviceName ->
      navController.navigate("$DEST_DEVICE/$deviceId?deviceName=${Uri.encode(deviceName)}")
    }
  }
  val navigateToInspect: (deviceId: Long) -> Unit = remember {
    { navController.navigate("$DEST_DEVICE_SETTINGS/$it/$DEST_INSPECT") }
  }
  val navigateToDeviceSettings: (deviceId: Long) -> Unit = remember {
    { navController.navigate("$DEST_DEVICE_SETTINGS/$it") }
  }
  val navigateToControllers: (deviceId: Long) -> Unit = remember {
    { navController.navigate("$DEST_CONTROLLERS/$it") }
  }

  NavHost(navController = navController, startDestination = DEST_HOME) {
    // Home
    composable(DEST_HOME) { HomeRoute(innerPadding, updateTitle, navigateToDevice) }
    // Device
    composable(
        "$DEST_DEVICE/{deviceId}?deviceName={deviceName}",
        arguments =
            listOf(
                navArgument("deviceId") { type = NavType.LongType },
                navArgument("deviceName") {
                  type = NavType.StringType
                  defaultValue = ""
                },
            ),
    ) {
      DeviceRoute(
          innerPadding,
          updateTitleContent,
          updateActions,
          navigateToDeviceSettings,
          it.arguments?.getLong("deviceId")!!,
          it.arguments?.getString("deviceName") ?: "",
      )
    }
    // Device settings
    composable(
        "$DEST_DEVICE_SETTINGS/{deviceId}",
        arguments = listOf(navArgument("deviceId") { type = NavType.LongType }),
    ) {
      DeviceSettingsRoute(
          innerPadding,
          updateTitle,
          navigateToHome,
          navigateToInspect,
          navigateToControllers,
          it.arguments?.getLong("deviceId")!!,
      )
    }
    // Inspect device from Device Settings
    composable(
        "$DEST_DEVICE_SETTINGS/{deviceId}/$DEST_INSPECT",
        arguments = listOf(navArgument("deviceId") { type = NavType.LongType }),
    ) {
      InspectRoute(innerPadding, updateTitle, it.arguments?.getLong("deviceId")!!)
    }
    // Controllers
    composable(
        "$DEST_CONTROLLERS/{deviceId}",
        arguments = listOf(navArgument("deviceId") { type = NavType.LongType }),
    ) {
      ControllersRoute(innerPadding, updateTitle, it.arguments?.getLong("deviceId")!!)
    }
    // Commissionable devices
    composable(DEST_COMMISSIONABLE_DEVICES) { CommissionableRoute(innerPadding, updateTitle) }
    // Thread network utilities
    composable(DEST_THREAD) { ThreadRoute(innerPadding, updateTitle) }
  }
}
