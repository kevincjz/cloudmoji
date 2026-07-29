// Renders tools/og-image/card.html to public/og-image.png at the Open Graph
// canvas size (1200×630), using Playwright's Chromium so the emoji render in
// full colour — the static PNG this replaces had them as black silhouettes.
//
// Run: npm run og:image
import { chromium } from "playwright";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const card = resolve(here, "card.html");
const out = resolve(here, "../../public/og-image.png");

const browser = await chromium.launch();
const page = await browser.newPage({
  viewport: { width: 1200, height: 630 },
  deviceScaleFactor: 1,
});
await page.goto("file://" + card, { waitUntil: "networkidle" });
// Belt and braces: make sure the web fonts have actually painted.
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(300);
await page.screenshot({ path: out, clip: { x: 0, y: 0, width: 1200, height: 630 } });
await browser.close();
console.log("wrote " + out);
