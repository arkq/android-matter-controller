// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.fabrics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.data.FabricsRepository
import io.aether.android.data.models.ManagedFabric
import io.aether.android.matter.NodeId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class FabricsUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val fabrics: List<ManagedFabric> = emptyList(),
    @field:StringRes val errorRes: Int? = null,
)

@HiltViewModel
class FabricsViewModel
@Inject
constructor(
    private val repository: FabricsRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(FabricsUiState())
  val uiState: StateFlow<FabricsUiState> = _uiState.asStateFlow()

  fun loadFabrics(nodeId: NodeId) {
    viewModelScope.launch {
      val isFirstLoad = _uiState.value.fabrics.isEmpty() && _uiState.value.errorRes == null

      _uiState.update {
        it.copy(
            isInitialLoading = isFirstLoad,
            isRefreshing = !isFirstLoad,
            errorRes = null,
        )
      }

      runCatching {
            val fabrics = repository.readManagedFabrics(nodeId)
            _uiState.update {
              it.copy(
                  fabrics = fabrics,
                  isInitialLoading = false,
                  isRefreshing = false,
              )
            }
          }
          .onFailure { e ->
            Timber.e(e, "Error loading fabrics")
            _uiState.update {
              it.copy(
                  isInitialLoading = false,
                  isRefreshing = false,
                  errorRes = R.string.controllers_offline,
              )
            }
          }
    }
  }

  fun removeFabric(nodeId: NodeId, fabricIndex: Int) {
    viewModelScope.launch {
      runCatching {
            val currentIdx = repository.getCurrentFabricIndex(nodeId)
            if (fabricIndex == currentIdx) return@launch
            repository.removeFabric(nodeId, fabricIndex)
            loadFabrics(nodeId)
          }
          .onFailure { e -> Timber.e(e, "Error removing fabric") }
    }
  }
}
