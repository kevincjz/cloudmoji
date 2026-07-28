import type { Category, CategoryTab, EmojiEntry } from "../types";
import { CATEGORIES, EMOJIS } from "../data/emojis";

/**
 * The categories a child navigates *between*, in list order.
 *
 * The grid is one continuous list of every emoji, grouped into these sections,
 * and a chip scrolls the list to one of them. "All" is deliberately dropped: in
 * a continuous list every emoji is already on screen, so a chip meaning "show me
 * all of them" would be a button that does nothing — and a chip that can never
 * be the section you are in is a chip that can never light up.
 *
 * Derived from `CATEGORIES` rather than written out again, and kept *out* of
 * `src/data/emojis.ts`: that file is the single source of truth for content and
 * is generated into the iOS app's `EmojiData.json`, so it holds data and nothing
 * else. This is a view of that data. Mirrors `AppModel.sections` on iOS.
 */
export const SECTION_CATEGORIES: Array<CategoryTab & { id: Category }> =
  CATEGORIES.filter((c): c is CategoryTab & { id: Category } => c.id !== "all");

export interface EmojiSection {
  id: Category;
  icon: string;
  label: string;
  emojis: EmojiEntry[];
}

/** The whole catalogue, grouped, with each section's name in `labels[lang]`. */
export function buildSections(labelFor: (tab: CategoryTab) => string): EmojiSection[] {
  return SECTION_CATEGORIES.map((cat) => ({
    id: cat.id,
    icon: cat.icon,
    label: labelFor(cat),
    emojis: EMOJIS.filter((e) => e.cat === cat.id),
  }));
}
