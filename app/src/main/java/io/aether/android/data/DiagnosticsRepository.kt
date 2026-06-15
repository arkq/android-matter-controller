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
import io.aether.android.data.models.NetworkInterface
import io.aether.android.data.models.SoftwareDiagnosticsData
import io.aether.android.data.models.ThreadMetrics
import io.aether.android.matter.AttributeId
import io.aether.android.matter.Clusters
import io.aether.android.matter.Enums
import io.aether.android.matter.NodeId
import io.aether.android.matter.ROOT_ENDPOINT_ID
import io.aether.android.matter.WILDCARD_ATTRIBUTE_ID
import java.net.Inet6Address
import java.net.InetAddress
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
        rebootCount = attr(Clusters.GeneralDiagnostics.Attributes.RebootCount.ID).toInt() ?: 0,
        upTime = attr(Clusters.GeneralDiagnostics.Attributes.UpTime.ID).toLong(),
        totalOperationalHours =
            attr(Clusters.GeneralDiagnostics.Attributes.TotalOperationalHours.ID).toInt(),
        bootReason = attr(Clusters.GeneralDiagnostics.Attributes.BootReason.ID).toBootReason(),
        activeHardwareFaults =
            attr(Clusters.GeneralDiagnostics.Attributes.ActiveHardwareFaults.ID).toHardwareFaults(),
        activeRadioFaults =
            attr(Clusters.GeneralDiagnostics.Attributes.ActiveRadioFaults.ID).toRadioFaults(),
        activeNetworkFaults =
            attr(Clusters.GeneralDiagnostics.Attributes.ActiveNetworkFaults.ID).toNetworkFaults(),
        testEventTriggersEnabled =
            attr(Clusters.GeneralDiagnostics.Attributes.TestEventTriggersEnabled.ID).toBoolean(),
        deviceLoadStatus =
            attr(Clusters.GeneralDiagnostics.Attributes.DeviceLoadStatus.ID).toString(),
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
            ?: return SoftwareDiagnosticsData(threadMetrics = emptyList())
    fun attr(id: AttributeId) = clusterState.getAttributeState(id.toLong())?.value
    return SoftwareDiagnosticsData(
        threadMetrics =
            attr(Clusters.SoftwareDiagnostics.Attributes.ThreadMetrics.ID).toThreadMetrics(),
        currentHeapFree = attr(Clusters.SoftwareDiagnostics.Attributes.CurrentHeapFree.ID).toLong(),
        currentHeapUsed = attr(Clusters.SoftwareDiagnostics.Attributes.CurrentHeapUsed.ID).toLong(),
        currentHeapHighWatermark =
            attr(Clusters.SoftwareDiagnostics.Attributes.CurrentHeapHighWatermark.ID).toLong(),
    )
  }

  private val interfaceTypeMap =
      Enums.DiagnosticsGeneralClusterInterfaceType.entries.associateBy(
          Enums.DiagnosticsGeneralClusterInterfaceType::value
      )

  private fun Any?.toNetworkInterfaces(): List<NetworkInterface> =
      when (this) {
        is List<*> ->
            this.filterIsInstance<ChipStructs.GeneralDiagnosticsClusterNetworkInterface>().map {
              NetworkInterface(
                  name = it.name,
                  isOperational = it.isOperational,
                  offPremiseServicesReachableIPv4 = it.offPremiseServicesReachableIPv4,
                  offPremiseServicesReachableIPv6 = it.offPremiseServicesReachableIPv6,
                  hardwareAddress = it.hardwareAddress,
                  ipv4Addresses = it.IPv4Addresses.map { addr -> InetAddress.getByAddress(addr) },
                  ipv6Addresses =
                      it.IPv6Addresses.map { addr ->
                        Inet6Address.getByAddress(addr) as Inet6Address
                      },
                  type =
                      interfaceTypeMap[it.type.toInt().toUInt()]
                          ?: Enums.DiagnosticsGeneralClusterInterfaceType.Unspecified,
              )
            }
        else -> emptyList()
      }

  private val bootReasonMap =
      Enums.DiagnosticsGeneralClusterBootReason.entries.associateBy(
          Enums.DiagnosticsGeneralClusterBootReason::value
      )

  private fun Any?.toBootReason(): Enums.DiagnosticsGeneralClusterBootReason? =
      when (this) {
        is Number -> bootReasonMap[this.toInt().toUInt()]
        else -> null
      }

  private val hardwareFaultMap =
      Enums.DiagnosticsGeneralClusterHardwareFault.entries.associateBy(
          Enums.DiagnosticsGeneralClusterHardwareFault::value
      )

  private fun Any?.toHardwareFaults(): List<Enums.DiagnosticsGeneralClusterHardwareFault> =
      when (this) {
        is List<*> ->
            this.filterIsInstance<Number>().mapNotNull { hardwareFaultMap[it.toInt().toUInt()] }
        else -> emptyList()
      }

  private val radioFaultMap =
      Enums.DiagnosticsGeneralClusterRadioFault.entries.associateBy(
          Enums.DiagnosticsGeneralClusterRadioFault::value
      )

  private fun Any?.toRadioFaults(): List<Enums.DiagnosticsGeneralClusterRadioFault> =
      when (this) {
        is List<*> ->
            this.filterIsInstance<Number>().mapNotNull { radioFaultMap[it.toInt().toUInt()] }
        else -> emptyList()
      }

  private val networkFaultMap =
      Enums.DiagnosticsGeneralClusterNetworkFault.entries.associateBy(
          Enums.DiagnosticsGeneralClusterNetworkFault::value
      )

  private fun Any?.toNetworkFaults(): List<Enums.DiagnosticsGeneralClusterNetworkFault> =
      when (this) {
        is List<*> ->
            this.filterIsInstance<Number>().mapNotNull { networkFaultMap[it.toInt().toUInt()] }
        else -> emptyList()
      }

  private fun Any?.toThreadMetrics(): List<ThreadMetrics> =
      when (this) {
        is List<*> ->
            this.filterIsInstance<ChipStructs.SoftwareDiagnosticsClusterThreadMetricsStruct>().map {
              ThreadMetrics(
                  id = it.id,
                  name = it.name.orElse(null),
                  stackFreeCurrent = it.stackFreeCurrent?.toInt(),
                  stackFreeMinimum = it.stackFreeMinimum?.toInt(),
                  stackSize = it.stackSize?.toInt(),
              )
            }
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
