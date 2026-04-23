// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import io.aether.android.screens.home.DeviceViewHolder
import io.aether.android.screens.home.HomeFragmentRecyclerViewTest
import org.junit.Assert

/** Suite of utility objects and functions used across our Android Espresso/UIAutomator tests. */
object HomeScreen {
  fun verifyInHomeScreen(delayInSecs: Int) {
    Thread.sleep(delayInSecs * 1000L)
    // Verify Add Device button is displayed.
    onView(withId(R.id.addDeviceButton)).check(matches(isDisplayed()))
  }

  fun navigateToSettingsScreen() {
    // Click on "Settings" icon in the top app bar.
    onView(withId(R.id.settings)).perform(click())
  }

  fun addDevice() {
    // Click on the Add Device (+) button.
    onView(withId(R.id.addDeviceButton)).perform(click())
  }

  fun enterDeviceName(device: UiDevice, deviceName: String) {
    // Had a hard time figure that one out. Maybe there's another, better way?
    device.findObject(UiSelector().className("android.widget.EditText").instance(0)).text =
        deviceName
    device.findObject(By.text("OK")).click()
  }

  fun selectDevice(count: Int) {
    onView(withId(R.id.devicesListRecyclerView))
        .perform(RecyclerViewActions.actionOnItemAtPosition<DeviceViewHolder>(count - 1, click()))
  }

  fun verifyDeviceShown(deviceName: String) {
    onView(withText(deviceName)).check(matches(isDisplayed()))
  }

  fun toggleOnOffState() {
    onView(withId(R.id.onoff_switch)).perform(click())
  }
}

object DeviceScreen {
  fun verifyDevice(count: Int, device: HomeFragmentRecyclerViewTest.TestDevice) {
    onView(withText("Share " + device.getName(count.toLong()))).check(matches(isDisplayed()))
  }
}

/**
 * Need to use UI Automator because we yield control to external activity (Google Play Services) for
 * commissioning.
 */
object GpsCommissioningQrCodeScreen {
  fun selectSetupWithoutQrCode(device: UiDevice) {
    device.findObject(By.text("Set up without QR code")).click()
  }
}

/**
 * Need to use UI Automator because we yield control to external activity (Google Play Services) for
 * commissioning.
 */
object GpsCommissioningSetupCodeScreen {
  fun enterSetupCode(device: UiDevice, setupCode: String) {
    val setupCodeTextbox = UiSelector().className("android.widget.EditText").instance(0)
    device.findObject(setupCodeTextbox).text = setupCode
    device.findObject(By.text("Next")).click()
  }
}

/**
 * Need to use UI Automator because we yield control to external activity (Google Play Services) for
 * commissioning.
 */
object GpsCommissioningDeviceConnectedScreen {
  fun waitUntilShown(device: UiDevice, timeoutSecs: Int) {
    Assert.assertNotNull(
        device.wait(Until.hasObject(By.text("Device connected")), timeoutSecs * 1000L))
  }

  fun selectDone(device: UiDevice) {
    device.findObject(By.text("Done")).click()
  }
}

object SettingsScreen {
  fun selectDeveloperUtilities() {
    onView(withText("Developer utilities")).perform(click())
  }
}

object DeveloperUtilitiesScreen {
  fun selectCommissionableDevices() {
    onView(withText("Commissionable devices")).perform(click())
  }
}

object CommissionableDevicesScreen {
  fun verifyAtLeastOneDeviceCommissionable() {
    // Give enough time for the commissionable devices to be scanned.
    Thread.sleep(5000)
    onView(withId(R.id.compose_view)).check(matches(hasMinimumChildCount(1)))
  }

  fun navigateToHomeScreen() {
    // Click on the Home icon
    onView(withId(R.id.topAppBar)).perform(click())
  }
}

fun navigateBack() {
  pressBack()
}

/**
 * Useful when debugging issues. This dumps the views to be able to inspect what's available at that
 * point to extract what's needed.
 */
fun dumpViewsAndExit() {
  onView(withText("BogusToThrowExceptionAndInspectViews")).perform(click())
}
