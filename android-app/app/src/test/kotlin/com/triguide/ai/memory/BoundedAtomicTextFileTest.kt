package com.triguide.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * RED suite for the expected [BoundedAtomicTextFile] helper API (S2).
 *
 * Expected constructor:
 * `BoundedAtomicTextFile(file: File, maxBytes: Int = 65536, beforeReplace: (File) -> Unit = {})`
 * with `read(): String?`, `write(text: String): Boolean`, `delete(): Boolean`.
 *
 * Assumptions documented in docs/CURSOR_STORAGE_RESULT.md. JVM tests do not prove
 * power-loss durability on every Android filesystem.
 */
class BoundedAtomicTextFileTest {

    @Test fun growingOrEndlessInputIsReadOnlyUpToTheByteBudget() {
        var bytesRead = 0
        val endless = object : InputStream() {
            override fun read(): Int { bytesRead++; return 65 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                repeat(length) { bytes[offset + it] = 65 }; bytesRead += length; return length
            }
        }
        assertNull(BoundedAtomicTextFile.readBoundedUtf8(endless, 32))
        assertTrue("must stop after maxBytes+1, read $bytesRead", bytesRead <= 33)
    }

    @Test fun malformedUtf8IsRejectedWithoutChangingTheFile() {
        val file = File(tempDir(), "malformed.txt")
        val original = byteArrayOf(0xC3.toByte(), 0x28)
        file.writeBytes(original)
        assertNull(BoundedAtomicTextFile(file).read())
        assertTrue(original.contentEquals(file.readBytes()))
    }

