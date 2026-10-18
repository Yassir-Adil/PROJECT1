package com.triguide.ai.domain

/**
 * Words used in speech. Unknown detector labels stay in the model's own spelling.
 * A missing translation is not replaced with a guess.
 */
object SpokenLabels {
    fun english(label: String): String {
        val cleaned = label.trim().ifBlank { "object" }
        return cleaned.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

    fun arabic(label: String): String {
        return when (label.trim().lowercase()) {
            "person" -> "شخص"
            "chair" -> "كرسي"
            "bottle" -> "زجاجة"
            "cup" -> "كوب"
            "couch" -> "كنبة"
            "laptop" -> "حاسوب"
            "tv" -> "تلفاز"
            "dining table", "table" -> "طاولة"
            "car" -> "سيارة"
            "bicycle" -> "دراجة"
            "dog" -> "كلب"
            "cat" -> "قطة"
            "backpack" -> "حقيبة"
            "cell phone" -> "هاتف"
            else -> label.trim().ifBlank { "جسم" }
        }
    }

    fun englishCount(label: String, count: Int): String {
        val name = label.trim().ifBlank { "object" }
        if (count <= 1) {
            val article = if (name.first().lowercaseChar() in "aeiou") "an" else "a"
            return "$article $name"
        }
        val plural = when (name.lowercase()) {
            "person" -> "people"
            "chair" -> "chairs"
            "bottle" -> "bottles"
            "cup" -> "cups"
            "couch" -> "couches"
            else -> name
        }
        return "$count $plural"
    }

    fun arabicCount(label: String, count: Int): String {
        val name = arabic(label)
        return if (count <= 1) name else "$count $name"
    }
}
