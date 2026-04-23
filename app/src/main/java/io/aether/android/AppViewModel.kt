// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
) : ViewModel() {
  // TODO: Tried to support updating the shared Scaffold TopAppBar title
  // via a shared AppViewModel. Did not work. Revisit eventually, if this
  // makes the code cleaner to do it this way instead.
  private var _topAppBarTitle = MutableStateFlow("Sample App")
  val topAppBarTitle: StateFlow<String> = _topAppBarTitle.asStateFlow()

  fun setAppBarTitle(title: String) {
    _topAppBarTitle.value = title
  }
}
