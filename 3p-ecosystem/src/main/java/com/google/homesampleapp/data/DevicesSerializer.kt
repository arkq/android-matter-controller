// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package com.google.homesampleapp.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.homesampleapp.Devices
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object DevicesSerializer : Serializer<Devices> {

  override val defaultValue: Devices = Devices.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): Devices {
    try {
      return Devices.parseFrom(input)
    } catch (exception: InvalidProtocolBufferException) {
      throw CorruptionException("Cannot read proto.", exception)
    }
  }

  override suspend fun writeTo(t: Devices, output: OutputStream) = t.writeTo(output)
}

val Context.devicesDataStore: DataStore<Devices> by
    dataStore(fileName = "devices_store.proto", serializer = DevicesSerializer)
