package com.familychannels.domain.usecase

import com.familychannels.domain.model.Channel
import com.familychannels.domain.repo.FamilyRepository

class LoadChannelsUseCase(private val repo: FamilyRepository) {
    suspend operator fun invoke(): List<Channel> = repo.listChannels()
}
