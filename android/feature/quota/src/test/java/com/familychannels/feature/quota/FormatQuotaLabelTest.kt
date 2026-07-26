package com.familychannels.feature.quota

import com.familychannels.domain.model.WatchQuota
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatQuotaLabelTest {
    @Test
    fun showsRemainingMinutes() {
        val quota = WatchQuota(12, 48, 60, true)
        assertEquals(
            "Temps restant : 12 min",
            formatQuotaLabel(quota, "Temps restant : %d min", "Temps écoulé"),
        )
    }

    @Test
    fun showsOverWhenBlocked() {
        val quota = WatchQuota(0, 60, 60, false)
        assertEquals(
            "Temps écoulé",
            formatQuotaLabel(quota, "Temps restant : %d min", "Temps écoulé"),
        )
    }
}
