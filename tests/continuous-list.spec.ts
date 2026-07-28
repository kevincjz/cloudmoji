import { test, expect, type Page, type Locator } from "@playwright/test";
import { freezeAnimations } from "./speech";

/**
 * The emoji grid is one continuous list of every emoji, grouped into category
 * sections, and the category chips scroll it rather than filter it.
 *
 * This came from watching a 27-month-old: with a filtered grid he tried to
 * scroll past the end of Animals looking for the other categories. His model —
 * one list, categories as places inside it — is the one that ships now.
 *
 * These run under every project in `playwright.config.ts`, portrait and
 * landscape, because the complaint originated in landscape and the rail has to
 * do exactly what the strip does. `chip()` is the only thing that differs.
 */

/** In list order — the first section, and the last. */
const FIRST_SECTION = "fruits";
const LAST_SECTION = "faces";
/** The first tile of the first section, and of the last. */
const FIRST_GLYPH = "🍎";
const LAST_SECTION_GLYPH = "😀";

const ALL_SECTIONS = [
  "fruits",
  "food",
  "animals",
  "vehicles",
  "nature",
  "objects",
  "people",
  "faces",
];

/** Landscape swaps the strip for the rail; the ids differ, the job does not. */
async function chip(page: Page, id: string): Promise<Locator> {
  const rail = page.getByTestId(`rail-cat-${id}`);
  return (await rail.count()) > 0 ? rail : page.getByTestId(`cat-${id}`);
}

const grid = (page: Page) => page.getByTestId("emoji-grid");

const scrollTop = (page: Page) => grid(page).evaluate((el) => el.scrollTop);

/** How far a section's header sits below the top of the visible grid. */
function headerOffset(page: Page, id: string) {
  return page.evaluate((section) => {
    const g = document.querySelector('[data-testid="emoji-grid"]')!;
    const h = document.querySelector(`[data-testid="section-header-${section}"]`);
    if (!h) return null;
    return h.getBoundingClientRect().top - g.getBoundingClientRect().top;
  }, id);
}

async function activeChips(page: Page) {
  return page.evaluate(() =>
    [...document.querySelectorAll("[data-active='true']")].map(
      (el) => (el as HTMLElement).dataset.testid!,
    ),
  );
}

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await grid(page).waitFor();
  await freezeAnimations(page);
});

