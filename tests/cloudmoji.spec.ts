import { test, expect } from "@playwright/test";
import {
  instrumentSpeech,
  spoken,
  clearSpoken,
  lastSpoken,
  selectLanguage,
  freezeAnimations,
  EXPECTED_SPEECH_LANG,
} from "./speech";

const LANGS = ["en", "zh", "ms", "ja", "tl"] as const;

/** apple / banana in each language — the first two tiles of the "All" grid. */
const FIRST_WORDS: Record<string, [string, string]> = {
  en: ["apple", "banana"],
  zh: ["苹果", "香蕉"],
  ms: ["epal", "pisang"],
  ja: ["りんご", "バナナ"],
  tl: ["mansanas", "saging"],
};

const TOGGLE_LABEL: Record<string, string> = {
  en: "EN",
  zh: "中文",
  ms: "BM",
  ja: "日本語",
  tl: "TL",
};

test.beforeEach(async ({ page }) => {
  await instrumentSpeech(page);
  await page.goto("/");
  await page.getByTestId("emoji-grid").waitFor();
  await freezeAnimations(page);
});

test.describe("language picker", () => {
  test("offers exactly the five implemented languages", async ({ page }) => {
    await page.getByTestId("lang-toggle").click();
    const menu = page.getByTestId("lang-menu");
    await expect(menu).toBeVisible();

    for (const id of LANGS) {
      await expect(page.getByTestId(`lang-${id}`)).toBeVisible();
    }
    await expect(menu.locator("button")).toHaveCount(LANGS.length);

    // Thai is deliberately NOT implemented — guard against it creeping in.
    await expect(page.getByTestId("lang-th")).toHaveCount(0);
    await expect(menu).not.toContainText("Thai");
  });

  test("every option meets the 44px minimum touch target", async ({ page }) => {
    await page.getByTestId("lang-toggle").click();
    for (const id of LANGS) {
      const box = await page.getByTestId(`lang-${id}`).boundingBox();
      expect(box!.height, `lang-${id} height`).toBeGreaterThanOrEqual(44);
    }
  });

  test("selection persists across a reload", async ({ page }) => {
    await selectLanguage(page, "ja");
    await expect(page.getByTestId("lang-toggle")).toContainText("日本語");
    await page.reload();
    await page.getByTestId("emoji-grid").waitFor();
    await expect(page.getByTestId("lang-toggle")).toContainText("日本語");
  });
});

test.describe("audio on tap", () => {
  for (const lang of LANGS) {
    test(`${lang}: tapping an emoji speaks the right word in the right voice language`, async ({
      page,
    }) => {
      await selectLanguage(page, lang);
      await expect(page.getByTestId("lang-toggle")).toContainText(TOGGLE_LABEL[lang]);
      await clearSpoken(page);

      const tiles = page.getByTestId("emoji-grid").locator("button");
      await tiles.nth(0).click();
      await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(0);

      const first = await lastSpoken(page);
      expect(first!.text, "spoken text for the first tile").toBe(FIRST_WORDS[lang][0]);
      expect(first!.lang, "BCP-47 lang handed to speechSynthesis").toBe(
        EXPECTED_SPEECH_LANG[lang],
      );
      // Project rules: rate 0.85, pitch 1.1
      expect(first!.rate).toBeCloseTo(0.85, 2);
      expect(first!.pitch).toBeCloseTo(1.1, 2);

      await tiles.nth(1).click();
      await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(1);
      const second = await lastSpoken(page);
      expect(second!.text).toBe(FIRST_WORDS[lang][1]);
      expect(second!.lang).toBe(EXPECTED_SPEECH_LANG[lang]);
    });
  }

  test("mute stops speech, unmute resumes it", async ({ page }) => {
    const tiles = page.getByTestId("emoji-grid").locator("button");
    await clearSpoken(page);
    await page.getByTestId("mute-btn").click();
    await tiles.nth(0).click();
    await page.waitForTimeout(300);
    expect(await spoken(page), "muted tap must not speak").toHaveLength(0);

    await page.getByTestId("mute-btn").click();
    await tiles.nth(0).click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(0);
  });

  test("switching language re-speaks the same emoji in the new language", async ({ page }) => {
    const tiles = page.getByTestId("emoji-grid").locator("button");
    await selectLanguage(page, "en");
    await clearSpoken(page);
    await tiles.nth(0).click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(0);
    expect((await lastSpoken(page))!.text).toBe("apple");

    await selectLanguage(page, "tl");
    await clearSpoken(page);
    await tiles.nth(0).click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(0);
    expect((await lastSpoken(page))!.text).toBe("mansanas");
    expect((await lastSpoken(page))!.lang).toBe("fil-PH");
  });
});

