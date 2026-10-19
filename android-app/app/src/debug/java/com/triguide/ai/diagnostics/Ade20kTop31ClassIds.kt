package com.triguide.ai.diagnostics

/**
 * MLPerf's ADE20K Top-31 ground-truth masks reserve 0 as ignored/background
 * and represent the 31 foreground categories with IDs 1 through 31. The
 * category names follow the first 31 frequency-ordered entries in ADE20K's
 * objectInfo150 taxonomy, which is the Top-31 subset used by this diagnostic.
 */
internal object Ade20kTop31ClassIds {
    const val FOREGROUND_CLASS_COUNT = 31
    const val IGNORE_OR_BACKGROUND_ID = 0

    private val labelsById = arrayOf(
        "ignore/background",
        "wall",
        "building",
        "sky",
        "floor",
        "tree",
        "ceiling",
        "road",
        "bed",
        "windowpane",
        "grass",
        "cabinet",
        "sidewalk",
        "person",
        "earth",
        "door",
        "table",
        "mountain",
        "plant",
        "curtain",
        "chair",
        "car",
        "water",
        "painting",
        "sofa",
        "shelf",
        "house",
        "sea",
        "mirror",
        "rug",
        "field",
        "armchair",
    )

    fun isValid(classId: Int): Boolean = classId in IGNORE_OR_BACKGROUND_ID..FOREGROUND_CLASS_COUNT

    fun labelName(classId: Int): String = labelsById.getOrNull(classId) ?: "invalid_id"
}
