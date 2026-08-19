package com.familychannels.domain.model

data class ChildProfile(
    val id: String,
    val name: String,
    val avatarColor: String,
    val hasPin: Boolean = false,
)

data class Channel(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val youtubeChannelId: String,
)

data class VideoItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
)

data class WatchQuota(
    val minutesRemaining: Int,
    val minutesUsed: Int,
    val dailyLimitMinutes: Int,
    val canWatch: Boolean,
)
