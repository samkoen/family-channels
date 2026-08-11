package com.familychannels.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familychannels.domain.model.Channel
import com.familychannels.domain.usecase.LoadChannelsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val channels: List<Channel> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(
    private val loadChannels: LoadChannelsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { loadChannels() }
                .onSuccess { list ->
                    _state.value = HomeUiState(channels = list, loading = false)
                }
                .onFailure { error ->
                    _state.value = HomeUiState(
                        loading = false,
                        error = error.message ?: "load_failed",
                    )
                }
        }
    }
}
