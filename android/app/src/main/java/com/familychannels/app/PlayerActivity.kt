package com.familychannels.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.familychannels.data.ApiFactory
import com.familychannels.data.FamilyRepositoryImpl
import com.familychannels.data.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * In-app player: load the same HTTPS embed page as the mobile browser (real navigation).
 * loadDataWithBaseURL fails on this device; Chrome Custom Tabs is rejected by product.
 */
class PlayerActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private var videoId: String = ""
    private var loadAttempt = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        if (!VIDEO_ID_RE.matches(videoId)) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val header = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        header.addView(
            ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                contentDescription = "Back"
                setOnClickListener { finish() }
            },
        )
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(48, 56, 16, 8)
            text = getString(R.string.player_loading)
        }
        header.addView(statusView)
        root.addView(header)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(webView)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (url?.contains("404") == true || url?.contains("Not Found") == true) {
                    tryNextLoad()
                    return
                }
                statusView.visibility = View.GONE
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (request?.isForMainFrame == true) {
                    val code = errorResponse?.statusCode ?: 0
                    setStatus(getString(R.string.player_http_error, code))
                    tryNextLoad()
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val host = request?.url?.host.orEmpty()
                if (host.isEmpty()) return false
                val ok =
                    host.contains(SERVER_HOST) ||
                        host.contains("youtube.com") ||
                        host.contains("youtube-nocookie.com") ||
                        host.contains("googlevideo.com") ||
                        host.contains("google.com") ||
                        host.contains("gstatic.com") ||
                        host.contains("ytimg.com")
                return !ok
            }
        }

        loadEmbedPage()
        startQuotaHeartbeat()
    }

    private fun loadEmbedPage() {
        val url = when (loadAttempt) {
            0 -> "$SERVER_BASE/embed/$videoId"
            1 -> "$SERVER_BASE/static/player.html?v=$videoId"
            else -> {
                setStatus(getString(R.string.player_need_deploy))
                return
            }
        }
        setStatus(getString(R.string.player_loading_origin, url))
        webView.loadUrl(url)
    }

    private fun tryNextLoad() {
        loadAttempt++
        if (loadAttempt <= 1) {
            loadEmbedPage()
        } else {
            setStatus(getString(R.string.player_need_deploy))
        }
    }

    private fun setStatus(text: String) {
        statusView.text = text
        statusView.visibility = View.VISIBLE
    }

    private fun startQuotaHeartbeat() {
        val repo = FamilyRepositoryImpl(ApiFactory.create(), SessionStore(applicationContext))
        lifecycleScope.launch {
            runCatching { repo.heartbeat() }
            while (isActive) {
                delay(60_000)
                runCatching { repo.heartbeat() }
            }
        }
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val SERVER_HOST = "family-channels.onrender.com"
        private const val SERVER_BASE = "https://$SERVER_HOST"
        private val VIDEO_ID_RE = Regex("^[\\w-]{6,20}$")

        fun intent(context: Context, videoId: String): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_VIDEO_ID, videoId)
    }
}
