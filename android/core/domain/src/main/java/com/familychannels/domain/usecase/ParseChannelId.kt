package com.familychannels.domain.usecase

object ParseChannelId {
    private val channelIdRegex = Regex("^UC[\\w-]{22}$")

    fun fromRaw(raw: String): String? {
        val value = raw.trim()
        if (channelIdRegex.matches(value)) return value
        val path = value.substringAfter("youtube.com/", missingDelimiterValue = "")
        val parts = path.split("/").filter { it.isNotBlank() }
        if (parts.size >= 2 && parts[0] == "channel") return parts[1]
        return null
    }
}
