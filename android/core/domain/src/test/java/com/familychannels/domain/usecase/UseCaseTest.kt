package com.familychannels.domain.usecase

import com.familychannels.domain.model.Channel
import com.familychannels.domain.model.ChildProfile
import com.familychannels.domain.model.VideoItem
import com.familychannels.domain.repo.FamilyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinFamilyUseCaseTest {
    @Test
    fun joinsWithNormalizedCode() = runBlocking {
        val repo = FakeRepo()
        val result = JoinFamilyUseCase(repo)(" ab12cd ")
        assertEquals("AB12CD", repo.lastJoinCode)
        assertEquals(1, result.size)
    }
}

class CanWatchUseCaseTest {
    @Test
    fun returnsFalseWhenQuotaEmpty() = runBlocking {
        val repo = FakeRepo(canWatch = false)
        assertFalse(CanWatchUseCase(repo)())
    }

    @Test
    fun returnsTrueWhenQuotaAvailable() = runBlocking {
        val repo = FakeRepo(canWatch = true)
        assertTrue(CanWatchUseCase(repo)())
    }
}

class ParseChannelIdTest {
    @Test
    fun parsesDirectId() {
        val id = "UCabcdefghijklmnopqrstuv"
        assertEquals(id, ParseChannelId.fromRaw(id))
    }

    @Test
    fun parsesUrl() {
        val id = "UCabcdefghijklmnopqrstuv"
        assertEquals(id, ParseChannelId.fromRaw("https://youtube.com/channel/$id"))
    }
}

private class FakeRepo(
    private val canWatch: Boolean = true,
) : FamilyRepository {
    var lastJoinCode: String? = null

    override suspend fun join(familyCode: String): List<ChildProfile> {
        lastJoinCode = familyCode
        return listOf(ChildProfile("1", "Emma", "#333"))
    }

    override suspend fun createSession(familyCode: String, childId: String, pin: String) = "token"
    override suspend fun listChannels(): List<Channel> = emptyList()
    override suspend fun listVideos(channelId: String): List<VideoItem> = emptyList()
    override suspend fun getQuota() = com.familychannels.domain.model.WatchQuota(
        minutesRemaining = if (canWatch) 10 else 0,
        minutesUsed = 0,
        dailyLimitMinutes = 60,
        canWatch = canWatch,
    )
    override suspend fun heartbeat(minutes: Int) = getQuota()
}
