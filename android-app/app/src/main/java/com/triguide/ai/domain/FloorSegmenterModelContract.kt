package com.triguide.ai.domain

/**
 * Explicit contract for a future on-device floor/region segmenter.
 *
 * Phase 1: no model bytes are bundled. Do not treat any upstream class list
 * (Cityscapes road/sidewalk, ADE20K floor, etc.) as indoor walkable evidence.
 */
object FloorSegmenterModelContract {
    const val MODEL_ID = "triguide-floor-segmenter-unbundled"
    /** Placeholder until a cleared asset is recorded with SHA-256. */
    const val MODEL_SHA256_HEX = "0000000000000000000000000000000000000000000000000000000000000000"
    const val EXPECTED_ASSET_PATH = "models/floor_segmenter.tflite"
    const val SOURCE_ID = "floor-segmenter-diagnostic"

    /** Expected LIVE_STREAM input geometry once a model is cleared (Phase 2+). */
    const val EXPECTED_INPUT_WIDTH = 256
    const val EXPECTED_INPUT_HEIGHT = 256

    enum class ClassMappingStatus {
        /** No class may be interpreted as walkable / floor / clearance. */
        UNMAPPED,

        /**
         * An explicit indoor floor-like mapping exists in project docs.
         * Still not a mobility certificate; EvidenceFusionGate may consider mask evidence.
         * Production default remains [UNMAPPED] until a rights-cleared model is approved.
         */
        EXPERIMENTAL_INDOOR_FLOOR_DOCUMENTED,
    }

    val classMappingStatus: ClassMappingStatus = ClassMappingStatus.UNMAPPED

    /**
     * Human-readable contract note for diagnostics. Must not claim clearance,
     * walkability, or permission to move.
     */
    const val MAPPING_NOTE =
        "Class mapping UNMAPPED. No segmentation label is indoor walkable evidence. " +
            "Cityscapes road/sidewalk and ADE20K floor are not equivalent to a cleared corridor."

    fun provenancePlaceholder(): FloorMaskSource = FloorMaskSource(
        sourceId = SOURCE_ID,
        modelId = MODEL_ID,
        modelSha256 = FloorMaskModelSha256(MODEL_SHA256_HEX),
    )
}
