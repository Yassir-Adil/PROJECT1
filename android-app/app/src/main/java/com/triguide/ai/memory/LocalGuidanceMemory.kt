package com.triguide.ai.memory

import com.triguide.ai.domain.DirectionalCue
import com.triguide.ai.domain.NavigationConfig
import java.io.File

/**
 * Local notes about labels the detector has already reported.
 * Stored in app-private files. Nothing here is uploaded.
 * These notes never steer the robot and never fill in a missing box.
 */
data class SeenObjectNote(
    val label: String,
    val zone: String,
    val confidence: Float,
    val lastSeenElapsedMs: Long,
    val seenCount: Int,
)

class LocalGuidanceMemory(
    private val directory: File,
) {
    private val objectsFile = File(directory, "objects.tsv")
    private val sessionFile = File(directory, "session.txt")
    private val lock = Any()
    private val notes = LinkedHashMap<String, SeenObjectNote>()
    private var lastSignature = ""
    private var lastNoteAtMs = Long.MIN_VALUE
    private var sessionId = ""
    private var sessionStartedMs = 0L
    private var sessionSummary = ""

    init {
        directory.mkdirs()
        load()
        startSession(0L)
    }

    fun startSession(nowElapsedMs: Long) = synchronized(lock) {
        sessionId = "session-$nowElapsedMs"
        sessionStartedMs = nowElapsedMs
        sessionSummary = ""
        writeSession()
    }

    fun note(cues: List<DirectionalCue>, nowElapsedMs: Long) = synchronized(lock) {
        try {
            noteLocked(cues, nowElapsedMs)
        } catch (_: Exception) {
            // A notes failure must not stop the guidance loop.
        }
    }

    private fun noteLocked(cues: List<DirectionalCue>, nowElapsedMs: Long) {
        val placed = cues.mapNotNull { cue -> cueToNote(cue, nowElapsedMs) }
        if (placed.isEmpty()) return
        val signature = placed.joinToString("|") { note -> "${note.label}@${note.zone}" }
        if (signature == lastSignature && nowElapsedMs - lastNoteAtMs < NOTE_GAP_MS) {
            return
        }
        placed.forEach { incoming ->
            val key = incoming.label.lowercase()
            val previous = notes[key]
            notes[key] = if (previous == null) {
                incoming.copy(seenCount = 1)
            } else {
                incoming.copy(seenCount = previous.seenCount + 1)
            }
        }
        while (notes.size > MAX_LABELS) {
            val oldest = notes.entries.minByOrNull { it.value.lastSeenElapsedMs }?.key ?: break
            notes.remove(oldest)
        }
        lastSignature = signature
        lastNoteAtMs = nowElapsedMs
        writeObjects()
    }

    fun noteSummary(summary: String, nowElapsedMs: Long) = synchronized(lock) {
        val trimmed = summary.replace("\n", " ").take(MAX_SUMMARY)
        if (trimmed == sessionSummary) return@synchronized
        sessionSummary = trimmed
        if (sessionId.isEmpty()) {
            startSession(nowElapsedMs)
        }
        writeSession()
    }

    fun recent(): List<SeenObjectNote> = synchronized(lock) {
        notes.values.sortedByDescending { it.lastSeenElapsedMs }
    }

    fun summaryLine(): String = synchronized(lock) {
        "Object notes on this phone: ${notes.size} labels."
    }

    fun clear() = synchronized(lock) {
        notes.clear()
        lastSignature = ""
        objectsFile.delete()
        sessionSummary = ""
        writeSession()
    }

    private fun cueToNote(cue: DirectionalCue, nowElapsedMs: Long): SeenObjectNote? {
        val label = cue.label.trim()
        if (label.isEmpty()) return null
        if (!cue.confidence.isFinite() || cue.confidence < 0f || cue.confidence > 1f) return null
        if (!cue.centerXNormalized.isFinite() || cue.centerXNormalized !in 0f..1f) return null
        val zone = when {
            cue.centerXNormalized < NavigationConfig.CENTER_START -> "left"
            cue.centerXNormalized > NavigationConfig.CENTER_END -> "right"
            else -> "center"
        }
        return SeenObjectNote(
            label = label,
            zone = zone,
            confidence = cue.confidence,
            lastSeenElapsedMs = nowElapsedMs,
            seenCount = 1,
        )
    }

    private fun load() {
        if (!objectsFile.isFile) return
        objectsFile.readLines().forEach { line ->
            val parts = line.split('\t')
            if (parts.size != 5) return@forEach
            val confidence = parts[2].toFloatOrNull() ?: return@forEach
            val seenAt = parts[3].toLongOrNull() ?: return@forEach
            val count = parts[4].toIntOrNull() ?: return@forEach
            val label = parts[0].trim()
            if (label.isEmpty()) return@forEach
            notes[label.lowercase()] = SeenObjectNote(label, parts[1], confidence, seenAt, count)
        }
    }

    private fun writeObjects() {
        directory.mkdirs()
        val body = notes.values.joinToString("\n") { note ->
            listOf(
                note.label.replace('\t', ' '),
                note.zone,
                note.confidence.toString(),
                note.lastSeenElapsedMs.toString(),
                note.seenCount.toString(),
            ).joinToString("\t")
        }
        objectsFile.writeText(body)
    }

    private fun writeSession() {
        directory.mkdirs()
        sessionFile.writeText(
            "id=$sessionId\nstarted=$sessionStartedMs\nsummary=$sessionSummary\n",
        )
    }

    private companion object {
        const val NOTE_GAP_MS = 1_000L
        const val MAX_LABELS = 40
        const val MAX_SUMMARY = 180
    }
}
