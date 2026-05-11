// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipClusters.BasicInformationCluster
import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.NodeState
import io.aether.android.CommissioningWindowStatus
import io.aether.android.formatNodeId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Encapsulates the information of interest when querying a Matter device just after it has been
 * commissioned.
 */
data class DeviceMatterInfo(
    val endpoint: Int,
    val types: List<Long>,
    val serverClusters: List<Long>,
    val clientClusters: List<Long>,
    val parts: List<Int>,
)

/** Singleton to facilitate access to Clusters functionality. */
// Timed invoke timeout for commands like removeFabric that require a short grace period.
private const val TIMED_INVOKE_TIMEOUT_MS = 500
private const val ROOT_ENDPOINT = 0L
private const val OPERATIONAL_CREDENTIALS_CLUSTER_ID = 0x003EL
private const val FABRICS_ATTRIBUTE_ID = 0x0001L
private const val CURRENT_FABRIC_INDEX_ATTRIBUTE_ID = 0x0005L
private const val BASIC_INFORMATION_CLUSTER_ID = 0x0028L
private const val BASIC_INFORMATION_VENDOR_NAME_ATTRIBUTE_ID = 0x0001L
private const val BASIC_INFORMATION_VENDOR_ID_ATTRIBUTE_ID = 0x0002L
private const val BASIC_INFORMATION_HARDWARE_VERSION_STRING_ATTRIBUTE_ID = 0x0008L
private const val BASIC_INFORMATION_SOFTWARE_VERSION_STRING_ATTRIBUTE_ID = 0x000AL

data class BasicInformationAttributes(
    val vendorName: String? = null,
    val vendorId: Int? = null,
    val hardwareVersion: String? = null,
    val softwareVersion: String? = null,
)

@Singleton
class ClustersHelper @Inject constructor(private val chipClient: ChipClient) {

  // -----------------------------------------------------------------------------------------------
  // Convenience functions

