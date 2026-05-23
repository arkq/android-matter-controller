// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.protobuf.Timestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.MatterEndpoint
import io.aether.android.MatterFabricState
import io.aether.android.MatterNode
import io.aether.android.getTimestampForNow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import timber.log.Timber

/** Singleton repository that updates the dynamic state of the devices on the app fabric. */
@Singleton
class DevicesStateRepository @Inject constructor(@ApplicationContext context: Context) {

  data class EndpointStateSnapshot(
      val nodeId: Long,
      val endpointId: Int,
      val dateCaptured: Timestamp,
      val online: Boolean,
      val on: Boolean,
      val level: Int,
      val colorTemperature: Int,
  )

  // The datastore managed by DevicesStateRepository.
  private val devicesStateDataStore = context.devicesStateDataStore

  // The Flow to read data from the DataStore.
  val devicesStateFlow: Flow<MatterFabricState> =
      devicesStateDataStore.data.catch { exception ->
        // dataStore.data throws an IOException when an error is encountered when reading data
        if (exception is IOException) {
          Timber.e(exception, "Error reading devicesState.")
          emit(MatterFabricState.getDefaultInstance())
        } else {
          throw exception
        }
      }

  /** The latest endpoint state update. */
  private val _lastUpdatedEndpointState = MutableLiveData<EndpointStateSnapshot?>(null)
  val lastUpdatedEndpointState: LiveData<EndpointStateSnapshot?>
    get() = _lastUpdatedEndpointState

  suspend fun addEndpointState(
      nodeId: Long,
      endpointId: Int,
      isOnline: Boolean,
      isOn: Boolean,
      level: Int,
      colorTemperature: Int,
  ) {
    upsertEndpointState(nodeId, endpointId, isOnline, isOn, level, colorTemperature)
  }

  suspend fun upsertEndpointState(
      nodeId: Long,
      endpointId: Int,
      isOnline: Boolean,
      isOn: Boolean,
      level: Int,
      colorTemperature: Int,
  ) {
    val normalizedEndpoint = normalizeEndpoint(endpointId)
    val capturedAt = getTimestampForNow()

    devicesStateDataStore.updateData { state ->
      val stateBuilder = state.toBuilder()
      val nodeIndex = findNodeIndex(state, nodeId)
      if (nodeIndex == -1) {
        val endpointState =
            MatterEndpoint.newBuilder()
                .setEndpointId(normalizedEndpoint)
                .setOn(isOn)
                .setLevel(level)
                .setColorTemperature(colorTemperature)
                .build()
        val nodeState =
            MatterNode.newBuilder()
                .setNodeId(nodeId)
                .setDateCommissioned(capturedAt)
                .setOnline(isOnline)
                .addEndpoints(endpointState)
                .build()
        stateBuilder.addNodes(nodeState)
      } else {
        val nodeStateBuilder = state.getNodes(nodeIndex).toBuilder()
        nodeStateBuilder.online = isOnline

        val endpointIndex = findEndpointIndex(state.getNodes(nodeIndex), normalizedEndpoint)
        if (endpointIndex == -1) {
          val endpointState =
              MatterEndpoint.newBuilder()
                  .setEndpointId(normalizedEndpoint)
                  .setOn(isOn)
                  .setLevel(level)
                  .setColorTemperature(colorTemperature)
                  .build()
          nodeStateBuilder.addEndpoints(endpointState)
        } else {
          val existing = state.getNodes(nodeIndex).getEndpoints(endpointIndex)
          val updated =
              existing
                  .toBuilder()
                  .setEndpointId(normalizedEndpoint)
                  .setOn(isOn)
                  .setLevel(level)
                  .setColorTemperature(colorTemperature)
                  .build()
          nodeStateBuilder.setEndpoints(endpointIndex, updated)
        }
        stateBuilder.setNodes(nodeIndex, nodeStateBuilder.build())
      }
      stateBuilder.build()
    }

    _lastUpdatedEndpointState.value =
        EndpointStateSnapshot(
            nodeId = nodeId,
            endpointId = normalizedEndpoint,
            dateCaptured = capturedAt,
            online = isOnline,
            on = isOn,
            level = level,
            colorTemperature = colorTemperature,
        )
  }

  suspend fun loadEndpointState(nodeId: Long, endpointId: Int): EndpointStateSnapshot? {
    val normalizedEndpoint = normalizeEndpoint(endpointId)
    val devicesState = devicesStateFlow.first()
    val nodeState = devicesState.nodesList.firstOrNull { it.nodeId == nodeId } ?: return null
    val endpointState =
        nodeState.endpointsList.firstOrNull {
          normalizeEndpoint(it.endpointId) == normalizedEndpoint
        } ?: return null
    return EndpointStateSnapshot(
        nodeId = nodeState.nodeId,
        endpointId = normalizedEndpoint,
        dateCaptured = getTimestampForNow(),
        online = nodeState.online,
        on = endpointState.on,
        level = endpointState.level,
        colorTemperature = endpointState.colorTemperature,
    )
  }

  suspend fun getAllDevicesState(): MatterFabricState {
    return devicesStateFlow.first()
  }

  suspend fun removeNodeState(nodeId: Long) {
    devicesStateDataStore.updateData { state ->
      val nodeIndex = findNodeIndex(state, nodeId)
      if (nodeIndex == -1) {
        return@updateData state
      }
      val stateBuilder = state.toBuilder().removeNodes(nodeIndex)
      stateBuilder.build()
    }
  }

  suspend fun updateNodeOnlineState(nodeId: Long, isOnline: Boolean) {
    val capturedAt = getTimestampForNow()
    devicesStateDataStore.updateData { state ->
      val nodeIndex = findNodeIndex(state, nodeId)
      if (nodeIndex == -1) {
        Timber.e(
            "updateNodeOnlineState: missing node state for nodeId=%d; refusing to create a new node",
            nodeId,
        )
        return@updateData state
      }
      val nodeBuilder = state.getNodes(nodeIndex).toBuilder()
      nodeBuilder.online = isOnline
      val stateBuilder = state.toBuilder().setNodes(nodeIndex, nodeBuilder.build())
      stateBuilder.build()
    }
  }

  private fun findNodeIndex(state: MatterFabricState, nodeId: Long): Int {
    return (0 until state.nodesCount).firstOrNull { state.getNodes(it).nodeId == nodeId } ?: -1
  }

  private fun findEndpointIndex(nodeState: MatterNode, endpoint: Int): Int {
    return (0 until nodeState.endpointsCount).firstOrNull {
      normalizeEndpoint(nodeState.getEndpoints(it).endpointId) == endpoint
    } ?: -1
  }

  private fun normalizeEndpoint(endpoint: Int): Int {
    return if (endpoint != 0) endpoint else 1
  }

  suspend fun clearAllData() {
    devicesStateDataStore.updateData { devicesState ->
      devicesState.toBuilder().clearNodes().build()
    }
  }
}
