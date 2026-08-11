package com.familychannels.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.familychannels.ui.components.AppBackground
import com.familychannels.ui.i18n.Strings
import com.familychannels.ui.theme.SurfaceWhite
import com.familychannels.ui.theme.Teal
import com.familychannels.ui.theme.TealSoft
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
    val panelShape = RoundedCornerShape(22.dp)
    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onFinished,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        "←  ${strings.back}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE85D4C)),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (safeId == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceWhite,
                    shape = panelShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
                ) {
                    Text(
                        "Unavailable",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .shadow(
                            elevation = 10.dp,
                            shape = panelShape,
                            clip = false,
                            ambientColor = Teal.copy(alpha = 0.14f),
                            spotColor = Teal.copy(alpha = 0.2f),
                        ),
                    color = SurfaceWhite.copy(alpha = 0.96f),
                    shape = panelShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TealSoft),
                    ) {
                        LockedYouTubePlayer(
                            videoId = safeId,
                            onEnded = onFinished,
                            onPlayingTick = onHeartbeat,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
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
        // Must be a real https origin YouTube accepts — package-name origins break playback.
        val options = IFramePlayerOptions.Builder()
            .controls(1)
            .rel(0)
            .ivLoadPolicy(3)
            .ccLoadPolicy(0)
            .origin("https://www.youtube-nocookie.com")
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

                override fun onError(
                    youTubePlayer: YouTubePlayer,
                    error: PlayerConstants.PlayerError,
                ) {
                    // Retry once with cue+play — helps after transient embed init errors.
                    if (error == PlayerConstants.PlayerError.HTML_5_PLAYER ||
                        error == PlayerConstants.PlayerError.UNKNOWN
                    ) {
                        youTubePlayer.cueVideo(videoId, 0f)
                        youTubePlayer.play()
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
