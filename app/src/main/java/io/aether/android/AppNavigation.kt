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
import io.aether.android.screens.device.DeviceRoute
import io.aether.android.screens.device.settings.ControllersRoute
import io.aether.android.screens.device.settings.DeviceSettingsRoute
import io.aether.android.screens.device.settings.InspectRoute
import io.aether.android.screens.home.HomeRoute
import io.aether.android.screens.scanner.ScannerRoute
import io.aether.android.screens.thread.ThreadRoute

// Constants for Navigation destinations
const val DEST_HOME = "home"
const val DEST_SCANNER = "scanner"
const val DEST_THREAD = "thread"

const val ARG_NODE_ID = "nodeId"
const val ARG_DEVICE_NAME = "deviceName"

const val ROUTE_DEVICE = "device/{$ARG_NODE_ID}?$ARG_DEVICE_NAME={$ARG_DEVICE_NAME}"
const val ROUTE_DEVICE_SETTINGS = "device/{$ARG_NODE_ID}/settings"
const val ROUTE_DEVICE_DATA_MODEL = "device/{$ARG_NODE_ID}/data-model"
const val ROUTE_DEVICE_FABRICS = "device/{$ARG_NODE_ID}/fabrics"

fun routeToDevice(nodeId: Long, deviceName: String): String =
    "device/$nodeId?$ARG_DEVICE_NAME=${Uri.encode(deviceName)}"

fun routeToDeviceSettings(nodeId: Long): String = "device/$nodeId/settings"

fun routeToDeviceDataModel(nodeId: Long): String = "device/$nodeId/data-model"

fun routeToDeviceFabrics(nodeId: Long): String = "device/$nodeId/fabrics"

@Composable
fun AppNavigation(
    navController: NavHostController,
    innerPadding: PaddingValues,
    updateActions: (@Composable RowScope.() -> Unit) -> Unit,
) {
  // Lambdas to all destinations needed in our various routes.
  // [Top level Route Composables should not be passed the navController explicitly,
  // as NavController is an unstable type. Indirection like a lambda should be used
  // as the compiler considers lambdas stable.]
  val navigateToHome: () -> Unit = remember { { navController.navigate(DEST_HOME) } }
  val navigateToDevice: (nodeId: Long, deviceName: String) -> Unit = remember {
    { nodeId, deviceName -> navController.navigate(routeToDevice(nodeId, deviceName)) }
  }
  val navigateToInspect: (nodeId: Long) -> Unit = remember {
    { nodeId -> navController.navigate(routeToDeviceDataModel(nodeId)) }
  }
  val navigateToDeviceSettings: (nodeId: Long) -> Unit = remember {
    { nodeId -> navController.navigate(routeToDeviceSettings(nodeId)) }
  }
  val navigateToControllers: (nodeId: Long) -> Unit = remember {
    { nodeId -> navController.navigate(routeToDeviceFabrics(nodeId)) }
  }

  NavHost(navController = navController, startDestination = DEST_HOME) {
    // Home
    composable(DEST_HOME) { HomeRoute(innerPadding, navigateToDevice) }
    // Device
    composable(
        ROUTE_DEVICE,
        arguments =
            listOf(
                navArgument(ARG_NODE_ID) { type = NavType.LongType },
                navArgument(ARG_DEVICE_NAME) {
                  type = NavType.StringType
                  defaultValue = ""
                },
            ),
    ) {
      DeviceRoute(
          innerPadding,
          updateActions,
          navigateToDeviceSettings,
          it.arguments?.getLong(ARG_NODE_ID)!!,
      )
    }
    // Device settings
    composable(
        ROUTE_DEVICE_SETTINGS,
        arguments = listOf(navArgument(ARG_NODE_ID) { type = NavType.LongType }),
    ) {
      DeviceSettingsRoute(
          innerPadding,
          navigateToHome,
          navigateToInspect,
          navigateToControllers,
          it.arguments?.getLong(ARG_NODE_ID)!!,
      )
    }
    // Inspect device from Device Settings
    composable(
        ROUTE_DEVICE_DATA_MODEL,
        arguments = listOf(navArgument(ARG_NODE_ID) { type = NavType.LongType }),
    ) {
      InspectRoute(innerPadding, it.arguments?.getLong(ARG_NODE_ID)!!)
    }
    // Controllers
    composable(
        ROUTE_DEVICE_FABRICS,
        arguments = listOf(navArgument(ARG_NODE_ID) { type = NavType.LongType }),
    ) {
      ControllersRoute(innerPadding, it.arguments?.getLong(ARG_NODE_ID)!!)
    }
    // Matter Device Scanner
    composable(DEST_SCANNER) { ScannerRoute(innerPadding) }
    // Thread network utilities
    composable(DEST_THREAD) { ThreadRoute(innerPadding) }
  }
}
