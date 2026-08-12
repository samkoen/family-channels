package com.familychannels.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.FrameLayout
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
import com.familychannels.domain.error.QuotaExceededException
import com.familychannels.domain.repo.FamilyRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app player. Logs showed href=about:blank → loadUrl was called before WebView resumed.
 */
class PlayerActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var logView: TextView? = null
    private var videoId: String = ""
    private var loadAttempt = 0
    private var ytReady = false
    private var loadStarted = false
    private var quotaStopped = false
    private var lastFinishedUrl: String? = null
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

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        root.addView(webView)

        if (SHOW_DEBUG_UI) {
            val logScroll = ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#1A1A1A"))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(160),
                    android.view.Gravity.BOTTOM,
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
        }
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
            @Suppress("DEPRECATION")
            databaseEnabled = true
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        logDeviceInfo()
        logNetwork()

        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                log("console", "${msg?.message()} (${msg?.sourceId()}:${msg?.lineNumber()})")
                return true
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank() && title != "about:blank") {
                    log("title", title)
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                log("pageStart", url ?: "")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                lastFinishedUrl = url
                log("pageDone", url ?: "")
                hideWebDebugPanel()
                if (isRealUrl(url)) {
                    probePage()
                    scheduleAttemptTimeout()
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
            ) {
                log("sslError", "${error?.primaryError} ${error?.url}")
                handler?.proceed()
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
                    handler.postDelayed({ tryNextLoad("mainFrameError") }, 1200)
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
                    handler.postDelayed({ tryNextLoad("http${errorResponse?.statusCode}") }, 1200)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                val host = request?.url?.host.orEmpty()
                if (host.isEmpty()) return false
                val ok = isAllowedHost(host)
                if (!ok) log("blockNav", url)
                return !ok
            }
        }

        startQuotaHeartbeat()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        if (!loadStarted && !quotaStopped) {
            lifecycleScope.launch {
                val repo = watchRepo()
                val allowed = runCatching { repo.getQuota().canWatch }.getOrDefault(false)
                if (!allowed) {
                    stopForQuota()
                    return@launch
                }
                loadStarted = true
                webView.post {
                    log("layout", "webView ${webView.width}x${webView.height} — starting load")
                    loadEmbedPage()
                }
            }
        }
    }

    override fun onPause() {
        webView.onPause()
        webView.pauseTimers()
        super.onPause()
    }

    private fun isRealUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url != "about:blank" && !url.startsWith("data:")
    }

    private fun isAllowedHost(host: String): Boolean =
        host.contains(SERVER_HOST) ||
            host.contains("youtube.com") ||
            host.contains("youtube-nocookie.com") ||
            host.contains("googlevideo.com") ||
            host.contains("google.com") ||
            host.contains("gstatic.com") ||
            host.contains("ytimg.com") ||
            host.contains("ggpht.com")

    private fun logDeviceInfo() {
        log("videoId", videoId)
        val wvPkg = WebView.getCurrentWebViewPackage()
        log("webViewPkg", "${wvPkg?.packageName ?: "?"} ${wvPkg?.versionName ?: ""}")
        log("webViewUA", webView.settings.userAgentString)
    }

    private fun logNetwork() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        log("network", if (online) "online" else "OFFLINE")
    }

    private fun loadEmbedPage() {
        ytReady = false
        handler.removeCallbacksAndMessages(null)

        val url = when (loadAttempt) {
            0 -> "$SERVER_BASE/embed/$videoId"
            1 -> "$SERVER_BASE/static/player.html?v=$videoId"
            2 -> "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&fs=1"
            else -> {
                log("giveUp", "all strategies failed — copiez les logs")
                return
            }
        }
        log("load", "attempt=$loadAttempt url=$url")
        if (loadAttempt == 2) {
            webView.loadUrl(url, mapOf("Referer" to "$SERVER_BASE/"))
        } else {
            webView.loadUrl(url)
        }
        scheduleAttemptTimeout()
    }

    private fun scheduleAttemptTimeout() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (ytReady || isFinishing) return@postDelayed
            if (!isRealUrl(lastFinishedUrl)) {
                log("timeout", "still on $lastFinishedUrl after 25s")
                tryNextLoad("timeout25s")
            }
        }, 25_000)
    }

    private fun tryNextLoad(reason: String) {
        if (ytReady || isFinishing) return
        log("tryNext", "reason=$reason from attempt=$loadAttempt")
        loadAttempt++
        if (loadAttempt <= 2) {
            webView.post { loadEmbedPage() }
        } else {
            log("giveUp", "all strategies failed — copiez les logs")
        }
    }

    private fun probePage() {
        webView.evaluateJavascript(
            """
            (function(){
              var lines = [];
              lines.push('origin=' + location.origin);
              lines.push('href=' + location.href);
              var iframe = document.querySelector('iframe');
              var player = document.getElementById('player');
              if (iframe) lines.push('iframe=' + iframe.offsetWidth + 'x' + iframe.offsetHeight);
              if (player) lines.push('playerDiv=' + player.offsetWidth + 'x' + player.offsetHeight);
              if (typeof YT !== 'undefined') lines.push('YT=ok'); else lines.push('YT=missing');
              var dbg = document.getElementById('dbg');
              if (dbg && dbg.textContent) lines.push('dbgTail=' + dbg.textContent.slice(-120));
              return lines.join(' | ');
            })();
            """.trimIndent(),
        ) { result ->
            log("probe", result?.trim('"') ?: "")
        }
    }

    private fun hideWebDebugPanel() {
        webView.evaluateJavascript(
            """
            (function(){
              var el = document.getElementById('dbg');
              if (el) el.style.display = 'none';
            })();
            """.trimIndent(),
            null,
        )
    }

    private fun log(tag: String, msg: String) {
        val line = "${timeFmt.format(Date())} [$tag] $msg"
        Log.d(LOG_TAG, line)
        if (!SHOW_DEBUG_UI) return
        runOnUiThread {
            val view = logView ?: return@runOnUiThread
            val prev = view.text?.toString().orEmpty()
            view.text = if (prev.isBlank()) line else "$prev\n$line"
            (view.parent as? ScrollView)?.post {
                (view.parent as ScrollView).fullScroll(View.FOCUS_DOWN)
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
                log("ytReady", "OK")
            }
        }

        @JavascriptInterface
        fun onError(code: String) {
            runOnUiThread {
                log("ytError", "code=$code (153=referer)")
                handler.postDelayed({ if (!ytReady) tryNextLoad("ytError$code") }, 1200)
            }
        }

        @JavascriptInterface
        fun onState(state: String) {
            runOnUiThread { log("ytState", state) }
        }
    }

    private fun startQuotaHeartbeat() {
        lifecycleScope.launch {
            val repo = watchRepo()
            if (!tickHeartbeat(repo)) {
                stopForQuota()
                return@launch
            }
            while (isActive) {
                delay(60_000)
                if (!tickHeartbeat(repo)) {
                    stopForQuota()
                    return@launch
                }
            }
        }
    }

    private fun watchRepo(): FamilyRepository =
        FamilyRepositoryImpl(ApiFactory.create(), SessionStore(applicationContext))

    private suspend fun tickHeartbeat(repo: FamilyRepository): Boolean {
        return try {
            repo.heartbeat(1).canWatch
        } catch (_: QuotaExceededException) {
            false
        } catch (_: Exception) {
            true
        }
    }

    private fun stopForQuota() {
        if (quotaStopped || isFinishing) return
        quotaStopped = true
        loadStarted = true
        handler.removeCallbacksAndMessages(null)
        webView.apply {
            stopLoading()
            evaluateJavascript(
                """
                (function(){
                  try {
                    if (typeof player !== 'undefined' && player.stopVideo) player.stopVideo();
                  } catch (e) {}
                })();
                """.trimIndent(),
                null,
            )
            loadUrl("about:blank")
        }
        finish()
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
        /** Mettre à true pour réafficher la barre de logs verte en bas de l'écran. */
        private const val SHOW_DEBUG_UI = false
        private const val LOG_TAG = "FamilyPlayer"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val SERVER_HOST = "family-channels.onrender.com"
        private const val SERVER_BASE = "https://$SERVER_HOST"
        private val VIDEO_ID_RE = Regex("^[\\w-]{6,20}$")

        fun intent(context: Context, videoId: String): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_VIDEO_ID, videoId)
    }
}
