// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningWindow
import com.google.android.gms.home.matter.commissioning.ShareDeviceRequest
import com.google.android.gms.home.matter.common.DeviceDescriptor
import com.google.android.gms.home.matter.common.Discriminator
import io.aether.android.DISCRIMINATOR
import io.aether.android.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import io.aether.android.R
import io.aether.android.SETUP_PIN_CODE
import timber.log.Timber

@Composable
internal fun ShareDeviceSection(onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Icon(Icons.Outlined.Share, contentDescription = null)
    Spacer(modifier = Modifier.width(8.dp))
    Text(stringResource(R.string.share_device).uppercase())
  }
}

@Composable
internal fun ShareDeviceAlertDialog(
  show: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  if (!show) {
    return
  }

  AlertDialog(
    title = { Text(text = stringResource(R.string.share_device_dialog_title)) },
    text = { Text(stringResource(R.string.share_device_body)) },
    confirmButton = {
      Button(onClick = onConfirm) {
        Text(stringResource(R.string.yes_share_it))
      }
    },
    onDismissRequest = onDismiss,
    dismissButton = {
      Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
  )
}

internal fun shareDevice(
  context: Context,
  shareDeviceLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
  deviceViewModel: DeviceViewModel,
  deviceName: String,
) {
  Timber.d("ShareDevice: starting")

  val shareDeviceRequest =
    ShareDeviceRequest.builder()
      .setDeviceDescriptor(DeviceDescriptor.builder().build())
      .setDeviceName(deviceName)
      .setCommissioningWindow(
        CommissioningWindow.builder()
          .setDiscriminator(Discriminator.forLongValue(DISCRIMINATOR))
          .setPasscode(SETUP_PIN_CODE)
          .setWindowOpenMillis(SystemClock.elapsedRealtime())
          .setDurationSeconds(OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS.toLong())
          .build()
      )
      .build()
  Timber.d(
    "ShareDevice: shareDeviceRequest discriminator [${shareDeviceRequest.commissioningWindow.discriminator}]"
  )

  // The call to shareDevice() creates the IntentSender that will eventually be launched
  // in the fragment to trigger the multi-admin activity in GPS (step 3).
  Matter.getCommissioningClient(context)
    .shareDevice(shareDeviceRequest)
    .addOnSuccessListener { result ->
      Timber.d("ShareDevice: Success getting the IntentSender: result [${result}]")
      shareDeviceLauncher.launch(IntentSenderRequest.Builder(result).build())
    }
    .addOnFailureListener { error ->
      Timber.e(error)
      deviceViewModel.showMsgDialog(
        context.getString(R.string.share_device_failed),
        error.toString(),
      )
    }
}

@Preview(widthDp = 300)
@Composable
private fun ShareDeviceSectionPreview() {
  MaterialTheme { ShareDeviceSection({}) }
}
