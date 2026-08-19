package com.familychannels.data.api

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class JoinBody(val family_code: String)
data class ChildDto(
    val id: String,
    val name: String,
    val avatar_color: String,
    val has_pin: Boolean = false,
)
data class JoinResponse(val children: List<ChildDto>)
data class SessionBody(val family_code: String, val child_id: String, val pin: String? = null)
data class SessionResponse(val token: String, val child_id: String, val name: String)
data class ChannelDto(
    val id: String,
    val title: String,
    @Json(name = "thumbnail_url") val thumbnail_url: String = "",
    val youtube_channel_id: String,
)
data class VideoDto(
    val video_id: String,
    val title: String,
    @Json(name = "thumbnail_url") val thumbnail_url: String = "",
)
data class QuotaDto(
    val minutes_remaining: Int,
    val minutes_used: Int,
    val daily_limit_minutes: Int,
    val can_watch: Boolean,
)
data class HeartbeatBody(val minutes: Int = 1)

interface ChildApi {
    @POST("api/child/join")
    suspend fun join(@Body body: JoinBody): JoinResponse

    @POST("api/child/session")
    suspend fun session(@Body body: SessionBody): SessionResponse

    @GET("api/child/channels")
    suspend fun channels(@Header("Authorization") auth: String): List<ChannelDto>

    @GET("api/child/videos")
    suspend fun videos(
        @Header("Authorization") auth: String,
        @Query("channel_id") channelId: String,
    ): List<VideoDto>

    @GET("api/child/quota")
    suspend fun quota(@Header("Authorization") auth: String): QuotaDto

    @POST("api/child/watch/heartbeat")
    suspend fun heartbeat(
        @Header("Authorization") auth: String,
        @Body body: HeartbeatBody,
    ): QuotaDto
}
