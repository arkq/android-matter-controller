// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.matter.MatterDataModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads Matter data-model binary assets and exposes the cluster / device-type maps used
 * throughout the app.
 *
 * Binary files are stored under `assets/matter/` and follow the naming convention
 * `matter_<version>.bin` (e.g. `matter_1_0.bin`, `matter_1_1.bin`).  A special
 * `matter_latest.bin` file is used as the default when no version is specified.
 *
 * The loader is a singleton; call [load] to obtain a snapshot of the model for a
 * particular spec version, or use the convenience properties [clustersMap] and
 * [deviceTypesMap] which always reflect the latest version.
 */
@Singleton
class DataModelLoader @Inject constructor(@ApplicationContext private val context: Context) {

  // ---------------------------------------------------------------------------
  // Well-known cluster IDs and attribute references kept here for backward
  // compatibility with the rest of the codebase.
  // ---------------------------------------------------------------------------

  companion object {
    const val OnOffClusterId: Long = 6L
    const val LevelControlClusterId: Long = 8L
    const val ColorControlClusterId: Long = 768L

    data class ClusterAttribute(val clusterId: Long, val attributeId: Long)

    val OnOffAttribute = ClusterAttribute(OnOffClusterId, 0L)
    val LevelAttribute = ClusterAttribute(LevelControlClusterId, 0L)
    val ColorTemperatureAttribute = ClusterAttribute(ColorControlClusterId, 7L)

    private const val ASSETS_DIR = "matter"
    private const val LATEST_FILE = "matter_latest.bin"

    /** Converts a file name like `matter_1_0.bin` to a version string `1.0`. */
    private fun fileNameToVersion(name: String): String =
        name.removePrefix("matter_").removeSuffix(".bin").replace('_', '.')
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Returns the list of Matter spec versions available as binary assets,
   * sorted in ascending order (e.g. `["1.0", "1.1", "1.2"]`).
   *
   * The special `latest` entry is excluded from this list.
   */
  fun getVersions(): List<String> {
    val files = context.assets.list(ASSETS_DIR) ?: return emptyList()
    return files
        .filter { it.startsWith("matter_") && it.endsWith(".bin") && it != LATEST_FILE }
        .map { fileNameToVersion(it) }
        .sortedWith(compareBy { it.split(".").map(String::toInt) })
  }

  /**
   * Loads the data model for the given [version] (e.g. `"1.0"`).
   * When [version] is `null` the latest bundled model is returned.
   *
   * Returns an empty [MatterDataModel] if the requested asset cannot be found.
   */
  fun load(version: String? = null): MatterDataModel {
    val fileName =
        if (version == null) LATEST_FILE
        else "matter_" + version.replace('.', '_') + ".bin"
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

  /** Maps cluster ID → cluster name using the latest bundled data model. */
  val clustersMap: Map<Long, String> by lazy {
    load().clustersList.associate { it.id.toLong() to it.name }
  }

  /** Maps device-type ID → device-type name using the latest bundled data model. */
  val deviceTypesMap: Map<Long, String> by lazy {
    load().devicesList.associate { it.id.toLong() to it.name }
  }
}