  /** Fetches MatterDeviceInfo for each endpoint supported by the device. */
  suspend fun fetchDeviceMatterInfo(nodeId: Long): List<DeviceMatterInfo> {
    Timber.d("fetchDeviceMatterInfo(): nodeId [${nodeId}]")
    val matterDeviceInfoList = arrayListOf<DeviceMatterInfo>()
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return emptyList()
        }
    fetchDeviceMatterInfo(nodeId, connectedDevicePtr, 0, matterDeviceInfoList)
    return matterDeviceInfoList
  }

  /** Fetches MatterDeviceInfo for a specific endpoint. */
  suspend fun fetchDeviceMatterInfo(
      nodeId: Long,
      connectedDevicePtr: Long,
      endpointInt: Int,
      matterDeviceInfoList: ArrayList<DeviceMatterInfo>,
  ) {
    Timber.d("fetchDeviceMatterInfo(): nodeId [${nodeId}] endpoint [$endpointInt]")

    val partsListAttribute =
        readDescriptorClusterPartsListAttribute(connectedDevicePtr, endpointInt)
    Timber.d("partsListAttribute [${partsListAttribute}]")
    val parts = partsListAttribute.orEmpty()

    // DeviceListAttribute
    val deviceListAttribute =
        readDescriptorClusterDeviceListAttribute(connectedDevicePtr, endpointInt)
    val types = arrayListOf<Long>()
    deviceListAttribute.forEach { types.add(it.deviceType) }

    // ServerListAttribute
    val serverListAttribute =
        readDescriptorClusterServerListAttribute(connectedDevicePtr, endpointInt)
    val serverClusters = arrayListOf<Long>()
    serverListAttribute.forEach { serverClusters.add(it) }

    // ClientListAttribute
    val clientListAttribute =
        readDescriptorClusterClientListAttribute(connectedDevicePtr, endpointInt)
    val clientClusters = arrayListOf<Long>()
    clientListAttribute.forEach { clientClusters.add(it) }

    // Build the DeviceMatterInfo
    val deviceMatterInfo =
        DeviceMatterInfo(endpointInt, types, serverClusters, clientClusters, parts)
    matterDeviceInfoList.add(deviceMatterInfo)

    // Recursive call for the parts supported by the endpoint.
    // For each part (endpoint)
    parts.forEach { part ->
      Timber.d("Processing part [$part]")
      val endpointInt = part
      fetchDeviceMatterInfo(nodeId, connectedDevicePtr, endpointInt, matterDeviceInfoList)
    }
  }

  // -----------------------------------------------------------------------------------------------
  // DescriptorCluster functions

  /**
   * PartsListAttribute. These are the endpoints supported.
   *
   * ```
   * For example, on endpoint 0:
   *     sendReadPartsListAttribute part: [1]
   *     sendReadPartsListAttribute part: [2]
   * ```
   */
  suspend fun readDescriptorClusterPartsListAttribute(devicePtr: Long, endpoint: Int): List<Int>? {
    return suspendCoroutine { continuation ->
      getDescriptorClusterForDevice(devicePtr, endpoint)
          .readPartsListAttribute(
              object : ChipClusters.DescriptorCluster.PartsListAttributeCallback {
                override fun onSuccess(values: MutableList<Int>?) {
                  continuation.resume(values)
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  /**
   * DeviceListAttribute
   *
   * ```
   * For example, on endpoint 0:
   *   device: [long type: 22, int revision: 1] -> maps to Root node (0x0016) (utility device type)
   * on endpoint 1:
   *   device: [long type: 256, int revision: 1] -> maps to On/Off Light (0x0100)
   * ```
   */
  suspend fun readDescriptorClusterDeviceListAttribute(
      devicePtr: Long,
      endpoint: Int,
  ): List<ChipStructs.DescriptorClusterDeviceTypeStruct> {
    return suspendCoroutine { continuation ->
      getDescriptorClusterForDevice(devicePtr, endpoint)
          .readDeviceTypeListAttribute(
              object : ChipClusters.DescriptorCluster.DeviceTypeListAttributeCallback {
                override fun onSuccess(
                    values: List<ChipStructs.DescriptorClusterDeviceTypeStruct>
                ) {
                  continuation.resume(values)
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  /**
   * ServerListAttribute See
   * https://github.com/project-chip/connectedhomeip/blob/master/zzz_generated/app-common/app-common/zap-generated/ids/Clusters.h
   *
   * ```
   * For example: on endpoint 0
   *     sendReadServerListAttribute: [3]
   *     sendReadServerListAttribute: [4]
   *     sendReadServerListAttribute: [29]
   *     ... and more ...
   * on endpoint 1:
   *     sendReadServerListAttribute: [3]
   *     sendReadServerListAttribute: [4]
   *     sendReadServerListAttribute: [5]
   *     sendReadServerListAttribute: [6]
   *     sendReadServerListAttribute: [7]
   *     ... and more ...
   * on endpoint 2:
   *     sendReadServerListAttribute: [4]
   *     sendReadServerListAttribute: [6]
   *     sendReadServerListAttribute: [29]
   *     sendReadServerListAttribute: [1030]
   *
   * Some mappings:
   *     namespace Groups = 0x00000004 (4)
   *     namespace OnOff = 0x00000006 (6)
   *     namespace Descriptor = 0x0000001D (29)
   *     namespace OccupancySensing = 0x00000406 (1030)
   * ```
   */
  suspend fun readDescriptorClusterServerListAttribute(devicePtr: Long, endpoint: Int): List<Long> {
    return suspendCoroutine { continuation ->
      getDescriptorClusterForDevice(devicePtr, endpoint)
          .readServerListAttribute(
              object : ChipClusters.DescriptorCluster.ServerListAttributeCallback {
                override fun onSuccess(values: MutableList<Long>) {
                  continuation.resume(values)
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  /** ClientListAttribute */
  suspend fun readDescriptorClusterClientListAttribute(devicePtr: Long, endpoint: Int): List<Long> {
    return suspendCoroutine { continuation ->
      getDescriptorClusterForDevice(devicePtr, endpoint)
          .readClientListAttribute(
              object : ChipClusters.DescriptorCluster.ClientListAttributeCallback {
                override fun onSuccess(values: MutableList<Long>) {
                  continuation.resume(values)
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  private fun getDescriptorClusterForDevice(
      devicePtr: Long,
      endpoint: Int,
  ): ChipClusters.DescriptorCluster {
    return ChipClusters.DescriptorCluster(devicePtr, endpoint)
  }

  // -----------------------------------------------------------------------------------------------
  // ApplicationCluster functions

  suspend fun readApplicationBasicClusterAttributeList(deviceId: Long, endpoint: Int): List<Long> {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return emptyList()
        }
    return suspendCoroutine { continuation ->
      getApplicationBasicClusterForDevice(connectedDevicePtr, endpoint)
          .readAttributeListAttribute(
              object : ChipClusters.ApplicationBasicCluster.AttributeListAttributeCallback {
                override fun onSuccess(value: MutableList<Long>) {
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  private fun getApplicationBasicClusterForDevice(
      devicePtr: Long,
      endpoint: Int,
  ): ChipClusters.ApplicationBasicCluster {
    return ChipClusters.ApplicationBasicCluster(devicePtr, endpoint)
  }

  // -----------------------------------------------------------------------------------------------
  // BasicCluster functions

  suspend fun readBasicClusterVendorIDAttribute(deviceId: Long, endpoint: Int): Int? {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return null
        }
    return suspendCoroutine { continuation ->
      getBasicClusterForDevice(connectedDevicePtr, endpoint)
          .readVendorIDAttribute(
              object : ChipClusters.ApplicationBasicCluster.VendorIDAttributeCallback {
                override fun onSuccess(value: Int?) {
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  suspend fun readBasicClusterAttributeList(deviceId: Long, endpoint: Int): List<Long> {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return emptyList()
        }

    return suspendCoroutine { continuation ->
      getBasicClusterForDevice(connectedDevicePtr, endpoint)
          .readAttributeListAttribute(
              object : ChipClusters.ApplicationBasicCluster.AttributeListAttributeCallback {
                override fun onSuccess(values: MutableList<Long>) {
                  continuation.resume(values)
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  private fun getBasicClusterForDevice(
      devicePtr: Long,
      endpoint: Int,
  ): ChipClusters.ApplicationBasicCluster {
    return ChipClusters.ApplicationBasicCluster(devicePtr, endpoint)
  }

  /**
   * Writes NodeLabel attribute. See spec section "11.1.6.3. Attributes" of the "Basic Information
   * Cluster".
   *
   * @param deviceId device identifier
   * @param nodeLabel device name/node label
   */
  suspend fun writeBasicClusterNodeLabelAttribute(deviceId: Long, nodeLabel: String) {
    val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId)

    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
              continuation.resume(Unit)
            }

            override fun onError(ex: Exception) {
              continuation.resumeWithException(ex)
            }
          }

      BasicInformationCluster(connectedDevicePtr, 0).writeNodeLabelAttribute(callback, nodeLabel)
    }
  }

  /**
   * Reads the vendor name attribute. See spec section "11.1.6.3. Attributes" of the "Basic
   * Information Cluster".
   *
   * @param deviceId the device identifier.
   * @return the vendor name
   */
  suspend fun readBasicClusterVendorNameAttribute(deviceId: Long): String {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return ""
        }

    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.CharStringAttributeCallback {
            override fun onSuccess(value: String) {
              continuation.resume(value)
            }

            override fun onError(ex: Exception) {
              continuation.resumeWithException(ex)
            }
          }

      BasicInformationCluster(connectedDevicePtr, 0).readVendorNameAttribute(callback)
    }
  }

  /**
   * Reads node's product name attribute. See spec section "11.1.6.3. Attributes" of the "Basic
   * Information Cluster".
   *
   * @param deviceId the device identifier
   * @return the product name
   */
  suspend fun readBasicClusterProductNameAttribute(deviceId: Long): String {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return ""
        }

    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.CharStringAttributeCallback {
            override fun onSuccess(value: String) {
              continuation.resume(value)
            }

            override fun onError(ex: Exception) {
              continuation.resumeWithException(ex)
            }
          }

      BasicInformationCluster(connectedDevicePtr, 0).readProductNameAttribute(callback)
    }
  }

  /**
   * Reads NodeLabel attribute. See spec section "11.1.6.3. Attributes" of the "Basic Information
   * Cluster".
   *
   * @param deviceId device identifier
   * @return the NodeLabel
   */
  suspend fun readBasicClusterNodeLabelAttribute(deviceId: Long): String? {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return null
        }

    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.CharStringAttributeCallback {
            override fun onSuccess(value: String?) {
              continuation.resume(value)
            }

            override fun onError(ex: Exception) {
              continuation.resumeWithException(ex)
            }
          }

      BasicInformationCluster(connectedDevicePtr, 0).readNodeLabelAttribute(callback)
    }
  }

  // -----------------------------------------------------------------------------------------------
  // OnOffCluster functions

  suspend fun toggleDeviceStateOnOffCluster(deviceId: Long, endpoint: Int) {
    Timber.d("toggleDeviceStateOnOffCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return
        }
    return suspendCoroutine { continuation ->
      getOnOffClusterForDevice(connectedDevicePtr, endpoint)
          .toggle(
              object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                  continuation.resume(Unit)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readOnOffAttribute command failure")
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  suspend fun setOnOffDeviceStateOnOffCluster(deviceId: Long, isOn: Boolean, endpoint: Int) {
    Timber.d(
        "setOnOffDeviceStateOnOffCluster() [${deviceId}] isOn [${isOn}] endpoint [${endpoint}]"
    )
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return
        }
    if (isOn) {
      // ON
      return suspendCoroutine { continuation ->
        getOnOffClusterForDevice(connectedDevicePtr, endpoint)
            .on(
                object : ChipClusters.DefaultClusterCallback {
                  override fun onSuccess() {
                    Timber.d("Success for setOnOffDeviceStateOnOffCluster")
                    continuation.resume(Unit)
                  }

                  override fun onError(ex: Exception) {
                    Timber.e(ex, "Failure for setOnOffDeviceStateOnOffCluster")
                    continuation.resumeWithException(ex)
                  }
                }
            )
      }
    } else {
      // OFF
      return suspendCoroutine { continuation ->
        getOnOffClusterForDevice(connectedDevicePtr, endpoint)
            .off(
                object : ChipClusters.DefaultClusterCallback {
                  override fun onSuccess() {
                    Timber.d("Success for getOnOffDeviceStateOnOffCluster")
                    continuation.resume(Unit)
                  }

                  override fun onError(ex: Exception) {
                    Timber.e(ex, "Failure for getOnOffDeviceStateOnOffCluster")
                    continuation.resumeWithException(ex)
                  }
                }
            )
      }
    }
  }

  suspend fun getDeviceStateOnOffCluster(deviceId: Long, endpoint: Int): Boolean? {
    Timber.d("getDeviceStateOnOffCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return null
        }
    return suspendCoroutine { continuation ->
      getOnOffClusterForDevice(connectedDevicePtr, endpoint)
          .readOnOffAttribute(
              object : ChipClusters.BooleanAttributeCallback {
                override fun onSuccess(value: Boolean) {
                  Timber.d("readOnOffAttribute success: [$value]")
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readOnOffAttribute command failure")
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  private fun getOnOffClusterForDevice(devicePtr: Long, endpoint: Int): ChipClusters.OnOffCluster {
    return ChipClusters.OnOffCluster(devicePtr, endpoint)
  }

  // -----------------------------------------------------------------------------------------------
  // LevelControlCluster functions

  suspend fun setLevelStateLevelControlCluster(deviceId: Long, level: Int, endpoint: Int) {
    Timber.d(
        "setLevelStateLevelControlCluster() [${deviceId}] level [${level}] endpoint [${endpoint}]"
    )
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return
        }
    return suspendCoroutine { continuation ->
      getLevelControlClusterForDevice(connectedDevicePtr, endpoint)
          .moveToLevel(
              object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                  Timber.d("Success for setLevelStateLevelControlCluster")
                  continuation.resume(Unit)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "Failure for setLevelStateLevelControlCluster")
                  continuation.resumeWithException(ex)
                }
              },
              level,
              0,
              0,
              0,
          )
    }
  }

  suspend fun getDeviceStateLevelControlCluster(deviceId: Long, endpoint: Int): Int? {
    Timber.d("getDeviceStateLevelControlCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return null
        }
    return suspendCoroutine { continuation ->
      getLevelControlClusterForDevice(connectedDevicePtr, endpoint)
          .readCurrentLevelAttribute(
              object : ChipClusters.LevelControlCluster.CurrentLevelAttributeCallback {
                override fun onSuccess(value: Int?) {
                  Timber.d("readLevelControlAttribute success: [$value]")
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readLevelControlAttribute command failure")
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  private fun getLevelControlClusterForDevice(
      devicePtr: Long,
      endpoint: Int,
  ): ChipClusters.LevelControlCluster {
    return ChipClusters.LevelControlCluster(devicePtr, endpoint)
  }

  // -----------------------------------------------------------------------------------------------
  // ColorControlCluster functions

  suspend fun setColorTemperatureColorControlCluster(
      deviceId: Long,
      colorTemperature: Int,
      endpoint: Int,
  ) {
    Timber.d(
        "setColorTemperatureColorControlCluster() [${deviceId}] colorTemperature [${colorTemperature}] endpoint [${endpoint}]"
    )
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return
        }
    return suspendCoroutine { continuation ->
      getColorControlClusterForDevice(connectedDevicePtr, endpoint)
          .moveToColorTemperature(
              object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                  Timber.d("Success for setColorTemperatureColorControlCluster")
                  continuation.resume(Unit)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "Failure for setColorTemperatureColorControlCluster")
                  continuation.resumeWithException(ex)
                }
              },
              colorTemperature,
              0,
              0,
              0,
          )
    }
  }

  suspend fun getColorTemperatureColorControlCluster(deviceId: Long, endpoint: Int): Int? {
    Timber.d("getDeviceStateColorControlCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return null
        }
    return suspendCoroutine { continuation ->
      getColorControlClusterForDevice(connectedDevicePtr, endpoint)
          .readColorTemperatureMiredsAttribute(
              object : ChipClusters.IntegerAttributeCallback {
                override fun onSuccess(value: Int) {
                  Timber.d("readColorTemperatureMiredsAttribute success: [$value]")
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readColorTemperatureMiredsAttribute command failure")
                  continuation.resumeWithException(ex)
                }
              }
          )
    }
  }

  /**
   * Reads the AttributeList of the Color Control cluster for the given [nodeId] and [endpoint].
   * Returns the list of supported attribute IDs, or an empty list on error. Use this to check
   * whether the optional Color Temperature attribute (id 7) is present before flagging a device as
   * supporting color temperature control.
   */
  suspend fun readColorControlClusterAttributeList(nodeId: Long, endpoint: Int): List<Long> {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer for readColorControlClusterAttributeList.")
          return emptyList()
        }
    return suspendCoroutine { continuation ->
      getColorControlClusterForDevice(connectedDevicePtr, endpoint)
          .readAttributeListAttribute(
              object : ChipClusters.ColorControlCluster.AttributeListAttributeCallback {
                override fun onSuccess(value: MutableList<Long>) {
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readColorControlClusterAttributeList failure")
                  continuation.resume(emptyList())
                }
              }
          )
    }
  }

  private fun getColorControlClusterForDevice(
      devicePtr: Long,
      endpoint: Int,
  ): ChipClusters.ColorControlCluster {
    return ChipClusters.ColorControlCluster(devicePtr, endpoint)
  }

  // -----------------------------------------------------------------------------------------------
  // Administrator Commissioning Cluster (11.19)

  suspend fun openCommissioningWindowAdministratorCommissioningCluster(
      deviceId: Long,
      endpoint: Int,
      timeoutSeconds: Int,
      pakeVerifier: ByteArray,
      discriminator: Int,
      iterations: Long,
      salt: ByteArray,
      timedInvokeTimeoutMs: Int,
  ) {
    Timber.d("openCommissioningWindowAdministratorCommissioningCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer.")
          return
        }

    /*
    ChipClusters.DefaultClusterCallback var1, Integer var2, byte[] var3, Integer var4, Long var5, byte[] var6, int var7
     */
    return suspendCoroutine { continuation ->
      getAdministratorCommissioningClusterForDevice(connectedDevicePtr, endpoint)
          .openCommissioningWindow(
              object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                  continuation.resume(Unit)
                }

                override fun onError(ex: java.lang.Exception?) {
                  Timber.e(
                      ex,
                      "getAdministratorCommissioningClusterForDevice.openCommissioningWindow command failure",
                  )
                  continuation.resumeWithException(ex!!)
                }
              },
              timeoutSeconds,
              pakeVerifier,
              discriminator,
              iterations,
              salt,
              timedInvokeTimeoutMs,
          )
    }
  }

  /**
   * Closes a node's commissioning window. See spec section "11.18.8.3. RevokeCommissioning
   * Command".
   *
   * @param devicePtr connected device pointer.
   */
  suspend fun closeCommissioningWindow(devicePtr: Long) {
    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
              Timber.d("Window is closed successfully")
              continuation.resume(Unit)
            }

            override fun onError(ex: Exception) {
              Timber.e(ex, "Failed to close window")
            }
          }
      ChipClusters.AdministratorCommissioningCluster(devicePtr, 0)
          .revokeCommissioning(callback, 100)
    }
  }

  /**
   * Checks if a device has an open commissioning window. See spec section "11.18.7. Attributes" of
   * the "Administrator Commissioning Cluster".
   *
   * @param devicePtr connected device pointer.
   * @return true if a window is open, false otherwise.
   */
  suspend fun isCommissioningWindowOpen(devicePtr: Long): Boolean {
    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.IntegerAttributeCallback {
            override fun onSuccess(value: Int) {
              when (value) {
                CommissioningWindowStatus.WindowNotOpen.status -> {
                  continuation.resume(false)
                }
                CommissioningWindowStatus.EnhancedWindowOpen.status,
                CommissioningWindowStatus.BasicWindowOpen.status -> {
                  continuation.resume(true)
                }
              }
            }

            override fun onError(ex: Exception) {
              Timber.e("Failed to check window status. Cause: ${ex.localizedMessage}")
              continuation.resumeWithException(ex)
            }
          }

      ChipClusters.AdministratorCommissioningCluster(devicePtr, 0)
          .readWindowStatusAttribute(callback)
    }
  }

  /**
   * Reads vendor/manufacturer fields and version strings from Basic Information in a single read
   * request limited to the required attributes.
   */
  suspend fun readBasicInformationAttributes(nodeId: Long): BasicInformationAttributes? {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer for nodeId: ${formatNodeId(nodeId)}")
          return null
        }

    return try {
      suspendCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        val basicInfoPaths =
            listOf(
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT,
                    BASIC_INFORMATION_CLUSTER_ID,
                    BASIC_INFORMATION_VENDOR_NAME_ATTRIBUTE_ID,
                ),
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT,
                    BASIC_INFORMATION_CLUSTER_ID,
                    BASIC_INFORMATION_VENDOR_ID_ATTRIBUTE_ID,
                ),
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT,
                    BASIC_INFORMATION_CLUSTER_ID,
                    BASIC_INFORMATION_HARDWARE_VERSION_STRING_ATTRIBUTE_ID,
                ),
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT,
                    BASIC_INFORMATION_CLUSTER_ID,
                    BASIC_INFORMATION_SOFTWARE_VERSION_STRING_ATTRIBUTE_ID,
                ),
            )

        chipClient.chipDeviceController.readPath(
            object : ReportCallback {
              override fun onError(
                  attributePath: ChipAttributePath?,
                  eventPath: ChipEventPath?,
                  ex: Exception,
              ) {
                if (completed.compareAndSet(false, true)) {
                  continuation.resumeWithException(ex)
                }
              }

              override fun onReport(nodeState: NodeState) {
                if (completed.compareAndSet(false, true)) {
                  continuation.resume(extractBasicInformationAttributes(nodeState))
                }
              }
            },
            connectedDevicePtr,
            basicInfoPaths,
            emptyList(),
            false,
        )
      }
    } catch (e: Exception) {
      Timber.e(e, "readBasicInformationAttributes failed")
      null
    }
  }

  private fun extractBasicInformationAttributes(nodeState: NodeState): BasicInformationAttributes {
    val clusterState =
        nodeState
            .getEndpointState(ROOT_ENDPOINT.toInt())
            ?.getClusterState(BASIC_INFORMATION_CLUSTER_ID) ?: return BasicInformationAttributes()

    val vendorName =
        clusterState.getAttributeState(BASIC_INFORMATION_VENDOR_NAME_ATTRIBUTE_ID)?.value.asString()
    val vendorId =
        clusterState.getAttributeState(BASIC_INFORMATION_VENDOR_ID_ATTRIBUTE_ID)?.value.asInt()
    val hardwareVersion =
        clusterState
            .getAttributeState(BASIC_INFORMATION_HARDWARE_VERSION_STRING_ATTRIBUTE_ID)
            ?.value
            .asString()
    val softwareVersion =
        clusterState
            .getAttributeState(BASIC_INFORMATION_SOFTWARE_VERSION_STRING_ATTRIBUTE_ID)
            ?.value
            .asString()

    return BasicInformationAttributes(
        vendorName = vendorName,
        vendorId = vendorId,
        hardwareVersion = hardwareVersion,
        softwareVersion = softwareVersion,
    )
  }

  /**
   * Reads the list of fabrics (controllers) from the Operational Credentials Cluster.
   *
   * @param nodeId the Matter node ID
   * @return list of fabric descriptor structs, or null on error
   */
  suspend fun readFabricsAttribute(
      nodeId: Long
  ): List<ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct>? {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer for nodeId: ${formatNodeId(nodeId)}")
          return null
        }
    return try {
      suspendCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        chipClient.chipDeviceController.readPath(
            object : ReportCallback {
              override fun onError(
                  attributePath: ChipAttributePath?,
                  eventPath: ChipEventPath?,
                  ex: Exception,
              ) {
                if (completed.compareAndSet(false, true)) {
                  continuation.resumeWithException(ex)
                }
              }

              override fun onReport(nodeState: NodeState) {
                if (completed.compareAndSet(false, true)) {
                  continuation.resume(extractFabricsFromNodeState(nodeState))
                }
              }
            },
            connectedDevicePtr,
            listOf(
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT,
                    OPERATIONAL_CREDENTIALS_CLUSTER_ID,
                    FABRICS_ATTRIBUTE_ID,
                )
            ),
            emptyList(),
            false,
        )
      }
    } catch (e: Exception) {
      Timber.e(e, "readFabricsAttribute failed")
      null
    }
  }

  /**
   * Reads the current accessing fabric index from the Operational Credentials cluster.
   *
   * @return fabric index, or null when unavailable
   */
  suspend fun readCurrentFabricIndexAttribute(nodeId: Long): Int? {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e("readCurrentFabricIndexAttribute: can't get connectedDevicePointer")
          return null
        }

    return try {
      suspendCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        chipClient.chipDeviceController.readPath(
            object : ReportCallback {
              override fun onError(
                  attributePath: ChipAttributePath?,
                  eventPath: ChipEventPath?,
                  ex: Exception,
              ) {
                if (completed.compareAndSet(false, true)) {
                  continuation.resumeWithException(ex)
                }
              }

              override fun onReport(nodeState: NodeState) {
                if (completed.compareAndSet(false, true)) {
                  val currentFabricIndex =
                      nodeState
                          .getEndpointState(ROOT_ENDPOINT.toInt())
                          ?.getClusterState(OPERATIONAL_CREDENTIALS_CLUSTER_ID)
                          ?.getAttributeState(CURRENT_FABRIC_INDEX_ATTRIBUTE_ID)
                          ?.value
                          .asInt()
                  continuation.resume(currentFabricIndex)
                }
              }
            },
            connectedDevicePtr,
            listOf(
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT,
                    OPERATIONAL_CREDENTIALS_CLUSTER_ID,
                    CURRENT_FABRIC_INDEX_ATTRIBUTE_ID,
                )
            ),
            emptyList(),
            false,
        )
      }
    } catch (e: Exception) {
      Timber.e(e, "readCurrentFabricIndexAttribute failed: nodeId [$nodeId]")
      null
    }
  }

  private fun extractFabricsFromNodeState(
      nodeState: NodeState
  ): List<ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct> {
    val attributeState =
        nodeState
            .getEndpointState(ROOT_ENDPOINT.toInt())
            ?.getClusterState(OPERATIONAL_CREDENTIALS_CLUSTER_ID)
            ?.getAttributeState(FABRICS_ATTRIBUTE_ID) ?: return emptyList()

    val value = attributeState.value
    if (value is List<*>) {
      val directValues =
          value.filterIsInstance<ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct>()
      if (directValues.isNotEmpty()) {
        return directValues
      }

      val mappedValues = value.mapNotNull { decodeFabricDescriptor(it) }
      if (mappedValues.isNotEmpty()) {
        return mappedValues
      }
    }

    val json = attributeState.json
    return when {
      json.has("value") -> decodeFabricDescriptorsFromJsonValue(json.get("value"))
      json.has("Value") -> decodeFabricDescriptorsFromJsonValue(json.get("Value"))
      else -> emptyList()
    }
  }

  private fun decodeFabricDescriptorsFromJsonValue(
      value: Any?
  ): List<ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct> =
      when (value) {
        is JSONArray ->
            List(value.length()) { index -> decodeFabricDescriptor(value.opt(index)) }
                .filterNotNull()
        is List<*> -> value.mapNotNull { decodeFabricDescriptor(it) }
        else -> emptyList()
      }

  private fun decodeFabricDescriptor(
      value: Any?
  ): ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct? =
      when (value) {
        is ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct -> value
        is JSONObject -> decodeFabricDescriptorFromJson(value)
        is Map<*, *> -> decodeFabricDescriptorFromMap(value)
        else -> null
      }

  private fun decodeFabricDescriptorFromJson(
      json: JSONObject
  ): ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct? {
    val vendorId = json.optIntOrNull("vendorID") ?: json.optIntOrNull("vendorId") ?: return null
    val fabricId = json.optLongOrNull("fabricID") ?: json.optLongOrNull("fabricId") ?: return null
    val nodeId = json.optLongOrNull("nodeID") ?: json.optLongOrNull("nodeId") ?: return null
    val fabricIndex =
        json.optIntOrNull("fabricIndex") ?: json.optIntOrNull("FabricIndex") ?: return null
    val label = json.optString("label", json.optString("Label", ""))
    return ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct(
        byteArrayOf(),
        vendorId,
        fabricId,
        nodeId,
        label,
        fabricIndex,
    )
  }

  private fun decodeFabricDescriptorFromMap(
      value: Map<*, *>
  ): ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct? {
    val vendorId = value.intValue("vendorID") ?: value.intValue("vendorId") ?: return null
    val fabricId = value.longValue("fabricID") ?: value.longValue("fabricId") ?: return null
    val nodeId = value.longValue("nodeID") ?: value.longValue("nodeId") ?: return null
    val fabricIndex = value.intValue("fabricIndex") ?: return null
    val label = value["label"]?.toString() ?: ""
    return ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct(
        byteArrayOf(),
        vendorId,
        fabricId,
        nodeId,
        label,
        fabricIndex,
    )
  }

  private fun JSONObject.optIntOrNull(key: String): Int? =
      if (!has(key) || isNull(key)) null else opt(key).asInt()

  private fun JSONObject.optLongOrNull(key: String): Long? =
      if (!has(key) || isNull(key)) null else opt(key).asLong()

  private fun Map<*, *>.intValue(key: String): Int? = this[key].asInt()

  private fun Map<*, *>.longValue(key: String): Long? = this[key].asLong()

  private fun Any?.asInt(): Int? =
      when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: removePrefix("0x").toIntOrNull(16)
        else -> null
      }

  private fun Any?.asLong(): Long? =
      when (this) {
        is Number -> toLong()
        is String -> toLongOrNull() ?: removePrefix("0x").toLongOrNull(16)
        else -> null
      }

  private fun Any?.asString(): String? =
      when (this) {
        is String -> this
        else -> null
      }

  /**
   * Reads the list of NOCs from the Operational Credentials Cluster.
   *
   * @param nodeId the Matter node ID
   * @return list of NOC structs, or null on error
   */
  suspend fun readNOCsAttribute(
      nodeId: Long
  ): List<ChipStructs.OperationalCredentialsClusterNOCStruct>? {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer for nodeId: ${formatNodeId(nodeId)}")
          return null
        }
    return try {
      suspendCoroutine { continuation ->
        ChipClusters.OperationalCredentialsCluster(connectedDevicePtr, 0)
            .readNOCsAttribute(
                object : ChipClusters.OperationalCredentialsCluster.NOCsAttributeCallback {
                  override fun onSuccess(
                      values: List<ChipStructs.OperationalCredentialsClusterNOCStruct>
                  ) {
                    continuation.resume(values)
                  }

                  override fun onError(ex: Exception) {
                    continuation.resumeWithException(ex)
                  }
                }
            )
      }
    } catch (e: Exception) {
      Timber.e(e, "readNOCsAttribute failed")
      null
    }
  }

  /**
   * Removes a fabric (controller) from the device.
   *
   * @param nodeId the Matter node ID
   * @param fabricIndex the index of the fabric to remove
   */
  suspend fun removeFabric(nodeId: Long, fabricIndex: Int) {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer for nodeId: ${formatNodeId(nodeId)}")
          throw IllegalStateException("Failed to get connected device pointer")
        }
    return suspendCoroutine { continuation ->
      ChipClusters.OperationalCredentialsCluster(connectedDevicePtr, 0)
          .removeFabric(
              object : ChipClusters.OperationalCredentialsCluster.NOCResponseCallback {
                override fun onSuccess(
                    statusCode: Int,
                    fabricIndex: java.util.Optional<Int>,
                    debugText: java.util.Optional<String>,
                ) {
                  if (statusCode == 0) {
                    Timber.d("removeFabric succeeded: statusCode=$statusCode")
                    continuation.resume(Unit)
                  } else {
                    val debugMessage = debugText.orElse("")
                    val error =
                        IllegalStateException(
                            "removeFabric returned non-success statusCode=$statusCode debugText=$debugMessage"
                        )
                    Timber.e(error.message)
                    continuation.resumeWithException(error)
                  }
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "removeFabric failed")
                  continuation.resumeWithException(ex)
                }
              },
              fabricIndex,
              TIMED_INVOKE_TIMEOUT_MS,
          )
    }
  }

  private fun getAdministratorCommissioningClusterForDevice(
      devicePtr: Long,
      endpoint: Int,
  ): ChipClusters.AdministratorCommissioningCluster {
    return ChipClusters.AdministratorCommissioningCluster(devicePtr, endpoint)
  }
}
