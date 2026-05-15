// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.matter.MatterDataModel
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
}
