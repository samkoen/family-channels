package com.familychannels.domain.usecase

import com.familychannels.domain.model.VideoItem
import com.familychannels.domain.repo.FamilyRepository

class LoadVideosUseCase(private val repo: FamilyRepository) {
    suspend operator fun invoke(channelId: String): List<VideoItem> {
        require(channelId.isNotBlank()) { "invalid_channel_id" }
        return repo.listVideos(channelId)
    }
}
