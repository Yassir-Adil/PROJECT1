package com.triguide.ai.diagnostics

/** Lifetime of a SYNTHETIC pattern only. Not a live evidence receipt or a navigation contract. */
internal class SemanticRenderSession(private val startedNs: Long, durationSeconds: Int) {
    private val durationNs: Long
    private var stopped = false
    private var lastObservedNs = startedNs
    init {
        require(startedNs >= 0)
        require(durationSeconds in 3..15)
        durationNs = durationSeconds * 1_000_000_000L
    }
    fun allowDraw(nowNs: Long): Boolean {
        if (stopped || nowNs < lastObservedNs) return false
        lastObservedNs = nowNs
        if (nowNs - startedNs > durationNs) { stopped = true; return false }
        return true
    }
    fun stop() { stopped = true }
}
