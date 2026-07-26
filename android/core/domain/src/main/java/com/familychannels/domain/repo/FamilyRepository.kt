package com.familychannels.domain.repo

import com.familychannels.domain.model.Channel
import com.familychannels.domain.model.ChildProfile
import com.familychannels.domain.model.VideoItem
import com.familychannels.domain.model.WatchQuota

interface FamilyRepository {
    suspend fun join(familyCode: String): List<ChildProfile>
    suspend fun createSession(familyCode: String, childId: String): String
    suspend fun listChannels(): List<Channel>
    suspend fun listVideos(channelId: String): List<VideoItem>
    suspend fun getQuota(): WatchQuota
    suspend fun heartbeat(minutes: Int = 1): WatchQuota
}
