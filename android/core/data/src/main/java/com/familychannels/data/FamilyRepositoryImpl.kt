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
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class FamilyRepositoryImpl(
    private val api: ChildApi,
    private val store: SessionStore,
) : FamilyRepository {

    override suspend fun join(familyCode: String): List<ChildProfile> {
        return mapApiErrors {
            val response = api.join(JoinBody(familyCode.trim().uppercase()))
            response.children.map {
                ChildProfile(it.id, it.name, it.avatar_color)
            }
        }
    }

    override suspend fun createSession(familyCode: String, childId: String): String {
        return mapApiErrors {
            val response = api.session(SessionBody(familyCode.trim().uppercase(), childId))
            store.saveSession(response.token, familyCode.trim().uppercase(), childId)
            response.token
        }
    }

    override suspend fun listChannels(): List<Channel> {
        return mapApiErrors {
            api.channels(auth()).map {
                Channel(it.id, it.title, it.thumbnail_url, it.youtube_channel_id)
            }
        }
    }

    override suspend fun listVideos(channelId: String): List<VideoItem> {
        return mapApiErrors {
            api.videos(auth(), channelId).map {
                VideoItem(it.video_id, it.title, it.thumbnail_url)
            }
        }
    }

    override suspend fun getQuota(): WatchQuota {
        return mapApiErrors {
            val q = api.quota(auth())
            WatchQuota(
                q.minutes_remaining,
                q.minutes_used,
                q.daily_limit_minutes,
                q.can_watch,
            )
        }
    }

    override suspend fun heartbeat(minutes: Int): WatchQuota {
        return mapApiErrors {
            val q = api.heartbeat(auth(), HeartbeatBody(minutes))
            WatchQuota(
                q.minutes_remaining,
                q.minutes_used,
                q.daily_limit_minutes,
                q.can_watch,
            )
        }
    }

    private suspend fun auth(): String {
        val token = store.token() ?: error("missing_token")
        return "Bearer $token"
    }

    private inline fun <T> mapApiErrors(block: () -> T): T {
        try {
            return block()
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("timeout_server_waking", e)
        } catch (e: IOException) {
            throw IllegalStateException("network_error", e)
        } catch (e: HttpException) {
            val msg = when (e.code()) {
                404 -> "family_not_found"
                422 -> "invalid_code"
                else -> "server_error_${e.code()}"
            }
            throw IllegalStateException(msg, e)
        }
    }
}
