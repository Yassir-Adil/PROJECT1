package com.triguide.ai.diagnostics

/**
 * Same-Frame co-acquisition provenance for debug Raw Depth diagnostics.
 *
 * Mints an unforgeable [RawDepthPairProof] only after both resources are acquired
 * from one unchanged positive frame timestamp. Invalidates the proof before
 * closing confidence then depth. Diagnostic only: never navigation authority.
 */
internal object RawDepthPairScope {

    /**
     * Active only inside [withFrame]'s [block]. Private constructor — no factory
     * that can mint a proof outside actual same-Frame co-acquisition.
     */
    class Proof private constructor(
        val frameTimestampNs: Long,
    ) {
        @Volatile
        private var active: Boolean = true

        val isActive: Boolean
            get() = active

        private fun invalidate() {
            active = false
        }

        companion object {
            // Kotlin enclosing objects cannot access a nested private constructor.
            // Keep creation AND acquisition together here, never expose mint(stamp).
            internal fun <F, D : AutoCloseable, C : AutoCloseable, R> acquireAndUse(
                frame: F,
                timestampOf: (F) -> Long,
                acquireDepth: (F) -> D,
                acquireConfidence: (F) -> C,
                block: (RawDepthPairProof, D, C) -> R,
            ): R {
                val stamp0 = timestampOf(frame)
                require(stamp0 > 0L) { "frame timestamp must be positive" }

                return acquireDepth(frame).use { depth ->
                    acquireConfidence(frame).use { confidence ->
                        val stamp1 = timestampOf(frame)
                        check(stamp1 == stamp0 && stamp1 > 0L) {
                            "frame timestamp changed during acquisition"
                        }
                        val proof = Proof(stamp0)
                        try {
                            block(proof, depth, confidence)
                        } finally {
                            // Invalidate before either close(), even on block failure.
                            proof.invalidate()
                        }
                    }
                }
            }
        }
    }

    fun <F, D : AutoCloseable, C : AutoCloseable, R> withFrame(
        frame: F,
        timestampOf: (F) -> Long,
        acquireDepth: (F) -> D,
        acquireConfidence: (F) -> C,
        block: (RawDepthPairProof, D, C) -> R,
    ): R = Proof.acquireAndUse(frame, timestampOf, acquireDepth, acquireConfidence, block)
}

/** Top-level name expected by tests and gate; aliases the unforgeable nested proof. */
internal typealias RawDepthPairProof = RawDepthPairScope.Proof
