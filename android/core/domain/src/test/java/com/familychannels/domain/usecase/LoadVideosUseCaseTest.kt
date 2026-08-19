package com.familychannels.domain.usecase

import com.familychannels.domain.model.VideoItem
import com.familychannels.domain.repo.FamilyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadVideosUseCaseTest {
    @Test
    fun loadsVideosForChannel() = runBlocking {
        val repo = object : FamilyRepository by EmptyRepo() {
            override suspend fun listVideos(channelId: String) =
                listOf(VideoItem("v1", "Title", "https://x"))
        }
        val videos = LoadVideosUseCase(repo)("ch1")
        assertEquals("v1", videos.first().videoId)
    }
}

private open class EmptyRepo : FamilyRepository {
    override suspend fun join(familyCode: String) = emptyList<com.familychannels.domain.model.ChildProfile>()
    override suspend fun createSession(familyCode: String, childId: String, pin: String) = ""
    override suspend fun listChannels() = emptyList<com.familychannels.domain.model.Channel>()
    override suspend fun listVideos(channelId: String) = emptyList<VideoItem>()
    override suspend fun getQuota() = com.familychannels.domain.model.WatchQuota(0, 0, 0, false)
    override suspend fun heartbeat(minutes: Int) = getQuota()
}
