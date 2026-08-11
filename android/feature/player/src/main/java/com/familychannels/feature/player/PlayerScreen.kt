package com.familychannels.feature.player

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.familychannels.ui.components.AppBackground
import com.familychannels.ui.i18n.Strings
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    videoId: String,
    allowedIds: Set<String>,
    strings: Strings,
    onFinished: () -> Unit,
    onHeartbeat: () -> Unit,
) {
    val safeId = remember(videoId, allowedIds) {
        videoId.takeIf { it.isNotBlank() && (allowedIds.isEmpty() || it in allowedIds) }
    }
    // Quota heartbeat while the player screen stays open.
    LaunchedEffect(safeId) {
        if (safeId == null) return@LaunchedEffect
        onHeartbeat()
        while (true) {
            delay(60_000)
            onHeartbeat()
        }
    }
    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (safeId == null) {
                Text(
                    "Unavailable",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                // No clip/shadow around the WebView — those often cause a black surface.
                YoutubeEmbedWebView(
                    videoId = safeId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YoutubeEmbedWebView(
    videoId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                webView = this
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                loadEmbed(videoId)
            }
        },
        update = { view ->
            if (view.tag != videoId) {
                view.loadEmbed(videoId)
            }
        },
    )
}

private fun WebView.loadEmbed(videoId: String) {
    tag = videoId
    val html =
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
          <style>
            html, body { margin:0; padding:0; background:#000; height:100%; overflow:hidden; }
            .wrap { position:fixed; inset:0; }
            iframe { width:100%; height:100%; border:0; }
          </style>
        </head>
        <body>
          <div class="wrap">
            <iframe
              src="https://www.youtube.com/embed/$videoId?playsinline=1&rel=0&modestbranding=1&fs=1"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
              allowfullscreen
              referrerpolicy="origin">
            </iframe>
          </div>
        </body>
        </html>
        """.trimIndent()
    loadDataWithBaseURL(
        "https://www.youtube.com",
        html,
        "text/html",
        "UTF-8",
        null,
    )
}
