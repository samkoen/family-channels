package com.familychannels.feature.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.familychannels.ui.components.AppBackground
import com.familychannels.ui.i18n.Strings
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun PlayerScreen(
    videoId: String,
    allowedIds: Set<String>,
    strings: Strings,
    onFinished: () -> Unit,
    onHeartbeat: () -> Unit,
) {
    val safeId = remember(videoId) { videoId.takeIf { it in allowedIds } }
    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            TextButton(onClick = onFinished, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(strings.back, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (safeId == null) {
                Text(
                    "Unavailable",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LockedYouTubePlayer(
                    videoId = safeId,
                    onEnded = onFinished,
                    onPlayingTick = onHeartbeat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LockedYouTubePlayer(
    videoId: String,
    onEnded: () -> Unit,
    onPlayingTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val playerView = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
        }
    }
    DisposableEffect(playerView, videoId) {
        lifecycleOwner.lifecycle.addObserver(playerView)
        val options = IFramePlayerOptions.Builder()
            .controls(1)
            .rel(0)
            .ivLoadPolicy(3)
            .ccLoadPolicy(0)
            .origin("https://${context.packageName}")
            .build()
        playerView.initialize(
            object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    youTubePlayer.loadVideo(videoId, 0f)
                }

                override fun onStateChange(
                    youTubePlayer: YouTubePlayer,
                    state: PlayerConstants.PlayerState,
                ) {
                    if (state == PlayerConstants.PlayerState.ENDED) {
                        onEnded()
                    }
                    if (state == PlayerConstants.PlayerState.PLAYING) {
                        onPlayingTick()
                    }
                }
            },
            options,
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }
    AndroidView(factory = { playerView }, modifier = modifier)
}
