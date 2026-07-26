package com.familychannels.feature.quota

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private var lastHeartbeatAt = 0L

    fun refresh() {
        viewModelScope.launch {
            runCatching { canWatch.currentQuota() }
                .onSuccess { _quota.value = it }
        }
    }

    fun heartbeat() {
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAt < 60_000) return
        lastHeartbeatAt = now
        viewModelScope.launch {
            runCatching { repo.heartbeat(1) }
                .onSuccess { _quota.value = it }
                .onFailure { refresh() }
        }
    }
}

fun formatQuotaLabel(quota: WatchQuota?, timeLeftTemplate: String, timeOver: String): String {
    if (quota == null) return ""
    if (!quota.canWatch) return timeOver
    return timeLeftTemplate.format(quota.minutesRemaining)
}
