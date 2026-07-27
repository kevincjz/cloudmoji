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

  test("the generated JSON stays inside the Swift closed enums", () => {
    // Language and Category are closed Swift enums (CaseIterable, decoded
    // straight from this JSON) with nothing in the Swift layer checking that
    // the generated content stays inside them. A new category makes
    // EmojiRepository.init throw at launch; a new language is silently
    // dropped by the generator, which hardcodes the five per-language fields.
    // This test is the guard: it fails loudly, on the web side, before either
    // can reach a device.
    const data = JSON.parse(readFileSync(BUNDLED, "utf8"));
    const swiftFile = "ios/CloudmojiCore/Sources/CloudmojiCore/Models.swift";

    const CATEGORY_CASES = [
      "fruits", "food", "animals", "vehicles", "nature", "objects", "people", "faces",
    ];
    const LANGUAGE_CASES = ["en", "zh", "ms", "ja", "tl"];

    for (const e of data.emojis as { emoji: string; cat: string }[]) {
      expect(
        CATEGORY_CASES.includes(e.cat),
        `emoji ${e.emoji} has cat "${e.cat}", which is not one of ${CATEGORY_CASES.join(", ")} — ` +
          `the Category enum in ${swiftFile} must be updated too`,
      ).toBe(true);
    }

    for (const c of data.categories as { id: string }[]) {
      expect(
        c.id === "all" || CATEGORY_CASES.includes(c.id),
        `category tab id "${c.id}" is not "all" or one of ${CATEGORY_CASES.join(", ")} — ` +
          `the Category enum in ${swiftFile} must be updated too`,
      ).toBe(true);
    }

    const languageIds = (data.languages as { id: string }[]).map((l) => l.id);
    for (const id of languageIds) {
      expect(
        LANGUAGE_CASES.includes(id),
        `languages[] contains id "${id}", which is not one of ${LANGUAGE_CASES.join(", ")} — ` +
          `the Language enum in ${swiftFile} must be updated too`,
      ).toBe(true);
    }
    expect(
      [...languageIds].sort(),
      `languages[].id must be exactly ${LANGUAGE_CASES.join(", ")} (found: ${languageIds.join(", ")}) — ` +
        `the Language enum in ${swiftFile} must match`,
    ).toEqual([...LANGUAGE_CASES].sort());

    const numberWordKeys = Object.keys(data.numberWords);
    for (const key of numberWordKeys) {
      expect(
        LANGUAGE_CASES.includes(key),
        `numberWords has key "${key}", which is not one of ${LANGUAGE_CASES.join(", ")} — ` +
          `the Language enum in ${swiftFile} must be updated too`,
      ).toBe(true);
    }
    expect(
      [...numberWordKeys].sort(),
      `Object.keys(numberWords) must be exactly ${LANGUAGE_CASES.join(", ")} (found: ${numberWordKeys.join(", ")}) — ` +
        `the Language enum in ${swiftFile} must match`,
    ).toEqual([...LANGUAGE_CASES].sort());

    for (const l of data.languages as { id: string; voicePrefixes: string[] }[]) {
      expect(
        l.voicePrefixes.length > 0,
        `language "${l.id}" has an empty voicePrefixes list — VoiceResolver would have nothing to try`,
      ).toBe(true);
    }
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
