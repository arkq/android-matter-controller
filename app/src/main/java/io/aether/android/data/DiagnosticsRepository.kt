// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.NodeState
import io.aether.android.chip.ChipClient
import io.aether.android.data.models.GeneralDiagnosticsData
import io.aether.android.data.models.SoftwareDiagnosticsData
import io.aether.android.matter.AttributeId
import io.aether.android.matter.Clusters
import io.aether.android.matter.NodeId
import io.aether.android.matter.ROOT_ENDPOINT_ID
import io.aether.android.matter.WILDCARD_ATTRIBUTE_ID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

class DiagnosticsRepository @Inject constructor(private val chipClient: ChipClient) {

  suspend fun readGeneralDiagnostics(nodeId: NodeId): GeneralDiagnosticsData? =
      runCatching {
            val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
            suspendCancellableCoroutine { continuation ->
              val readCallback =
                  object : ReportCallback {
                    override fun onError(
                        path: ChipAttributePath?,
                        event: ChipEventPath?,
                        ex: Exception,
                    ) {
                      if (continuation.isActive) continuation.resumeWithException(ex)
                    }

                    override fun onReport(nodeState: NodeState) {
                      if (continuation.isActive) {
                        continuation.resume(extractGeneralDiagnosticsData(nodeState))
                      }
                    }
                  }
              val readPaths =
                  listOf(
                      ChipAttributePath.newInstance(
                          ROOT_ENDPOINT_ID.toLong(),
                          Clusters.GeneralDiagnostics.ID.toLong(),
                          WILDCARD_ATTRIBUTE_ID.toLong(),
                      ),
                  )
              chipClient.chipDeviceController.readPath(
                  readCallback,
                  devicePtr,
                  readPaths,
                  null,
                  false,
              )
            }
          }
          .onFailure { e -> Timber.e(e, "Failed to read General Diagnostics for nodeId: $nodeId") }
          .getOrNull()

  private fun extractGeneralDiagnosticsData(nodeState: NodeState): GeneralDiagnosticsData {
    val clusterState =
        nodeState
            .getEndpointState(ROOT_ENDPOINT_ID.toInt())
            ?.getClusterState(Clusters.GeneralDiagnostics.ID.toLong())
            ?: return GeneralDiagnosticsData(networkInterfaces = emptyList(), rebootCount = 0)
    fun attr(id: AttributeId) = clusterState.getAttributeState(id.toLong())?.value
    return GeneralDiagnosticsData(
        networkInterfaces =
            attr(Clusters.GeneralDiagnostics.Attributes.NetworkInterfaces.ID).toNetworkInterfaces(),
        rebootCount = attr(Clusters.GeneralDiagnostics.Attributes.RebootCount.ID)?.toInt() ?: 0,
        upTime = attr(Clusters.GeneralDiagnostics.Attributes.UpTime.ID)?.toLong(),
        totalOperationalHours =
            attr(Clusters.GeneralDiagnostics.Attributes.TotalOperationalHours.ID)?.toInt(),
        // bootReason = attr(Clusters.GeneralDiagnostics.Attributes.BootReason.ID)?.toInt(),
        // activeHardwareFaults =
        //     attr(Clusters.GeneralDiagnostics.Attributes.ActiveHardwareFaults.ID)
        //         ?.toString()
        //         ?.split(",")
        //         ?.map { it.trim() },
        // activeRadioFaults =
        //     attr(Clusters.GeneralDiagnostics.Attributes.ActiveRadioFaults.ID)
        //         ?.toString()
        //         ?.split(",")
        //         ?.map { it.trim() },
        // activeNetworkFaults =
        //     attr(Clusters.GeneralDiagnostics.Attributes.ActiveNetworkFaults.ID)
        //         ?.toString()
        //         ?.split(",")
        //         ?.map { it.trim() },
        // testEventTriggersEnabled =
        //
        // attr(Clusters.GeneralDiagnostics.Attributes.TestEventTriggersEnabled.ID)?.toBoolean(),
        // deviceLoadStatus =
        //     attr(Clusters.GeneralDiagnostics.Attributes.DeviceLoadStatus.ID)?.toString(),
    )
  }

  suspend fun readSoftwareDiagnostics(nodeId: NodeId): SoftwareDiagnosticsData? =
      runCatching {
            val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
            suspendCancellableCoroutine { continuation ->
              val readCallback =
                  object : ReportCallback {
                    override fun onError(
                        path: ChipAttributePath?,
                        event: ChipEventPath?,
                        ex: Exception,
                    ) {
                      if (continuation.isActive) continuation.resumeWithException(ex)
                    }

                    override fun onReport(nodeState: NodeState) {
                      if (continuation.isActive) {
                        continuation.resume(extractSoftwareDiagnosticsData(nodeState))
                      }
                    }
                  }
              val readPaths =
                  listOf(
                      ChipAttributePath.newInstance(
                          ROOT_ENDPOINT_ID.toLong(),
                          Clusters.SoftwareDiagnostics.ID.toLong(),
                          WILDCARD_ATTRIBUTE_ID.toLong(),
                      ),
                  )
              chipClient.chipDeviceController.readPath(
                  readCallback,
                  devicePtr,
                  readPaths,
                  null,
                  false,
              )
            }
          }
          .onFailure { e -> Timber.e(e, "Failed to read Software Diagnostics for nodeId: $nodeId") }
          .getOrNull()

  private fun extractSoftwareDiagnosticsData(nodeState: NodeState): SoftwareDiagnosticsData {
    val clusterState =
        nodeState
            .getEndpointState(ROOT_ENDPOINT_ID.toInt())
            ?.getClusterState(Clusters.SoftwareDiagnostics.ID.toLong())
            ?: return SoftwareDiagnosticsData()
    fun attr(id: AttributeId) = clusterState.getAttributeState(id.toLong())?.value
    return SoftwareDiagnosticsData(
        threadMetrics = attr(Clusters.SoftwareDiagnostics.Attributes.ThreadMetrics.ID)?.toString(),
        currentHeapFree =
            attr(Clusters.SoftwareDiagnostics.Attributes.CurrentHeapFree.ID)?.toLong(),
        currentHeapUsed =
            attr(Clusters.SoftwareDiagnostics.Attributes.CurrentHeapUsed.ID)?.toLong(),
        currentHeapHighWatermark =
            attr(Clusters.SoftwareDiagnostics.Attributes.CurrentHeapHighWatermark.ID)?.toLong(),
    )
  }

  private fun Any?.toNetworkInterfaces():
      List<ChipStructs.GeneralDiagnosticsClusterNetworkInterface> =
      when (this) {
        is List<*> -> this.filterIsInstance<ChipStructs.GeneralDiagnosticsClusterNetworkInterface>()
        else -> emptyList()
      }

  private fun Any?.toBoolean(): Boolean? =
      when (this) {
        is Boolean -> this
        else -> null
      }

  private fun Any?.toInt(): Int? =
      when (this) {
        is Number -> this.toInt()
        else -> null
      }

  private fun Any?.toLong(): Long? =
      when (this) {
        is Number -> this.toLong()
        else -> null
      }
}
