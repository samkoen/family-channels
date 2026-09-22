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
    private val looseVideoId = Regex(
        """(?:[?&]v=|/embed/|/shorts/|/live/|youtu\.be/)([\w-]{6,20})""",
    )

    /**
     * Video id from a YouTube *exit* URL (watch / shorts / youtu.be / intent).
     * Does not treat /embed/ as leaving — the iframe needs those to play.
     */
    fun leaveAppVideoId(url: String): String? {
        if (url.isBlank()) return null
        val raw = url.trim()
        if (raw.startsWith("vnd.youtube:", ignoreCase = true)) {
            val id = raw.substringAfter(":").substringBefore("?").substringBefore("&")
                .substringBefore("#")
            return id.takeIf { videoIdRegex.matches(it) }
        }
        val uri = parse(raw)
        if (uri != null) {
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path.orEmpty()
            if (host == "youtu.be" || host.endsWith(".youtu.be")) {
                val id = path.trim('/').substringBefore('/')
                return id.takeIf { videoIdRegex.matches(it) }
            }
            if (isYouTubeHost(host) || raw.startsWith("intent:", ignoreCase = true) ||
                raw.startsWith("youtube:", ignoreCase = true)
            ) {
                val head = path.trimEnd('/').trimStart('/').substringBefore('/')
                if (head == "watch") {
                    return queryParam(uri.query, "v")?.takeIf { videoIdRegex.matches(it) }
                }
                if (head == "shorts" || head == "live") {
                    val id = path.trim('/').substringAfter('/').substringBefore('/')
                    return id.takeIf { videoIdRegex.matches(it) }
                }
            }
        }
        if (!looksLikeYouTube(raw)) return null
        if (raw.contains("/embed/", ignoreCase = true) &&
            !raw.contains("/watch", ignoreCase = true)
        ) {
            return null
        }
        return looseVideoId.find(raw)?.groupValues?.get(1)?.takeIf { videoIdRegex.matches(it) }
    }

    private fun looksLikeYouTube(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("youtube.com") ||
            u.contains("youtu.be") ||
            u.contains("youtube-nocookie.com") ||
            u.startsWith("intent:") ||
            u.startsWith("youtube:") ||
            u.startsWith("vnd.youtube:")
    }

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
