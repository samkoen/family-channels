package com.familychannels.data

import com.familychannels.domain.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelMappingTest {
    @Test
    fun mapsDtoFields() {
        val channel = Channel(
            id = "1",
            title = "Demo",
            thumbnailUrl = "https://example.com/a.jpg",
            youtubeChannelId = "UCabcdefghijklmnopqrstuv",
        )
        assertEquals("Demo", channel.title)
        assertEquals("UCabcdefghijklmnopqrstuv", channel.youtubeChannelId)
    }
}
