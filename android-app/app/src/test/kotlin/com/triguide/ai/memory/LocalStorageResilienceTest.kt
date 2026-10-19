package com.triguide.ai.memory

import com.triguide.ai.domain.DirectionalCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * RED regression suite for the S1/S2 local-storage resilience contract.
 *
 * Codex runs these before releasing S2. Failures against current production
 * code are expected. Do not claim Android physical I/O durability from JVM alone.
 */
class LocalStorageResilienceTest {

    @Test fun failedDeleteDoesNotClaimDurableSuccessOrLoseInMemoryName() {
        val directory = tempDir("delete-failed")
        val store = PersonConsentStore(directory)
        store.setFeatureEnabled(true)
        store.requestSave("Ahmad", 1)
        store.confirmPending(2)
        val path = File(directory, "consented-people.tsv")
        assertTrue(path.delete()); assertTrue(path.mkdir())
        assertFalse(store.delete("Ahmad"))
        assertEquals(listOf("Ahmad"), store.list().map { it.name })
        assertTrue(path.isDirectory)
    }

    @Test fun invalidFeatureFileCannotEnableConsent() {
        for (invalid in listOf("enabled=true\nenabled=false\n", "garbage\nenabled=true\n")) {
            val directory = tempDir("invalid-feature")
            File(directory, "person-feature.txt").writeText(invalid)
            assertFalse(PersonConsentStore(directory).snapshot.value.featureEnabled)
        }
    }

    @Test fun failedDurableEnableDoesNotEnableConsent() {
        val directory = tempDir("enable-failed")
        assertTrue(File(directory, "person-feature.txt").mkdir())
        val store = PersonConsentStore(directory)
        store.setFeatureEnabled(true)
        assertFalse(store.snapshot.value.featureEnabled)
    }

    // --- Unavailable / non-directory storage must not crash APIs ---

    @Test
    fun localGuidanceMemorySurvivesRegularFileDirectoryPath() {
        val path = File(tempRoot(), "memory-as-file").apply { writeText("not-a-directory") }
        val memory = try {
            LocalGuidanceMemory(path)
        } catch (t: Throwable) {
            fail("construction must not throw when directory path is a regular file: $t")
            return
        }
        try {
            memory.startSession(10L)
            memory.note(listOf(cue("chair", 0.9f, 0.5f)), nowElapsedMs = 20L)
            memory.noteSummary("brief", nowElapsedMs = 30L)
            memory.recent()
            memory.summaryLine()
            memory.clear()
        } catch (t: Throwable) {
            fail("LocalGuidanceMemory APIs must not throw on unavailable storage: $t")
        }
        assertTrue(path.isFile)
        assertEquals("not-a-directory", path.readText())
    }

    @Test
    fun personConsentStoreSurvivesRegularFileDirectoryPath() {
        val path = File(tempRoot(), "people-as-file").apply { writeText("not-a-directory") }
        val store = try {
            PersonConsentStore(path)
        } catch (t: Throwable) {
            fail("construction must not throw when directory path is a regular file: $t")
            return
        }
        try {
            assertFalse(store.snapshot.value.featureEnabled)
            store.setFeatureEnabled(true)
            store.setFeatureEnabled(false)
            store.requestSave("Ahmad", 1L)
            store.confirmPending(2L)
            store.cancelPending()
            store.delete("Ahmad")
            store.list()
            store.statusLine()
            assertNull(store.recognize())
            assertFalse(store.storeFace("Ahmad", byteArrayOf(1, 2, 3)))
        } catch (t: Throwable) {
            fail("PersonConsentStore APIs must not throw on unavailable storage: $t")
        }
        assertTrue(path.isFile)
        assertEquals("not-a-directory", path.readText())
    }

    // --- Oversized / malformed persistence: bound, reject, keep originals ---

    @Test
    fun oversizedObjectsFileIsRejectedWithoutDeletion() {
        val directory = tempDir("objects-oversize")
        val objects = File(directory, "objects.tsv")
        val payload = "x".repeat(70_000)
        objects.writeText("keep-me\tcenter\t0.5\t100\t1\n$payload")
        val before = objects.readBytes()

        val memory = LocalGuidanceMemory(directory)
        assertTrue(
            "oversized objects.tsv must remain on disk",
            objects.isFile,
        )
        assertTrue(before.contentEquals(objects.readBytes()))
        assertTrue(
            "oversized objects.tsv must not be loaded as notes",
            memory.recent().isEmpty(),
        )
    }

    @Test
    fun oversizedPeopleFileIsRejectedWithoutDeletion() {
        val directory = tempDir("people-oversize")
        val people = File(directory, "consented-people.tsv")
        val payload = "y".repeat(70_000)
        people.writeText("Keep\t100\t0\n$payload")
        File(directory, "person-feature.txt").writeText("enabled=true\n")
        val before = people.readBytes()

        val store = PersonConsentStore(directory)
        assertTrue(people.isFile)
        assertTrue(before.contentEquals(people.readBytes()))
        assertTrue(store.list().isEmpty())
        assertTrue(store.snapshot.value.featureEnabled)
    }

