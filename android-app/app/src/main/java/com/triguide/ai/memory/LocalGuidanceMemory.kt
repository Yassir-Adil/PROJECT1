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

class LocalGuidanceMemory internal constructor(
    private val directory: File,
    private val scheduleWrite: (() -> Unit) -> Unit,
    private val beforePersist: () -> Unit = {},
) {
    constructor(directory: File) : this(directory, { write -> write() })

    private val objectsFile = File(directory, "objects.tsv")
    private val sessionFile = File(directory, "session.txt")
    private val objectsStore = BoundedAtomicTextFile(objectsFile)
    private val sessionStore = BoundedAtomicTextFile(sessionFile)
    private val lock = Any()
    private val diskLock = Any()
    /** Insertion / file chronology order; elapsed realtime is metadata only. */
    private val notes = LinkedHashMap<String, SeenObjectNote>()
    private var lastSignature = ""
    private var lastNoteAtMs = Long.MIN_VALUE
    private var sessionId = ""
    private var sessionStartedMs = 0L
    private var sessionSummary = ""
    private var objectStateChanged = false
    private data class DiskSnapshot(val objects: String?, val session: String)
    @Volatile private var latestSnapshot: DiskSnapshot? = null
    // Accessed under diskLock only. A failed write must remain retryable.
    private var lastPersistedSnapshot: DiskSnapshot? = null

    init {
        ensureDirectory()
        load()
        startSession(0L)
    }

    fun startSession(nowElapsedMs: Long) {
        synchronized(lock) {
            sessionId = "session-$nowElapsedMs"
            sessionStartedMs = nowElapsedMs
            sessionSummary = ""
            updateSnapshotLocked()
        }
        schedulePersistence()
    }

    fun note(cues: List<DirectionalCue>, nowElapsedMs: Long) {
        val changed = try {
            synchronized(lock) { noteLocked(cues, nowElapsedMs) }
        } catch (_: Exception) {
            // A notes failure must not stop the guidance loop.
            false
        }
        if (changed) schedulePersistence()
    }

    private fun noteLocked(cues: List<DirectionalCue>, nowElapsedMs: Long): Boolean {
        val placed = cues.mapNotNull { cue -> cueToNote(cue, nowElapsedMs) }
        if (placed.isEmpty()) return false
        val signature = placed.joinToString("|") { note -> "${note.label}@${note.zone}" }
        if (signature == lastSignature && nowElapsedMs - lastNoteAtMs < NOTE_GAP_MS) {
            return false
        }
        placed.forEach { incoming ->
            val key = incoming.label.lowercase()
            val previous = notes.remove(key)
            val nextCount = when {
                previous == null -> 1
                previous.seenCount >= Int.MAX_VALUE -> Int.MAX_VALUE
                else -> previous.seenCount + 1
            }
            notes[key] = incoming.copy(seenCount = nextCount)
        }
        trimToCap()
        lastSignature = signature
        lastNoteAtMs = nowElapsedMs
        objectStateChanged = true
        updateSnapshotLocked()
        return true
    }

    fun noteSummary(summary: String, nowElapsedMs: Long) {
        val changed = synchronized(lock) {
            val trimmed = summary.replace("\n", " ").take(MAX_SUMMARY)
            if (trimmed == sessionSummary) return@synchronized false
            if (sessionId.isEmpty()) {
                sessionId = "session-$nowElapsedMs"
                sessionStartedMs = nowElapsedMs
            }
            sessionSummary = trimmed
            updateSnapshotLocked()
            true
        }
        if (changed) schedulePersistence()
    }

    fun recent(): List<SeenObjectNote> = synchronized(lock) {
        // Newest insertion / file chronology first. Do not sort by elapsedRealtime.
        notes.values.reversed()
    }

    fun summaryLine(): String = synchronized(lock) {
        "Object notes on this phone: ${notes.size} labels."
    }

    /** Legacy API; a UI claiming durable deletion must use [clearPersisted]. */
    fun clear() {
        clearPersisted()
    }

    /** Explicit durable action, unlike best-effort note writes; may wait for disk. */
    fun clearPersisted(): Boolean = synchronized(diskLock) {
        synchronized(lock) {
            if (!ensureDirectory() || !objectsStore.delete()) return@synchronized false
            val cleared = DiskSnapshot("", sessionTextLocked(""))
            if (!sessionStore.write(cleared.session)) return@synchronized false
            notes.clear()
            lastSignature = ""
            lastNoteAtMs = Long.MIN_VALUE
            sessionSummary = ""
            objectStateChanged = false
            latestSnapshot = cleared
            lastPersistedSnapshot = cleared
            true
        }
    }

    private fun cueToNote(cue: DirectionalCue, nowElapsedMs: Long): SeenObjectNote? {
        val label = sanitizeLabel(cue.label) ?: return null
        if (!cue.confidence.isFinite() || cue.confidence < 0f || cue.confidence > 1f) return null
        if (!cue.centerXNormalized.isFinite() || cue.centerXNormalized !in 0f..1f) return null
        if (nowElapsedMs < 0L) return null
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
        val body = objectsStore.read() ?: return
        body.lineSequence().forEach { line ->
            if (line.isEmpty()) return@forEach
            val parts = line.split('\t')
            if (parts.size != 5) return@forEach
            val label = sanitizeLabel(parts[0]) ?: return@forEach
            val zone = parts[1]
            if (zone !in VALID_ZONES) return@forEach
            val confidence = parts[2].toFloatOrNull() ?: return@forEach
            if (!confidence.isFinite() || confidence < 0f || confidence > 1f) return@forEach
            val seenAt = parts[3].toLongOrNull() ?: return@forEach
            if (seenAt < 0L) return@forEach
            val count = parts[4].toIntOrNull() ?: return@forEach
            if (count < 1) return@forEach
            val key = label.lowercase()
            notes.remove(key)
            notes[key] = SeenObjectNote(label, zone, confidence, seenAt, count)
        }
        trimToCap()
    }

    private fun trimToCap() {
        while (notes.size > MAX_LABELS) {
            val oldest = notes.keys.firstOrNull() ?: break
            notes.remove(oldest)
        }
    }

    /** Called only under the short RAM lock; encodes both stores as one state. */
    private fun updateSnapshotLocked() {
        val objects = notes.values.joinToString("\n") { note ->
            listOf(
                note.label.replace('\t', ' '),
                note.zone,
                note.confidence.toString(),
                note.lastSeenElapsedMs.toString(),
                note.seenCount.toString(),
            ).joinToString("\t")
        }
        // Session startup/summary must not overwrite a rejected legacy objects file.
        latestSnapshot = DiskSnapshot(if (objectStateChanged) objects else null, sessionTextLocked(sessionSummary))
    }

    private fun sessionTextLocked(summary: String) =
        "id=$sessionId\nstarted=$sessionStartedMs\nsummary=$summary\n"

    private fun schedulePersistence() {
        try {
            // Only disk work is conflated. RAM mutations above always happen immediately.
            // Read the latest immutable snapshot when executed, not when enqueued.
            scheduleWrite { persistLatest() }
        } catch (_: Exception) {
            // Best-effort notes remain usable in memory even if scheduling fails.
        }
    }

    private fun persistLatest() = synchronized(diskLock) {
        try {
            val snapshot = latestSnapshot ?: return@synchronized
            if (snapshot === lastPersistedSnapshot) return@synchronized
            beforePersist()
            if (!ensureDirectory()) return@synchronized
            val objectsSaved = snapshot.objects?.let { objectsStore.write(it) } ?: true
            val sessionSaved = sessionStore.write(snapshot.session)
            if (objectsSaved && sessionSaved) lastPersistedSnapshot = snapshot
        } catch (_: Exception) {
            // Independent files, not a cross-file transaction or a durable-save claim.
        }
    }

    private fun ensureDirectory(): Boolean {
        return try {
            when {
                directory.isDirectory -> true
                directory.exists() -> false
                else -> directory.mkdirs() || directory.isDirectory
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val NOTE_GAP_MS = 1_000L
        const val MAX_LABELS = 40
        const val MAX_SUMMARY = 180
        val VALID_ZONES = setOf("left", "center", "right")

        fun sanitizeLabel(raw: String): String? {
            val label = raw.trim()
            if (label.isEmpty()) return null
            if (label.any { it.isISOControl() }) return null
            return label
        }
    }
}
