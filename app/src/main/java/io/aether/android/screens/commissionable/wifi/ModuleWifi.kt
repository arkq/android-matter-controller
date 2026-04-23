// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.commissionable.wifi

import android.content.Context
import android.net.wifi.WifiManager
import io.aether.android.screens.commissionable.MatterBeaconInject
import io.aether.android.screens.commissionable.MatterBeaconProducer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModuleWifi {
  @Binds
  @IntoSet
  abstract fun bindsMatterBeaconProducer(impl: MatterBeaconProducerWifi): MatterBeaconProducer

  companion object {
    @Provides
    @MatterBeaconInject
    fun providesWifiManager(@ApplicationContext context: Context): WifiManager {
      return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
  }
}
