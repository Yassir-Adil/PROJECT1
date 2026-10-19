package com.triguide.ai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineVoiceSelectorTest {
    @Test
    fun onlineHighQualityVoiceIsExcluded() {
        val online = candidate(
            id = "en-online-hi",
            language = "en",
            localeTag = "en-US",
            quality = 500,
            latency = 100,
            requiresNetwork = true,
        )
        val offline = candidate(
            id = "en-offline-lo",
            language = "en",
            localeTag = "en-US",
            quality = 200,
            latency = 400,
        )
        val pick = OfflineVoiceSelector.select(listOf(online, offline), "en", "en-US")
        assertEquals("en-offline-lo", pick?.id)
        assertFalse(OfflineVoiceSelector.eligible(listOf(online), "en").any())
    }

    @Test
    fun englishAndArabicStaySeparate() {
        val en = candidate(id = "en-a", language = "en", localeTag = "en-US", quality = 400)
        val ar = candidate(id = "ar-a", language = "ar", localeTag = "ar", quality = 500)
        assertEquals("en-a", OfflineVoiceSelector.select(listOf(en, ar), "en")?.id)
        assertEquals("ar-a", OfflineVoiceSelector.select(listOf(en, ar), "ar")?.id)
        assertTrue(OfflineVoiceSelector.hasOffline(listOf(en, ar), "en"))
        assertTrue(OfflineVoiceSelector.hasOffline(listOf(en, ar), "ar"))
        assertFalse(OfflineVoiceSelector.hasOffline(listOf(en), "ar"))
    }

    @Test
    fun ranksByQualityThenLatencyThenName() {
        val lowQuality = candidate(id = "b-name", language = "en", localeTag = "en-US", quality = 300, latency = 100)
        val highSlow = candidate(id = "a-name", language = "en", localeTag = "en-US", quality = 400, latency = 300)
        val highFast = candidate(id = "c-name", language = "en", localeTag = "en-US", quality = 400, latency = 100)
        val ranked = OfflineVoiceSelector.ranked(
            listOf(lowQuality, highSlow, highFast),
            languageCode = "en",
            preferredLocaleTag = "en-US",
        )
        assertEquals(listOf("c-name", "a-name", "b-name"), ranked.map { it.id })
    }

    @Test
    fun nameTieBreakIsDeterministic() {
        val zebra = candidate(id = "zebra", language = "en", localeTag = "en-US", quality = 400, latency = 200)
        val alpha = candidate(id = "alpha", language = "en", localeTag = "en-US", quality = 400, latency = 200)
        val ranked = OfflineVoiceSelector.ranked(listOf(zebra, alpha), "en", "en-US")
        assertEquals(listOf("alpha", "zebra"), ranked.map { it.id })
        assertEquals("alpha", OfflineVoiceSelector.select(listOf(zebra, alpha), "en", "en-US")?.id)
    }

    @Test
    fun noEligibleOfflineVoiceReturnsNone() {
        assertNull(
            OfflineVoiceSelector.select(
                listOf(
                    candidate(id = "net", language = "en", localeTag = "en-US", requiresNetwork = true),
                    candidate(id = "missing", language = "en", localeTag = "en-US", installed = false),
                    candidate(id = "ar-only", language = "ar", localeTag = "ar"),
                ),
                languageCode = "en",
            ),
        )
        assertTrue(OfflineVoiceSelector.ranked(emptyList(), "en").isEmpty())
    }

    @Test
    fun preferredLocaleWinsWhenQualityAndLatencyTie() {
        val gb = candidate(id = "en-gb", language = "en", localeTag = "en-GB", quality = 400, latency = 200)
        val us = candidate(id = "en-us", language = "en", localeTag = "en-US", quality = 400, latency = 200)
        assertEquals(
            "en-us",
            OfflineVoiceSelector.select(listOf(gb, us), "en", preferredLocaleTag = "en-US")?.id,
        )
        // No preferred match still falls back to best offline English (name tie-break).
        assertEquals(
            "en-gb",
            OfflineVoiceSelector.select(listOf(gb), "en", preferredLocaleTag = "en-US")?.id,
        )
    }

    private fun candidate(
        id: String,
        language: String,
        localeTag: String,
        quality: Int = 300,
        latency: Int = 200,
        requiresNetwork: Boolean = false,
        installed: Boolean = true,
    ) = OfflineVoiceCandidate(
        id = id,
        language = language,
        localeTag = localeTag,
        quality = quality,
        latency = latency,
        requiresNetwork = requiresNetwork,
        installed = installed,
    )
}
