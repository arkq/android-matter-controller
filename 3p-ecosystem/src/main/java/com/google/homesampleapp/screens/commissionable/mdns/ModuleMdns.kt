// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package com.google.homesampleapp.screens.commissionable.mdns

import com.google.homesampleapp.screens.commissionable.MatterBeaconProducer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModuleMdns {
  @Binds
  @IntoSet
  abstract fun bindsMatterBeaconProducer(impl: MatterBeaconProducerMdns): MatterBeaconProducer
}
