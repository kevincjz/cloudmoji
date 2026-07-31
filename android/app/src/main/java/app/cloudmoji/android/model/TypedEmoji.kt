package app.cloudmoji.android.model

/**
 * One emoji a child has tapped (or, reused for the word bubble, a category
 * chip's icon and label), and the word that was spoken for it. Mirrors iOS
 * `TypingRow.swift`'s `TypedEmoji`.
 *
 * [id] is a monotonically increasing counter owned by whichever
 * [app.cloudmoji.android.model.WordsViewModel] mints it — not the glyph —
 * because the same emoji can be tapped twice in a row and a Compose `key`
 * keyed on the glyph would collide the second 🍎 with the first.
 */
data class TypedEmoji(
    val id: Long,
    val emoji: String,
    val word: String,
)

/** A request to scroll the Words grid to a section. The token is what makes
 * tapping the same chip twice work: a child who scrolled away from Animals
 * and taps Animals again must go back there, and an unchanged value would be
 * no change at all as far as a `LaunchedEffect(jump)` is concerned. Mirrors
 * iOS `EmojiGrid.swift`'s `SectionJump`. */
data class SectionJump(val id: String, val token: Int)