test.describe("count mode", () => {
  test.beforeEach(async ({ page }) => {
    await page.getByTestId("tab-count").click();
    await page.getByTestId("count-item-0").waitFor();
  });

  test("japanese counts noun-first with the ～つ counter", async ({ page }) => {
    await selectLanguage(page, "ja");
    await page.getByTestId("count-shuffle").click();
    await clearSpoken(page);

    await page.getByTestId("count-item-0").click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(0);
    const one = await lastSpoken(page);
    expect(one!.lang).toBe("ja-JP");
    expect(one!.text, "count 1 must end in ひとつ").toMatch(/ ひとつ$/);
    // noun comes first, and no の / を / が particle is inserted
    expect(one!.text).not.toMatch(/^ひとつ/);
    expect(one!.text).not.toContain("の");

    await page.getByTestId("count-item-1").click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(1);
    expect((await lastSpoken(page))!.text).toMatch(/ ふたつ$/);

    await page.getByTestId("count-item-2").click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(2);
    expect((await lastSpoken(page))!.text).toMatch(/ みっつ$/);
  });

  test("tagalog applies the -ng / na linker correctly", async ({ page }) => {
    await selectLanguage(page, "tl");
    await page.getByTestId("count-shuffle").click();
    await clearSpoken(page);

    await page.getByTestId("count-item-0").click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(0);
    const one = await lastSpoken(page);
    expect(one!.lang).toBe("fil-PH");
    expect(one!.text, "1 = isang + noun").toMatch(/^isang /);

    await page.getByTestId("count-item-1").click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(1);
    expect((await lastSpoken(page))!.text).toMatch(/^dalawang /);

    await page.getByTestId("count-item-2").click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(2);
    expect((await lastSpoken(page))!.text).toMatch(/^tatlong /);
  });

  test("tagalog uses the separate 'na' linker for apat/anim/siyam", async ({ page }) => {
    await selectLanguage(page, "tl");
    // count starts at 3 and increments with Next!, so advance to 4
    await page.getByTestId("count-item-0").click();
    await page.getByTestId("count-item-1").click();
    await page.getByTestId("count-item-2").click();
    await page.getByTestId("count-next").click();
    await page.getByTestId("count-item-3").waitFor();
    await clearSpoken(page);

    for (let i = 0; i < 4; i++) await page.getByTestId(`count-item-${i}`).click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThanOrEqual(4);
    const all = await spoken(page);
    const fourth = all.filter((u) => u.text.startsWith("apat"));
    expect(fourth.length, 'count 4 must speak "apat na <noun>"').toBeGreaterThan(0);
    expect(fourth[0].text).toMatch(/^apat na \S/);
  });

  test("chinese and malay counting still work", async ({ page }) => {
    await selectLanguage(page, "zh");
    await page.getByTestId("count-shuffle").click();
    await clearSpoken(page);
    await page.getByTestId("count-item-0").click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(0);
    expect((await lastSpoken(page))!.text).toMatch(/^一/);
    expect((await lastSpoken(page))!.lang).toBe("zh-CN");

    await selectLanguage(page, "ms");
    await page.getByTestId("count-shuffle").click();
    await clearSpoken(page);
    await page.getByTestId("count-item-0").click();
    await expect.poll(() => spoken(page).then((s) => s.length)).toBeGreaterThan(0);
    expect((await lastSpoken(page))!.text).toMatch(/^satu /);
    expect((await lastSpoken(page))!.lang).toBe("ms-MY");
  });
});

test.describe("layout", () => {
  test("tab bar tap targets meet the 64px minimum", async ({ page }) => {
    for (const id of ["tab-words", "tab-count"]) {
      const box = await page.getByTestId(id).boundingBox();
      expect(box!.height, `${id} height`).toBeGreaterThanOrEqual(64);
    }
  });

  test("emoji grid scrolls and every tile meets the 64px minimum", async ({ page }) => {
    const grid = page.getByTestId("emoji-grid");
    const metrics = await grid.evaluate((el) => ({
      scrollHeight: el.scrollHeight,
      clientHeight: el.clientHeight,
    }));
    expect(metrics.scrollHeight, "grid must overflow so there is something to scroll").toBeGreaterThan(
      metrics.clientHeight,
    );

    await grid.evaluate((el) => el.scrollBy(0, 400));
    await expect.poll(() => grid.evaluate((el) => el.scrollTop)).toBeGreaterThan(0);

    const tile = await grid.locator("button").first().boundingBox();
    expect(tile!.height).toBeGreaterThanOrEqual(64);
  });

  test("the page itself never scrolls horizontally", async ({ page }) => {
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth + 1,
    );
    expect(overflow).toBe(false);
  });
});

test.describe("about panel", () => {
  test("FAQ lists all five languages and no unimplemented ones", async ({ page }) => {
    await page.getByTestId("about-btn").click();
    const panel = page.getByTestId("about-panel");
    await expect(panel).toBeVisible();

    await panel.getByRole("button", { name: /Which languages are supported/ }).click();
    const text = await panel.innerText();
    for (const name of ["English", "Mandarin", "Melayu", "Japanese", "Tagalog"]) {
      expect(text, `FAQ should mention ${name}`).toContain(name);
    }
    expect(text, "Thai is not implemented and must not be advertised").not.toContain("Thai");
  });

  test("version history leads with v1.3", async ({ page }) => {
    await page.getByTestId("about-btn").click();
    const panel = page.getByTestId("about-panel");
    await expect(panel).toContainText("Version History");
    await expect(panel).toContainText("v1.3");
    await expect(panel).toContainText("Cloudmoji v1.3");
  });
});
