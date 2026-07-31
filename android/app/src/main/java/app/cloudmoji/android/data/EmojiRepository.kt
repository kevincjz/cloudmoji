package app.cloudmoji.android.data

import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.CategoryTab
import app.cloudmoji.android.model.Countable
import app.cloudmoji.android.model.EmojiEntry
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.LanguageMeta

/**
 * The generated content catalogue, ready for the rest of the app to query.
 * Mirrors iOS CloudmojiCore's `EmojiRepository`: this is the only type that
 * knows the file format ([EmojiDataDto]/[EmojiRepositoryLoader]) — everyone
 * else uses this API.
 *
 * Build one with [EmojiRepositoryLoader], or use [empty] for the degraded
 * case where the bundled resource cannot be loaded.
 */
class EmojiRepository private constructor(
    val emojis: List<EmojiEntry>,
    val countables: List<Countable>,
    /** In the JSON's canonical order: "all", then the eight categories. */
    val categories: List<CategoryTab>,
    val languages: List<LanguageMeta>,
    private val numberWords: Map<Language, List<String>>,
    private val animalSounds: Map<String, Map<Language, String>>,
) {
    /** All entries in [category], in catalogue order. */
    fun entries(category: Category): List<EmojiEntry> = emojis.filter { it.category == category }

    /** The entry for this glyph, or `null` if it is not in the catalogue. */
    fun entry(emoji: String): EmojiEntry? = emojis.firstOrNull { it.emoji == emoji }

    /**
     * Number word for a count, or `null` when the count is out of range.
     * Japanese has no ～つ form past ten, so callers must handle `null` rather
     * than fabricating a counter.
     */
    fun numberWord(language: Language, count: Int): String? {
        val words = numberWords[language] ?: return null
        if (count < 1 || count > words.size) return null
        return words[count - 1]
    }

    fun meta(language: Language): LanguageMeta? = languages.firstOrNull { it.id == language }

    /** Every glyph that has a noise, whatever the language. */
    val animalSoundGlyphs: Set<String> get() = animalSounds.keys

    /**
     * What this animal says, in this language — "woof woof", 汪汪, ワンワン.
     *
     * `null` when the glyph has no entry, which is how the caller knows there
     * is no noise rather than being handed the animal's name by mistake.
     * There is deliberately **no fallback to English**: a Chinese-speaking
     * child hearing an English voice say "woof" is worse than hearing
     * nothing, and a missing row is a content bug the tests catch rather than
     * something to paper over at runtime.
     */
    fun animalSound(glyph: String, language: Language): String? {
        val sound = animalSounds[glyph]?.get(language)
        return sound?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val EXPECTED_EMOJI_COUNT = 200
        const val EXPECTED_COUNTABLE_COUNT = 84
        const val EXPECTED_LANGUAGE_COUNT = 5
        const val EXPECTED_CATEGORY_COUNT = 9
        const val EXPECTED_ANIMAL_SOUND_COUNT = 20

        /**
         * A repository with no content. The degraded case when the bundled
         * resource cannot be loaded — the app shows an empty grid rather than
         * crashing in front of a child. Reaching this in production means the
         * build is broken. Deliberately skips the count validation [from]
         * applies to real content: an intentionally empty repository is not a
         * parity failure.
         */
        val empty: EmojiRepository = EmojiRepository(
            emojis = emptyList(),
            countables = emptyList(),
            categories = emptyList(),
            languages = emptyList(),
            numberWords = emptyMap(),
            animalSounds = emptyMap(),
        )

        /**
         * Converts a decoded [EmojiDataDto] into a repository, validating
         * every count the generator promises and every language/category code
         * an entry references. A mismatch on either front means the
         * generator and this port have drifted, so this throws rather than
         * building a silently incomplete repository — see
         * `docs/superpowers/plans/2026-07-30-android-app.md`'s Phase 1 gate.
         *
         * `internal`, not `public`: [EmojiDataDto] is the wire format and
         * stays package-private, so callers go through [EmojiRepositoryLoader]
         * instead of constructing a DTO themselves.
         */
        internal fun from(dto: EmojiDataDto): EmojiRepository {
            val languages = dto.languages.map { it.toDomain() }
            val categories = dto.categories.map { it.toDomain() }
            val emojis = dto.emojis.map { it.toDomain() }
            val countables = dto.countables.map { it.toDomain() }
            val numberWords = dto.numberWords.mapKeysToLanguage("numberWords")
            val animalSounds = (dto.animalSounds ?: emptyMap()).mapValues { (_, byLanguage) ->
                byLanguage.mapKeysToLanguage("animalSounds")
            }

            validateCount("emojis", emojis.size, EXPECTED_EMOJI_COUNT)
            validateCount("countables", countables.size, EXPECTED_COUNTABLE_COUNT)
            validateCount("languages", languages.size, EXPECTED_LANGUAGE_COUNT)
            validateCount("categories", categories.size, EXPECTED_CATEGORY_COUNT)
            validateCount("animal sounds", animalSounds.size, EXPECTED_ANIMAL_SOUND_COUNT)

            return EmojiRepository(
                emojis = emojis,
                countables = countables,
                categories = categories,
                languages = languages,
                numberWords = numberWords,
                animalSounds = animalSounds,
            )
        }

        private fun validateCount(label: String, actual: Int, expected: Int) {
            if (actual != expected) {
                throw EmojiCatalogException(
                    "EmojiData.json has $actual $label, expected $expected",
                )
            }
        }

        private fun LanguageMetaDto.toDomain(): LanguageMeta = LanguageMeta(
            id = requireLanguage(id, "languages"),
            short = short,
            name = name,
            speech = speech,
            voicePrefixes = voicePrefixes,
        )

        private fun CategoryTabDto.toDomain(): CategoryTab = CategoryTab(
            id = id,
            icon = icon,
            labels = labels,
        )

        private fun EmojiEntryDto.toDomain(): EmojiEntry = EmojiEntry(
            emoji = emoji,
            category = Category.fromId(cat)
                ?: throw EmojiCatalogException("EmojiData.json emoji '$emoji' has unknown category '$cat'"),
            en = en,
            zh = zh,
            ms = ms,
            ja = ja,
            tl = tl,
        )

        private fun CountableDto.toDomain(): Countable = Countable(
            emoji = emoji,
            en = en,
            enPlural = enPlural,
            zh = zh,
            ms = ms,
            ja = ja,
            tl = tl,
        )

        private fun <V> Map<String, V>.mapKeysToLanguage(context: String): Map<Language, V> =
            mapKeys { (code, _) -> requireLanguage(code, context) }

        private fun requireLanguage(code: String, context: String): Language =
            Language.fromCode(code)
                ?: throw EmojiCatalogException("EmojiData.json $context has unknown language code '$code'")
    }
}
