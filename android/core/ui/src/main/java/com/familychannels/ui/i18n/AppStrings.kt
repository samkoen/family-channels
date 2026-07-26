package com.familychannels.ui.i18n

data class Strings(
    val appName: String,
    val enterCode: String,
    val continueLabel: String,
    val chooseProfile: String,
    val channels: String,
    val videos: String,
    val timeLeft: String,
    val timeOver: String,
    val language: String,
    val joinHint: String,
    val back: String,
)

object AppStrings {
    val supported = listOf("fr", "en", "he")

    fun of(lang: String): Strings = when (normalize(lang)) {
        "en" -> en
        "he" -> he
        else -> fr
    }

    fun normalize(lang: String): String =
        lang.lowercase().takeIf { it in supported } ?: "fr"

    fun next(lang: String): String {
        val current = normalize(lang)
        val index = supported.indexOf(current)
        return supported[(index + 1) % supported.size]
    }

    fun isRtl(lang: String): Boolean = normalize(lang) == "he"

    private val fr = Strings(
        appName = "Family Channels",
        enterCode = "Code famille",
        continueLabel = "Continuer",
        chooseProfile = "Qui regarde ?",
        channels = "Chaînes",
        videos = "Vidéos",
        timeLeft = "%d min restantes",
        timeOver = "Temps écoulé pour aujourd’hui",
        language = "Langue",
        joinHint = "Entre ton code famille pour voir tes chaînes.",
        back = "Retour",
    )

    private val en = Strings(
        appName = "Family Channels",
        enterCode = "Family code",
        continueLabel = "Continue",
        chooseProfile = "Who is watching?",
        channels = "Channels",
        videos = "Videos",
        timeLeft = "%d min left",
        timeOver = "Time is up for today",
        language = "Language",
        joinHint = "Enter your family code to see your channels.",
        back = "Back",
    )

    private val he = Strings(
        appName = "Family Channels",
        enterCode = "קוד משפחה",
        continueLabel = "המשך",
        chooseProfile = "מי צופה?",
        channels = "ערוצים",
        videos = "סרטונים",
        timeLeft = "נותרו %d דק׳",
        timeOver = "הזמן להיום הסתיים",
        language = "שפה",
        joinHint = "הזינו את קוד המשפחה כדי לראות את הערוצים.",
        back = "חזרה",
    )
}