    @Test
    fun malformedObjectsRowsAreRejectedOnLoad() {
        val directory = tempDir("objects-malformed")
        File(directory, "objects.tsv").writeText(
            listOf(
                "ok\tcenter\t0.5\t10\t1",
                "bad-cols\tcenter\t0.5",
                "nan\tcenter\tNaN\t10\t1",
                "inf\tcenter\tInfinity\t10\t1",
                "neg-conf\tcenter\t-0.1\t10\t1",
                "hi-conf\tcenter\t1.1\t10\t1",
                "bad-zone\tup\t0.5\t10\t1",
                "neg-time\tcenter\t0.5\t-1\t1",
                "neg-count\tcenter\t0.5\t10\t-3",
                "ctrl\u0001label\tcenter\t0.5\t10\t1",
                "tab\tin\tname\tcenter\t0.5\t10\t1",
            ).joinToString("\n"),
        )

        val memory = LocalGuidanceMemory(directory)
        val labels = memory.recent().map { it.label.lowercase() }.toSet()
        assertEquals(setOf("ok"), labels)
        val ok = memory.recent().single()
        assertEquals("center", ok.zone)
        assertEquals(0.5f, ok.confidence, 0.0001f)
        assertEquals(10L, ok.lastSeenElapsedMs)
        assertEquals(1, ok.seenCount)
    }

    @Test
    fun malformedPeopleRowsAreRejectedOnLoad() {
        val directory = tempDir("people-malformed")
        File(directory, "person-feature.txt").writeText("enabled=true\n")
        File(directory, "consented-people.tsv").writeText(
            listOf(
                "Valid\t10\t0",
                "bad-cols\t10",
                "NegTime\t-5\t0",
                "A\t10\t0",
                "ctrl\u0007name\t10\t0",
                "Has\tTab\tName\t10\t0",
            ).joinToString("\n"),
        )

        val store = PersonConsentStore(directory)
        assertEquals(listOf("Valid"), store.list().map { it.name })
        assertFalse(store.list().single().faceStored)
    }

    @Test
    fun noteRejectsInvalidConfidenceZonesAndControlLabels() {
        val memory = LocalGuidanceMemory(tempDir("note-invalid"))
        memory.note(
            cues = listOf(
                cue("nan", Float.NaN, 0.5f),
                cue("pinf", Float.POSITIVE_INFINITY, 0.5f),
                cue("ninf", Float.NEGATIVE_INFINITY, 0.5f),
                cue("low", -0.01f, 0.5f),
                cue("high", 1.01f, 0.5f),
                cue("badx", 0.5f, Float.NaN),
                cue("\u0000ctrl", 0.9f, 0.5f),
                cue("line\nbreak", 0.9f, 0.5f),
                cue("ok", 0.9f, 0.5f),
            ),
            nowElapsedMs = 1_000L,
        )
        assertEquals(listOf("ok"), memory.recent().map { it.label })
    }

    // --- Caps enforced on LOAD, not only on write ---

    @Test
    fun objectLabelCapIsEnforcedOnLoad() {
        val directory = tempDir("objects-cap-load")
        val lines = (1..45).joinToString("\n") { index ->
            "label$index\tcenter\t0.5\t${index.toLong()}\t1"
        }
        File(directory, "objects.tsv").writeText(lines)

        val memory = LocalGuidanceMemory(directory)
        assertTrue(
            "loaded object notes must respect max 40 labels, was ${memory.recent().size}",
            memory.recent().size <= 40,
        )
        assertEquals(40, memory.recent().size)
    }

    @Test
    fun confirmedPeopleCapIsEnforcedOnLoad() {
        val directory = tempDir("people-cap-load")
        File(directory, "person-feature.txt").writeText("enabled=true\n")
        val lines = (1..25).joinToString("\n") { index ->
            "Person$index\t${index.toLong()}\t0"
        }
        File(directory, "consented-people.tsv").writeText(lines)

        val store = PersonConsentStore(directory)
        assertTrue(
            "loaded people must respect max 20 names, was ${store.list().size}",
            store.list().size <= 20,
        )
        assertEquals(20, store.list().size)
    }

    // --- Count saturation / boot clock ordering ---

    @Test
    fun seenCountIncrementSaturatesInsteadOfOverflowing() {
        val directory = tempDir("count-saturate")
        File(directory, "objects.tsv").writeText(
            "chair\tcenter\t0.9\t100\t${Int.MAX_VALUE}",
        )
        val memory = LocalGuidanceMemory(directory)
        memory.note(listOf(cue("chair", 0.9f, 0.5f)), nowElapsedMs = 200L)
        val count = memory.recent().single { it.label.equals("chair", ignoreCase = true) }.seenCount
        assertEquals(Int.MAX_VALUE, count)
        assertTrue("seenCount must not wrap negative", count > 0)
    }

