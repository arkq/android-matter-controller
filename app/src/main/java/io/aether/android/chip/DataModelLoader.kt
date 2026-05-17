// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.R
import io.aether.android.matter.MatterDataModel
import io.aether.android.matter.MatterPrivilege
import io.aether.android.matter.MatterType
import javax.inject.Inject
import javax.inject.Singleton

/** A reference to a specific attribute on a specific cluster. */
data class ClusterAttribute(val clusterId: Long, val attributeId: Long)

/**
 * Loads Matter data-model binary assets and exposes the cluster / device-type maps used throughout
 * the app.
 *
 * Binary files are stored under `assets/matter/` and follow the naming convention `v<version>.bin`
 * (e.g. `v1.0.bin`, `v1.1.bin`).
 *
 * The loader is a singleton; call [load] to obtain a snapshot of the model for a particular spec
 * version, or use the convenience properties [clustersMap] and [devicesMap] which always reflect
 * the latest version.
 */
@Singleton
class DataModelLoader @Inject constructor(@ApplicationContext private val context: Context) {

  // ---------------------------------------------------------------------------
  // Well-known cluster IDs and attribute references kept here for backward
  // compatibility with the rest of the codebase.
  // ---------------------------------------------------------------------------

  companion object {
    // Well-known cluster IDs
    const val OnOffClusterId: Long = 6L
    const val LevelControlClusterId: Long = 8L
    const val ColorControlClusterId: Long = 768L

    val OnOffAttribute = ClusterAttribute(OnOffClusterId, 0L)
    val LevelAttribute = ClusterAttribute(LevelControlClusterId, 0L)
    val ColorTemperatureAttribute = ClusterAttribute(ColorControlClusterId, 7L)

    private const val ASSETS_DIR = "matter"

    /** Converts a file name like `v1.0.bin` to a version string `1.0`. */
    private fun fileNameToVersion(name: String): String =
        name.removePrefix("v").removeSuffix(".bin")
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Returns the list of Matter spec versions available as binary assets, sorted in ascending order
   * (e.g. `["1.0", "1.1", "1.2"]`).
   */
  fun getVersions(): List<String> {
    val files = context.assets.list(ASSETS_DIR) ?: return emptyList()
    return files
        .filter { it.startsWith("v") && it.endsWith(".bin") }
        .map { fileNameToVersion(it) }
        .sortedWith(
            Comparator { a, b ->
              val aParts = a.split(".").map(String::toInt)
              val bParts = b.split(".").map(String::toInt)
              aParts.zip(bParts).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x - y }
                  ?: (aParts.size - bParts.size)
            }
        )
  }

  /**
   * Loads the data model for the given [version] (e.g. `"1.0"`). When [version] is `null` the
   * latest bundled model is returned.
   *
   * Returns an empty [MatterDataModel] if the requested asset cannot be found.
   */
  fun load(version: String? = null): MatterDataModel {
    val fileName = if (version == null) "v${getVersions().last()}.bin" else "v$version.bin"
    return try {
      context.assets.open("$ASSETS_DIR/$fileName").use { stream ->
        MatterDataModel.parseFrom(stream)
      }
    } catch (_: Exception) {
      MatterDataModel.getDefaultInstance()
    }
  }

  // ---------------------------------------------------------------------------
  // Convenience properties backed by the latest model
  // ---------------------------------------------------------------------------

  /** Cached latest bundled data model, loaded and parsed only once on first access. */
  private val latestModel: MatterDataModel by lazy { load() }

  /** Maps device ID → device name using the latest bundled data model. */
  val devicesMap: Map<Long, String> by lazy {
    latestModel.devicesList.associate { it.id.toLong() to it.name }
  }

  /** Maps cluster ID → cluster name using the latest bundled data model. */
  val clustersMap: Map<Long, String> by lazy {
    latestModel.clustersList.associate { it.id.toLong() to it.name }
  }

