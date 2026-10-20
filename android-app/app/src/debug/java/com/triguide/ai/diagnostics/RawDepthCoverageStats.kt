package com.triguide.ai.diagnostics

/**
 * Diagnostic observed-coverage aggregates over successfully decoded
 * [RawDepthSummary] pairs. Counts every validated decoded pair, not gate
 * accepts and not unique depth estimates. Holds no Images or buffers.
 */
internal data class RawDepthCoverageSnapshot(
    val decodedPairs: Long,
    val sampledPixels: Long,
    val validDepthPixels: Long,
    val confidentPixels: Long,
    val pairsWithConfidentPixels: Long,
    val validFraction: Double?,
    val confidentFraction: Double?,
    val capacityReached: Boolean,
)

/**
 * Bounded, thread-safe accumulator for decoded raw-depth pair coverage.
 *
 * Long counters stay within [maxPairs] * [MAX_SAMPLED_PIXELS] (both Int-bounded),
 * so sums cannot overflow Long under the validation rules.
 */
internal class RawDepthCoverageStats(
    private val maxPairs: Int = DEFAULT_MAX_PAIRS,
) {
    init {
        require(maxPairs > 0) { "maxPairs must be > 0" }
    }

    private val lock = Any()

    private var decodedPairs: Long = 0L
    private var sampledPixels: Long = 0L
    private var validDepthPixels: Long = 0L
    private var confidentPixels: Long = 0L
    private var pairsWithConfidentPixels: Long = 0L
    private var stopped: Boolean = false

    fun record(summary: RawDepthSummary): Boolean = synchronized(lock) {
        if (stopped || decodedPairs >= maxPairs.toLong()) {
            return false
        }
        if (!isValidCounts(summary)) {
            return false
        }

        decodedPairs += 1L
        sampledPixels += summary.sampledPixels.toLong()
        validDepthPixels += summary.validDepthPixels.toLong()
        confidentPixels += summary.confidentPixels.toLong()
        if (summary.confidentPixels > 0) {
            pairsWithConfidentPixels += 1L
        }
        return true
    }

    fun snapshot(): RawDepthCoverageSnapshot = synchronized(lock) {
        val observed = decodedPairs > 0L
        val sampled = sampledPixels
        RawDepthCoverageSnapshot(
            decodedPairs = decodedPairs,
            sampledPixels = sampledPixels,
            validDepthPixels = validDepthPixels,
            confidentPixels = confidentPixels,
            pairsWithConfidentPixels = pairsWithConfidentPixels,
            validFraction = if (observed) validDepthPixels.toDouble() / sampled.toDouble() else null,
            confidentFraction = if (observed) confidentPixels.toDouble() / sampled.toDouble() else null,
            capacityReached = decodedPairs >= maxPairs.toLong(),
        )
    }

    fun stop() = synchronized(lock) {
        stopped = true
    }

    private fun isValidCounts(summary: RawDepthSummary): Boolean {
        val sampled = summary.sampledPixels
        val valid = summary.validDepthPixels
        val confident = summary.confidentPixels
        if (sampled !in 1..MAX_SAMPLED_PIXELS) {
            return false
        }
        if (valid < 0 || confident < 0) {
            return false
        }
        if (confident > valid || valid > sampled) {
            return false
        }
        return true
    }

    companion object {
        const val DEFAULT_MAX_PAIRS: Int = 10_000
        const val MAX_SAMPLED_PIXELS: Int = 1_048_576
    }
}