test.describe("one continuous list", () => {
  /**
   * The whole premise. Before this change the grid held one category at a time,
   * so this could not have been true for any pair of categories at once.
   *
   * Mutation: restore `EMOJIS.filter((e) => e.cat === category)` as the grid's
   * input — the last section's glyph leaves the DOM and this fails.
   */
  test("every category is in the DOM at once, with no tap anywhere", async ({ page }) => {
    await expect(page.getByTestId(`emoji-${FIRST_GLYPH}`)).toHaveCount(1);
    await expect(page.getByTestId(`emoji-${LAST_SECTION_GLYPH}`)).toHaveCount(1);
    for (const section of ALL_SECTIONS) {
      await expect(
        page.getByTestId(`section-header-${section}`),
        `${section} has no section header`,
      ).toHaveCount(1);
    }
    // 200 emojis, one list. A filtered grid holds at most 56.
    expect(await grid(page).locator("button").count()).toBe(200);
  });

  /**
   * The behaviour the whole change is about, and the easiest one to fake: a
   * test that merely checks the Animals header exists proves nothing.
   *
   * So this asserts the list MOVED — a scroll offset that was zero is not, and
   * the target header has arrived at the top of the viewport — and that the
   * apple is still in the list rather than having been filtered out from under
   * the child.
   *
   * Mutation: delete `container.scrollTop = ...` from the tween in
   * `EmojiGrid.tsx` (or make `onSelectCategory` a no-op). Both leave the header
   * present and every other assertion in this file green.
   */
  test("tapping a chip moves the list to that section", async ({ page }) => {
    expect(await scrollTop(page), "the list should start at the top").toBe(0);
    await expect(page.getByTestId(`emoji-${FIRST_GLYPH}`)).toBeInViewport();

    await (await chip(page, "animals")).click();

    // Landed flush: the header is at the top of the grid, not merely somewhere
    // on screen. Polled, because the list glides rather than teleporting — an
    // instant read lands mid-flight.
    await expect
      .poll(() => headerOffset(page, "animals").then((v) => Math.abs(v!)), { timeout: 5000 })
      .toBeLessThan(8);
    expect(await scrollTop(page), "the list did not move").toBeGreaterThan(200);
    await expect(page.getByTestId("emoji-🐶")).toBeInViewport();

    // Nothing was removed — the fruit is still there, just above the fold.
    await expect(page.getByTestId(`emoji-${FIRST_GLYPH}`)).toHaveCount(1);
    await expect(page.getByTestId(`emoji-${FIRST_GLYPH}`)).not.toBeInViewport();
  });

  /**
   * Tapping the same chip a second time has to work. The jump is driven by a
   * state change, and a naive implementation passes the section id alone — so
   * the second tap is a no-op value and the child, who has scrolled away, taps
   * Animals and nothing happens.
   *
   * Mutation: drop `token` from the `SectionJump` the chip sets.
   */
  test("the same chip taps twice", async ({ page }) => {
    const animals = await chip(page, "animals");
    await animals.click();
    // Waited out in full, not merely started. Polling on "the offset moved" is
    // satisfied mid-glide, and scrolling away from under a tween that is still
    // running just gets overwritten by it — which left this test proving that
    // the FIRST tap worked twice.
    await expect
      .poll(() => headerOffset(page, "animals").then((v) => Math.abs(v!)), { timeout: 5000 })
      .toBeLessThan(8);

    await grid(page).evaluate((el) => {
      el.scrollTop = el.scrollHeight;
    });
    await expect
      .poll(() => headerOffset(page, "animals").then((v) => v!))
      .toBeLessThan(-500);

    await animals.click();
    // `Math.abs`, and it is load-bearing: after scrolling to the foot of the
    // list the Animals header is thousands of pixels ABOVE the viewport, so a
    // bare `< 8` is satisfied by the list never moving at all. That is exactly
    // the shape of dead test this project keeps finding.
    await expect
      .poll(() => headerOffset(page, "animals").then((v) => Math.abs(v!)), { timeout: 5000 })
      .toBeLessThan(8);
  });

  /**
   * The child's own way round the app: no chips at all, just scrolling.
   *
   * Walks the list a screen at a time and records which section headers came
   * into view. Every one of the eight must, which is only possible if they are
   * all in the same scrollable list.
   *
   * Mutation: filter the grid again — seven of the eight never appear.
   */
  test("scrolling alone reaches every category", async ({ page }) => {
    const seen = new Set<string>();
    const step = await grid(page).evaluate((el) => el.clientHeight * 0.75);
    const maxScroll = await grid(page).evaluate((el) => el.scrollHeight - el.clientHeight);
    expect(maxScroll, "the list must actually overflow").toBeGreaterThan(0);

    for (let top = 0; top <= maxScroll + step; top += step) {
      await grid(page).evaluate((el, y) => {
        el.scrollTop = y;
      }, top);
      const visible = await page.evaluate(() => {
        const g = document.querySelector('[data-testid="emoji-grid"]')!;
        const box = g.getBoundingClientRect();
        return [...document.querySelectorAll('[data-testid^="section-header-"]')]
          .filter((h) => {
            const r = h.getBoundingClientRect();
            return r.bottom > box.top && r.top < box.bottom;
          })
          .map((h) => (h as HTMLElement).dataset.testid!.replace("section-header-", ""));
      });
      visible.forEach((id) => seen.add(id));
    }

    expect([...seen].sort(), "categories reachable by scrolling alone").toEqual(
      [...ALL_SECTIONS].sort(),
    );
  });

  /**
   * The chips have to follow the scroll, or the highlight is a lie: the child
   * flings down to the faces and the strip still says Fruits.
   *
   * Driven by scrolling only — no chip is tapped — so a highlight wired
   * straight to the tap cannot pass.
   *
   * Mutation: delete the `onActiveSection(current)` call in `EmojiGrid.tsx`.
   */
  test("the lit chip follows the scroll, with nothing tapped", async ({ page }) => {
    await expect.poll(() => activeChips(page)).toContain(
      (await (await chip(page, FIRST_SECTION)).getAttribute("data-testid"))!,
    );

    await grid(page).evaluate((el) => {
      el.scrollTop = el.scrollHeight;
    });

    const lastId = (await (await chip(page, LAST_SECTION)).getAttribute("data-testid"))!;
    await expect
      .poll(() => activeChips(page), { timeout: 5000 })
      .toEqual([lastId]);

    await grid(page).evaluate((el) => {
      el.scrollTop = 0;
    });
    const firstId = (await (await chip(page, FIRST_SECTION)).getAttribute("data-testid"))!;
    await expect.poll(() => activeChips(page), { timeout: 5000 }).toEqual([firstId]);
  });

  /**
   * A section header is not a touch target and must not eat the gap between two
   * rows of emojis, in either direction.
   */
  test("a section header keeps the tiles on either side of it apart", async ({ page }) => {
    const m = await page.evaluate(() => {
      const header = document.querySelector('[data-testid="section-header-food"]')!;
      const above = document.querySelector('[data-testid="emoji-🍐"]')!.getBoundingClientRect();
      const below = document.querySelector('[data-testid="emoji-🍚"]')!.getBoundingClientRect();
      return { gap: below.top - above.bottom, header: header.getBoundingClientRect().height };
    });
    expect(m.header, "the header drew nothing").toBeGreaterThan(10);
    // The last tile of one section and the first of the next are still two
    // adjacent child-facing targets — `CLAUDE.md` rule 2 does not stop at a
    // section boundary, and a zero-height header would put them 0px apart.
    expect(m.gap, "gap between the last Fruit and the first Food").toBeGreaterThanOrEqual(8);
  });
});
