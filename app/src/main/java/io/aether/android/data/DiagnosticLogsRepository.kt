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

  // CHIP TLV control byte constants (bits[7:5]=tag type, bits[4:0]=element type)
  private companion object {
    const val TLV_ANONYMOUS_STRUCTURE = 0x15 // anonymous tag, structure element
    const val TLV_END_CONTAINER = 0x18 // end of container
    const val TLV_CTX_UINT8 = 0x24 // context tag, unsigned int 1 byte
    const val TLV_INTENT_TAG = 0x00 // RetrieveLogsRequest field 0: Intent
    const val TLV_PROTOCOL_TAG = 0x01 // RetrieveLogsRequest field 1: RequestedProtocol
    const val INTENT_END_USER_SUPPORT = 0x00
    const val PROTOCOL_RESPONSE_PAYLOAD = 0x00
    const val TAG_ANONYMOUS = -1
    const val TAG_TYPE_ANONYMOUS = 0
    const val TAG_TYPE_CONTEXT = 1
    const val ELEM_SINT8 = 0x00
    const val ELEM_SINT16 = 0x01
    const val ELEM_SINT32 = 0x02
    const val ELEM_SINT64 = 0x03
    const val ELEM_UINT8 = 0x04
    const val ELEM_UINT16 = 0x05
    const val ELEM_UINT32 = 0x06
    const val ELEM_UINT64 = 0x07
    const val ELEM_BOOL_FALSE = 0x08
    const val ELEM_BOOL_TRUE = 0x09
    const val ELEM_FLOAT = 0x0A
    const val ELEM_DOUBLE = 0x0B
    const val ELEM_UTF8_1 = 0x0C // UTF-8 string with 1-byte length prefix
    const val ELEM_UTF8_2 = 0x0D // UTF-8 string with 2-byte length prefix
    const val ELEM_BYTES_1 = 0x10 // byte string with 1-byte length prefix
    const val ELEM_BYTES_2 = 0x11 // byte string with 2-byte length prefix
    const val ELEM_BYTES_4 = 0x12 // byte string with 4-byte length prefix
    const val STATUS_TAG = 0 // RetrieveLogsResponse field 0: Status
    const val LOG_CONTENT_TAG = 1 // RetrieveLogsResponse field 1: LogContent
    const val STATUS_NO_LOGS = 2
    const val STATUS_BUSY = 3
    const val STATUS_DENIED = 4
  }

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
          TLV_ANONYMOUS_STRUCTURE.toByte(),
          TLV_CTX_UINT8.toByte(),
          TLV_INTENT_TAG.toByte(),
          INTENT_END_USER_SUPPORT.toByte(),
          TLV_CTX_UINT8.toByte(),
          TLV_PROTOCOL_TAG.toByte(),
          PROTOCOL_RESPONSE_PAYLOAD.toByte(),
          TLV_END_CONTAINER.toByte(),
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

    fun readInt16() = readByte() or (readByte() shl 8)

    fun readInt32() = readByte() or (readByte() shl 8) or (readByte() shl 16) or (readByte() shl 24)

    // Skip structure start
    if (end() || readByte() != TLV_ANONYMOUS_STRUCTURE) return null

    var status = -1
    var logContent: ByteArray? = null

    while (!end()) {
      val control = readByte()
      val elementType = control and 0x1F
      val tagControl = (control ushr 5) and 0x07

      if (elementType == TLV_END_CONTAINER) break

      val tag =
          when (tagControl) {
            TAG_TYPE_ANONYMOUS -> TAG_ANONYMOUS
            TAG_TYPE_CONTEXT -> readByte() // context tag: next byte is the tag number
            else -> break // unsupported tag type
          }

      when (elementType) {
        ELEM_SINT8 -> {
          val v = readByte()
          if (tag == STATUS_TAG) status = v.toByte().toInt()
        }
        ELEM_SINT16 -> i += 2
        ELEM_SINT32 -> i += 4
        ELEM_SINT64 -> i += 8
        ELEM_UINT8 -> {
          val v = readByte()
          if (tag == STATUS_TAG) status = v
        }
        ELEM_UINT16 -> i += 2
        ELEM_UINT32 -> i += 4
        ELEM_UINT64 -> i += 8
        ELEM_BOOL_FALSE,
        ELEM_BOOL_TRUE -> {} // no value bytes
        ELEM_FLOAT -> i += 4
        ELEM_DOUBLE -> i += 8
        ELEM_UTF8_1 -> i += readByte()
        ELEM_UTF8_2 -> i += readInt16()
        ELEM_BYTES_1 -> {
          val len = readByte()
          if (tag == LOG_CONTENT_TAG && len > 0 && i + len <= tlv.size) {
            logContent = tlv.copyOfRange(i, i + len)
          }
          i += len
        }
        ELEM_BYTES_2 -> {
          val len = readInt16()
          if (tag == LOG_CONTENT_TAG && len > 0 && i + len <= tlv.size) {
            logContent = tlv.copyOfRange(i, i + len)
          }
          i += len
        }
        ELEM_BYTES_4 -> {
          val len = readInt32()
          if (tag == LOG_CONTENT_TAG && len > 0 && i + len <= tlv.size) {
            logContent = tlv.copyOfRange(i, i + len)
          }
          i += len
        }
        else -> break // unknown element, bail
      }
    }

    // Status NoLogs/Busy/Denied – no content to show
    if (status in STATUS_NO_LOGS..STATUS_DENIED) return null

    val bytes = logContent ?: return null
    return bytes.toString(Charsets.UTF_8)
  }
}
