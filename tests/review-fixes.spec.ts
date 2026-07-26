import { test, expect } from "@playwright/test";
import { instrumentSpeech, spoken, clearSpoken, selectLanguage, freezeAnimations } from "./speech";

test.beforeEach(async ({ page }) => {
  await instrumentSpeech(page);
  await page.goto("/");
  await page.getByTestId("emoji-grid").waitFor();
  await freezeAnimations(page);
});

test.describe("queued audio is cancellable", () => {
  const queueThree = async (page: import("@playwright/test").Page) => {
    const tiles = page.getByTestId("emoji-grid").locator("button");
    for (const i of [0, 1, 2]) await tiles.nth(i).click();
    await clearSpoken(page);
    await page.getByTestId("replay-btn").click();
    await page.waitForTimeout(150);
  };

  test("mute stops a replay already in flight", async ({ page }) => {
    await queueThree(page);
    const atCut = (await spoken(page)).length;
    await page.getByTestId("mute-btn").click();
    await page.waitForTimeout(3000);
    expect((await spoken(page)).length, "nothing may speak after mute").toBe(atCut);
  });

  test("clear stops a replay already in flight", async ({ page }) => {
    await queueThree(page);
    const atCut = (await spoken(page)).length;
    await page.getByTestId("clear-btn").click();
    await page.waitForTimeout(3000);
    expect((await spoken(page)).length).toBe(atCut);
  });

  test("switching language stops the old language mid-replay", async ({ page }) => {
    await queueThree(page);
    const atCut = (await spoken(page)).length;
    await selectLanguage(page, "ja");
    await page.waitForTimeout(3000);
    const after = await spoken(page);
    const leaked = after.slice(atCut).filter((u) => /^[a-z ]+$/.test(u.text));
    expect(leaked, "no English words may play after switching to Japanese").toEqual([]);
  });

  test("leaving the tab stops a replay already in flight", async ({ page }) => {
    await queueThree(page);
    const atCut = (await spoken(page)).length;
    await page.getByTestId("tab-count").click();
    await page.waitForTimeout(3000);
    expect((await spoken(page)).length).toBe(atCut);
  });

  test("shuffle stops the previous round's completion phrase", async ({ page }) => {
    await page.getByTestId("tab-count").click();
    await page.getByTestId("count-item-0").waitFor();
    for (const i of [0, 1, 2]) await page.getByTestId(`count-item-${i}`).click();
    await clearSpoken(page);
    await page.getByTestId("count-shuffle").click();   // interrupt the celebration
    await page.waitForTimeout(3000);
    const after = await spoken(page);
    expect(after.filter((u) => u.text.endsWith("!")), "old completion phrase leaked").toEqual([]);
  });
});

test.describe("English plurals", () => {
  test("every countable pluralises correctly at 2", async ({ page }) => {
    const wrong = await page.evaluate(async () => {
      const c = await import("/src/data/countables.ts");
      const IRREG: Record<string, string> = {
        tooth: "teeth", mouse: "mice", foot: "feet", child: "children",
        person: "people", goose: "geese", sheep: "sheep", fish: "fish",
        man: "men", woman: "women", leaf: "leaves", knife: "knives", piano: "pianos",
      };
      const regular = (n: string) => {
        if (n === "fish") return "fish";
        if (n.endsWith("y") && !/[aeiou]y$/.test(n)) return n.slice(0, -1) + "ies";
        if (/(?:s|sh|ch|x|z)$/.test(n)) return n + "es";
        return n + "s";
      };
      const out: string[] = [];
      for (const item of c.COUNTABLES as { en: string; enPlural?: string }[]) {
        const app = item.enPlural ?? regular(item.en);
        const expected = IRREG[item.en] ?? regular(item.en);
        if (app !== expected) out.push(`${item.en} -> "${app}" (expected "${expected}")`);
      }
      return out;
    });
    expect(wrong).toEqual([]);
  });

  test("Count mode speaks teeth and mice, not tooths and mouses", async ({ page }) => {
    const phrases = await page.evaluate(async () => {
      const c = await import("/src/data/countables.ts");
      const build = (item: { en: string; enPlural?: string }, num: number) => {
        const w = c.NUMBER_WORDS.en[num - 1];
        let p = item.en;
        if (num > 1 && item.enPlural) p = item.enPlural;
        else if (num > 1) {
          if (item.en === "fish") p = "fish";
          else if (item.en.endsWith("y") && !/[aeiou]y$/.test(item.en)) p = item.en.slice(0, -1) + "ies";
          else if (/(?:s|sh|ch|x|z)$/.test(item.en)) p = item.en + "es";
          else p = item.en + "s";
        }
        return `${w} ${p}`;
      };
      const find = (en: string) => (c.COUNTABLES as { en: string; enPlural?: string }[]).find((x) => x.en === en)!;
      return { tooth: build(find("tooth"), 2), mouse: build(find("mouse"), 2) };
    });
    expect(phrases.tooth).toBe("two teeth");
    expect(phrases.mouse).toBe("two mice");
  });
});

