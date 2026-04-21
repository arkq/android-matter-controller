// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package com.google.homesampleapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/** Google Home Sample Application for Matter (GHSAFM) */
@HiltAndroidApp
class GHSAFM3pEcoApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    // Setup Timber for logging.
    Timber.plant(
        object : Timber.DebugTree() {
          // Override [log] to add a "global prefix" prefix to the tag.
          override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, "${APP_NAME}-$tag", message, t)
          }

          // Override [createStackElementTag] to include additional information to the tag.
          // (e.g. a "method name" to the tag).
          /**
           * Not enabled for now, but leaving here since it may be useful when debugging. override
           * fun createStackElementTag(element: StackTraceElement): String { return String.format(
           * "%s:%s", super.createStackElementTag(element), element.methodName, ) }
           */
        })
  }
}
