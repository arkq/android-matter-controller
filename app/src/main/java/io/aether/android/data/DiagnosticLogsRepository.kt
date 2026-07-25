// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import chip.devicecontroller.model.InvokeElement
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.matter.Clusters
import io.aether.android.matter.NodeId
import io.aether.android.matter.ROOT_ENDPOINT_ID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class DiagnosticLogsRepository
@Inject
constructor(
    private val chipClient: ChipClient,
    private val clustersHelper: ClustersHelper,
) {

  private val retrieveLogsTimeoutDuration = 30.seconds

  suspend fun isDiagnosticLogsClusterSupported(nodeId: NodeId): Boolean =
      runCatching {
            val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
            val serverClusters =
                clustersHelper.readDescriptorClusterServerListAttribute(devicePtr, ROOT_ENDPOINT_ID)
            serverClusters.any { it == Clusters.DiagnosticLogs.ID }
          }
          .onFailure { e ->
            Timber.e(e, "Failed to check DiagnosticLogs cluster support for nodeId=$nodeId")
          }
          .getOrDefault(false)

  /**
   * Retrieves device logs using the DiagnosticLogs cluster RetrieveLogsRequest command with
   * ResponsePayload protocol. Returns the log text, or null on failure.
   */
  suspend fun retrieveLogs(nodeId: NodeId): String? =
      runCatching {
            withTimeout(retrieveLogsTimeoutDuration) {
              val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
              val response =
                  chipClient.invokeWithResponse(
                      devicePtr,
                      InvokeElement.newInstance(
                          ROOT_ENDPOINT_ID.toLong(),
                          Clusters.DiagnosticLogs.ID.toLong(),
                          Clusters.DiagnosticLogs.CommandsIncoming.RetrieveLogsRequest.ID.toLong(),
                          encodeRetrieveLogsRequest(),
                          "",
                      ),
                      imTimeoutMs = 30_000,
                  )
              val tlv = response?.getTlvByteArray() ?: return@withTimeout null
              decodeRetrieveLogsResponse(tlv)
            }
          }
          .onFailure { e -> Timber.e(e, "Failed to retrieve diagnostic logs for nodeId=$nodeId") }
          .getOrNull()

  /**
   * Encodes a RetrieveLogsRequest TLV payload:
   * - tag 0 (Intent): enum8 = 0 (EndUserSupport)
   * - tag 1 (RequestedProtocol): enum8 = 0 (ResponsePayload)
   */
  private fun encodeRetrieveLogsRequest(): ByteArray =
      byteArrayOf(
          0x15, // Structure start
          0x24.toByte(),
          0x00,
          0x00, // Context tag 0, uint8: Intent=EndUserSupport
          0x24.toByte(),
          0x01,
          0x00, // Context tag 1, uint8: Protocol=ResponsePayload
          0x18, // End container
      )

  /**
   * Decodes a RetrieveLogsResponse TLV payload. Returns the log content as a String (UTF-8), or
   * null if the status indicates no logs or the response is malformed.
   *
   * Response structure:
   * - tag 0 (Status): enum8
   * - tag 1 (LogContent): byte string
   */
  private fun decodeRetrieveLogsResponse(tlv: ByteArray): String? {
    var i = 0

    fun end() = i >= tlv.size

    fun readByte(): Int {
      if (end()) return -1
      return tlv[i++].toInt() and 0xFF
    }

    // Skip structure start (0x15)
    if (end() || readByte() != 0x15) return null

    var status = -1
    var logContent: ByteArray? = null

    while (!end()) {
      val control = readByte()
      val elementType = control and 0x1F
      val tagControl = (control ushr 5) and 0x07

      if (elementType == 0x18) break // end container

      val tag =
          when (tagControl) {
            0 -> -1 // anonymous
            1 -> readByte() // context tag: next byte is tag value
            else -> break // unsupported tag type
          }

      when (elementType) {
        0x00 -> { // signed int 1 byte
          val v = readByte()
          if (tag == 0) status = v.toByte().toInt()
        }
        0x01 -> i += 2 // signed int 2 bytes, skip
        0x02 -> i += 4 // signed int 4 bytes, skip
        0x03 -> i += 8 // signed int 8 bytes, skip
        0x04 -> { // unsigned int 1 byte
          val v = readByte()
          if (tag == 0) status = v
        }
        0x05 -> i += 2 // unsigned int 2 bytes, skip
        0x06 -> i += 4 // unsigned int 4 bytes, skip
        0x07 -> i += 8 // unsigned int 8 bytes, skip
        0x08,
        0x09 -> {} // bool false/true, no value bytes
        0x0A -> i += 4 // float, skip
        0x0B -> i += 8 // double, skip
        0x0C -> { // UTF-8 string, 1-byte length
          val len = readByte()
          i += len
        }
        0x0D -> { // UTF-8 string, 2-byte length
          val len = readByte() or (readByte() shl 8)
          i += len
        }
        0x10 -> { // byte string, 1-byte length
          val len = readByte()
          if (tag == 1 && len > 0 && i + len <= tlv.size) {
            logContent = tlv.copyOfRange(i, i + len)
          }
          i += len
        }
        0x11 -> { // byte string, 2-byte length
          val len = readByte() or (readByte() shl 8)
          if (tag == 1 && len > 0 && i + len <= tlv.size) {
            logContent = tlv.copyOfRange(i, i + len)
          }
          i += len
        }
        0x12 -> { // byte string, 4-byte length
          val len = readByte() or (readByte() shl 8) or (readByte() shl 16) or (readByte() shl 24)
          if (tag == 1 && len > 0 && i + len <= tlv.size) {
            logContent = tlv.copyOfRange(i, i + len)
          }
          i += len
        }
        else -> break // unknown element, bail
      }
    }

    // Status 2 = NoLogs, Status 3 = Busy, Status 4 = Denied – no content to show
    if (status in 2..4) return null

    val bytes = logContent ?: return null
    return bytes.toString(Charsets.UTF_8)
  }
}
