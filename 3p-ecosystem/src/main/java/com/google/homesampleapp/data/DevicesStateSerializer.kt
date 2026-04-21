// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package com.google.homesampleapp.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.homesampleapp.DevicesState
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object DevicesStateSerializer : Serializer<DevicesState> {

  override val defaultValue: DevicesState = DevicesState.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): DevicesState {
    try {
      return DevicesState.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read proto.", exception)
    }
  }

  override suspend fun writeTo(t: DevicesState, output: OutputStream) = t.writeTo(output)
}

val Context.devicesStateDataStore: DataStore<DevicesState> by
    dataStore(fileName = "devices_state__store.proto", serializer = DevicesStateSerializer)
