import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { build, serialise } from "../tools/generate-ios-data/index";

const BUNDLED = resolve(
  import.meta.dirname,
  "../ios/CloudmojiCore/Sources/CloudmojiCore/Resources/EmojiData.json",
);

test.describe("iOS data parity", () => {
  test("the bundled JSON matches the web data", () => {
    const committed = readFileSync(BUNDLED, "utf8");
    // Regenerating is the fix: npm run generate:ios
    expect(committed).toBe(serialise(build()));
  });

  test("the bundled JSON carries the full content set", () => {
    const data = JSON.parse(readFileSync(BUNDLED, "utf8"));
    expect(data.emojis).toHaveLength(200);
    expect(data.countables).toHaveLength(84);
    expect(data.languages).toHaveLength(5);
    expect(data.categories).toHaveLength(9);
    for (const [lang, words] of Object.entries(data.numberWords)) {
      expect(words, `numberWords.${lang}`).toHaveLength(10);
    }
  });

  test("Word mode and Count mode name the same thing in zh, ms, ja, and tl", () => {
    const data = JSON.parse(readFileSync(BUNDLED, "utf8"));
    const byEmoji = new Map(data.emojis.map((e: never) => [e["emoji"], e]));
    const mismatches: string[] = [];
    for (const item of data.countables) {
      const word = byEmoji.get(item.emoji) as Record<string, string> | undefined;
      if (!word) continue; // count-only entries such as 🌟 are legitimate
      // zh and ms bake a measure word into the countable noun ("只狗", "ekor anjing"),
      // so the countable must end with the bare word AND be strictly longer than it —
      // otherwise a dropped classifier (countable === bare word) would pass trivially.
      for (const lang of ["zh", "ms"]) {
        const countable = item[lang];
        const bare = word[lang];
        if (!countable.endsWith(bare) || countable.length <= bare.length) {
          mismatches.push(
            `${item.emoji} ${lang}: countable "${countable}" must end with Word-mode "${bare}" and be strictly longer (missing its classifier)`,
          );
        }
      }
      // ja and tl stay bare — the counter lives in the number word (ja) or the linker (tl) —
      // so the countable noun must be exactly equal to the Word-mode noun.
      for (const lang of ["ja", "tl"]) {
        const countable = item[lang];
        const bare = word[lang];
        if (countable !== bare) {
          mismatches.push(
            `${item.emoji} ${lang}: countable "${countable}" must equal Word-mode "${bare}" (ja/tl stay bare)`,
          );
        }
      }
    }
    expect(mismatches).toEqual([]);
  });
});
