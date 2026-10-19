package com.triguide.ai.diagnostics

import kotlin.math.min

/** Source-space semantic samples, not confidence, height, free-space or navigation evidence. */
internal data class DiagnosticSurfaceMap(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val mirrored: Boolean,
    val completedMs: Long,
    val cells: IntArray,
) {
    fun fraction(id: Int): Double = cells.count { it == id }.toDouble() / cells.size
}

internal object DiagnosticSurfaces {
    const val GRID = 64
    const val WALL = 1
    const val FLOOR = 4
    const val RUG = 29
    const val MAX_AGE_MS = 1_000L
    private const val MIN_VISIBLE_FRACTION = 0.10

    /** Undo the exact centered aspect-fit canvas used by DiagnosticSegmenter.prepare. */
    fun sample(map: IntArray, width: Int, height: Int, mirrored: Boolean, completedMs: Long): DiagnosticSurfaceMap {
        require(width > 0 && height > 0 && completedMs >= 0)
        require(map.size == DiagnosticSegmenter.SIZE * DiagnosticSegmenter.SIZE)
        require(map.all(Ade20kTop31ClassIds::isValid))
        val size = DiagnosticSegmenter.SIZE
        val scale = min(size.toDouble() / width, size.toDouble() / height)
        val contentWidth = width * scale
        val contentHeight = height * scale
        val padX = (size - contentWidth) / 2
        val padY = (size - contentHeight) / 2
        val cells = IntArray(GRID * GRID) { index ->
            val x = (padX + ((index % GRID + 0.5) / GRID) * contentWidth).toInt().coerceIn(0, size - 1)
            val y = (padY + ((index / GRID + 0.5) / GRID) * contentHeight).toInt().coerceIn(0, size - 1)
            map[y * size + x]
        }
        return DiagnosticSurfaceMap(width, height, mirrored, completedMs, cells)
    }

    fun fresh(map: DiagnosticSurfaceMap?, nowMs: Long, running: Boolean): DiagnosticSurfaceMap? =
        map?.takeIf { running && it.completedMs <= nowMs && nowMs - it.completedMs <= MAX_AGE_MS }

    /** Describes only sufficiently represented classes. Rug height/edges are deliberately unknown. */
    fun description(map: DiagnosticSurfaceMap): Pair<String, String> {
        val english = mutableListOf<String>()
        val arabic = mutableListOf<String>()
        if (map.fraction(WALL) >= MIN_VISIBLE_FRACTION) {
            english += "The segmentation model suggests a wall in the image."
            arabic += "نموذج التقسيم يرجّح وجود جدار في الصورة."
        }
        if (map.fraction(RUG) >= MIN_VISIBLE_FRACTION) {
            english += "A rug-like region is visible; its edge height is not measured."
            arabic += "تظهر منطقة تشبه السجادة؛ ارتفاع حافتها غير مقاس."
        } else if (map.fraction(FLOOR) >= MIN_VISIBLE_FRACTION) {
            english += "A floor-like region is visible; obstacles and steps are not ruled out."
            arabic += "تظهر منطقة تشبه الأرضية؛ هذا لا يستبعد العوائق أو الدرج."
        }
        return english.joinToString(" ") to arabic.joinToString(" ")
    }

    fun combinedDescription(surface: DiagnosticSurfaceMap?, objects: com.triguide.ai.domain.SceneLine,
        hasUsableObjects: Boolean): com.triguide.ai.domain.SceneLine {
        val text = surface?.let(::description)
        val englishObjects = if (hasUsableObjects) objects.english else "The object detector has no usable labels."
        val arabicObjects = if (hasUsableObjects) objects.arabic else "كاشف الأشياء لا يملك أسماء موثوقة في هذه اللقطة."
        return com.triguide.ai.domain.SceneLine(
            listOfNotNull(text?.first?.takeIf(String::isNotBlank), englishObjects).joinToString(" "),
            listOfNotNull(text?.second?.takeIf(String::isNotBlank), arabicObjects).joinToString(" "),
        )
    }
}
