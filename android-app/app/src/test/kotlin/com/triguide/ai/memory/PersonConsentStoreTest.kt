package com.triguide.ai.memory

import com.triguide.ai.domain.DirectionalCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PersonConsentStoreTest {
    @Test
    fun aNameIsStoredOnlyAfterTheFeatureIsOnAndTheUserConfirms() {
        val directory = tempDir()
        val store = PersonConsentStore(directory)
        assertFalse(store.snapshot.value.featureEnabled)
        assertTrue(store.list().isEmpty())

        val blocked = store.handleUtterance("remember this person as Ahmad", nowElapsedMs = 10L)
        assertTrue(blocked.english.contains("off"))
        assertTrue(blocked.english.contains("Nothing was saved"))
        assertFalse(File(directory, "consented-people.tsv").exists())
        assertNull(store.snapshot.value.pendingName)

        store.setFeatureEnabled(true)
        val pending = store.handleUtterance("Voice: remember this person as Ahmad", nowElapsedMs = 20L)
        assertEquals("Ahmad", store.snapshot.value.pendingName)
        assertTrue(pending.english.contains("Confirm on screen"))
        assertTrue(pending.english.contains("No photo is stored"))
        assertTrue(pending.english.contains("name only"))
        assertFalse(pending.english.contains("until you confirm"))
        assertTrue(store.list().isEmpty())

        val saved = store.confirmPending(30L)
        assertTrue(saved.english.contains("Ahmad"))
        assertTrue(saved.english.contains("No photo"))
        val person = store.list().single()
        assertEquals("Ahmad", person.name)
        assertFalse(person.faceStored)
        assertNull(store.snapshot.value.pendingName)
        val file = File(directory, "consented-people.tsv").readText()
        assertTrue(file.contains("Ahmad"))
        assertTrue(file.endsWith("\t0") || file.contains("\t0"))
        assertNoFaceFiles(directory)
    }

    @Test
    fun settingsCanPrepareAnArabicNameWithoutStoringAPhoto() {
        val store = PersonConsentStore(tempDir())
        store.setFeatureEnabled(true)
        val pending = store.requestSave("أحمد", 5L)
        assertEquals("أحمد", store.snapshot.value.pendingName)
        assertTrue(pending.english.contains("No photo is stored"))
        assertTrue(pending.english.contains("name only"))
        assertFalse(pending.english.contains("until you confirm"))
        assertTrue(pending.arabic.contains("لا تُحفظ أي صورة"))
        assertFalse(pending.arabic.contains("قبل التأكيد"))
        assertTrue(store.list().isEmpty())
        store.confirmPending(6L)
        val person = store.list().single()
        assertEquals("أحمد", person.name)
        assertFalse(person.faceStored)
    }

    @Test
    fun theCameraCannotStoreAFaceOrMatchAStranger() {
        val directory = tempDir()
        val store = PersonConsentStore(directory)
        store.setFeatureEnabled(true)
        store.requestSave("Ahmad", 1L)
        store.confirmPending(2L)
        assertFalse(store.storeFace("Ahmad", byteArrayOf(1, 2, 3, 4)))
        assertNull(store.recognize())
        assertNoFaceFiles(directory)
        assertFalse(store.list().single().faceStored)
    }

    @Test
    fun deleteRemovesOnlyTheNamedPerson() {
        val store = PersonConsentStore(tempDir())
        store.setFeatureEnabled(true)
        store.requestSave("Ahmad", 1L)
        store.confirmPending(2L)
        store.requestSave("Sara", 3L)
        store.confirmPending(4L)
        assertTrue(store.delete("Ahmad"))
        assertEquals(listOf("Sara"), store.list().map { it.name })
    }

    @Test
    fun objectNotesStayLocalAndDoNotInventDepth() {
        val directory = tempDir()
        val memory = LocalGuidanceMemory(directory)
        memory.note(
            cues = listOf(DirectionalCue("person", 0.80f, 0.15f, 0.10f)),
            nowElapsedMs = 1_000L,
        )
        val note = memory.recent().single()
        assertEquals("person", note.label)
        assertEquals("left", note.zone)
        assertTrue(File(directory, "objects.tsv").readText().contains("person"))
        assertFalse(File(directory, "objects.tsv").readText().contains("meter"))
        memory.clear()
        assertTrue(memory.recent().isEmpty())
    }

    private fun tempDir(): File {
        return File("build/tmp/consent-${System.nanoTime()}").apply { mkdirs() }
    }

    private fun assertNoFaceFiles(directory: File) {
        val names = directory.walkTopDown().map { it.name.lowercase() }.toList()
        assertFalse(names.any { it.endsWith(".jpg") || it.endsWith(".png") || it.contains("embedding") || it == "faces" })
    }
}
