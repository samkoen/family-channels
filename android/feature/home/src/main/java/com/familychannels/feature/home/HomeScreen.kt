package com.familychannels.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.familychannels.domain.model.Channel
import com.familychannels.domain.model.WatchQuota
import com.familychannels.ui.components.AppBackground
import com.familychannels.ui.components.QuotaBar
import com.familychannels.ui.i18n.Strings
import com.familychannels.ui.theme.Danger

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(strings.channels, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(12.dp))
            QuotaHeader(quotaLabel, quota)
            LazyColumn(
                modifier = Modifier.padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.channels) { channel ->
                    ChannelRow(channel, onChannelClick)
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun QuotaHeader(label: String, quota: WatchQuota?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (quota?.canWatch == false) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (quota != null && quota.canWatch) {
            QuotaBar(
                remaining = quota.minutesRemaining,
                limit = quota.dailyLimitMinutes,
                modifier = Modifier.fillMaxWidth(0.45f),
            )
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, onClick: (Channel) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick(channel) },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Text(channel.title, style = MaterialTheme.typography.titleLarge)
        }
    }
}
