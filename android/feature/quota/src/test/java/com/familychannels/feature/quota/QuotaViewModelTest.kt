package com.familychannels.feature.quota

import com.familychannels.domain.error.QuotaExceededException
import com.familychannels.domain.model.Channel
import com.familychannels.domain.model.ChildProfile
import com.familychannels.domain.model.VideoItem
import com.familychannels.domain.model.WatchQuota
import com.familychannels.domain.repo.FamilyRepository
import com.familychannels.domain.usecase.CanWatchUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaViewModelTest {
    @Test
    fun refreshNowUpdatesQuotaState() = runTest {
        val quota = WatchQuota(12, 48, 60, true)
        val vm = QuotaViewModel(CanWatchUseCase(FakeQuotaRepo(quota)), FakeQuotaRepo(quota))
        vm.refreshNow()
        assertEquals(12, vm.quota.value?.minutesRemaining)
        assertTrue(vm.quota.value?.canWatch == true)
    }

    @Test
    fun heartbeatNowUpdatesQuotaOnSuccess() = runTest {
        val initial = WatchQuota(10, 50, 60, true)
        val afterBeat = WatchQuota(9, 51, 60, true)
        val repo = FakeQuotaRepo(initial, heartbeatResult = afterBeat)
        val vm = QuotaViewModel(CanWatchUseCase(repo), repo)
        vm.refreshNow()
        val result = vm.heartbeatNow()
        assertEquals(afterBeat, result)
        assertEquals(9, vm.quota.value?.minutesRemaining)
    }

    @Test
    fun heartbeatNowMarksBlockedWhenQuotaExceeded() = runTest {
        val initial = WatchQuota(1, 59, 60, true)
        val repo = FakeQuotaRepo(initial, throwOnHeartbeat = QuotaExceededException())
        val vm = QuotaViewModel(CanWatchUseCase(repo), repo)
        vm.refreshNow()
        val result = vm.heartbeatNow()
        assertNull(result)
        assertFalse(vm.quota.value?.canWatch ?: true)
    }

    @Test
    fun heartbeatNowReturnsNullWhenNoPriorQuota() = runTest {
        val repo = FakeQuotaRepo(
            WatchQuota(0, 60, 60, false),
            throwOnHeartbeat = QuotaExceededException(),
        )
        val vm = QuotaViewModel(CanWatchUseCase(repo), repo)
        val result = vm.heartbeatNow()
        assertNull(result)
        assertNull(vm.quota.value)
    }
}

private class FakeQuotaRepo(
    private var quota: WatchQuota,
    private val heartbeatResult: WatchQuota? = null,
    private val throwOnHeartbeat: Exception? = null,
) : FamilyRepository {
    override suspend fun join(familyCode: String): List<ChildProfile> = emptyList()
    override suspend fun createSession(familyCode: String, childId: String) = "token"
    override suspend fun listChannels(): List<Channel> = emptyList()
    override suspend fun listVideos(channelId: String): List<VideoItem> = emptyList()
    override suspend fun getQuota(): WatchQuota = quota

    override suspend fun heartbeat(minutes: Int): WatchQuota {
        throwOnHeartbeat?.let { throw it }
        val next = heartbeatResult ?: quota.copy(
            minutesUsed = quota.minutesUsed + minutes,
            minutesRemaining = maxOf(0, quota.minutesRemaining - minutes),
            canWatch = quota.minutesRemaining - minutes > 0,
        )
        quota = next
        return next
    }
}
