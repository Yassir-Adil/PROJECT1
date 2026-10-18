package com.triguide.ai.domain

/**
 * On-demand description of the latest detector boxes.
 * It is not called from the control loop and it never calls a region safe.
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

    fun fromReported(lines: List<String>): SceneLine {
        if (lines.isEmpty()) {
            return SceneLine(
                english = "No reported objects in the latest frame. An empty frame is not a clear path and is not a floor reading.",
                arabic = "لا أجسام مبلغ عنها في آخر إطار. الإطار الفارغ ليس مساراً واضحاً وليس قراءة للأرضية.",
            )
        }
        val list = lines.joinToString(", ")
        return SceneLine(
            english = "Reported in the latest frame: $list. This describes detector boxes only. It is not a clearance and not a floor reading.",
            arabic = "في آخر إطار: $list. هذا وصف لصناديق الكاشف فقط. ليس تصريحاً بالطريق وليس قراءة للأرضية.",
        )
    }

    fun text(line: SceneLine, language: ResolvedVoiceLanguage): String {
        return when (language) {
            ResolvedVoiceLanguage.ARABIC -> line.arabic
            ResolvedVoiceLanguage.ENGLISH -> line.english
        }
    }
}