    @Test
    fun oldElapsedRealtimeValuesDoNotSortNewBootEntriesAsOlder() {
        val directory = tempDir("boot-order")
        File(directory, "objects.tsv").writeText(
            "legacy\tcenter\t0.5\t${Long.MAX_VALUE / 2}\t1",
        )
        val memory = LocalGuidanceMemory(directory)
        memory.note(listOf(cue("fresh", 0.9f, 0.5f)), nowElapsedMs = 40L)

        val recent = memory.recent()
        assertEquals(2, recent.size)
        assertEquals(
            "file/insertion order must keep the new-boot note ahead of a huge prior-boot elapsed value",
            "fresh",
            recent.first().label,
        )
    }

    // --- Consent / face defaults ---

    @Test
    fun consentRemainsOffByDefaultAndFacePathsStayDisabled() {
        val directory = tempDir("consent-defaults")
        val store = PersonConsentStore(directory)
        assertFalse(store.snapshot.value.featureEnabled)
        assertTrue(store.list().isEmpty())
        assertNull(store.snapshot.value.pendingName)
        assertNull(store.recognize())
        assertFalse(store.storeFace("Anyone", byteArrayOf(9, 9, 9)))
        assertFalse(File(directory, "person-feature.txt").exists() &&
            File(directory, "person-feature.txt").readText().contains("enabled=true"))
        val names = directory.walkTopDown().map { it.name.lowercase() }.toList()
        assertFalse(names.any { it.endsWith(".jpg") || it.endsWith(".png") || it.contains("embedding") })
    }

    // --- Failed durable writes / privacy disable ---

    @Test
    fun failedDurablePersonWriteNeverClaimsNameWasSaved() {
        val directory = tempDir("people-write-fail")
        val store = PersonConsentStore(directory)
        store.setFeatureEnabled(true)
        store.requestSave("Ahmad", 1L)
        assertEquals("Ahmad", store.snapshot.value.pendingName)

        val peoplePath = File(directory, "consented-people.tsv")
        if (peoplePath.exists()) {
            assertTrue(peoplePath.delete())
        }
        assertTrue(
            "precondition: make consented-people.tsv a directory so writeText fails",
            peoplePath.mkdir(),
        )

        val spoken = try {
            store.confirmPending(2L)
        } catch (t: Throwable) {
            fail("confirmPending must not throw when durable write fails: $t")
            return
        }
        assertNotNull(spoken)
        assertFalse(
            "must not claim a durable save when persistence failed: ${spoken.english}",
            spoken.english.contains("is saved", ignoreCase = true),
        )
        assertFalse(
            spoken.id.equals("remember-confirmed", ignoreCase = true),
        )
        assertFalse(store.list().any { it.name.equals("Ahmad", ignoreCase = true) })
        assertTrue(peoplePath.isDirectory)
    }

    @Test
    fun disablingPrivacyFeatureUpdatesMemoryEvenWhenPersistenceFails() {
        val directory = tempDir("feature-disable-fail")
        val store = PersonConsentStore(directory)
        store.setFeatureEnabled(true)
        store.requestSave("Sara", 1L)
        assertTrue(store.snapshot.value.featureEnabled)
        assertEquals("Sara", store.snapshot.value.pendingName)

        val featurePath = File(directory, "person-feature.txt")
        assertTrue(featurePath.delete() || !featurePath.exists())
        assertTrue(
            "precondition: make person-feature.txt a directory so writeText fails",
            featurePath.mkdir(),
        )

        try {
            store.setFeatureEnabled(false)
        } catch (t: Throwable) {
            fail("setFeatureEnabled(false) must not throw when persistence fails: $t")
        }
        assertFalse(
            "privacy feature must be off in memory even if the feature file cannot be written",
            store.snapshot.value.featureEnabled,
        )
        assertNull(store.snapshot.value.pendingName)
        assertTrue(featurePath.isDirectory)
    }

    @Test
    fun startSessionAndNoteSummarySurviveBrokenSessionFilePath() {
        val directory = tempDir("session-broken")
        val session = File(directory, "session.txt")
        assertTrue(session.mkdir())
        val memory = LocalGuidanceMemory(directory)
        try {
            memory.startSession(5L)
            memory.noteSummary("hello", 6L)
            memory.clear()
        } catch (t: Throwable) {
            fail("session write failures must be swallowed: $t")
        }
        assertTrue(session.isDirectory)
    }

    private fun cue(label: String, confidence: Float, centerX: Float): DirectionalCue {
        return DirectionalCue(
            label = label,
            confidence = confidence,
            centerXNormalized = centerX,
            areaFraction = 0.05f,
        )
    }

    private fun tempRoot(): File {
        return File("build/tmp/storage-resilience").apply { mkdirs() }
    }

    private fun tempDir(name: String): File {
        return File(tempRoot(), "$name-${System.nanoTime()}").apply { mkdirs() }
    }
}
