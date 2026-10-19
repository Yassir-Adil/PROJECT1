package com.triguide.ai.memory

import com.triguide.ai.domain.DirectionalCue
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MemoryPersistenceIsolationTest {
    private fun directory() = Files.createTempDirectory("triguide-memory-isolation-").toFile()
    private fun cue(label: String) = DirectionalCue(label, 0.9f, 0.5f, 0.1f)

    @Test fun conflatingDiskJobsDoesNotDropRamLabelsOrSummary() {
        val directory = directory()
        var pending: (() -> Unit)? = null
        val memory = LocalGuidanceMemory(directory, scheduleWrite = { pending = it })
        memory.note(listOf(cue("chair")), 10)
        memory.noteSummary("A chair was described", 11)
        memory.note(listOf(cue("door")), 12)
        assertEquals(setOf("chair", "door"), memory.recent().map { it.label }.toSet())
        pending!!.invoke()
        assertTrue(File(directory, "session.txt").readText().contains("A chair was described"))
        assertEquals(2, LocalGuidanceMemory(directory).recent().size)
    }

    @Test fun oldQueuedJobPersistsLatestSnapshotRatherThanResurrectingOldState() {
        val jobs = mutableListOf<() -> Unit>()
        val directory = directory()
        val memory = LocalGuidanceMemory(directory, scheduleWrite = { jobs += it })
        memory.note(listOf(cue("chair")), 10)
        memory.note(listOf(cue("door")), 12)
        jobs.first().invoke()
        assertEquals(setOf("chair", "door"), LocalGuidanceMemory(directory).recent().map { it.label }.toSet())
    }

    @Test fun readersAndMutationsDoNotWaitForBlockedDiskWork() {
        val directory = directory()
        var pending: (() -> Unit)? = null
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blockDisk = AtomicBoolean(false)
        val memory = LocalGuidanceMemory(directory, scheduleWrite = { pending = it }, beforePersist = {
            if (blockDisk.get()) {
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
            }
        })
        memory.note(listOf(cue("chair")), 10)
        blockDisk.set(true)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val disk = executor.submit { pending!!.invoke() }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val read = executor.submit<Int> {
                memory.note(listOf(cue("door")), 12)
                memory.summaryLine()
                memory.recent().size
            }
            assertEquals(2, read.get(3, TimeUnit.SECONDS))
            release.countDown()
            disk.get(3, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS))
        }
    }

    @Test fun failedDurableClearKeepsRamAndReportsFailure() {
        val directory = directory()
        val memory = LocalGuidanceMemory(directory)
        memory.note(listOf(cue("chair")), 10)
        val objects = File(directory, "objects.tsv")
        assertTrue(objects.delete())
        assertTrue(objects.mkdir())
        assertFalse(memory.clearPersisted())
        assertEquals(listOf("chair"), memory.recent().map { it.label })
    }

    @Test fun queuedWriteAfterSuccessfulClearCannotResurrectLabelsOrSummary() {
        val directory = directory()
        var pending: (() -> Unit)? = null
        val memory = LocalGuidanceMemory(directory, scheduleWrite = { pending = it })
        memory.note(listOf(cue("chair")), 10)
        memory.noteSummary("old description", 11)
        val oldJob = pending!!
        assertTrue(memory.clearPersisted())
        oldJob()
        assertTrue(memory.recent().isEmpty())
        assertFalse(File(directory, "session.txt").readText().contains("old description"))
        assertTrue(LocalGuidanceMemory(directory).recent().isEmpty())
    }

    @Test fun rejectedSchedulingDoesNotLoseRamOrThrowIntoGuidance() {
        val memory = LocalGuidanceMemory(directory(), scheduleWrite = { throw IOException("injected") })
        memory.note(listOf(cue("chair")), 10)
        memory.noteSummary("brief", 11)
        assertEquals(1, memory.recent().size)
    }

    @Test fun failedPersistenceCanRetrySameSnapshot() {
        val directory = directory()
        var pending: (() -> Unit)? = null
        val memory = LocalGuidanceMemory(directory, scheduleWrite = { pending = it })
        memory.note(listOf(cue("chair")), 10)
        val objects = File(directory, "objects.tsv")
        assertTrue(objects.mkdir())
        val retry = pending!!
        retry()
        assertTrue(objects.delete())
        retry()
        assertTrue(objects.readText().contains("chair"))
    }
}
