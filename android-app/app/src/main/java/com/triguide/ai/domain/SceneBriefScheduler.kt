package com.triguide.ai.domain

/**
 * Speaks a short box report when several objects are in frame and navigation did not just speak.
 * One object is already named by the turn or hold cue. An empty frame is not repeated here.
 */
class SceneBriefScheduler(
    private val minGapMs: Long = MIN_GAP_MS,
    private val repeatMs: Long = REPEAT_MS,
) {
    private var lastKey: String? = null
    private var lastAtMs: Long? = null

    fun nextCue(
        cues: List<DirectionalCue>,
        nowElapsedMs: Long,
        navigationSpokeThisTick: Boolean,
    ): VoiceCue? {
        if (navigationSpokeThisTick) return null
        val line = SceneDescription.briefFromCues(cues)
        val placed = signature(cues)
        // Silence-by-default: only a dense scene gets a rare brief, never every frame.
        if (placed.count < 3) return null
        val since = lastAtMs?.let { previous -> nowElapsedMs - previous }
        val dueRepeat = lastKey == placed.key && since != null && since >= repeatMs
        val changed = placed.key != lastKey
        if (!changed && !dueRepeat) return null
        if (since != null && since < minGapMs) return null
        lastKey = placed.key
        lastAtMs = nowElapsedMs
        return VoiceCues.sceneBrief(line.english, line.arabic)
    }

    private fun signature(cues: List<DirectionalCue>): Signature {
        val parts = cues.mapNotNull { cue ->
            if (!cue.confidence.isFinite() || cue.confidence < DetectorConfig.SCORE_THRESHOLD) return@mapNotNull null
            if (cue.centerXNormalized !in 0f..1f || cue.areaFraction <= 0f) return@mapNotNull null
            val zone = when {
                cue.centerXNormalized < NavigationConfig.CENTER_START -> "left"
                cue.centerXNormalized > NavigationConfig.CENTER_END -> "right"
                else -> "center"
            }
            "${cue.label.trim().lowercase()}@$zone"
        }.sorted()
        return Signature(parts.size, parts.joinToString("|"))
    }

    private data class Signature(val count: Int, val key: String)

    private companion object {
        const val MIN_GAP_MS = 12_000L
        const val REPEAT_MS = 30_000L
    }
}
