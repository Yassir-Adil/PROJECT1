package com.triguide.ai.diagnostics

import java.nio.ByteBuffer

/** Decoder for the exact INT32 class-map output of the three pinned diagnostic models. */
internal object Ade20kInt32ClassMap {
    const val PIXEL_COUNT = 512 * 512

    fun decode(output: ByteBuffer): IntArray {
        require(output.remaining() == PIXEL_COUNT * Int.SIZE_BYTES) {
            "Expected a 512x512 INT32 class map, got ${output.remaining()} bytes"
        }
        val result = IntArray(PIXEL_COUNT)
        // A slice defaults to big endian: preserve the tensor buffer's byte order explicitly.
        output.slice().order(output.order()).asIntBuffer().get(result)
        require(result.all(Ade20kTop31ClassIds::isValid)) {
            "Class map contains IDs outside background 0 and foreground 1..31"
        }
        return result
    }
}
