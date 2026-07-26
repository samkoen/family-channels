package com.familychannels.data

import com.familychannels.data.api.ChildApi
import com.familychannels.data.api.HeartbeatBody
import com.familychannels.data.api.JoinBody
import com.familychannels.data.api.SessionBody
import com.familychannels.domain.model.Channel
import com.familychannels.domain.model.ChildProfile
import com.familychannels.domain.model.VideoItem
import com.familychannels.domain.model.WatchQuota
import com.familychannels.domain.repo.FamilyRepository

class FamilyRepositoryImpl(
    private val api: ChildApi,
    private val store: SessionStore,
) : FamilyRepository {

    override suspend fun join(familyCode: String): List<ChildProfile> {
        val response = api.join(JoinBody(familyCode))
        return response.children.map {
            ChildProfile(it.id, it.name, it.avatar_color)
        }
    }

    override suspend fun createSession(familyCode: String, childId: String): String {
        val response = api.session(SessionBody(familyCode, childId))
        store.saveSession(response.token, familyCode, childId)
        return response.token
    }

    override suspend fun listChannels(): List<Channel> {
        return api.channels(auth()).map {
            Channel(it.id, it.title, it.thumbnail_url, it.youtube_channel_id)
        }
    }

    override suspend fun listVideos(channelId: String): List<VideoItem> {
        return api.videos(auth(), channelId).map {
            VideoItem(it.video_id, it.title, it.thumbnail_url)
        }
    }

    override suspend fun getQuota(): WatchQuota {
        val q = api.quota(auth())
        return WatchQuota(
            q.minutes_remaining,
            q.minutes_used,
            q.daily_limit_minutes,
            q.can_watch,
        )
    }

    override suspend fun heartbeat(minutes: Int): WatchQuota {
        val q = api.heartbeat(auth(), HeartbeatBody(minutes))
        return WatchQuota(
            q.minutes_remaining,
            q.minutes_used,
            q.daily_limit_minutes,
            q.can_watch,
        )
    }

    private suspend fun auth(): String {
        val token = store.token() ?: error("missing_token")
        return "Bearer $token"
    }
}
