// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import org.junit.Assert.*
import org.junit.Test

class UtilsTest {
  @Test
  fun stripLinkLocalInIpAddress_ok() {
    val ipAddress = "fe80::84b1:c2f6:b1b7:67d4"
    val linkLocalIpAddress = ipAddress + "%wlan"
    assertEquals(ipAddress, stripLinkLocalInIpAddress(ipAddress))
    assertEquals(ipAddress, stripLinkLocalInIpAddress(linkLocalIpAddress))
  }
}
