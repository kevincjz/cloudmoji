package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepository

/**
 * Builds the spoken phrase for "N of this thing", per language. Mirrors iOS
 * CloudmojiCore's `CountingGrammar` byte-for-byte.
 *
 * The rules differ structurally, not just lexically: `zh` and `ms` bake the
 * classifier into the noun, `ja` fuses the counter into the number and puts
 * the noun first, `tl` attaches a linker to the numeral.
 */
class CountingGrammar(private val repository: EmojiRepository) {

    fun phrase(item: Countable, count: Int, language: Language): String {
        val number = repository.numberWord(language, count)
            // No number word for this count — speak the bare noun rather than
            // fabricate a counter.
            ?: return item.noun(language)

        return when (language) {
            Language.English -> "$number ${englishPlural(item, count)}"
            // The measure word is already part of the noun (只狗), and Chinese
            // takes no space between numeral and classifier.
            Language.Chinese -> "$number${item.zh}"
            // Likewise the penjodoh bilangan (ekor anjing), space-separated.
            Language.Malay -> "$number ${item.ms}"
            // Noun first, counter last: "りんご みっつ". The number-の-noun order
            // is grammatical but bookish, and the ～つ counter is already fused
            // into the number word, so the noun never changes form.
            Language.Japanese -> "${item.ja} $number"
            // The linker attaches to the NUMERAL, not the noun, and the noun is
            // never pluralised after a numeral.
            Language.Tagalog -> "${tagalogLinked(number)} ${item.tl}"
        }
    }

    // MARK: - English

    fun englishPlural(item: Countable, count: Int): String {
        if (count <= 1) return item.en
        item.enPlural?.let { return it }
        return regularPlural(item.en)
    }

    companion object {

        fun regularPlural(noun: String): String {
            if (noun == "fish") return "fish"
            if (noun.endsWith("y")) {
                val beforeY = noun.dropLast(1).lastOrNull()
                if (beforeY != null && beforeY !in "aeiou") {
                    return noun.dropLast(1) + "ies"
                }
            }
            for (suffix in listOf("s", "sh", "ch", "x", "z")) {
                if (noun.endsWith(suffix)) return noun + "es"
            }
            return noun + "s"
        }

        // MARK: - Tagalog

        /**
         * Attaches the Tagalog linker to a numeral.
         * Vowel-final takes -ng (tatlo -> tatlong); n-final takes -g;
         * any other consonant takes a separate "na" (apat -> apat na).
         */
        fun tagalogLinked(number: String): String {
            val last = number.lowercase().lastOrNull() ?: return number
            if (last in "aeiou") return "${number}ng"
            if (last == 'n') return "${number}g"
            return "$number na"
        }
    }
}
