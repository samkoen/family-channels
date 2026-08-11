package com.familychannels.feature.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familychannels.domain.model.VideoItem
import com.familychannels.domain.usecase.LoadVideosUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VideosUiState(
    val videos: List<VideoItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class VideosViewModel(
    private val loadVideos: LoadVideosUseCase,
    private val channelId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(VideosUiState())
    val state: StateFlow<VideosUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = VideosUiState(loading = true)
            // One retry helps when Render is waking or the first YouTube scan is slow.
            var lastError: Throwable? = null
            repeat(2) { attempt ->
                val result = runCatching { loadVideos(channelId) }
                result.onSuccess { list ->
                    _state.value = VideosUiState(videos = list, loading = false)
                    return@launch
                }.onFailure { error ->
                    lastError = error
                    if (attempt == 0) {
                        kotlinx.coroutines.delay(1_500)
                    }
                }
            }
            _state.value = VideosUiState(
                loading = false,
                error = lastError?.message ?: "load_failed",
            )
        }
    }
}
