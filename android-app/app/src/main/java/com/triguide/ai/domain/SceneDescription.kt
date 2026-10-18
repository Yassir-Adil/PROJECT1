package com.triguide.ai.domain

/**
 * On-demand and brief descriptions of the latest detector boxes.
 * The first sentence is what the model reported. The second sentence is the limit.
 * Neither sentence calls a region safe, clear, or measured in distance.
 */
data class SceneLine(
    val english: String,
    val arabic: String,
)

object SceneDescription {
    fun waiting(): SceneLine {
        return SceneLine(
            english = "The detector has not reported a frame yet. This is not a clear path.",
            arabic = "لم يبلّغ الكاشف عن إطار بعد. هذا ليس مساراً واضحاً.",
        )
    }

    fun fromCues(cues: List<DirectionalCue>, matchedName: String? = null): SceneLine {
        val line = compose(place(cues), brief = false)
        val name = matchedName?.trim().orEmpty()
        if (name.isEmpty()) return line
        return SceneLine(
            english = "${line.english} A consented name matches this picture: $name. The match is not a distance.",
            arabic = "${line.arabic} اسم محفوظ يطابق هذه الصورة: $name. هذه المطابقة ليست مسافة.",
        )
    }

    /**
     * One zone only, so a brief cannot say left and center in the same line.
     * The zone is the same outer-third or center test the navigation phrases use.
     */
    fun briefFromCues(cues: List<DirectionalCue>): SceneLine {
        val best = cues
            .filter { cue -> cue.confidence.isFinite() && cue.centerXNormalized in 0f..1f && cue.areaFraction > 0f }
            .maxByOrNull { cue ->
                cue.confidence * cue.areaFraction.coerceAtLeast(NavigationConfig.RISK_AREA_FLOOR)
            }
        return compose(place(listOfNotNull(best)), brief = true)
    }

    fun text(line: SceneLine, language: ResolvedVoiceLanguage): String {
        return when (language) {
            ResolvedVoiceLanguage.ARABIC -> line.arabic
            ResolvedVoiceLanguage.ENGLISH -> line.english
        }
    }

    private data class Placed(
        val label: String,
        val zone: HorizontalZone,
    )

    private fun place(cues: List<DirectionalCue>): List<Placed> {
        return cues.mapNotNull { cue ->
            if (!cue.confidence.isFinite() || cue.confidence < DetectorConfig.SCORE_THRESHOLD) {
                return@mapNotNull null
            }
            if (!cue.centerXNormalized.isFinite() || cue.centerXNormalized !in 0f..1f) {
                return@mapNotNull null
            }
            if (!cue.areaFraction.isFinite() || cue.areaFraction <= 0f) {
                return@mapNotNull null
            }
            val zone = zoneOf(cue.centerXNormalized)
            Placed(cue.label.trim().ifBlank { "object" }, zone)
        }
    }

    private fun compose(placed: List<Placed>, brief: Boolean): SceneLine {
        if (placed.isEmpty()) {
            return if (brief) {
                SceneLine(
                    english = "No reported objects. An empty frame is not a clear path.",
                    arabic = "لا أجسام مبلغ عنها. الإطار الفارغ ليس مساراً واضحاً.",
                )
            } else {
                SceneLine(
                    english = "The detector reports no objects in the latest frame. " +
                        "An empty frame is not a clear path and is not a floor reading.",
                    arabic = "الكاشف لا يبلغ عن أجسام في آخر إطار. " +
                        "الإطار الفارغ ليس مساراً واضحاً وليس قراءة للأرضية.",
                )
            }
        }
        val englishParts = clauses(placed, arabic = false)
        val arabicParts = clauses(placed, arabic = true)
        val englishList = join(englishParts, arabic = false)
        val arabicList = join(arabicParts, arabic = true)
        return if (brief) {
            SceneLine(
                english = "Reported: $englishList. Boxes only, not a clear path.",
                arabic = "المبلغ: $arabicList. صناديق فقط، وليس مساراً واضحاً.",
            )
        } else {
            SceneLine(
                english = "The detector reports $englishList. " +
                    "This is a report of boxes. It is not a distance, not a clearance, and not a floor reading.",
                arabic = "الكاشف يبلغ عن $arabicList. " +
                    "هذا تقرير صناديق. ليس مسافة، وليس تصريحاً بالطريق، وليس قراءة للأرضية.",
            )
        }
    }

