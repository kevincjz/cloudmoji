import { test, expect, devices } from "@playwright/test";
import { freezeAnimations } from "./speech";

/**
 * A phone in landscape gives ~320px of height once Safari's chrome is drawn.
 * Stacking a header, typing row, category bar and tab bar into that left the
 * emoji grid under one row tall. These lock in the rail layout that fixed it.
 */
// Real Safari-landscape height: the browser's own chrome has already taken a
// quarter of the screen by the time the app renders.
test.use({ ...devices["iPhone 15 Pro Max landscape"], viewport: { width: 852, height: 320 } });

test.describe("landscape rail", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("emoji-grid").waitFor();
    await freezeAnimations(page);
  });

  test("the rail replaces the horizontal category and tab bars", async ({ page }) => {
    await expect(page.getByTestId("side-rail")).toBeVisible();
    await expect(page.getByTestId("category-bar")).toHaveCount(0);
    await expect(page.getByTestId("tab-bar")).toHaveCount(0);
    // the tabs still exist, in the rail
    await expect(page.getByTestId("tab-words")).toBeVisible();
    await expect(page.getByTestId("tab-count")).toBeVisible();
  });

  test("the grid gets at least 2 rows and the worst case is bounded", async ({ page }) => {
    const m = await page.getByTestId("emoji-grid").evaluate((el) => ({
      h: el.clientHeight,
      screenfuls: el.scrollHeight / el.clientHeight,
    }));
    // pre-rail this was 77px / 21 screenfuls
    expect(m.h, "grid height in landscape").toBeGreaterThanOrEqual(180);
    expect(m.screenfuls, "screenfuls to reach the end").toBeLessThan(12);
  });

  test("most categories are reachable without scrolling the rail", async ({ page }) => {
    const visible = await page.getByTestId("side-rail").evaluate((rail) => {
      const scroll = rail.firstElementChild!;
      const sb = scroll.getBoundingClientRect();
      return [...scroll.querySelectorAll("button")].filter((c) => {
        const b = c.getBoundingClientRect();
        return b.top >= sb.top - 1 && b.bottom <= sb.bottom + 1;
      }).length;
    });
    expect(visible, "categories visible without scrolling").toBeGreaterThanOrEqual(6);
  });

  test("switching tabs from the rail works and keeps the rail", async ({ page }) => {
    await page.getByTestId("tab-count").click();
    await page.getByTestId("count-item-0").waitFor();
    await expect(page.getByTestId("side-rail")).toBeVisible();
    await page.getByTestId("tab-words").click();
    await expect(page.getByTestId("emoji-grid")).toBeVisible();
  });

  test("a rail category filters the grid", async ({ page }) => {
    const all = await page.getByTestId("emoji-grid").locator("button").count();
    await page.getByTestId("rail-cat-fruits").click();
    await expect
      .poll(() => page.getByTestId("emoji-grid").locator("button").count())
      .toBeLessThan(all);
  });

  test("portrait still uses the horizontal bars", async ({ page }) => {
    await page.setViewportSize({ width: 430, height: 932 });
    await page.reload();
    await page.getByTestId("emoji-grid").waitFor();
    await expect(page.getByTestId("side-rail")).toHaveCount(0);
    await expect(page.getByTestId("category-bar")).toBeVisible();
    await expect(page.getByTestId("tab-bar")).toBeVisible();
  });
});

test.describe("scroll affordances", () => {
  test("the rail fades in the direction that has hidden categories", async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("side-rail").waitFor();
    const scroll = page.locator('[data-testid="side-rail"] .no-scroll');
    await expect
      .poll(() => scroll.evaluate((el) => el.scrollHeight > el.clientHeight + 1))
      .toBe(true);

    const fade = (side: string) =>
      page.getByTestId(`scroll-hint-${side}`).evaluate((el) => +getComputedStyle(el).opacity);

    // at the top: nothing hidden above, more below
    await expect.poll(() => fade("bottom")).toBe(1);
    expect(await fade("top")).toBe(0);

    await scroll.evaluate((el) => el.scrollTo({ top: el.scrollHeight }));
    await expect.poll(() => fade("top")).toBe(1);
    expect(await fade("bottom")).toBe(0);

    await scroll.evaluate((el) => el.scrollTo({ top: 0 }));
    await expect.poll(() => fade("bottom")).toBe(1);
  });

  test("the fade never blocks a tap", async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("side-rail").waitFor();
    for (const side of ["top", "bottom"]) {
      const pe = await page
        .getByTestId(`scroll-hint-${side}`)
        .evaluate((el) => getComputedStyle(el).pointerEvents);
      expect(pe, `${side} fade must not intercept taps`).toBe("none");
    }
  });
});

/**
 * Safe-area behaviour was previously untestable: env() cannot be overridden, so
 * notch handling could only be checked by eye on a real device — which is how
 * the inset came to be applied twice, floating the rail off the screen edge.
 * Routing insets through --sai-* variables makes a notched device simulable.
 */
