package com.triguide.ai.domain

/**
 * Counts quick taps for the Live Vision shortcut.
 *
 * A tap completes the gesture when it is the third tap and each tap arrived
 * within [MAX_GAP_MS] of the previous one. A longer gap starts a new group.
 * The detector does not know about scrolling or buttons. The caller sends only taps.
 */
class TripleTapDetector(
    private val maxGapMs: Long = MAX_GAP_MS,
    private val requiredTaps: Int = REQUIRED_TAPS,
) {
    private val times = ArrayDeque<Long>()

    fun onTap(timeMs: Long): TapMark {
        val previous = times.lastOrNull()
        if (previous != null && (timeMs < previous || timeMs - previous > maxGapMs)) {
            times.clear()
        }
        times.addLast(timeMs)
        if (times.size >= requiredTaps) {
            times.clear()
            return TapMark(completed = true, countInGroup = requiredTaps)
        }
        return TapMark(completed = false, countInGroup = times.size)
    }

    fun reset() {
        times.clear()
    }

    companion object {
        /** Upper end of the 600–800 ms quick-tap window, measured between taps. */
        const val MAX_GAP_MS = 750L
        const val REQUIRED_TAPS = 3
    }
}

data class TapMark(
    val completed: Boolean,
    val countInGroup: Int,
)
