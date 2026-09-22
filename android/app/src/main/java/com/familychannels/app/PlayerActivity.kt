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
import com.familychannels.domain.player.PlayerNavPolicy
import com.familychannels.domain.repo.FamilyRepository
import com.familychannels.ui.i18n.AppStrings
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
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
    private var channelId: String = ""
    private var loadAttempt = 0
    private var ytReady = false
    private var loadStarted = false
    private var quotaStopped = false
    private var lastFinishedUrl: String? = null
    private var relatedRequestId: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WebView.setWebContentsDebuggingEnabled(true)

        videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
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
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
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

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?,
            ): Boolean {
                log("blockWindow", "popup")
                return false
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
                lockPlayerLayout()
                hookRelatedClicks()
                injectAndroidChrome()
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
                return handlePlayerNavigation(url, request?.isForMainFrame != false)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handlePlayerNavigation(url.orEmpty(), isMainFrame = true)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()
                val leaveId = PlayerNavPolicy.leaveAppVideoId(url)
                if (leaveId != null) {
                    val dest = requestHeader(request, "Sec-Fetch-Dest")
                    if (leaveId != videoId && dest in NAV_FETCH_DEST) {
                        handler.post { playRelatedInApp(leaveId) }
                    }
                    log("blockRes", url)
                    return blockedResponse()
                }
                if (PlayerNavPolicy.shouldBlockResource(url, videoId)) {
                    log("blockRes", url)
                    return blockedResponse()
                }
                return super.shouldInterceptRequest(view, request)
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

    /** Never open YouTube. A related /watch click plays in this WebView instead. */
    private fun handlePlayerNavigation(url: String, isMainFrame: Boolean): Boolean {
        val leaveId = PlayerNavPolicy.leaveAppVideoId(url)
        if (leaveId != null) {
            if (leaveId != videoId) {
                playRelatedInApp(leaveId)
            } else {
                log("blockNav", url)
            }
            return true
        }
        val allow = if (isMainFrame) {
            PlayerNavPolicy.shouldAllowMainFrame(url, videoId, SERVER_HOST)
        } else {
            !PlayerNavPolicy.shouldBlockResource(url, videoId)
        }
        if (!allow) log("blockNav", url)
        return !allow
    }

    private fun playRelatedInApp(nextId: String) {
        if (!VIDEO_ID_RE.matches(nextId) || nextId == videoId) return
        relatedRequestId = nextId
        log("related", nextId)
        lifecycleScope.launch {
            val allowed = if (channelId.isBlank()) {
                true
            } else {
                runCatching { watchRepo().canPlayVideo(channelId, nextId) }.getOrDefault(false)
            }
            if (nextId != relatedRequestId) return@launch
            if (!allowed) {
                log("canPlay", "$nextId allowed=false")
                return@launch
            }
            videoId = nextId
            val safe = nextId.replace("\\", "\\\\").replace("'", "\\'")
            runOnUiThread {
                log("canPlay", "$nextId allowed=true in-app")
                webView.evaluateJavascript(
                    """
                    (function(){
                      pendingId = '$safe';
                      videoId = '$safe';
                      var lock = document.getElementById('end-lock');
                      if (lock) lock.hidden = true;
                      try {
                        if (player && player.loadVideoById) {
                          player.loadVideoById('$safe');
                          return 'ok';
                        }
                      } catch (e) {}
                      return 'missing';
                    })();
                    """.trimIndent(),
                ) { result ->
                    if (result?.contains("missing") == true) {
                        loadAttempt = 0
                        ytReady = false
                        loadEmbedPage()
                    }
                }
            }
        }
    }

    private fun isRealUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url != "about:blank" && !url.startsWith("data:")
    }

    private fun blockedResponse(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            403,
            "Blocked",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(ByteArray(0)),
        )

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
            else -> {
                log("giveUp", "all strategies failed — copiez les logs")
                return
            }
        }
        log("load", "attempt=$loadAttempt url=$url")
        webView.loadUrl(url)
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
        if (loadAttempt <= 1) {
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

    /**
     * YouTube paints a "YouTube" button + unrelated videos in leftover
     * iframe height. Force 16:9 even if the hosted HTML is still old.
     */
    private fun lockPlayerLayout() {
        webView.evaluateJavascript(
            """
            (function(){
              var style = document.getElementById('fc-lock-style');
              if (!style) {
                style = document.createElement('style');
                style.id = 'fc-lock-style';
                document.documentElement.appendChild(style);
              }
              style.textContent =
                'html,body{margin:0;padding:0;background:#000!important;' +
                'overflow:hidden!important;height:100%!important;width:100%!important;}' +
                'body{display:flex!important;align-items:center!important;' +
                'justify-content:center!important;}' +
                '#stage{position:fixed!important;inset:0!important;display:flex!important;' +
                'align-items:center!important;justify-content:center!important;background:#000!important;}' +
                '#player,#player-box,iframe{width:min(100vw,calc(100vh * 16 / 9))!important;' +
                'height:min(100vh,calc(100vw * 9 / 16))!important;' +
                'max-width:100%!important;max-height:100%!important;border:0!important;}';
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
                lockPlayerLayout()
                hookRelatedClicks()
                injectAndroidChrome()
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

        @JavascriptInterface
        fun onEnded() {
            runOnUiThread { log("ytEnded", "keep player for related list") }
        }

        @JavascriptInterface
        fun requestVideos() {
            if (channelId.isBlank()) {
                notifyVideos("""{"ok":true,"videos":[]}""")
                return
            }
            lifecycleScope.launch {
                val page = runCatching { watchRepo().listVideos(channelId, 0) }.getOrNull()
                val videos = JSONArray()
                page?.videos?.forEach { item ->
                    videos.put(
                        JSONObject()
                            .put("video_id", item.videoId)
                            .put("title", item.title)
                            .put("thumbnail_url", item.thumbnailUrl),
                    )
                }
                notifyVideos(
                    JSONObject()
                        .put("ok", page != null)
                        .put("videos", videos)
                        .toString(),
                )
            }
        }

        @JavascriptInterface
        fun requestCanPlay(nextId: String) {
            if (!VIDEO_ID_RE.matches(nextId)) {
                notifyCanPlay(nextId, false)
                return
            }
            if (channelId.isBlank()) {
                notifyCanPlay(nextId, true)
                return
            }
            lifecycleScope.launch {
                val repo = watchRepo()
                val allowed = runCatching { repo.canPlayVideo(channelId, nextId) }
                    .getOrDefault(false)
                if (allowed) {
                    videoId = nextId
                }
                notifyCanPlay(nextId, allowed)
            }
        }
    }

    private fun notifyVideos(json: String) {
        val quoted = JSONObject.quote(json)
        runOnUiThread {
            log("videos", "n=${json.length}")
            webView.evaluateJavascript(
                "if (window.onVideosResult) onVideosResult($quoted);",
                null,
            )
        }
    }

    private fun notifyCanPlay(id: String, allowed: Boolean) {
        val safeId = id.replace("\\", "\\\\").replace("'", "\\'")
        val js = "if (window.onCanPlayResult) onCanPlayResult('$safeId', ${if (allowed) "true" else "false"});"
        runOnUiThread {
            log("canPlay", "$id allowed=$allowed")
            webView.evaluateJavascript(js, null)
        }
    }

    private fun hookRelatedClicks() {
        webView.evaluateJavascript(
            """
            (function(){
              if (typeof pendingId === 'undefined') window.pendingId = '';
              window.lockEnded = function(){
                var lock = document.getElementById('end-lock');
                if (lock) lock.hidden = true;
              };
              window.onCanPlayResult = function(id, allowed){
                if (!id || id !== pendingId) return;
                if (allowed) {
                  videoId = id;
                  var lock = document.getElementById('end-lock');
                  if (lock) lock.hidden = true;
                  try { if (player && player.loadVideoById) player.loadVideoById(id); } catch (e) {}
                } else {
                  try { if (player && player.loadVideoById) player.loadVideoById(videoId); } catch (e) {}
                }
              };
              window.enforceSameVideo = window.enforceSameChannel = function(){
                if (!player || !player.getVideoData) return;
                try {
                  var data = player.getVideoData();
                  var next = data && data.video_id;
                  if (!next || next === videoId) return;
                  pendingId = next;
                  if (window.Android && Android.requestCanPlay) Android.requestCanPlay(next);
                  else {
                    try { if (player && player.loadVideoById) player.loadVideoById(videoId); } catch (e) {}
                  }
                } catch (e) {}
              };
            })();
            """.trimIndent(),
            null,
        )
    }

    /** Same Chrome guard: cover YouTube logo/title, open more-videos in-app. */
    private fun injectAndroidChrome() {
        val moreLabel = AppStrings.of(Locale.getDefault().language).moreVideos
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        webView.evaluateJavascript(
            """
            (function(){
              var style = document.getElementById('fc-android-chrome');
              if (!style) {
                style = document.createElement('style');
                style.id = 'fc-android-chrome';
                document.documentElement.appendChild(style);
              }
              style.textContent =
                '#fc-yt-logo-block,#fc-yt-watermark-block,#fc-more-btn{position:absolute;z-index:20;border:0;pointer-events:auto;}' +
                '#fc-yt-logo-block{left:0;bottom:0;width:72%;height:64px;background:transparent;}' +
                '#fc-yt-watermark-block{top:0;right:0;width:140px;height:56px;background:transparent;}' +
                '#fc-more-btn{left:58px;bottom:10px;z-index:21;width:auto;height:auto;padding:6px 10px;' +
                'border-radius:8px;background:rgba(8,10,14,0.88);color:#eef3f7;font:650 12px/1.2 sans-serif;cursor:pointer;}' +
                '#fc-more-panel{position:absolute;inset:0;z-index:22;overflow:auto;background:rgba(8,10,14,0.94);' +
                'color:#eef3f7;padding:10px 10px 56px;}' +
                '#fc-more-panel[hidden]{display:none;}' +
                '#fc-more-panel .fc-more-close{float:right;border:0;background:transparent;color:#eef3f7;font-size:22px;}' +
                '#fc-more-panel .fc-more-title{margin:0 36px 12px 0;font-weight:650;}' +
                '#fc-more-panel .fc-more-item{display:flex;align-items:center;gap:10px;width:100%;margin:0 0 8px;' +
                'padding:0;border:0;background:transparent;color:inherit;text-align:left;}' +
                '#fc-more-panel .fc-more-item img{width:96px;height:54px;object-fit:cover;border-radius:8px;}';
              var box = document.getElementById('player-box') || document.getElementById('player-wrap') || document.body;
              function related(id){
                if (!id || id === videoId) return;
                pendingId = id;
                if (window.Android && Android.requestCanPlay) Android.requestCanPlay(id);
              }
              if (window.FamilyPlayerGuard && FamilyPlayerGuard.install) {
                FamilyPlayerGuard.install({
                  box: box,
                  getVideoId: function(){ return videoId; },
                  getPlayer: function(){ return player; },
                  onRelated: related,
                  moreLabel: '$moreLabel'
                });
              }
              if (!document.getElementById('fc-yt-logo-block')) {
                var logo = document.createElement('div');
                logo.id = 'fc-yt-logo-block';
                logo.setAttribute('aria-hidden', 'true');
                box.appendChild(logo);
              }
              if (!document.getElementById('fc-yt-watermark-block')) {
                var mark = document.createElement('div');
                mark.id = 'fc-yt-watermark-block';
                mark.setAttribute('aria-hidden', 'true');
                box.appendChild(mark);
              }
              if (!document.getElementById('fc-more-btn') && window.Android && Android.requestVideos) {
                var btn = document.createElement('button');
                btn.id = 'fc-more-btn';
                btn.type = 'button';
                btn.textContent = '$moreLabel';
                var panel = document.createElement('div');
                panel.id = 'fc-more-panel';
                panel.hidden = true;
                box.appendChild(btn);
                box.appendChild(panel);
                function closePanel(){ panel.hidden = true; panel.innerHTML = ''; }
                btn.onclick = function(ev){
                  ev.preventDefault();
                  ev.stopPropagation();
                  if (!panel.hidden) { closePanel(); return; }
                  window.onVideosResult = function(raw){
                    var data = typeof raw === 'string' ? JSON.parse(raw) : raw;
                    var videos = data && data.videos ? data.videos : [];
                    panel.innerHTML = '';
                    var close = document.createElement('button');
                    close.type = 'button';
                    close.className = 'fc-more-close';
                    close.textContent = '×';
                    close.onclick = closePanel;
                    panel.appendChild(close);
                    var heading = document.createElement('p');
                    heading.className = 'fc-more-title';
                    heading.textContent = '$moreLabel';
                    panel.appendChild(heading);
                    videos.forEach(function(v){
                      if (!v || !v.video_id) return;
                      var item = document.createElement('button');
                      item.type = 'button';
                      item.className = 'fc-more-item';
                      if (v.thumbnail_url) {
                        var img = document.createElement('img');
                        img.src = v.thumbnail_url;
                        img.alt = '';
                        item.appendChild(img);
                      }
                      var t = document.createElement('span');
                      t.textContent = v.title || v.video_id;
                      item.appendChild(t);
                      item.onclick = function(){ closePanel(); related(v.video_id); };
                      panel.appendChild(item);
                    });
                    panel.hidden = false;
                  };
                  Android.requestVideos();
                };
              }
            })();
            """.trimIndent(),
            null,
        )
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
        private const val EXTRA_CHANNEL_ID = "channel_id"
        private const val SERVER_HOST = "family-channels.onrender.com"
        private const val SERVER_BASE = "https://$SERVER_HOST"
        private val VIDEO_ID_RE = Regex("^[\\w-]{6,20}$")
        private val NAV_FETCH_DEST = setOf("document", "iframe", "frame")

        private fun requestHeader(request: WebResourceRequest?, name: String): String {
            val headers = request?.requestHeaders ?: return ""
            return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
                ?.lowercase()
                .orEmpty()
        }

        fun intent(context: Context, videoId: String, channelId: String = ""): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO_ID, videoId)
                .putExtra(EXTRA_CHANNEL_ID, channelId)
    }
}
