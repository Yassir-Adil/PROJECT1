package com.triguide.ai.domain

import java.util.ArrayDeque
import java.util.Locale

/**
 * Ring buffer of navigation decision evidence for diagnostics and QA.
 * Text only — does not store camera frames or person images.
 */
data class DecisionEvidenceEvent(
    val elapsedMs: Long,
    val action: GuidanceAction,
    val pathRisk: PathRiskLevel,
    val reason: String,
    val diagnostic: String,
    val perceptionLabel: String,
    val subjectLabel: String,
    val subjectSide: String,
    val confidence: Float,
)

class DecisionEvidenceRecorder(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val events = ArrayDeque<DecisionEvidenceEvent>(capacity.coerceAtLeast(1))

    fun record(decision: NavigationDecision, elapsedMs: Long) {
        synchronized(events) {
            if (events.size >= capacity) events.removeFirst()
            events.addLast(
                DecisionEvidenceEvent(
                    elapsedMs = elapsedMs,
                    action = decision.action,
                    pathRisk = decision.pathRisk,
                    reason = decision.reason,
                    diagnostic = decision.diagnostic,
                    perceptionLabel = decision.perceptionLabel,
                    subjectLabel = decision.subjectLabel,
                    subjectSide = decision.subjectSide,
                    confidence = decision.confidence,
                ),
            )
        }
    }

    fun snapshot(): List<DecisionEvidenceEvent> = synchronized(events) { events.toList() }

    fun clear() = synchronized(events) { events.clear() }

    fun formatLatest(limit: Int = 8): String {
        val rows = snapshot().takeLast(limit.coerceAtLeast(1))
        if (rows.isEmpty()) return "No decision evidence recorded yet."
        return buildString {
            appendLine("AI Evidence Recorder (text only; no frames)")
            rows.asReversed().forEach { e ->
                appendLine("---")
                appendLine("t_ms=${e.elapsedMs}")
                appendLine("action=${e.action.userLabel} risk=${e.pathRisk}")
                appendLine("source=${e.perceptionLabel}")
                if (e.subjectLabel.isNotBlank()) {
                    appendLine(
                        "subject=${e.subjectLabel}" +
                            if (e.subjectSide.isNotBlank()) " side=${e.subjectSide}" else "",
                    )
                }
                appendLine("confidence=${String.format(Locale.US, "%.2f", e.confidence)}")
                appendLine("reason=${e.reason}")
                appendLine("diag=${e.diagnostic}")
            }
            append("Not a safety proof. Corridor blockers matter more than label count.")
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 40
    }
}
