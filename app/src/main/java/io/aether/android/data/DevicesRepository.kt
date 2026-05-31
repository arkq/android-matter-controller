// SPDX-FileCopyrightText: 2020 The Android Open Source Project
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.Device
import io.aether.android.Devices
import io.aether.android.MatterEndpoint
import io.aether.android.MatterFabricState
import io.aether.android.MatterNode
import io.aether.android.convertToAppDeviceType
import io.aether.android.convertToMatterDeviceType
import io.aether.android.getTimestampForNow
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.toDeviceTypeId
import io.aether.android.matter.toEndpointId
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Singleton repository that updates and persists the set of devices in the homesampleapp fabric.
 */
@Singleton
class DevicesRepository @Inject constructor(@ApplicationContext context: Context) {

  // Devices metadata is persisted in the same Proto DataStore as dynamic fabric state.
  private val devicesStateDataStore = context.devicesStateDataStore

  // The Flow to read data from the DataStore.
  val devicesFlow: Flow<Devices> =
      devicesStateDataStore.data
          .map { state ->
            val flattenedDevices =
                state.nodesList.flatMap { node ->
                  node.endpointsList.map { endpoint -> toDevice(node, endpoint) }
                }
            Devices.newBuilder().addAllDevices(flattenedDevices).build()
          }
          .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
              Timber.e(exception, "Error reading devices.")
              emit(Devices.getDefaultInstance())
            } else {
              throw exception
            }
          }

  suspend fun addDevice(device: Device) {
    Timber.d("addDevice: device [${device}]")
    val normalizedEndpoint = endpointOf(device)
    devicesStateDataStore.updateData { state ->
      val stateBuilder = state.toBuilder()
      val nodeIndex = findNodeIndex(state, device.nodeId)
      if (nodeIndex == -1) {
        val newNodeBuilder =
            MatterNode.newBuilder()
                .setNodeId(device.nodeId.toLong())
                .setVendorId(device.vendorId.toIntOrNull() ?: 0)
                .setVendorName(device.vendorName)
                .setProductId(device.productId.toIntOrNull() ?: 0)
                .setProductName(device.productName)
                .setDateCommissioned(getTimestampForNow())
                .setName(device.name)
                .setOnline(false)
        newNodeBuilder.addEndpoints(toEndpoint(device, normalizedEndpoint.toEndpointId()))
        val newNode = newNodeBuilder.build()
        stateBuilder.addNodes(newNode)
      } else {
        val existingNode = state.getNodes(nodeIndex)
        val nodeBuilder = existingNode.toBuilder()
        if (device.name.isNotBlank()) {
          nodeBuilder.name = device.name
        }
        if (device.vendorName.isNotBlank()) {
          nodeBuilder.vendorName = device.vendorName
        }
        if (device.productName.isNotBlank()) {
          nodeBuilder.productName = device.productName
        }
        if (device.vendorId.isNotBlank()) {
          nodeBuilder.vendorId = device.vendorId.toIntOrNull() ?: existingNode.vendorId
        }
        if (device.productId.isNotBlank()) {
          nodeBuilder.productId = device.productId.toIntOrNull() ?: existingNode.productId
        }
        val endpointIndex = findEndpointIndex(existingNode, normalizedEndpoint.toEndpointId())
        val updatedEndpoint =
            toEndpoint(
                device,
                normalizedEndpoint.toEndpointId(),
                if (endpointIndex == -1) null else existingNode.getEndpoints(endpointIndex),
            )
        if (endpointIndex == -1) {
          nodeBuilder.addEndpoints(updatedEndpoint)
        } else {
          nodeBuilder.setEndpoints(endpointIndex, updatedEndpoint)
        }
        stateBuilder.setNodes(nodeIndex, nodeBuilder.build())
      }
      stateBuilder.build()
    }
  }

  suspend fun addOrUpdateEndpoint(
      nodeId: NodeId,
      nodeName: String,
      vendorId: Int,
      vendorName: String,
      productId: Int,
      productName: String,
      endpoint: MatterEndpoint,
  ) {
    devicesStateDataStore.updateData { state ->
      val stateBuilder = state.toBuilder()
      val nodeIndex = findNodeIndex(state, nodeId)
      if (nodeIndex == -1) {
        val nodeBuilder =
            MatterNode.newBuilder()
                .setNodeId(nodeId.toLong())
                .setName(nodeName)
                .setVendorId(vendorId)
                .setVendorName(vendorName)
                .setProductId(productId)
                .setProductName(productName)
                .setDateCommissioned(getTimestampForNow())
                .setOnline(false)
        nodeBuilder.addEndpoints(endpoint)
        stateBuilder.addNodes(nodeBuilder.build())
      } else {
        val existingNode = state.getNodes(nodeIndex)
        val nodeBuilder = existingNode.toBuilder()
        if (nodeName.isNotBlank()) nodeBuilder.name = nodeName
        if (vendorName.isNotBlank()) nodeBuilder.vendorName = vendorName
        if (productName.isNotBlank()) nodeBuilder.productName = productName
        if (vendorId != 0) nodeBuilder.vendorId = vendorId
        if (productId != 0) nodeBuilder.productId = productId

        val normalizedEndpoint = endpointOf(endpoint)
        val endpointIndex = findEndpointIndex(existingNode, normalizedEndpoint.toEndpointId())
        if (endpointIndex == -1) {
          nodeBuilder.addEndpoints(endpoint)
        } else {
          nodeBuilder.setEndpoints(endpointIndex, endpoint)
        }
        stateBuilder.setNodes(nodeIndex, nodeBuilder.build())
      }
      stateBuilder.build()
    }
  }

  suspend fun updateDevice(device: Device) {
    Timber.d("updateDevice: device [${device}]")
    val nodeIndex = findNodeIndex(device.nodeId)
    if (nodeIndex == -1) {
      throw Exception("Device not found: ${device.nodeId}")
    }
    addDevice(device)
  }

  suspend fun updateDeviceType(nodeId: NodeId, deviceType: Device.DeviceType) {
    Timber.d("updateDeviceType: nodeId [${nodeId}] deviceType [${deviceType}]")
    val nodeIndex = findNodeIndex(nodeId)
    if (nodeIndex == -1) {
      Timber.e(
          "Unable to get device information to update its type: nodeId [${nodeId}] deviceType [${deviceType}]"
      )
      return
    }
    devicesStateDataStore.updateData { state ->
      val nodeBuilder = state.getNodes(nodeIndex).toBuilder()
      val endpoints = state.getNodes(nodeIndex).endpointsList
      for (i in endpoints.indices) {
        val endpointBuilder = endpoints[i].toBuilder()
        endpointBuilder.clearDeviceTypes()
        val matterType = convertToMatterDeviceType(deviceType)
        if (matterType != 0) {
          endpointBuilder.addDeviceTypes(matterType)
        }
        nodeBuilder.setEndpoints(i, endpointBuilder.build())
      }
      state.toBuilder().setNodes(nodeIndex, nodeBuilder.build()).build()
    }
  }

  suspend fun removeDevice(nodeId: NodeId) {
    Timber.d("removeDevice: nodeId [${nodeId}]")
    val nodeIndex = findNodeIndex(nodeId)
    if (nodeIndex == -1) {
      throw Exception("Device not found: ${nodeId}")
    }
    devicesStateDataStore.updateData { state -> state.toBuilder().removeNodes(nodeIndex).build() }
  }

  suspend fun getDevice(nodeId: NodeId): Device = getDeviceByNodeId(nodeId)

  suspend fun getDeviceByNodeId(nodeId: NodeId): Device {
    return getAllDevices().devicesList.firstOrNull { it.nodeId == nodeId }
        ?: throw Exception("Device not found for nodeId: ${nodeId}")
  }

  suspend fun getDevicesByNodeId(nodeId: NodeId): List<Device> {
    return getAllDevices().devicesList.filter { it.nodeId == nodeId }
  }

  suspend fun getAllDevices(): Devices {
    return devicesFlow.first()
  }

  suspend fun clearAllData() {
    devicesStateDataStore.updateData { state -> state.toBuilder().clearNodes().build() }
  }

  private suspend fun findNodeIndex(nodeId: NodeId): Int {
    val state = devicesStateDataStore.data.first()
    return findNodeIndex(state, nodeId)
  }

  private fun findNodeIndex(state: MatterFabricState, nodeId: NodeId): Int {
    val nodesCount = state.nodesCount
    for (index in 0 until nodesCount) {
      if (state.getNodes(index).nodeId == nodeId.toLong()) {
        return index
      }
    }
    return -1
  }

  private fun findEndpointIndex(node: MatterNode, endpointId: EndpointId): Int {
    val endpointsCount = node.endpointsCount
    for (index in 0 until endpointsCount) {
      if (endpointOf(node.getEndpoints(index)) == endpointId.toInt()) {
        return index
      }
    }
    return -1
  }

  private fun endpointOf(device: Device): Int {
    return if (device.endpointId.toInt() != 0) device.endpointId.toInt() else 1
  }

  private fun endpointOf(endpoint: MatterEndpoint): Int {
    return if (endpoint.endpointId != 0) endpoint.endpointId else 1
  }

  private fun toDevice(node: MatterNode, endpoint: MatterEndpoint): Device {
    val type =
        endpoint.deviceTypesList.firstOrNull()?.toLong()?.toDeviceTypeId()?.let {
          convertToAppDeviceType(it)
        } ?: Device.DeviceType.TYPE_UNKNOWN
    return Device.newBuilder()
        .setNodeId(node.nodeId)
        .setEndpointId(endpointOf(endpoint).toEndpointId())
        .setName(if (node.name.isNotBlank()) node.name else endpoint.label)
        .setVendorId(node.vendorId.toString())
        .setVendorName(node.vendorName)
        .setProductId(node.productId.toString())
        .setProductName(node.productName)
        .setDeviceType(type)
        .setSupportsLevelControl(endpoint.supportsLevelControl)
        .setSupportsColorTemperature(endpoint.supportsColorTemperature)
        .setOn(endpoint.on)
        .setLevel(endpoint.level)
        .setColorTemperature(endpoint.colorTemperature)
        .build()
  }

  private fun toEndpoint(
      device: Device,
      endpointId: EndpointId,
      existing: MatterEndpoint? = null,
  ): MatterEndpoint {
    val builder = (existing ?: MatterEndpoint.getDefaultInstance()).toBuilder()
    builder.endpointId = endpointId.toInt()
    builder.label = device.name
    builder.supportsLevelControl = device.supportsLevelControl
    builder.supportsColorTemperature = device.supportsColorTemperature
    builder.on = device.on
    builder.level = device.level
    builder.colorTemperature = device.colorTemperature
    builder.clearDeviceTypes()
    val matterType = convertToMatterDeviceType(device.deviceType)
    if (matterType != 0) {
      builder.addDeviceTypes(matterType)
    }
    return builder.build()
  }
}
