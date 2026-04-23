// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.commissionable

import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import io.aether.android.DeveloperUtilitiesScreen
import io.aether.android.HomeScreen
import io.aether.android.MainActivity
import io.aether.android.SettingsScreen
import io.aether.android.screens.commissionable.ble.MatterBeaconProducerBleFake
import io.aether.android.screens.commissionable.ble.ModuleBle
import io.aether.android.screens.commissionable.mdns.MatterBeaconProducerMdnsFake
import io.aether.android.screens.commissionable.wifi.MatterBeaconProducerWifiFake
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test to verify that Matter beacons are properly shown in CommissionableFragment.
 *
 * The test navigates to the "Commissionable devices" screen, and then each beacon producer (BLE,
 * mDNS, Wi-Fi) emits a beacon at a specific interval.
 *
 * Simply visually inspect that all these beacons are properly shown on the screen (e.g. proper
 * icon, inactive mDNS services shows with different color, etc).
 */

// See https://developer.android.com/training/dependency-injection/hilt-testing
@UninstallModules(ModuleBle::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class CommissionableFragmentTest {
  @Module
  @InstallIn(SingletonComponent::class)
  internal abstract class ModuleBleTest {
    @Singleton
    @Binds
    @IntoSet
    abstract fun bindsMatterBeaconProducer(
        fakeBeacon: MatterBeaconProducerBleFake
    ): MatterBeaconProducer
  }

  @Module
  @InstallIn(SingletonComponent::class)
  internal abstract class ModuleMdnsTest {
    @Singleton
    @Binds
    @IntoSet
    abstract fun bindsMatterBeaconProducer(
        fakeBeacon: MatterBeaconProducerMdnsFake
    ): MatterBeaconProducer
  }

  @Module
  @InstallIn(SingletonComponent::class)
  internal abstract class ModuleWifiTest {
    @Singleton
    @Binds
    @IntoSet
    abstract fun bindsMatterBeaconProducer(
        fakeBeacon: MatterBeaconProducerWifiFake
    ): MatterBeaconProducer
  }

  @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
  @get:Rule var hiltRule = HiltAndroidRule(this)

  @Before
  fun init() {
    // Hilt injection.
    hiltRule.inject()
  }

  // ---------------------------------------------------------------------------
  // Test

  @Test
  fun testFragmentBehavior() {
    // Navigate to "Commissionable devices" screen.
    HomeScreen.navigateToSettingsScreen()
    SettingsScreen.selectDeveloperUtilities()
    DeveloperUtilitiesScreen.selectCommissionableDevices()

    // Let the producers run for 60 seconds, giving the user a chance to verify that all beacons
    // are properly shown on screen.
    Thread.sleep(60000)
  }
}
