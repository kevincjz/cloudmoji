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
