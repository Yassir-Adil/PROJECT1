package com.triguide.ai.domain

/**
 * Plain-text status dump for the Diagnostics screen.
 * It records what the app is doing. It is not a safety proof.
 */
data class DiagnosticsSnapshot(
    val versionName: String,
    val cameraPermission: String,
    val detection: String,
    val decision: String,
    val reason: String,
    val confidence: String,
    val wheelDegrees: String,
    val source: String,
    val floor: String,
    val voice: String,
    val lastCue: String,
    val performance: String,
    val hardware: String,
    val shortcut: String,
)

object DiagnosticsReport {
    fun text(snapshot: DiagnosticsSnapshot): String {
        return buildString {
            appendLine("TriGuide diagnostics")
            appendLine("Version: ${snapshot.versionName}")
            appendLine("Camera permission: ${snapshot.cameraPermission}")
            appendLine("Detection: ${snapshot.detection}")
            appendLine("Decision: ${snapshot.decision}")
            appendLine("Reason: ${snapshot.reason}")
            appendLine("Confidence: ${snapshot.confidence}")
            appendLine("Front wheel degrees: ${snapshot.wheelDegrees}")
            appendLine("Source: ${snapshot.source}")
            appendLine("Floor segmentation: ${snapshot.floor}")
            appendLine("Voice: ${snapshot.voice}")
            appendLine("Last voice cue: ${snapshot.lastCue}")
            appendLine("Performance: ${snapshot.performance}")
            appendLine("Hardware: ${snapshot.hardware}")
            appendLine("Shortcut: ${snapshot.shortcut}")
            append("This report is a status dump. It is not a safety proof.")
        }
    }
}
