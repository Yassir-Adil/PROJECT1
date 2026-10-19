package com.triguide.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptionEvidenceStatsTest {

    @Test
    fun incrementsReasonCodesWithoutPixels() {
        val stats = PerceptionEvidenceStats()
        stats.increment(PerceptionEvidenceStats.REJECTED_STALE)
        stats.increment(PerceptionEvidenceStats.REJECTED_STALE)
        stats.increment(PerceptionEvidenceStats.VOICE_SUPPRESSED)
        assertEquals(2L, stats.get(PerceptionEvidenceStats.REJECTED_STALE))
        assertEquals(1L, stats.get(PerceptionEvidenceStats.VOICE_SUPPRESSED))
        val text = stats.formatSummary()
        assertTrue(text.contains(PerceptionEvidenceStats.REJECTED_STALE))
        assertFalse(text.contains("bitmap", ignoreCase = true))
        assertFalse(text.contains("pixel", ignoreCase = true))
    }
}
