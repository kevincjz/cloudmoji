import { test, expect } from "@playwright/test";

test("privacy page clearly separates the native app from the website", async ({ page }) => {
  await page.goto("/privacy/index.html");

  await expect(page).toHaveTitle("Cloudmoji Privacy Policy");
  await expect(page.getByRole("heading", { name: "Privacy Policy" })).toBeVisible();
  await expect(page.getByText("The native app does not collect or send personal data")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Separate Cloudmoji website" })).toBeVisible();
  await expect(page.getByText(/Vercel Web Analytics/)).toBeVisible();
  await expect(page.getByText(/first camera permission prompt is shown only after/i)).toBeVisible();
  await expect(page.getByText(/one protected play-session record/i)).toBeVisible();
  await expect(page.getByText(/play-session commands use Apple/i)).toBeVisible();
  await expect(page.getByText(/phone applies the command and returns the authoritative state/i)).toBeVisible();

  const html = await page.locator("html").evaluate((node) => node.outerHTML);
  expect(html).not.toContain("@vercel/analytics");
  expect(html).not.toContain("fonts.googleapis.com/css");

  await expect(page.getByRole("link", { name: "Support" })).toHaveAttribute(
    "href",
    "/support",
  );
});
