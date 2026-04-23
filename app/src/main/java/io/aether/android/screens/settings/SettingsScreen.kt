// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.aether.android.R
import io.aether.android.VERSION_NAME
import io.aether.android.screens.common.HtmlInfoDialog
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.rememberPreferenceState
import me.zhanghai.compose.preference.switchPreference

@Composable
internal fun SettingsRoute(
  innerPadding: PaddingValues,
  updateTitle: (title: String) -> Unit,
  navigateToDeveloperUtilities: () -> Unit,
) {
  LaunchedEffect(Unit) {
    updateTitle("Settings")
  }

  SettingsScreen(innerPadding, navigateToDeveloperUtilities)
}

@Composable
private fun SettingsScreen(innerPadding: PaddingValues, navigateToDeveloperUtilities: () -> Unit) {
  var showAboutDialog by remember { mutableStateOf(false) }
  var showHalfsheetDialog by remember { mutableStateOf(false) }
  // Cannot use extension function for Halfsheet Preference, onValueChange needed.
  val showHalfsheetPref = rememberPreferenceState("halfsheet_preference", false)

  LazyColumn(modifier = Modifier.fillMaxSize()) {
    switchPreference(
      key = "offline_devices_preference",
      defaultValue = true,
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_signal_wifi_off_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "Offline devices") },
      summary = { Text(text = if (it) "Show offline devices" else "Do not show offline devices") },
    )
    item {
      // Need to use this form as we must have access to onValueChange.
      var value by showHalfsheetPref
      SwitchPreference(
        value = value,
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.baseline_notifications_24),
            contentDescription = null, // decorative element
          )
        },
        title = { Text(text = "Halfsheet notification") },
        summary = {
          Text(
            text =
              if (showHalfsheetPref.value)
                "Show proactive commissionable discovery notifications for Matter devices"
              else "Do not show proactive commissionable discovery notifications for Matter devices"
          )
        },
        onValueChange = {
          value = it
          showHalfsheetDialog = true
        },
      )
    }
    preference(
      key = "developer_utilities_preference",
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_developer_mode_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "Developer utilities") },
      summary = { Text(text = "Various utility functions for developers who want to dig deeper!") },
      onClick = navigateToDeveloperUtilities,
    )
    preference(
      key = "about_preference",
      icon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_baseline_help_24),
          contentDescription = null, // decorative element
        )
      },
      title = { Text(text = "About this app") },
      summary = { Text(text = "More information about this application") },
      onClick = { showAboutDialog = true },
    )
  }
  if (showAboutDialog) {
    HtmlInfoDialog(
      "About this app",
      stringResource(R.string.about_app, VERSION_NAME),
      onClick = { showAboutDialog = false },
    )
  }
  if (showHalfsheetDialog) {
    HtmlInfoDialog(
      "Halfsheet Notification",
      stringResource(R.string.halfsheet_notification_alert),
      onClick = { showHalfsheetDialog = false },
    )
  }
}