test.describe("resilience", () => {
  test("a corrupt stored language recovers instead of crashing Count mode", async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (e) => errors.push(e.message));
    await page.addInitScript(() => localStorage.setItem("cm_lang", JSON.stringify("es")));
    await page.reload();
    await page.getByTestId("emoji-grid").waitFor();
    await freezeAnimations(page);   // reload drops the injected stylesheet
    await expect(page.getByTestId("lang-toggle")).toContainText("EN");
    await page.getByTestId("tab-count").click();
    await page.getByTestId("count-item-0").waitFor();
    await page.getByTestId("count-item-0").click();
    await page.waitForTimeout(400);
    expect(errors, "corrupt language must not crash").toEqual([]);
  });

  test("the typing row is capped at 50", async ({ page }) => {
    await page.evaluate(() => {
      const g = document.querySelector('[data-testid="emoji-grid"]')!;
      const btns = [...g.querySelectorAll("button")].slice(0, 55);
      btns.forEach((b) => (b as HTMLElement).click());
    });
    await page.waitForTimeout(500);
    await expect(page.getByTestId("typed-emoji")).toHaveCount(50);
  });
});

test.describe("child safety", () => {
  test("the Ko-fi link is behind a parental gate", async ({ page }) => {
    await page.getByTestId("about-btn").click();
    await page.getByTestId("kofi-btn").click();
    await expect(page.getByTestId("parental-gate")).toBeVisible();
    // a toddler mashing the button must not get through
    await page.getByTestId("gate-input").fill("1");
    await page.getByTestId("gate-submit").click();
    await expect(page.getByTestId("gate-error")).toBeVisible();
    await expect(page.getByTestId("parental-gate")).toBeVisible();
  });

  test("the stats panel is behind a parental gate", async ({ page }) => {
    // exactly 5 — a 6th would land on the gate overlay, not the title
    for (let i = 0; i < 5; i++) await page.getByTestId("app-title").click();
    await expect(page.getByTestId("parental-gate")).toBeVisible();
    await expect(page.getByTestId("stats-panel")).toHaveCount(0);
    await page.getByTestId("gate-cancel").click();
    await expect(page.getByTestId("stats-panel")).toHaveCount(0);
  });

  test("the privacy policy names every collector that ships", async ({ page }) => {
    await page.getByTestId("about-btn").click();
    const panel = page.getByTestId("about-panel");
    await panel.getByRole("button", { name: /Privacy Policy/ }).click();
    const text = await panel.innerText();
    for (const term of ["Web Analytics", "Speed Insights", "Google Fonts"]) {
      expect(text, `privacy policy must disclose ${term}`).toContain(term);
    }
    expect(text, "must not claim certified compliance").not.toMatch(/We comply with COPPA/);
  });
});

test.describe("touch targets", () => {
  test("every child-facing control is at least 64px tall", async ({ page }) => {
    const tiles = page.getByTestId("emoji-grid").locator("button");
    for (const i of [0, 1]) await tiles.nth(i).click();
    await page.waitForTimeout(200);

    const small = await page.evaluate(() => {
      const CHILD_FACING = [
        "typed-emoji", "replay-btn", "delete-btn", "clear-btn",
        "count-shuffle", "count-next", "tab-words", "tab-count",
      ];
      const out: string[] = [];
      document.querySelectorAll("[data-testid]").forEach((el) => {
        const id = (el as HTMLElement).dataset.testid!;
        const isChild = CHILD_FACING.includes(id) || id.startsWith("cat-") || id.startsWith("emoji-");
        if (!isChild) return;
        const b = el.getBoundingClientRect();
        if (b.height > 0 && b.height < 64) out.push(`${id}: ${Math.round(b.height)}px`);
      });
      return out;
    });
    expect(small, "child-facing controls under the 64px rule").toEqual([]);
  });
});
