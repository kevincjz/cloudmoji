package app.cloudmoji.android.model

import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category sectioning ([buildSections], against the real catalogue — mirrors
 * `EmojiRepositoryTest`'s own use of [TestCatalog]) and the flattened
 * jump-index math ([sectionHeaderIndex]/[sectionAtItemIndex], against small
 * synthetic fixtures so the boundary arithmetic can be pinned exactly).
 */
class EmojiSectionTest {

    private val repository = EmojiRepositoryLoader.fromJson(TestCatalog.json)

    // MARK: - buildSections

    @Test
    fun `every enabled category becomes a section in canonical order, dropping the all tab`() {
        val sections = buildSections(repository, Category.entries.toSet())

        assertEquals(Category.entries.map { it.id }, sections.map { it.id })
        assertTrue("no section may be the synthetic all tab", sections.none { it.tab.category == null })
    }

    @Test
    fun `a section holds exactly that category's entries, in catalogue order`() {
        val sections = buildSections(repository, Category.entries.toSet())

        val animals = sections.first { it.id == Category.Animals.id }
        assertEquals(repository.entries(Category.Animals).map { it.emoji }, animals.entries.map { it.emoji })
        assertTrue(animals.entries.all { it.category == Category.Animals })
    }

    @Test
    fun `a disabled category has no section at all`() {
        val sections = buildSections(repository, Category.entries.toSet() - Category.Animals)

        assertTrue(sections.none { it.id == Category.Animals.id })
        assertEquals(Category.entries.size - 1, sections.size)
    }

    @Test
    fun `an empty enabled set produces no sections`() {
        assertTrue(buildSections(repository, emptySet()).isEmpty())
    }

    // MARK: - sectionHeaderIndex / sectionAtItemIndex

    private fun tab(id: String): CategoryTab =
        CategoryTab(id = id, icon = "?", labels = mapOf("en" to id))

    private fun entry(emoji: String, category: Category): EmojiEntry =
        EmojiEntry(emoji = emoji, category = category, en = emoji, zh = emoji, ms = emoji, ja = emoji, tl = emoji)

    /** Fruits: 2 entries (header + 2 = 3 flattened items, indices 0-2).
     * Animals: 3 entries (header + 3 = 4 flattened items, indices 3-6). */
    private val fixture = listOf(
        EmojiSection(tab("fruits"), listOf(entry("a", Category.Fruits), entry("b", Category.Fruits))),
        EmojiSection(
            tab("animals"),
            listOf(entry("c", Category.Animals), entry("d", Category.Animals), entry("e", Category.Animals)),
        ),
    )

    @Test
    fun `the first section's header sits at index 0`() {
        assertEquals(0, sectionHeaderIndex(fixture, "fruits"))
    }

    @Test
    fun `a later section's header sits past every earlier section's header-plus-entries`() {
        // fruits: 1 header + 2 entries = 3 flattened items.
        assertEquals(3, sectionHeaderIndex(fixture, "animals"))
    }

    @Test
    fun `an unknown section id has no header index`() {
        assertNull(sectionHeaderIndex(fixture, "faces"))
    }

    @Test
    fun `every flattened index inside a section resolves back to that section`() {
        // fruits occupies indices 0..2 (header, a, b); animals occupies 3..6 (header, c, d, e).
        assertEquals("fruits", sectionAtItemIndex(fixture, 0)) // header
        assertEquals("fruits", sectionAtItemIndex(fixture, 1)) // a
        assertEquals("fruits", sectionAtItemIndex(fixture, 2)) // b
        assertEquals("animals", sectionAtItemIndex(fixture, 3)) // header
        assertEquals("animals", sectionAtItemIndex(fixture, 4)) // c
        assertEquals("animals", sectionAtItemIndex(fixture, 6)) // e, the last item
    }

    @Test
    fun `an index past the flattened list has no section`() {
        assertNull(sectionAtItemIndex(fixture, 7))
        assertNull(sectionAtItemIndex(fixture, 100))
    }

    @Test
    fun `a negative index has no section`() {
        assertNull(sectionAtItemIndex(fixture, -1))
    }

    @Test
    fun `sectionHeaderIndex and sectionAtItemIndex agree with each other`() {
        for (section in fixture) {
            val headerIndex = sectionHeaderIndex(fixture, section.id)
            assertEquals(section.id, sectionAtItemIndex(fixture, headerIndex!!))
        }
    }
}