  val genericAttributes: List<GenericAttributeDefinition> by lazy {
    listOf(
        GenericAttributeDefinition(
            id = 0xFFF8L,
            name = "GeneratedCommandList",
            typeValue = MatterType.TYPE_LIST_UINT32,
            readPrivilege = MatterPrivilege.PRIVILEGE_VIEW,
        ),
        GenericAttributeDefinition(
            id = 0xFFF9L,
            name = "AcceptedCommandList",
            typeValue = MatterType.TYPE_LIST_UINT32,
            readPrivilege = MatterPrivilege.PRIVILEGE_VIEW,
        ),
        GenericAttributeDefinition(
            id = 0xFFFAL,
            name = "EventList",
            typeValue = MatterType.TYPE_LIST_UINT32,
            readPrivilege = MatterPrivilege.PRIVILEGE_VIEW,
        ),
        GenericAttributeDefinition(
            id = 0xFFFBL,
            name = "AttributeList",
            typeValue = MatterType.TYPE_LIST_UINT32,
            readPrivilege = MatterPrivilege.PRIVILEGE_VIEW,
        ),
        GenericAttributeDefinition(
            id = 0xFFFCL,
            name = "FeatureMap",
            typeValue = MatterType.TYPE_UINT32,
            readPrivilege = MatterPrivilege.PRIVILEGE_VIEW,
        ),
        GenericAttributeDefinition(
            id = 0xFFFDL,
            name = "ClusterRevision",
            typeValue = MatterType.TYPE_UINT16,
            readPrivilege = MatterPrivilege.PRIVILEGE_VIEW,
        ),
    )
  }

  fun shortTypeLabel(matterType: MatterType): String =
      when (matterType) {
        MatterType.TYPE_BOOL -> "BOOL"
        MatterType.TYPE_STRING,
        MatterType.TYPE_OCTSTR -> "STR"
        MatterType.TYPE_UINT8,
        MatterType.TYPE_ENUM8,
        MatterType.TYPE_MAP8 -> "U8"
        MatterType.TYPE_UINT16,
        MatterType.TYPE_ENUM16,
        MatterType.TYPE_MAP16 -> "U16"
        MatterType.TYPE_UINT24,
        MatterType.TYPE_UINT32,
        MatterType.TYPE_CLUSTER_ID,
        MatterType.TYPE_ATTRIBUTE_ID,
        MatterType.TYPE_ENDPOINT_NO,
        MatterType.TYPE_DEVTYPE_ID,
        MatterType.TYPE_GROUP_ID,
        MatterType.TYPE_VENDOR_ID,
        MatterType.TYPE_MESSAGE_ID,
        MatterType.TYPE_SNAPSHOT_STREAM_ID,
        MatterType.TYPE_TLS_ENDPOINT_ID -> "U32"
        MatterType.TYPE_UINT64,
        MatterType.TYPE_EPOCH_S,
        MatterType.TYPE_EPOCH_US,
        MatterType.TYPE_FABRIC_IDX,
        MatterType.TYPE_NODE_ID,
        MatterType.TYPE_SUBJECT_ID,
        MatterType.TYPE_TLSCAID,
        MatterType.TYPE_TLSCCDID -> "U64"
        MatterType.TYPE_INT8 -> "I8"
        MatterType.TYPE_INT16 -> "I16"
        MatterType.TYPE_INT32 -> "I32"
        MatterType.TYPE_INT64 -> "I64"
        MatterType.TYPE_LIST_STRING,
        MatterType.TYPE_LIST_OCTSTR -> "LIST[STR]"
        MatterType.TYPE_LIST_UINT8 -> "LIST[U8]"
        MatterType.TYPE_LIST_UINT16 -> "LIST[U16]"
        MatterType.TYPE_LIST_UINT32,
        MatterType.TYPE_LIST_CLUSTER_ID,
        MatterType.TYPE_LIST_GROUP_ID,
        MatterType.TYPE_LIST_ENDPOINT_NO -> "LIST[U32]"
        MatterType.TYPE_LIST_SUBJECT_ID -> "LIST[U64]"
        MatterType.TYPE_UNKNOWN,
        MatterType.UNRECOGNIZED -> "N/A"
        else -> matterType.name
      }

  data class GenericAttributeDefinition(
      val id: Long,
      val name: String,
      val typeValue: MatterType = MatterType.TYPE_UNKNOWN,
      val readPrivilege: MatterPrivilege = MatterPrivilege.PRIVILEGE_UNKNOWN,
      val writePrivilege: MatterPrivilege = MatterPrivilege.PRIVILEGE_UNKNOWN,
  )
}

@StringRes
fun MatterPrivilege.labelRes(): Int =
    when (this) {
      MatterPrivilege.PRIVILEGE_VIEW -> R.string.device_explorer_privilege_view
      MatterPrivilege.PRIVILEGE_OPERATE -> R.string.device_explorer_privilege_operate
      MatterPrivilege.PRIVILEGE_MANAGE -> R.string.device_explorer_privilege_manage
      MatterPrivilege.PRIVILEGE_ADMIN -> R.string.device_explorer_privilege_administer
      else -> R.string.device_explorer_privilege_not_available
    }
