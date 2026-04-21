// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package com.google.homesampleapp.screens.commissionable.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import com.google.homesampleapp.screens.commissionable.MatterBeaconInject
import com.google.homesampleapp.screens.commissionable.MatterBeaconProducer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * If this is instantiated, then all permissions have been cleared and Bluetooth is enabled. See
 * [SettingsDeveloperUtilitiesNestedFragment].
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModuleBle {
  @Binds
  @IntoSet
  abstract fun bindsMatterBeaconProducer(impl: MatterBeaconProducerBle): MatterBeaconProducer

  companion object {
    @Provides
    @MatterBeaconInject
    fun providesBluetoothLeScanner(@ApplicationContext context: Context): BluetoothLeScanner? {
      return BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner
    }
  }
}
