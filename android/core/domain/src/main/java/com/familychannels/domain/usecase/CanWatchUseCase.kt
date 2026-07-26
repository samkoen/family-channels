package com.familychannels.domain.usecase

import com.familychannels.domain.model.WatchQuota
import com.familychannels.domain.repo.FamilyRepository

class CanWatchUseCase(private val repo: FamilyRepository) {
    suspend operator fun invoke(): Boolean = repo.getQuota().canWatch

    suspend fun currentQuota(): WatchQuota = repo.getQuota()
}
