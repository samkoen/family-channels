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
            runCatching { loadVideos(channelId) }
                .onSuccess { list ->
                    _state.value = VideosUiState(videos = list, loading = false)
                }
                .onFailure {
                    _state.value = VideosUiState(loading = false, error = "load_failed")
                }
        }
    }
}