    @Test fun zeroProgressBulkReadCannotSpinIndefinitely() {
        var reads = 0
        val stream = object : InputStream() {
            override fun read(bytes: ByteArray, offset: Int, length: Int) = 0
            override fun read(): Int { reads++; return 65 }
        }
        assertNull(BoundedAtomicTextFile.readBoundedUtf8(stream, 4))
        assertEquals(5, reads)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeLimitCannotAllocateOrWrite() { BoundedAtomicTextFile(File(tempDir(), "negative"), -1) }

    @Test(expected = IllegalArgumentException::class)
    fun unboundedLimitCannotAllocate() { BoundedAtomicTextFile(File(tempDir(), "huge"), Int.MAX_VALUE) }

    @Test
    fun writeThenReadRoundTripPreservesUtf8Text() {
        val file = File(tempDir(), "roundtrip.txt")
        val helper = BoundedAtomicTextFile(file)
        assertTrue(helper.write("hello أحمد"))
        assertEquals("hello أحمد", helper.read())
        assertEquals("hello أحمد", file.readText(StandardCharsets.UTF_8))
    }

    @Test
    fun defaultMaxBytesIs65536() {
        val file = File(tempDir(), "default-cap.txt")
        val helper = BoundedAtomicTextFile(file)
        val tooLarge = "a".repeat(65_537)
        assertFalse(helper.write(tooLarge))
        assertFalse(file.exists())
        assertNull(helper.read())
    }

    @Test
    fun writeRejectsOversizedPayloadWithoutCreatingCorruptTarget() {
        val file = File(tempDir(), "oversize-write.txt")
        file.writeText("keep")
        val helper = BoundedAtomicTextFile(file, maxBytes = 32)
        assertFalse(helper.write("x".repeat(64)))
        assertEquals("keep", file.readText(StandardCharsets.UTF_8))
        assertEquals("keep", helper.read())
    }

    @Test
    fun readRejectsOversizedExistingFileWithoutDeletingIt() {
        val file = File(tempDir(), "oversize-read.txt")
        val body = "y".repeat(200)
        file.writeText(body)
        val helper = BoundedAtomicTextFile(file, maxBytes = 64)
        assertNull(helper.read())
        assertTrue(file.isFile)
        assertEquals(body, file.readText(StandardCharsets.UTF_8))
    }

    @Test
    fun readReturnsNullForMissingFile() {
        val file = File(tempDir(), "missing.txt")
        val helper = BoundedAtomicTextFile(file)
        assertNull(helper.read())
        assertFalse(file.exists())
    }

    @Test
    fun writeReturnsFalseAndPreservesOriginalWhenBeforeReplaceAborts() {
        val file = File(tempDir(), "abort-replace.txt")
        file.writeText("original")
        var observedTarget: File? = null
        val helper = BoundedAtomicTextFile(file, maxBytes = 65536) { target ->
            observedTarget = target
            throw IOException("injected replace abort")
        }
        assertFalse(helper.write("replacement"))
        assertEquals("original", file.readText(StandardCharsets.UTF_8))
        assertEquals("original", helper.read())
        assertEquals(file.canonicalFile, observedTarget?.canonicalFile)
        assertNoOrphanStagingFiles(file.parentFile!!, file.name)
    }

    @Test
    fun writeCleansHelperOwnedStagingAfterBeforeReplaceAbort() {
        val dir = tempDir()
        val file = File(dir, "stage-clean.txt")
        file.writeText("v1")
        val helper = BoundedAtomicTextFile(file) {
            throw IOException("stop")
        }
        assertFalse(helper.write("v2"))
        assertNoOrphanStagingFiles(dir, file.name)
        assertEquals("v1", file.readText(StandardCharsets.UTF_8))
    }

    @Test
    fun writeCatchesStorageExceptionsAndReturnsFalse() {
        val dir = tempDir()
        val file = File(dir, "io-fail.txt")
        file.writeText("safe")
        // Turn the parent into an unusable location for staging by replacing the
        // destination path with a directory after construction of a sibling helper.
        val blocked = File(dir, "blocked.txt")
        assertTrue(blocked.mkdir())
        val helper = BoundedAtomicTextFile(blocked)
        val result = try {
            helper.write("payload")
        } catch (t: Throwable) {
            fail("write must catch storage exceptions and return false, not throw: $t")
            return
        }
        assertFalse(result)
        assertTrue(blocked.isDirectory)
    }

    @Test
    fun readCatchesStorageExceptionsAndReturnsNull() {
        val dir = tempDir()
        val file = File(dir, "read-dir.txt")
        assertTrue(file.mkdir())
        val helper = BoundedAtomicTextFile(file)
        val result = try {
            helper.read()
        } catch (t: Throwable) {
            fail("read must catch storage exceptions and return null, not throw: $t")
            return
        }
        assertNull(result)
        assertTrue(file.isDirectory)
    }

    @Test
    fun deleteRemovesExistingFileAndIsIdempotent() {
        val file = File(tempDir(), "delete-me.txt")
        val helper = BoundedAtomicTextFile(file)
        assertTrue(helper.write("gone"))
        assertTrue(helper.delete())
        assertFalse(file.exists())
        assertTrue(helper.delete())
        assertNull(helper.read())
    }

    @Test
    fun successfulReplaceLeavesNoStagingArtifacts() {
        val dir = tempDir()
        val file = File(dir, "clean-success.txt")
        val helper = BoundedAtomicTextFile(file)
        assertTrue(helper.write("one"))
        assertTrue(helper.write("two"))
        assertEquals("two", helper.read())
        assertNoOrphanStagingFiles(dir, file.name)
    }

    @Test
    fun writeDoesNotUseNonAtomicTruncateInPlaceFallback() {
        val file = File(tempDir(), "no-truncate.txt")
        file.writeText("ABCDEFGH")
        var replaced = false
        val helper = BoundedAtomicTextFile(file, maxBytes = 65536) { target ->
            // If implementation truncated in place before this hook, original is already lost.
            assertEquals(
                "original bytes must still be present immediately before atomic replace",
                "ABCDEFGH",
                target.readText(StandardCharsets.UTF_8),
            )
            replaced = true
        }
        assertTrue(helper.write("xyz"))
        assertTrue("beforeReplace must run for a successful replace path", replaced)
        assertEquals("xyz", file.readText(StandardCharsets.UTF_8))
    }

    @Test
    fun emptyWriteIsAllowedAndReadable() {
        val file = File(tempDir(), "empty.txt")
        val helper = BoundedAtomicTextFile(file, maxBytes = 16)
        assertTrue(helper.write(""))
        assertEquals("", helper.read())
        assertTrue(file.isFile)
        assertEquals(0L, file.length())
    }

    @Test
    fun multiByteUtf8BoundaryIsMeasuredInBytesNotChars() {
        val file = File(tempDir(), "utf8-bytes.txt")
        // Arabic letter "أ" is 2 bytes in UTF-8; 20 chars => 40 bytes.
        val text = "أ".repeat(20)
        val helper = BoundedAtomicTextFile(file, maxBytes = 39)
        assertFalse(helper.write(text))
        assertFalse(file.exists())
        val ok = BoundedAtomicTextFile(file, maxBytes = 40)
        assertTrue(ok.write(text))
        assertEquals(text, ok.read())
    }

    private fun assertNoOrphanStagingFiles(directory: File, targetName: String) {
        val orphans = directory.listFiles()
            ?.filter { candidate ->
                candidate.isFile &&
                    candidate.name != targetName &&
                    (
                        candidate.name.contains(targetName) ||
                            candidate.name.endsWith(".tmp") ||
                            candidate.name.endsWith(".stg") ||
                            candidate.name.contains(".tmp") ||
                            candidate.name.startsWith(".$targetName")
                        )
            }
            .orEmpty()
        assertTrue(
            "helper-owned staging files must be cleaned up: ${orphans.map { it.name }}",
            orphans.isEmpty(),
        )
    }

    private fun tempDir(): File {
        return File("build/tmp/bounded-atomic-${System.nanoTime()}").apply { mkdirs() }
    }
}
