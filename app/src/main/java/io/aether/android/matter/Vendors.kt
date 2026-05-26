// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.matter

import io.aether.android.formatVendorId

/**
 * Known Matter Vendor IDs from the CSA Distributed Compliance Ledger (DCL).
 *
 * Source: https://webui.dcl.csa-iot.org/vendors
 */
val VENDORS =
    mapOf<Int, String>(
        0x0000 to "Unspecified",
        0x1011 to "Google",
        0x1135 to "Amazon",
        0x1217 to "Apple",
        0x1341 to "Apple",
        0x100B to "Philips Hue (Signify)",
        0x100C to "IKEA",
        0x1321 to "Samsung SmartThings",
        0x10D9 to "Legrand",
        0x117C to "Eve Systems",
        0x1020 to "Comcast",
        0x1049 to "Wulian",
        0x1037 to "Tuya",
        0x111D to "Belkin (Wemo)",
        0x1349 to "Apple Home",
        0x1384 to "Apple Keychain",
        0x6006 to "Google LLC",
        0xFFF1 to "Test Vendor 1",
        0xFFF2 to "Test Vendor 2",
        0xFFF3 to "Test Vendor 3",
        0xFFF4 to "Test Vendor 4",
    )

/** Returns a human-readable vendor label for a Matter VID, including the hex code. */
fun vendorLabel(vendorID: Int, providedLabel: String? = null): String {
  val name = providedLabel?.takeIf { it.isNotBlank() } ?: VENDORS[vendorID]
  val hex = formatVendorId(vendorID)
  return if (name != null) "$name ($hex)" else hex
}
