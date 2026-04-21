// SPDX-FileCopyrightText: 2020 The Android Open Source Project
// SPDX-License-Identifier: Apache-2.0

package com.google.homesampleapp.data

import android.content.Context
import androidx.lifecycle.asLiveData
import com.google.homesampleapp.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import timber.log.Timber

/** Singleton repository that updates and persists settings and user preferences. */
@Singleton
class UserPreferencesRepository @Inject constructor(@ApplicationContext context: Context) {
  // The datastore managed by UserPreferencesRepository.
  private val userPreferencesDataStore = context.userPreferencesDataStore

  // The Flow to read data from the DataStore.
  val userPreferencesFlow: Flow<UserPreferences> =
      userPreferencesDataStore.data.catch { exception ->
        // dataStore.data throws an IOException when an error is encountered when reading data
        if (exception is IOException) {
          Timber.e(exception, "Error reading user preferences.")
          emit(UserPreferences.getDefaultInstance())
        } else {
          throw exception
        }
      }

  val userPreferencesLiveData = userPreferencesFlow.asLiveData()

  suspend fun updateHideOfflineDevices(hide: Boolean) {
    Timber.d("updateHideOfflineDevices [$hide]")
    userPreferencesDataStore.updateData { prefs ->
      prefs.toBuilder().setHideOfflineDevices(hide).build()
    }
  }

  suspend fun shouldShowHalfsheetNotification(): Boolean {
    Timber.d("shouldShowHalfsheetNotification")
    return userPreferencesFlow.first().showHalfsheetNotification
  }

  suspend fun updateShowHalfsheetNotification(show: Boolean) {
    Timber.d("updateShowHalfsheetNotification [$show]")
    userPreferencesDataStore.updateData { prefs ->
      prefs.toBuilder().setShowHalfsheetNotification(show).build()
    }
  }

  suspend fun getData(): UserPreferences {
    return userPreferencesFlow.first()
  }
}
