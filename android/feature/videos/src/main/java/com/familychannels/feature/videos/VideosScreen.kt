package com.familychannels.feature.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.familychannels.domain.model.VideoItem
import com.familychannels.ui.components.AppBackground
import com.familychannels.ui.components.ErrorBanner
import com.familychannels.ui.components.LoadingPanel
import com.familychannels.ui.components.PlayGlyph
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
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        items(state.videos, key = { it.videoId }) { video ->
                            VideoCard(video, onVideoClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoItem, onClick: (VideoItem) -> Unit) {
    SoftCard(
        onClick = { onClick(video) },
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(TealSoft),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.05f),
                                    Color.Black.copy(alpha = 0.45f),
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayGlyph(
                        modifier = Modifier.padding(start = 3.dp),
                        tint = Color(0xFF0B6E72),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
