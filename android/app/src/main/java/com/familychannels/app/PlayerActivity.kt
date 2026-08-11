package com.familychannels.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app player with always-visible debug log (black screen diagnosis).
 */
class PlayerActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var logView: TextView
    private var videoId: String = ""
    private var loadAttempt = 0
    private var ytReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WebView.setWebContentsDebuggingEnabled(true)

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

        val top = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        top.addView(
            ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                contentDescription = "Back"
                setOnClickListener { finish() }
            },
        )
        root.addView(top)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(webView)

        val logScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(160),
            )
        }
        logView = TextView(this).apply {
            setTextColor(Color.parseColor("#7DFF7D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            movementMethod = ScrollingMovementMethod()
        }
        logScroll.addView(logView)
        root.addView(logScroll)

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

        logDeviceInfo()

        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                log("console", "${msg?.message()} (${msg?.sourceId()}:${msg?.lineNumber()})")
                return true
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                log("title", title ?: "")
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                log("pageStart", url ?: "")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                log("pageDone", url ?: "")
                probePage()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                log(
                    "webError",
                    "main=${request?.isForMainFrame} url=${request?.url} " +
                        "code=${error?.errorCode} ${error?.description}",
                )
                if (request?.isForMainFrame == true) {
                    handler.postDelayed({ tryNextLoad("mainFrameError") }, 1500)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                log(
                    "httpError",
                    "main=${request?.isForMainFrame} ${request?.url} " +
                        "status=${errorResponse?.statusCode}",
                )
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                    handler.postDelayed({ tryNextLoad("http${errorResponse?.statusCode}") }, 1500)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                val host = request?.url?.host.orEmpty()
                if (host.isEmpty()) return false
                val ok =
                    host.contains(SERVER_HOST) ||
                        host.contains("youtube.com") ||
                        host.contains("youtube-nocookie.com") ||
                        host.contains("googlevideo.com") ||
                        host.contains("google.com") ||
                        host.contains("gstatic.com") ||
                        host.contains("ytimg.com") ||
                        host.contains("ggpht.com")
                if (!ok) log("blockNav", url)
                return !ok
            }
        }

        loadEmbedPage()
        handler.postDelayed({ if (!ytReady && !isFinishing) tryNextLoad("timeout8s") }, 8000)
        startQuotaHeartbeat()
    }

    private fun logDeviceInfo() {
        log("videoId", videoId)
        val wvPkg = WebView.getCurrentWebViewPackage()
        log("webViewPkg", "${wvPkg?.packageName ?: "?"} ${wvPkg?.versionName ?: ""}")
        log("webViewUA", webView.settings.userAgentString)
    }

    private fun loadEmbedPage() {
        ytReady = false
        val url = when (loadAttempt) {
            0 -> "$SERVER_BASE/embed/$videoId"
            1 -> "$SERVER_BASE/static/player.html?v=$videoId"
            2 -> "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&fs=1"
            else -> {
                log("giveUp", "all strategies failed")
                return
            }
        }
        log("load", "attempt=$loadAttempt url=$url")
        val headers = if (loadAttempt == 2) {
            mapOf("Referer" to "$SERVER_BASE/")
        } else {
            emptyMap()
        }
        webView.loadUrl(url, headers)
    }

    private fun tryNextLoad(reason: String) {
        if (ytReady || isFinishing) return
        log("tryNext", "reason=$reason from attempt=$loadAttempt")
        loadAttempt++
        if (loadAttempt <= 2) {
            loadEmbedPage()
            handler.postDelayed({ if (!ytReady && !isFinishing) tryNextLoad("timeout8s") }, 8000)
        } else {
            log("giveUp", "all strategies failed")
        }
    }

    private fun probePage() {
        webView.evaluateJavascript(
            """
            (function(){
              var iframe = document.querySelector('iframe');
              var player = document.getElementById('player');
              var dbg = document.getElementById('dbg');
              var lines = [];
              lines.push('probe origin=' + location.origin);
              lines.push('probe href=' + location.href);
              if (iframe) {
                lines.push('iframe src=' + iframe.src);
                lines.push('iframe size=' + iframe.offsetWidth + 'x' + iframe.offsetHeight);
              } else {
                lines.push('iframe=none');
              }
              if (player) lines.push('playerDiv=' + player.offsetWidth + 'x' + player.offsetHeight);
              if (dbg) lines.push('pageDbg=' + dbg.textContent.slice(-200));
              if (typeof YT !== 'undefined') lines.push('YT=defined'); else lines.push('YT=undefined');
              return lines.join(' | ');
            })();
            """.trimIndent(),
        ) { result ->
            log("probe", result?.trim('"') ?: "")
        }
    }

    private fun log(tag: String, msg: String) {
        val line = "${timeFmt.format(Date())} [$tag] $msg"
        runOnUiThread {
            val prev = logView.text?.toString().orEmpty()
            logView.text = if (prev.isBlank()) line else "$prev\n$line"
            (logView.parent as? ScrollView)?.post {
                (logView.parent as ScrollView).fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics,
        ).toInt()

    private inner class AndroidBridge {
        @JavascriptInterface
        fun log(msg: String) {
            log("js", msg)
        }

        @JavascriptInterface
        fun onReady() {
            runOnUiThread {
                ytReady = true
                handler.removeCallbacksAndMessages(null)
                log("ytReady", "playback should start")
            }
        }

        @JavascriptInterface
        fun onError(code: String) {
            runOnUiThread {
                log("ytError", "code=$code (153=referer, 150/101=restricted)")
                handler.postDelayed({ if (!ytReady) tryNextLoad("ytError$code") }, 1500)
            }
        }

        @JavascriptInterface
        fun onState(state: String) {
            runOnUiThread { log("ytState", state) }
        }
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
        handler.removeCallbacksAndMessages(null)
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
