package com.triguide.ai.domain

/**
 * Counts consecutive same-direction swipes for eyes-free Live Vision control.
 * Three matching swipes within [MAX_GAP_MS] fire one [LiveVisionGesture].
 * TalkBack users should also get CustomAccessibilityAction equivalents in the UI.
 */
enum class LiveVisionSwipeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

enum class LiveVisionGesture {
    /** Three swipes up: on-demand scene describe. */
    DESCRIBE,

    /** Three swipes down: open More (settings / diagnostics / simulator). */
    OPEN_MORE,

    /** Three swipes right: speak the current status line only. */
    SPEAK_STATUS,

    /** Three swipes left: restart camera guidance. */
    RESTART_GUIDANCE,
}

data class LiveVisionSwipeMark(
    val countInGroup: Int,
    val completed: Boolean,
    val gesture: LiveVisionGesture?,
)

class LiveVisionGestureDetector(
    private val requiredSwipes: Int = REQUIRED_SWIPES,
    private val maxGapMs: Long = MAX_GAP_MS,
) {
    private var lastDirection: LiveVisionSwipeDirection? = null
    private var lastAtMs: Long = Long.MIN_VALUE / 2
    private var count: Int = 0

    fun onSwipe(direction: LiveVisionSwipeDirection, nowElapsedMs: Long): LiveVisionSwipeMark {
        val same = lastDirection == direction && nowElapsedMs - lastAtMs <= maxGapMs
        if (same) {
            count += 1
        } else {
            lastDirection = direction
            count = 1
        }
        lastAtMs = nowElapsedMs
        val done = count >= requiredSwipes
        val gesture = if (done) {
            count = 0
            lastDirection = null
            gestureFor(direction)
        } else {
            null
        }
        return LiveVisionSwipeMark(
            countInGroup = if (done) requiredSwipes else count,
            completed = done,
            gesture = gesture,
        )
    }

    fun reset() {
        lastDirection = null
        lastAtMs = Long.MIN_VALUE / 2
        count = 0
    }

    private fun gestureFor(direction: LiveVisionSwipeDirection): LiveVisionGesture {
        return when (direction) {
            LiveVisionSwipeDirection.UP -> LiveVisionGesture.DESCRIBE
            LiveVisionSwipeDirection.DOWN -> LiveVisionGesture.OPEN_MORE
            LiveVisionSwipeDirection.RIGHT -> LiveVisionGesture.SPEAK_STATUS
            LiveVisionSwipeDirection.LEFT -> LiveVisionGesture.RESTART_GUIDANCE
        }
    }

    companion object {
        const val REQUIRED_SWIPES = 3
        const val MAX_GAP_MS = 700L
        /** Minimum drag distance in px; UI converts from density. */
        const val MIN_SWIPE_DP = 56f
    }
}
