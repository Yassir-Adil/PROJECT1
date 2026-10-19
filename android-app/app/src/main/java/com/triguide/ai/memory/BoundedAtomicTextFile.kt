package com.triguide.ai.memory

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.min

/**
 * Bounded UTF-8 text persistence with same-directory atomic replacement.
 *
 * Uses [Files.move] with [StandardCopyOption.ATOMIC_MOVE] (API 26+). Content is
 * fsynced on the staging file before replace. If staging or replace fails, the
 * previous target bytes are left untouched. There is no truncate-in-place fallback.
 *
 * Atomic replacement is not a power-loss proof on every filesystem; callers must
 * not treat a successful [write] as durable across abrupt power loss.
 */
class BoundedAtomicTextFile(
    private val file: File,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
    private val beforeReplace: (File) -> Unit = {},
) {
    init {
        requireValidMaxBytes(maxBytes)
    }

    fun read(): String? {
        return try {
            if (!file.isFile) return null
            // Do not trust file.length() + readBytes(): TOCTOU can grow the file.
            file.inputStream().use { input ->
                readBoundedUtf8(input, maxBytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun write(text: String): Boolean {
        var staging: File? = null
        return try {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            if (bytes.size > maxBytes) return false
            val parent = file.parentFile ?: return false
            if (!parent.isDirectory) {
                if (parent.exists()) return false
                if (!parent.mkdirs() && !parent.isDirectory) return false
            }
            staging = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
            FileOutputStream(staging).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            beforeReplace(file)
            try {
                Files.move(
                    staging.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // Contract: never fall back to a non-atomic overwrite.
                return false
            }
            staging = null
            true
        } catch (_: Exception) {
            false
        } finally {
            staging?.let { leftover ->
                try {
                    if (leftover.exists()) leftover.delete()
                } catch (_: Exception) {
                    // Best-effort cleanup of helper-owned staging only.
                }
            }
        }
    }

    fun delete(): Boolean {
        return try {
            if (!file.exists()) return true
            if (!file.isFile) return false
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Int = 65_536
        const val ABSOLUTE_MAX_BYTES: Int = 1_048_576

        /**
         * Reads at most [maxBytes] + 1 bytes from [input] without closing it.
         * Returns null when the stream exceeds [maxBytes] or is not strict UTF-8.
         */
        internal fun readBoundedUtf8(input: InputStream, maxBytes: Int): String? {
            requireValidMaxBytes(maxBytes)
            val budget = maxBytes + 1
            val chunk = ByteArray(min(4096, budget).coerceAtLeast(1))
            val collected = ByteArrayOutputStream(min(maxBytes, 4096).coerceAtLeast(0))
            var total = 0
            while (total < budget) {
                val toRead = min(chunk.size, budget - total)
                val n = input.read(chunk, 0, toRead)
                if (n < 0) break
                if (n == 0) {
                    // Ensure progress even for a stream whose bulk read returns zero.
                    val single = input.read()
                    if (single < 0) break
                    collected.write(single)
                    total++
                    continue
                }
                collected.write(chunk, 0, n)
                total += n
            }
            if (total > maxBytes) return null
            return decodeStrictUtf8(collected.toByteArray())
        }

        private fun requireValidMaxBytes(maxBytes: Int) {
            require(maxBytes in 0..ABSOLUTE_MAX_BYTES) {
                "maxBytes must be in 0..$ABSOLUTE_MAX_BYTES, was $maxBytes"
            }
        }

        private fun decodeStrictUtf8(bytes: ByteArray): String? {
            return try {
                val decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                null
            }
        }
    }
}