    private fun clauses(placed: List<Placed>, arabic: Boolean): List<String> {
        val people = placed.filter { it.label.equals("person", ignoreCase = true) }
        val others = placed.filterNot { it.label.equals("person", ignoreCase = true) }
        val parts = mutableListOf<String>()
        HorizontalZone.entries.forEach { zone ->
            val count = people.count { it.zone == zone }
            if (count > 0) {
                parts += if (arabic) {
                    "${peopleArabic(count)} ${zonePhrase(zone, arabic = true)}"
                } else {
                    "${peopleEnglish(count)} ${zonePhrase(zone, arabic = false)}"
                }
            }
        }
        val grouped = others.groupBy { it.label.lowercase() to it.zone }
            .entries
            .sortedByDescending { it.value.size }
            .take(MAX_OTHER_GROUPS)
        grouped.forEach { (key, items) ->
            val (label, zone) = key
            parts += if (arabic) {
                "${SpokenLabels.arabicCount(label, items.size)} ${zonePhrase(zone, arabic = true)}"
            } else {
                "${SpokenLabels.englishCount(label, items.size)} ${zonePhrase(zone, arabic = false)}"
            }
        }
        return parts
    }

    private fun peopleEnglish(count: Int): String {
        return if (count == 1) "1 person" else "$count people"
    }

    private fun peopleArabic(count: Int): String {
        return when (count) {
            1 -> "شخص واحد"
            2 -> "شخصان"
            else -> "$count أشخاص"
        }
    }

    private fun zoneOf(centerX: Float): HorizontalZone {
        return when {
            centerX < NavigationConfig.LEFT_THIRD_END -> HorizontalZone.LEFT
            centerX > NavigationConfig.RIGHT_THIRD_START -> HorizontalZone.RIGHT
            centerX < NavigationConfig.CENTER_START -> HorizontalZone.LEFT
            centerX > NavigationConfig.CENTER_END -> HorizontalZone.RIGHT
            else -> HorizontalZone.CENTER
        }
    }

    /** One zone word. Center is "in the center". A side is "on the left" or "on the right". */
    private fun zonePhrase(zone: HorizontalZone, arabic: Boolean): String {
        return if (arabic) {
            when (zone) {
                HorizontalZone.CENTER -> "في ${zoneArabic(zone)}"
                else -> "على ${zoneArabic(zone)}"
            }
        } else {
            when (zone) {
                HorizontalZone.CENTER -> "in the ${zoneEnglish(zone)}"
                else -> "on the ${zoneEnglish(zone)}"
            }
        }
    }

    private fun zoneEnglish(zone: HorizontalZone): String {
        return when (zone) {
            HorizontalZone.LEFT -> "left"
            HorizontalZone.CENTER -> "center"
            HorizontalZone.RIGHT -> "right"
        }
    }

    private fun zoneArabic(zone: HorizontalZone): String {
        return when (zone) {
            HorizontalZone.LEFT -> "اليسار"
            HorizontalZone.CENTER -> "الوسط"
            HorizontalZone.RIGHT -> "اليمين"
        }
    }

    private fun join(parts: List<String>, arabic: Boolean): String {
        if (parts.isEmpty()) return if (arabic) "أجسام" else "objects"
        if (parts.size == 1) return parts[0]
        return parts.joinToString(". ")
    }

    private const val MAX_OTHER_GROUPS = 3
}
