package com.familychannels.feature.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.familychannels.domain.model.VideoItem
import com.familychannels.ui.components.AppBackground
import com.familychannels.ui.components.ErrorBanner
import com.familychannels.ui.components.LoadingPanel
import com.familychannels.ui.components.ScreenHeader
import com.familychannels.ui.components.ScreenScaffold
import com.familychannels.ui.components.SoftCard
import com.familychannels.ui.i18n.AppStrings.messageForError
import com.familychannels.ui.i18n.Strings
import com.familychannels.ui.theme.TealSoft

@Composable
fun VideosScreen(
    viewModel: VideosViewModel,
    strings: Strings,
    onVideoClick: (VideoItem) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    AppBackground(modifier = Modifier.fillMaxSize()) {
        ScreenScaffold {
            ScreenHeader(
                title = strings.videos,
                subtitle = strings.appName,
            )
            Spacer(modifier = Modifier.height(16.dp))
            when {
                state.loading -> {
                    LoadingPanel(
                        title = strings.serverWaking,
                        hint = strings.serverWakingHint,
                    )
                }
                state.error != null -> {
                    ErrorBanner(strings.messageForError(state.error))
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        items(state.videos, key = { it.videoId }) { video ->
                            VideoRow(video, onVideoClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRow(video: VideoItem, onClick: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val thumbUrl = remember(video.videoId, video.thumbnailUrl) {
        video.thumbnailUrl.takeIf { it.isNotBlank() }
            ?: "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg"
    }
    val imageModel = remember(thumbUrl) {
        ImageRequest.Builder(context)
            .data(thumbUrl)
            .crossfade(true)
            .build()
    }
    SoftCard(
        onClick = { onClick(video) },
        contentPadding = PaddingValues(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TealSoft),
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                video.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
