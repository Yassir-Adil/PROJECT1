package com.triguide.ai.domain

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Privacy-safe local counters for evidence pipeline observability.
 *
 * Counts reason codes only. Never stores image pixels, faces, or personal data.
 */
class PerceptionEvidenceStats {
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    fun increment(reasonCode: String) {
        val key = reasonCode.trim().ifEmpty { "unspecified" }
        counters.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
    }

    fun get(reasonCode: String): Long = counters[reasonCode]?.get() ?: 0L

    fun snapshot(): Map<String, Long> =
        counters.entries.associate { it.key to it.value.get() }.toSortedMap()

    fun clear() = counters.clear()

    fun formatSummary(): String {
        val rows = snapshot()
        if (rows.isEmpty()) return "Evidence stats: (empty)"
        return buildString {
            appendLine("Evidence stats (reason codes only; image content excluded)")
            rows.forEach { (code, count) -> appendLine("$code=$count") }
        }.trimEnd()
    }

    companion object {
        const val REJECTED_STALE = "rejected_stale"
        const val REJECTED_OUT_OF_ORDER = "rejected_out_of_order"
        const val REJECTED_INVALID = "rejected_invalid"
        const val REJECTED_EMPTY = "rejected_empty"
        const val DROPPED_BACKPRESSURE = "dropped_backpressure"
        const val VOICE_SUPPRESSED = "voice_suppressed"
        const val MASK_UNAVAILABLE = "mask_unavailable"
        const val MASK_UNMAPPED = "mask_unmapped"
    }
}
