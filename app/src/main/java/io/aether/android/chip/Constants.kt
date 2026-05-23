// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

// TODO: Generate these from the Matter data model

// A reference to a specific attribute on a specific cluster
data class ClusterAttribute(val clusterId: Long, val attributeId: Long)

// Matter endpoint constants
const val ROOT_ENDPOINT: Long = 0L

// Well-known cluster IDs
const val CLUSTER_ON_OFF: Long = 6L
const val CLUSTER_LEVEL_CONTROL: Long = 8L
const val CLUSTER_COLOR_CONTROL: Long = 768L

// Well-known attributes used by the app
val ON_OFF_ATTRIBUTE = ClusterAttribute(CLUSTER_ON_OFF, 0L)
val LEVEL_ATTRIBUTE = ClusterAttribute(CLUSTER_LEVEL_CONTROL, 0L)
val COLOR_TEMPERATURE_ATTRIBUTE = ClusterAttribute(CLUSTER_COLOR_CONTROL, 7L)

// Matter device type IDs from the Matter data model
const val DEVICE_TYPE_ON_OFF_LIGHT: Long = 256L // 0x0100
const val DEVICE_TYPE_DIMMABLE_LIGHT: Long = 257L // 0x0101
const val DEVICE_TYPE_ON_OFF_LIGHT_SWITCH: Long = 259L // 0x0103
const val DEVICE_TYPE_ON_OFF_PLUGIN_UNIT: Long = 266L // 0x010A
const val DEVICE_TYPE_COLOR_TEMPERATURE_LIGHT: Long = 268L // 0x010C
const val DEVICE_TYPE_EXTENDED_COLOR_LIGHT: Long = 269L // 0x010D
