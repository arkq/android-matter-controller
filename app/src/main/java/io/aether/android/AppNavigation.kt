// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.aether.android.screens.commissionable.CommissionableRoute
import io.aether.android.screens.device.DeviceRoute
import io.aether.android.screens.home.HomeRoute
import io.aether.android.screens.inspect.InspectRoute
import io.aether.android.screens.thread.ThreadRoute

// Constants for Navigation destinations
const val DEST_HOME = "home"
const val DEST_DEVICE = "device"
const val DEST_INSPECT = "inspect"
const val DEST_COMMISSIONABLE_DEVICES = "commissionable_devices"
const val DEST_THREAD = "thread"

@Composable
fun AppNavigation(
  navController: NavHostController,
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  ) {
  // Lambdas to all destinations needed in our various routes.
  // [Top level Route Composables should not be passed the navController explicitly,
  // as NavController is an unstable type. Indirection like a lambda should be used
  // as the compiler considers lambdas stable.]
  val navigateToHome: () -> Unit = remember {
    { navController.navigate(DEST_HOME) }
  }
  val navigateToDevice: (deviceId: Long) -> Unit = remember {
    { navController.navigate("$DEST_DEVICE/$it") }
  }
  val navigateToInspect: (deviceId: Long) -> Unit = remember {
    { navController.navigate("$DEST_INSPECT/$it") }
  }

  NavHost(navController = navController, startDestination = DEST_HOME) {
    // Home
    composable(DEST_HOME) { backStackEntry ->
        HomeRoute(innerPadding, updateTitle, navigateToDevice)
    }
    // Device
    composable(
      "$DEST_DEVICE/{deviceId}",
        arguments = listOf(navArgument("deviceId") { type = NavType.LongType }))
    {
      DeviceRoute(
        innerPadding,
        updateTitle,
        navigateToHome,
        navigateToInspect,
        it.arguments?.getLong("deviceId")!!)
    }
    // Inspect device
    composable(
      "$DEST_INSPECT/{deviceId}",
      arguments = listOf(navArgument("deviceId") { type = NavType.LongType }))
    {
      InspectRoute(innerPadding, updateTitle, it.arguments?.getLong("deviceId")!!)
    }
    // Commissionable devices
    composable(DEST_COMMISSIONABLE_DEVICES) {
      CommissionableRoute(innerPadding, updateTitle)
    }
    // Thread network utilities
    composable(DEST_THREAD) {
      ThreadRoute(innerPadding, updateTitle)
    }
  }
}
