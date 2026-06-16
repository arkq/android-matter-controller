// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.data.models.ThreadNetworkDiagnosticsData

@Composable
fun ThreadNetworkDiagnostics(data: ThreadNetworkDiagnosticsData) {
  DiagnosticsSection(title = stringResource(R.string.device_diagnostics_section_thread_network)) {}
}
