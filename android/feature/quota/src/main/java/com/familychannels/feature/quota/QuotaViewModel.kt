package com.familychannels.feature.quota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familychannels.domain.error.QuotaExceededException
import com.familychannels.domain.model.WatchQuota
import com.familychannels.domain.repo.FamilyRepository
import com.familychannels.domain.usecase.CanWatchUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class QuotaViewModel(
    private val canWatch: CanWatchUseCase,
    private val repo: FamilyRepository,
) : ViewModel() {
    private val _quota = MutableStateFlow<WatchQuota?>(null)
    val quota: StateFlow<WatchQuota?> = _quota

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    suspend fun refreshNow() {
        runCatching { canWatch.currentQuota() }
            .onSuccess { _quota.value = it }
    }

    suspend fun heartbeatNow(): WatchQuota? {
        return try {
            repo.heartbeat(1).also { _quota.value = it }
        } catch (_: QuotaExceededException) {
            _quota.value = _quota.value?.copy(canWatch = false)
            null
        }
    }
}

fun formatQuotaLabel(quota: WatchQuota?, timeLeftTemplate: String, timeOver: String): String {
    if (quota == null) return ""
    if (!quota.canWatch) return timeOver
    return timeLeftTemplate.format(quota.minutesRemaining)
}
