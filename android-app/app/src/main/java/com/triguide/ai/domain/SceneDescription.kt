package com.triguide.ai.domain

/**
 * On-demand and brief descriptions of the latest detector boxes.
 * Spoken lines stay short and human. They never claim a clear or safe path.
 */
data class SceneLine(
    val english: String,
    val arabic: String,
)

object SceneDescription {
    /**
     * Labels that may be spoken by name when confidence is strong.
     * Unstable COCO classes that often misfire on household scenes stay out
     * (Describe narration only — navigation risk still uses the raw detector).
     */
    private val SPEAKABLE = setOf(
        "person", "chair", "couch", "sofa", "bottle", "cup", "dining table", "table",
        "laptop", "tv", "car", "bicycle", "dog", "cat", "backpack", "cell phone",
        "bed", "refrigerator", "sink", "oven", "microwave", "bench",
        "potted plant", "book", "remote", "keyboard", "mouse",
    )

    /**
     * Even at high score these labels are spoken as weak “something”.
     * Redmi captures repeatedly mistook round tables for toilet.
     */
    private val NEVER_NAME_CONFIDENTLY = setOf(
        "toilet", "toothbrush", "frisbee", "hair drier", "toaster",
    )

    /** Below this, even a known label is spoken as a weak “something”. */
    private const val STRONG_LABEL_CONFIDENCE = 0.55f

    /**
     * Person needs a higher bar: packaging and furniture have been boxed as person
     * on Redmi (about 0.36–0.70). Confident naming requires this floor.
     */
    private const val STRONG_PERSON_CONFIDENCE = 0.75f

    private const val MAX_OTHER_GROUPS = 3

    fun waiting(): SceneLine {
        return SceneLine(
            english = "No picture yet.",
            arabic = "ما في صورة بعد.",
        )
    }

    fun fromCues(cues: List<DirectionalCue>, matchedName: String? = null): SceneLine {
        val line = composeSnapshot(place(cues))
        val name = matchedName?.trim().orEmpty()
        if (name.isEmpty()) return line
        return SceneLine(
            english = "${line.english} Saved name: $name.",
            arabic = "${line.arabic} اسم محفوظ: $name.",
        )
    }

    /**
     * One zone only for the rare auto brief. Same zone bands as navigation phrases.
     */
    fun briefFromCues(cues: List<DirectionalCue>): SceneLine {
        val best = cues
            .filter { cue -> cue.confidence.isFinite() && cue.centerXNormalized in 0f..1f && cue.areaFraction > 0f }
            .maxByOrNull { cue ->
                cue.confidence * cue.areaFraction.coerceAtLeast(NavigationConfig.RISK_AREA_FLOOR)
            }
        return composeSnapshot(place(listOfNotNull(best)))
    }

    fun text(line: SceneLine, language: ResolvedVoiceLanguage): String {
        return when (language) {
            ResolvedVoiceLanguage.ARABIC -> line.arabic
            ResolvedVoiceLanguage.ENGLISH -> line.english
        }
    }

