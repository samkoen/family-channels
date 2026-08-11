package com.familychannels.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.familychannels.domain.model.Channel
import com.familychannels.domain.model.WatchQuota
import com.familychannels.ui.components.AppBackground
import com.familychannels.ui.components.BrandMark
import com.familychannels.ui.components.ChevronGlyph
import com.familychannels.ui.components.ErrorBanner
import com.familychannels.ui.components.LoadingPanel
import com.familychannels.ui.components.QuotaBadge
import com.familychannels.ui.components.ScreenHeader
import com.familychannels.ui.components.ScreenScaffold
import com.familychannels.ui.components.SoftCard
import com.familychannels.ui.i18n.AppStrings.messageForError
import com.familychannels.ui.i18n.Strings
import com.familychannels.ui.theme.TealSoft

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    strings: Strings,
    quotaLabel: String,
    quota: WatchQuota?,
    onChannelClick: (Channel) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    AppBackground(modifier = Modifier.fillMaxSize()) {
        ScreenScaffold {
            ScreenHeader(
                title = strings.channels,
                subtitle = strings.appName,
                trailing = { BrandMark(size = 44.dp) },
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (quota != null) {
                QuotaBadge(
                    label = quotaLabel,
                    remaining = quota.minutesRemaining,
                    limit = quota.dailyLimitMinutes,
                    exhausted = !quota.canWatch,
                )
            } else {
                Text(
                    quotaLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state.loading -> {
                    LoadingPanel(
                        title = strings.serverWaking,
                        hint = strings.serverWakingHint,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        items(state.channels) { channel ->
                            ChannelRow(channel, onChannelClick)
                        }
                    }
                }
            }
            state.error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                ErrorBanner(strings.messageForError(it))
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, onClick: (Channel) -> Unit) {
    SoftCard(
        onClick = { onClick(channel) },
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(TealSoft),
            ) {
                AsyncImage(
                    model = channel.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                            ),
                        ),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(TealSoft),
                contentAlignment = Alignment.Center,
            ) {
                ChevronGlyph()
            }
        }
    }
}