test.describe("safe-area insets (simulated notch)", () => {
  const NOTCH = 59;

  const applyNotch = (page: import("@playwright/test").Page) =>
    page.addStyleTag({
      content: `:root { --sai-left: ${NOTCH}px; --sai-right: ${NOTCH}px; --sai-bottom: 21px; }`,
    });

  test("the rail paints to the screen edge but keeps its icons clear of the notch", async ({
    page,
  }) => {
    await page.goto("/");
    await page.getByTestId("side-rail").waitFor();
    await freezeAnimations(page);
    await applyNotch(page);

    const box = await page.getByTestId("side-rail").boundingBox();
    expect(box!.x, "rail background must reach the physical left edge").toBe(0);

    const firstCat = await page.getByTestId("rail-cat-all").boundingBox();
    expect(
      firstCat!.x,
      "rail icons must sit clear of the notch",
    ).toBeGreaterThanOrEqual(NOTCH);

    // The rail grows by the inset rather than padding into it, so the icons
    // keep their full width instead of being squeezed.
    expect(box!.width).toBeGreaterThanOrEqual(148 + NOTCH - 1);
  });

  test("no dead strip is left between the screen edge and the rail", async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("side-rail").waitFor();
    await freezeAnimations(page);
    await applyNotch(page);
    // Whatever is painted at x=0 must be the rail, not the page gradient
    // showing through an indent.
    const atEdge = await page.evaluate(() => {
      const el = document.elementFromPoint(2, window.innerHeight / 2);
      return el?.closest('[data-testid="side-rail"]') !== null;
    });
    expect(atEdge, "the left edge must belong to the rail").toBe(true);
  });

  test("the grid stays clear of the opposite rounded corner", async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("emoji-grid").waitFor();
    await freezeAnimations(page);
    await applyNotch(page);
    const tiles = page.getByTestId("emoji-grid").locator("button");
    const last = await tiles.nth(7).boundingBox();
    expect(last!.x + last!.width).toBeLessThanOrEqual(852 - NOTCH + 1);
  });
});

/**
 * Every case below was found by measuring a simulated notched device, not by a
 * failing test — the suite had no way to see them. These lock them shut.
 */
test.describe("landscape geometry under a notch", () => {
  const notch = (page: import("@playwright/test").Page) =>
    page.addStyleTag({
      content: `:root { --sai-left: 59px; --sai-right: 59px; --sai-bottom: 21px; }`,
    });

  test("the word bubble centres on the grid, not on the whole screen", async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("emoji-grid").waitFor();
    await freezeAnimations(page);
    await notch(page);

    await page.getByTestId("emoji-grid").locator("button").first().click();
    const bubble = page.getByTestId("word-bubble");
    await bubble.waitFor();

    const grid = (await page.getByTestId("emoji-grid").boundingBox())!;
    const b = (await bubble.boundingBox())!;
    const gridCentre = grid.x + grid.width / 2;
    const bubbleCentre = b.x + b.width / 2;
    // It used to sit half a rail-width (~78px) to the left of the grid centre.
    expect(Math.abs(bubbleCentre - gridCentre)).toBeLessThan(24);
  });

  test("the word bubble clears the home indicator", async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("emoji-grid").waitFor();
    await freezeAnimations(page);
    await notch(page);
    await page.getByTestId("emoji-grid").locator("button").first().click();
    const b = (await page.getByTestId("word-bubble").boundingBox())!;
    const h = page.viewportSize()!.height;
    expect(b.y + b.height, "bubble must sit above the 21px indicator band").toBeLessThanOrEqual(h - 21);
  });

  test("Count mode keeps Shuffle on screen at every round size", async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("tab-count").click();
    await page.getByTestId("count-item-0").waitFor();
    await freezeAnimations(page);
    await notch(page);
    const h = page.viewportSize()!.height;

    // count starts at 3 and climbs to 9; it used to push the controls off-screen from 5.
    for (let round = 0; round < 6; round++) {
      const items = await page.getByTestId(/^count-item-/).count();
      for (let i = 0; i < items; i++) await page.getByTestId(`count-item-${i}`).click();
      const shuffle = (await page.getByTestId("count-shuffle").boundingBox())!;
      expect(shuffle.y + shuffle.height, `round ${round}: Shuffle must stay on screen`)
        .toBeLessThanOrEqual(h);
      const next = page.getByTestId("count-next");
      if (await next.count()) {
        const nb = (await next.boundingBox())!;
        expect(nb.y + nb.height, `round ${round}: Next must stay on screen`).toBeLessThanOrEqual(h);
        await next.click();
        await page.getByTestId("count-item-0").waitFor();
      }
    }
  });

  test("the stats panel keeps its controls reachable", async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("emoji-grid").waitFor();
    await freezeAnimations(page);
    await notch(page);
    for (let i = 0; i < 5; i++) await page.getByTestId("app-title").click();
    await page.getByTestId("gate-input").fill(
      String(
        Number(await page.getByTestId("parental-gate").innerText().then((t) => t.match(/What is (\d+)/)![1])) *
          Number((await page.getByTestId("parental-gate").innerText()).match(/× (\d+)/)![1]),
      ),
    );
    await page.getByTestId("gate-submit").click();
    const panel = page.getByTestId("stats-panel");
    await panel.waitFor();

    const h = page.viewportSize()!.height;
    // The card is clamped and scrollable, so the requirement is that every
    // control can be REACHED — not that it happens to be visible immediately.
    // Before the clamp the card overflowed the scrim itself, with nothing
    // scrollable, so these were unreachable at any scroll position.
    const card = page.getByTestId("stats-panel").locator("> div");
    await expect(card).toBeVisible();
    const cardBox = (await card.boundingBox())!;
    expect(cardBox.y, "the card must not hang off the top").toBeGreaterThanOrEqual(0);
    expect(cardBox.y + cardBox.height, "or off the bottom").toBeLessThanOrEqual(h + 1);

    for (const id of ["stats-close", "stats-export", "stats-clear"]) {
      const el = page.getByTestId(id);
      if (!(await el.count())) continue; // export/clear need data to exist
      await el.scrollIntoViewIfNeeded();
      const box = (await el.boundingBox())!;
      expect(box.y, `${id} must be reachable`).toBeGreaterThanOrEqual(0);
      expect(box.y + box.height, `${id} must be reachable`).toBeLessThanOrEqual(h + 1);
    }
  });
});