    private data class Placed(
        val labelKey: String,
        val speakConfidently: Boolean,
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
            val key = cue.label.trim().lowercase().ifBlank { "object" }
            if (key in NEVER_NAME_CONFIDENTLY) {
                return@mapNotNull Placed(
                    labelKey = key,
                    speakConfidently = false,
                    zone = zoneOf(cue.centerXNormalized),
                )
            }
            val known = key in SPEAKABLE
            val floor = if (key == "person") STRONG_PERSON_CONFIDENCE else STRONG_LABEL_CONFIDENCE
            val strong = known && cue.confidence >= floor
            Placed(
                labelKey = key,
                speakConfidently = strong,
                zone = zoneOf(cue.centerXNormalized),
            )
        }
    }

    private fun composeSnapshot(placed: List<Placed>): SceneLine {
        if (placed.isEmpty()) {
            return SceneLine(
                english = "Nothing obvious ahead.",
                arabic = "ما في جسم ظاهر قدامك.",
            )
        }
        val englishParts = buildClauses(placed, arabic = false)
        val arabicParts = buildClauses(placed, arabic = true)
        return SceneLine(
            english = "Ahead: ${joinNatural(englishParts, arabic = false)}.",
            arabic = "قدامك ${joinNatural(arabicParts, arabic = true)}.",
        )
    }

    private fun buildClauses(placed: List<Placed>, arabic: Boolean): List<String> {
        val parts = mutableListOf<String>()
        val people = placed.filter { it.labelKey == "person" }
        val others = placed.filterNot { it.labelKey == "person" }

        HorizontalZone.entries.forEach { zone ->
            val confident = people.count { it.zone == zone && it.speakConfidently }
            val weak = people.count { it.zone == zone && !it.speakConfidently }
            if (confident > 0) {
                parts += if (arabic) {
                    "${peopleArabic(confident)} ${zonePhrase(zone, arabic = true)}"
                } else {
                    "${peopleEnglish(confident)} ${zonePhrase(zone, arabic = false)}"
                }
            }
            if (weak > 0) {
                parts += if (arabic) {
                    "${weakPersonArabic(weak)} ${zonePhrase(zone, arabic = true)}"
                } else {
                    "${weakPersonEnglish(weak)} ${zonePhrase(zone, arabic = false)}"
                }
            }
        }

        others
            .groupBy { Triple(it.labelKey, it.speakConfidently, it.zone) }
            .entries
            .sortedByDescending { it.value.size }
            .take(MAX_OTHER_GROUPS)
            .forEach { (key, group) ->
                val (labelKey, confident, zone) = key
                val count = group.size
                parts += if (arabic) {
                    objectArabic(labelKey, count, confident, zone)
                } else {
                    objectEnglish(labelKey, count, confident, zone)
                }
            }
        return parts
    }

    private fun objectEnglish(
        labelKey: String,
        count: Int,
        confident: Boolean,
        zone: HorizontalZone,
    ): String {
        val zoneText = zonePhrase(zone, arabic = false)
        if (!confident) {
            return if (count <= 1) {
                "what looks like something $zoneText"
            } else {
                "what looks like $count things $zoneText"
            }
        }
        val noun = SpokenLabels.englishCount(labelKey, count).replaceFirstChar { it.lowercase() }
        return "$noun $zoneText"
    }

    private fun objectArabic(
        labelKey: String,
        count: Int,
        confident: Boolean,
        zone: HorizontalZone,
    ): String {
        val zoneText = zonePhrase(zone, arabic = true)
        if (!confident) {
            return if (count <= 1) {
                "يبدو جسم $zoneText"
            } else {
                "يبدو $count أجسام $zoneText"
            }
        }
        val noun = SpokenLabels.arabicCount(labelKey, count)
        return "$noun $zoneText"
    }

    private fun peopleEnglish(count: Int): String {
        return if (count == 1) "a person" else "$count people"
    }

    private fun peopleArabic(count: Int): String {
        return when (count) {
            1 -> "شخص"
            2 -> "شخصان"
            else -> "$count أشخاص"
        }
    }

    private fun weakPersonEnglish(count: Int): String {
        return if (count <= 1) {
            "what looks like a person"
        } else {
            "what looks like $count people"
        }
    }

    private fun weakPersonArabic(count: Int): String {
        return if (count <= 1) {
            "يبدو شخص"
        } else {
            "يبدو $count أشخاص"
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

    private fun zonePhrase(zone: HorizontalZone, arabic: Boolean): String {
        return if (arabic) {
            when (zone) {
                HorizontalZone.CENTER -> "في الوسط"
                HorizontalZone.LEFT -> "على اليسار"
                HorizontalZone.RIGHT -> "على اليمين"
            }
        } else {
            when (zone) {
                HorizontalZone.CENTER -> "in the center"
                HorizontalZone.LEFT -> "on the left"
                HorizontalZone.RIGHT -> "on the right"
            }
        }
    }

    private fun joinNatural(parts: List<String>, arabic: Boolean): String {
        if (parts.isEmpty()) return if (arabic) "جسم" else "something"
        if (parts.size == 1) return parts[0]
        if (parts.size == 2) {
            return if (arabic) "${parts[0]} و${parts[1]}" else "${parts[0]} and ${parts[1]}"
        }
        val head = parts.dropLast(1).joinToString(if (arabic) " و" else ", ")
        val last = parts.last()
        return if (arabic) "$head و$last" else "$head, and $last"
    }
}
