package com.triguide.ai.domain

/**
 * Keeps a lateral decision from swapping sides on a single noisy frame.
 * Hold and Unknown apply immediately and clear the pending vote.
 * A new direction must repeat for [confirmFrames] samples.
 * A left/right reversal must repeat for [flipFrames] samples.
 */
class TemporalDecisionSmoother(
    private val policy: NavigationDecisionPolicy = NavigationDecisionEngine(),
    private val confirmFrames: Int = NavigationConfig.CONFIRM_FRAMES,
    private val flipFrames: Int = NavigationConfig.FLIP_FRAMES,
) {
    init {
        require(confirmFrames > 0) { "confirmFrames must be positive" }
        require(flipFrames > 0) { "flipFrames must be positive" }
    }

    private var committed: NavigationDecision? = null
    private var pending: GuidanceAction? = null
    private var pendingCount: Int = 0

    fun reset() {
        committed = null
        pending = null
        pendingCount = 0
    }

    fun update(observation: SceneObservation): NavigationDecision {
        val raw = policy.decide(observation)
        if (raw.action == GuidanceAction.HOLD || raw.action == GuidanceAction.UNKNOWN) {
            clearPending()
            committed = raw
            return raw
        }
        val current = committed
        if (current == null || current.action == GuidanceAction.HOLD || current.action == GuidanceAction.UNKNOWN) {
            return vote(raw, confirmFrames, holding = null)
        }
        if (current.action == raw.action) {
            clearPending()
            committed = raw
            return raw
        }
        val needed = if (NavigationConfig.oppositeFamily(current.action, raw.action)) {
            flipFrames
        } else {
            confirmFrames
        }
        return vote(raw, needed, holding = current)
    }

    private fun vote(
        raw: NavigationDecision,
        needed: Int,
        holding: NavigationDecision?,
    ): NavigationDecision {
        if (pending != raw.action) {
            pending = raw.action
            pendingCount = 1
        } else {
            pendingCount += 1
        }
        if (pendingCount >= needed) {
            clearPending()
            committed = raw
            return raw
        }
        if (holding == null) {
            val limit = if (raw.metricDepthUsed) {
                "The synthetic map has not been stable long enough to steer."
            } else {
                "This is not a confirmed passage. Depth is not measured."
            }
            return NavigationDecisions.fromAction(
                action = GuidanceAction.HOLD,
                reason = "Saw ${raw.action.userLabel} for $pendingCount of $needed frames. " +
                    "Holding until the direction stays. $limit",
                confidence = 0.25f,
                basedOnRealVision = raw.basedOnRealVision,
                metricDepthUsed = false,
                perceptionLabel = raw.perceptionLabel,
            )
        }
        return holding.withNarration(
            reason = "${holding.reason} A ${raw.action.userLabel} reading is at $pendingCount of $needed frames, " +
                "so the wheel stays on ${holding.action.userLabel}.",
            confidence = holding.confidence.coerceAtMost(0.40f).coerceAtLeast(0.20f),
        )
    }

    private fun clearPending() {
        pending = null
        pendingCount = 0
    }
}
