package com.triguide.ai.diagnostics

import java.util.ArrayDeque
import kotlin.math.ceil

internal data class SemanticDisplayTraceSnapshot(
    val uiEntries: Int, val uiRejected: Int, val uiReasons: Map<SemanticDisplayDecision, Int>,
    val viewEntries: Int, val viewRejected: Int, val viewReasons: Map<SemanticDisplayDecision, Int>,
    val drawEntries: Int, val drawRejected: Int, val drawReasons: Map<SemanticDisplayDecision, Int>,
    val timingSamples: Int, val uiAgeP50Ms: Double?, val uiAgeP90Ms: Double?,
    val queueP50Ms: Double?, val queueP90Ms: Double?, val minimumUiBudgetMs: Double?,
) {
    fun logFields(): String {
        fun reasons(values: Map<SemanticDisplayDecision, Int>) = values.entries
            .joinToString(",") { "${it.key.name.lowercase()}:${it.value}" }.ifEmpty { "none" }
        return "ui_entries=$uiEntries ui_rejected=$uiRejected ui_reasons=${reasons(uiReasons)} " +
            "view_entries=$viewEntries view_rejected=$viewRejected view_reasons=${reasons(viewReasons)} " +
            "draw_entries=$drawEntries draw_rejected=$drawRejected draw_reasons=${reasons(drawReasons)} " +
            "ui_timing_samples=$timingSamples timing_window=last_60 ui_timing_includes_rejected=true " +
            "ui_receipt_age_p50_ms=${uiAgeP50Ms ?: "unavailable"} ui_receipt_age_p90_ms=${uiAgeP90Ms ?: "unavailable"} " +
            "worker_to_ui_p50_ms=${queueP50Ms ?: "unavailable"} worker_to_ui_p90_ms=${queueP90Ms ?: "unavailable"} " +
            "minimum_ui_budget_ms=${minimumUiBudgetMs ?: "unavailable"} minimum_ui_budget_scope=session " +
            "scope=stage_entries_not_paint navigation=none human_visibility=unverified"
    }
}

/** Bounded stage diagnostics; never authorizes or extends a publication. No sensor-clock arithmetic. */
internal class SemanticDisplayTrace {
    private var stopped = false
    private val ui = linkedMapOf<SemanticDisplayDecision, Int>()
    private val view = linkedMapOf<SemanticDisplayDecision, Int>()
    private val draw = linkedMapOf<SemanticDisplayDecision, Int>()
    private val ages = ArrayDeque<Double>()
    private val queues = ArrayDeque<Double>()
    private var minimumUiBudgetMs: Double? = null

    @Synchronized fun ui(receivedNs: Long, completedNs: Long, nowNs: Long, decision: SemanticDisplayDecision) {
        if (stopped) return
        count(ui, decision)
        if (receivedNs < 0 || completedNs < receivedNs || nowNs < completedNs) return
        val age = (nowNs - receivedNs) / 1_000_000.0
        val queue = (nowNs - completedNs) / 1_000_000.0
        if (ages.size == CAPACITY) { ages.removeFirst(); queues.removeFirst() }
        ages.addLast(age); queues.addLast(queue)
        val budget = MAX_AGE_MS - age
        minimumUiBudgetMs = minimumUiBudgetMs?.let { minOf(it, budget) } ?: budget
    }

    @Synchronized fun view(decision: SemanticDisplayDecision) { if (!stopped) count(view, decision) }
    @Synchronized fun draw(decision: SemanticDisplayDecision) { if (!stopped) count(draw, decision) }
    @Synchronized fun stop() { stopped = true }

    @Synchronized fun snapshot(): SemanticDisplayTraceSnapshot {
        fun entries(values: Map<SemanticDisplayDecision, Int>) = values.values.sum()
        fun rejected(values: Map<SemanticDisplayDecision, Int>) = entries(values) - (values[SemanticDisplayDecision.ALLOWED] ?: 0)
        fun reasons(values: Map<SemanticDisplayDecision, Int>) = values.filterKeys { it != SemanticDisplayDecision.ALLOWED }
        fun p(values: ArrayDeque<Double>, q: Double): Double? {
            val sorted = values.sorted()
            return if (sorted.isEmpty()) null else sorted[(ceil(sorted.size*q).toInt()-1).coerceAtLeast(0)]
        }
        return SemanticDisplayTraceSnapshot(entries(ui), rejected(ui), reasons(ui), entries(view), rejected(view),
            reasons(view), entries(draw), rejected(draw), reasons(draw), ages.size,
            p(ages,.5), p(ages,.9), p(queues,.5), p(queues,.9), minimumUiBudgetMs)
    }

    private fun count(values: MutableMap<SemanticDisplayDecision, Int>, decision: SemanticDisplayDecision) {
        values[decision] = (values[decision] ?: 0) + 1
    }

    companion object { private const val CAPACITY = 60; private const val MAX_AGE_MS = 250.0 }
}
