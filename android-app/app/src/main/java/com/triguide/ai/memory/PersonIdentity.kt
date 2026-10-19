package com.triguide.ai.memory

import com.triguide.ai.domain.SpokenLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Opt-in names for people the user has confirmed.
 * The camera loop never calls this. No face crop and no embedding are written.
 * A name is stored only after [confirmPending]. Strangers are not enrolled.
 */
data class ConsentedPerson(
    val name: String,
    val consentedAtElapsedMs: Long,
    val faceStored: Boolean,
)

data class PersonConsentSnapshot(
    val featureEnabled: Boolean,
    val pendingName: String?,
    val people: List<ConsentedPerson>,
    val statusLine: String,
)

class PersonConsentStore(
    private val directory: File,
) {
    private val featureFile = File(directory, "person-feature.txt")
    private val peopleFile = File(directory, "consented-people.tsv")
    private val featureStore = BoundedAtomicTextFile(featureFile)
    private val peopleStore = BoundedAtomicTextFile(peopleFile)
    private val lock = Any()
    private var featureEnabled = false
    private var pendingName: String? = null
    /** Insertion / file chronology order; elapsed realtime is metadata only. */
    private val people = LinkedHashMap<String, ConsentedPerson>()
    private val snapshotState = MutableStateFlow(currentSnapshot())
    val snapshot: StateFlow<PersonConsentSnapshot> = snapshotState.asStateFlow()

    init {
        ensureDirectory()
        load()
        publish()
    }

    fun requestSave(spokenName: String, nowElapsedMs: Long): SpokenLine = synchronized(lock) {
        val name = cleanName(spokenName)
        if (!featureEnabled) {
            pendingName = null
            publish()
            return@synchronized disabledLine()
        }
        if (name == null) {
            return@synchronized SpokenLine(
                id = "remember-unnamed",
                english = "Say a name first. Nothing was saved.",
                arabic = "قل الاسم أولاً. لم يُحفظ شيء.",
            )
        }
        pendingName = name
        publish()
        SpokenLine(
            id = "remember-pending",
            english = "Confirm on screen to save this person as $name. No photo is stored. This save is a name only.",
            arabic = "أكّد على الشاشة حفظ هذا الشخص باسم $name. لا تُحفظ أي صورة. هذا الحفظ اسم فقط.",
        )
    }

    fun handleUtterance(utterance: String, nowElapsedMs: Long): SpokenLine {
        val name = RememberPersonCommand.parse(utterance)
        return if (name == null) {
            SpokenLine(
                id = "remember-unparsed",
                english = "That was not a request to remember a person. Nothing was saved.",
                arabic = "هذا ليس طلباً لتذكر شخص. لم يُحفظ شيء.",
            )
        } else {
            requestSave(name, nowElapsedMs)
        }
    }

    fun confirmPending(nowElapsedMs: Long): SpokenLine = synchronized(lock) {
        val name = pendingName
        if (!featureEnabled || name == null) {
            pendingName = null
            publish()
            return@synchronized SpokenLine(
                id = "remember-nothing-pending",
                english = "There is no person waiting to be saved.",
                arabic = "لا يوجد شخص بانتظار الحفظ.",
            )
        }
        if (nowElapsedMs < 0L) {
            pendingName = null
            publish()
            return@synchronized saveFailedLine()
        }
        val backup = LinkedHashMap(people)
        val priorPending = pendingName
        val key = name.lowercase()
        people.remove(key)
        people[key] = ConsentedPerson(
            name = name,
            consentedAtElapsedMs = nowElapsedMs,
            faceStored = false,
        )
        trimToCap()
        pendingName = null
        if (!writePeople()) {
            people.clear()
            people.putAll(backup)
            pendingName = priorPending
            publish()
            return@synchronized saveFailedLine()
        }
        publish()
        SpokenLine(
            id = "remember-confirmed",
            english = "$name is saved on this phone as a name only. No photo was stored.",
            arabic = "تم حفظ $name في هذا الهاتف كاسم فقط. لم تُحفظ صورة.",
        )
    }

    fun cancelPending(): SpokenLine = synchronized(lock) {
        pendingName = null
        publish()
        SpokenLine(
            id = "remember-cancelled",
            english = "The save was cancelled. Nothing new was stored.",
            arabic = "أُلغي الحفظ. لم يُخزن شيء جديد.",
        )
    }

    fun setFeatureEnabled(enabled: Boolean): Unit = synchronized(lock) {
        if (enabled) {
            // Durable enable only: a failed write must leave consent disabled in memory.
            if (!writeFeature(true)) {
                featureEnabled = false
                pendingName = null
                publish()
                return@synchronized
            }
            featureEnabled = true
            publish()
            return@synchronized
        }
        // Disabling updates memory and clears pending immediately even if disk fails.
        featureEnabled = false
        pendingName = null
        publish()
        writeFeature(false)
    }

    fun delete(name: String): Boolean = synchronized(lock) {
        val key = cleanName(name)?.lowercase() ?: return@synchronized false
        if (!people.containsKey(key)) return@synchronized false
        val backup = LinkedHashMap(people)
        people.remove(key)
        if (!writePeople()) {
            people.clear()
            people.putAll(backup)
            publish()
            return@synchronized false
        }
        publish()
        true
    }

    fun list(): List<ConsentedPerson> = synchronized(lock) {
        people.values.sortedBy { it.name.lowercase() }
    }

    /**
     * This build has no face model. It never matches a camera frame to a saved name.
     */
    fun recognize(): ConsentedPerson? = null

    /** Refuses every crop. A photo is not written. */
    fun storeFace(name: String, jpeg: ByteArray): Boolean {
        if (name.isEmpty() && jpeg.isEmpty()) return false
        return false
    }

    fun statusLine(): String = snapshotState.value.statusLine

    private fun load() {
        featureEnabled = parseFeatureEnabled(featureStore.read())
        val body = peopleStore.read() ?: return
        body.lineSequence().forEach { line ->
            if (line.isEmpty()) return@forEach
            val parts = line.split('\t')
            if (parts.size != 3) return@forEach
            val name = cleanName(parts[0]) ?: return@forEach
            val at = parts[1].toLongOrNull() ?: return@forEach
            if (at < 0L) return@forEach
            // Third column is legacy face flag; this build never stores faces.
            people.remove(name.lowercase())
            people[name.lowercase()] = ConsentedPerson(
                name = name,
                consentedAtElapsedMs = at,
                faceStored = false,
            )
        }
        trimToCap()
    }

    private fun trimToCap() {
        while (people.size > MAX_PEOPLE) {
            val oldest = people.keys.firstOrNull() ?: break
            people.remove(oldest)
        }
    }

    private fun writePeople(): Boolean {
        if (!ensureDirectory()) return false
        val body = people.values.joinToString("\n") { person ->
            "${person.name.replace('\t', ' ')}\t${person.consentedAtElapsedMs}\t0"
        }
        return peopleStore.write(body)
    }

    private fun writeFeature(enabled: Boolean): Boolean {
        if (!ensureDirectory()) return false
        return featureStore.write(if (enabled) "enabled=true\n" else "enabled=false\n")
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

    private fun publish() {
        snapshotState.value = currentSnapshot()
    }

    private fun currentSnapshot(): PersonConsentSnapshot {
        val saved = people.values.sortedBy { it.name.lowercase() }
        val pending = pendingName
        val line = when {
            !featureEnabled ->
                "Person memory is off. The camera does not save people. Saved names: ${saved.size}. No face photo is stored."
            pending != null ->
                "Confirm on screen to save $pending. Nothing is saved until you confirm. No face photo is stored."
            else ->
                "Person memory is on. Saved names: ${saved.size}. No one is enrolled from the camera. No face photo is stored."
        }
        return PersonConsentSnapshot(
            featureEnabled = featureEnabled,
            pendingName = pending,
            people = saved,
            statusLine = line,
        )
    }

    private fun disabledLine(): SpokenLine {
        return SpokenLine(
            id = "remember-not-enabled",
            english = "Person memory is off. Turn it on in Settings, then confirm each person. Nothing was saved.",
            arabic = "ذاكرة الأشخاص متوقفة. شغّلها من الإعدادات، ثم أكّد كل شخص. لم يُحفظ شيء.",
        )
    }

    private fun saveFailedLine(): SpokenLine {
        return SpokenLine(
            id = "remember-save-failed",
            english = "Could not store the name on this phone. Nothing new was stored.",
            arabic = "تعذّر حفظ الاسم على هذا الهاتف. لم يُخزن شيء جديد.",
        )
    }

    private companion object {
        const val MAX_PEOPLE = 20

        /**
         * Fail closed: enable only when the whole file trims exactly to
         * `enabled=true`. Conflicting or garbage content stays off.
         */
        fun parseFeatureEnabled(featureText: String?): Boolean {
            return featureText != null && featureText.trim() == "enabled=true"
        }

        fun cleanName(raw: String): String? {
            val name = raw.trim().replace('\t', ' ').replace('\n', ' ').take(40).trim()
            if (name.length < 2) return null
            if (name.any { it.isISOControl() }) return null
            return name
        }
    }
}

object RememberPersonCommand {
    private val english = Regex(
        """(?i)\b(?:remember|save) this person as\s+([A-Za-z][A-Za-z '\-]{0,40})""",
    )
    private val arabic = Regex(
        """(?:احفظ|تذكر)\s+هذا الشخص باسم\s+([\u0600-\u06FF][\u0600-\u06FF \-]{0,40})""",
    )

    fun parse(utterance: String): String? {
        val text = utterance.trim().trimEnd('.', '!', '?')
        val match = english.find(text) ?: arabic.find(text) ?: return null
        return match.groupValues[1].trim().trimEnd('.', '!', '?').ifBlank { null }
    }
}
