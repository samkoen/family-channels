package com.familychannels.domain.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNavPolicyTest {
    private val videoId = "abcdefghijk"
    private val server = "family-channels.onrender.com"

    @Test
    fun allowsAppEmbedAndStaticPlayer() {
        assertTrue(
            PlayerNavPolicy.shouldAllowMainFrame(
                "https://$server/embed/$videoId",
                videoId,
                server,
            ),
        )
        assertTrue(
            PlayerNavPolicy.shouldAllowMainFrame(
                "https://$server/static/player.html?v=$videoId",
                videoId,
                server,
            ),
        )
        assertTrue(PlayerNavPolicy.shouldAllowMainFrame("about:blank", videoId, server))
    }

    @Test
    fun blocksYoutubeAsWholePageAndOtherVideosOnOurServer() {
        assertFalse(
            PlayerNavPolicy.shouldAllowMainFrame(
                "https://www.youtube.com/embed/$videoId",
                videoId,
                server,
            ),
        )
        assertFalse(
            PlayerNavPolicy.shouldAllowMainFrame(
                "https://www.youtube.com/watch?v=$videoId",
                videoId,
                server,
            ),
        )
        assertFalse(
            PlayerNavPolicy.shouldAllowMainFrame(
                "https://m.youtube.com/",
                videoId,
                server,
            ),
        )
        assertTrue(
            PlayerNavPolicy.shouldAllowMainFrame(
                "https://$server/embed/otherVideo99",
                videoId,
                server,
            ),
        )
        assertFalse(
            PlayerNavPolicy.shouldAllowMainFrame(
                "intent://www.youtube.com/watch?v=$videoId#Intent;package=com.google.android.youtube;end",
                videoId,
                server,
            ),
        )
    }

    @Test
    fun blocksBrowseButAllowsRelatedEmbedsAndPlayerAssets() {
        assertTrue(
            PlayerNavPolicy.shouldBlockResource(
                "https://www.youtube.com/watch?v=$videoId",
                videoId,
            ),
        )
        assertFalse(
            PlayerNavPolicy.shouldBlockResource(
                "https://www.youtube.com/embed/otherVideo99",
                videoId,
            ),
        )
        assertFalse(
            PlayerNavPolicy.shouldBlockResource(
                "https://www.youtube-nocookie.com/embed/otherVideo99",
                videoId,
            ),
        )
        assertTrue(PlayerNavPolicy.shouldBlockResource("https://youtu.be/$videoId", videoId))
        assertTrue(PlayerNavPolicy.shouldBlockResource("https://www.youtube.com/feed/trending", videoId))
        assertTrue(PlayerNavPolicy.shouldBlockResource("https://www.youtube.com/", videoId))

        assertFalse(
            PlayerNavPolicy.shouldBlockResource(
                "https://www.youtube.com/embed/$videoId",
                videoId,
            ),
        )
        assertFalse(
            PlayerNavPolicy.shouldBlockResource(
                "https://www.youtube.com/iframe_api",
                videoId,
            ),
        )
        assertFalse(
            PlayerNavPolicy.shouldBlockResource(
                "https://www.youtube.com/s/player/abc/player.js",
                videoId,
            ),
        )
        assertFalse(
            PlayerNavPolicy.shouldBlockResource(
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                videoId,
            ),
        )
    }
}
