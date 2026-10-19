package com.triguide.ai.domain

/**
 * Release-gate metadata for the first relative-depth benchmark candidate.
 *
 * This is deliberately only a contract and preflight check. It does not load
 * a model, start an interpreter, read camera pixels, or produce navigation
 * evidence. A file that passes this check is merely eligible for a separate
 * local benchmark.
 */
object RelativeDepthModelContract {
    const val MODEL_ID = "midas-small-256-fp16-litert"
    const val EXPECTED_ASSET_PATH = "models/midas_small_256_fp16.tflite"
    const val EXPECTED_SHA256_HEX =
        "bec9bce704789e504ec306196fcb0aabe90fd25c2b9d7db382339741950890ca"
    const val SOURCE_URL = "https://huggingface.co/litert-community/MiDaS-small"
    const val LICENSE = "MIT"

    const val INPUT_WIDTH = 256
    const val INPUT_HEIGHT = 256
    const val INPUT_CHANNELS = 3
    const val OUTPUT_WIDTH = 256
    const val OUTPUT_HEIGHT = 256
    const val OUTPUT_CHANNELS = 1

    /** RGB ImageNet normalization required by this candidate export. */
    val INPUT_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    val INPUT_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** This model's output is relative inverse depth, not metres. */
    const val OUTPUT_SEMANTICS = "relative_inverse_depth"

    /**
     * Keep model provenance visible in diagnostics and review materials.
     * A passing preflight does not approve release or safety use.
     */
    const val REVIEW_NOTE =
        "Benchmark candidate only. Relative depth may raise diagnostic risk; " +
            "it cannot establish clearance, CLEAR, STRAIGHT, TURN, or permission to walk."

    fun preflight(
        assetPresent: Boolean,
        observedSha256Hex: String?,
    ): RelativeDepthModelPreflight {
        if (!assetPresent) {
            return RelativeDepthModelPreflight(
                readiness = RelativeDepthModelReadiness.NOT_BUNDLED,
                detail = "The candidate model is not bundled; no depth runtime may start.",
            )
        }

        val normalized = observedSha256Hex?.trim()?.lowercase()
        if (normalized == null || normalized.length != SHA256_HEX_LENGTH ||
            normalized.any { it !in HEX_DIGITS }
        ) {
            return RelativeDepthModelPreflight(
                readiness = RelativeDepthModelReadiness.INVALID_METADATA,
                detail = "A bundled model must provide a valid SHA-256 before benchmarking.",
            )
        }

        return if (normalized == EXPECTED_SHA256_HEX) {
            RelativeDepthModelPreflight(
                readiness = RelativeDepthModelReadiness.READY_FOR_BENCHMARK,
                detail = "Hash matches the pinned candidate; benchmark-only loading may be considered.",
            )
        } else {
            RelativeDepthModelPreflight(
                readiness = RelativeDepthModelReadiness.HASH_MISMATCH,
                detail = "Hash does not match the pinned candidate; model loading must remain disabled.",
            )
        }
    }

    private const val SHA256_HEX_LENGTH = 64
    private const val HEX_DIGITS = "0123456789abcdef"
}

enum class RelativeDepthModelReadiness {
    NOT_BUNDLED,
    INVALID_METADATA,
    HASH_MISMATCH,
    READY_FOR_BENCHMARK,
}

data class RelativeDepthModelPreflight(
    val readiness: RelativeDepthModelReadiness,
    val detail: String,
) {
    /** Only a benchmark may consume a ready asset; this is never navigation evidence. */
    val mayEnterBenchmark: Boolean
        get() = readiness == RelativeDepthModelReadiness.READY_FOR_BENCHMARK
}
