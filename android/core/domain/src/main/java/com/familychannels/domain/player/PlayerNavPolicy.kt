package com.familychannels.domain.player

import java.net.URI

/**
 * Keeps the child player on one approved video.
 *
 * YouTube's iframe fills leftover space (a tall portrait WebView) with a
 * "YouTube" button and unrelated recommendations. Clicks must never open
 * youtube.com, the YouTube app, or another embed.
 */
object PlayerNavPolicy {
    private val embedIdRegex = Regex("^/embed/([\\w-]{6,20})")
    private val browseHeads = setOf(
        "watch",
        "shorts",
        "results",
        "feed",
        "channel",
        "c",
        "user",
        "playlist",
        "gaming",
        "tv",
        "redirect",
        "hashtag",
        "account",
        "premium",
    )

    fun shouldAllowMainFrame(url: String, videoId: String, serverHost: String): Boolean {
        if (url.isBlank() || url == "about:blank") return true
        val uri = parse(url) ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "about") return true
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host?.lowercase() ?: return false
        if (!hostEquals(host, serverHost)) return false
        return isAppPlayerPath(uri, videoId)
    }

    fun shouldBlockResource(url: String, videoId: String): Boolean {
        val uri = parse(url) ?: return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path.orEmpty()
        if (host == "youtu.be" || host.endsWith(".youtu.be")) return true
        if (!isYouTubeHost(host)) return false
        if (isYouTubeBrowsePath(path)) return true
        return false
    }

    private val videoIdRegex = Regex("^[\\w-]{6,20}$")

    private fun isAppPlayerPath(uri: URI, videoId: String): Boolean {
        val path = uri.path ?: return false
        val embedId = embedVideoId(path)
        if (embedId != null) return videoIdRegex.matches(embedId)
        if (path == "/static/player.html") {
            val v = queryParam(uri.query, "v") ?: return false
            return videoIdRegex.matches(v)
        }
        return false
    }

    private fun isYouTubeHost(host: String): Boolean {
        val h = host.removePrefix("www.")
        return h == "youtube.com" ||
            h.endsWith(".youtube.com") ||
            h == "youtube-nocookie.com" ||
            h.endsWith(".youtube-nocookie.com")
    }

    private fun isYouTubeBrowsePath(path: String): Boolean {
        val trimmed = path.trimEnd('/')
        if (trimmed.isEmpty()) return true
        if (trimmed.startsWith("/@")) return true
        val head = trimmed.trimStart('/').substringBefore('/')
        return head in browseHeads
    }

    private fun embedVideoId(path: String): String? =
        embedIdRegex.find(path)?.groupValues?.get(1)

    private fun queryParam(query: String?, name: String): String? {
        if (query.isNullOrBlank()) return null
        return query.split("&").firstNotNullOfOrNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@firstNotNullOfOrNull null
            val key = part.substring(0, idx)
            val value = part.substring(idx + 1)
            if (key == name) value else null
        }
    }

    private fun hostEquals(host: String, serverHost: String): Boolean {
        val h = host.lowercase()
        val s = serverHost.lowercase()
        return h == s || h.endsWith(".$s")
    }

    private fun parse(url: String): URI? =
        try {
            URI(url)
        } catch (_: Exception) {
            null
        }
}
