import { test, expect } from "@playwright/test";

test("support page exposes the designated private contact and safe guidance", async ({ page }) => {
  // Vite's development server serves public assets at their literal path.
  // Production maps the clean App Store URL, /support, to this same document.
  await page.goto("/support/index.html");

  await expect(page).toHaveTitle("Cloudmoji Support");
  await expect(page.getByRole("heading", { name: "Cloudmoji Support" })).toBeVisible();

  const logo = page.getByRole("img", { name: "Cloudmoji" });
  await expect(logo).toHaveAttribute("src", "../icons/icon-192.png");
  await expect
    .poll(() => logo.evaluate((image: HTMLImageElement) => image.naturalWidth))
    .toBe(192);

  const email = page.getByRole("link", { name: "kevin.chan@sproutlearn.co" });
  await expect(email).toHaveAttribute(
    "href",
    "mailto:kevin.chan@sproutlearn.co?subject=Cloudmoji%20Support",
  );

  await expect(page.getByText(/no names, photos, recordings/i)).toBeVisible();
  await expect(page.getByRole("link", { name: "Report a Problem" })).toHaveAttribute(
    "href",
    "https://reportaproblem.apple.com",
  );
  await expect(page.getByRole("heading", { name: "The play timer locked Cloudmoji" })).toBeVisible();
  await expect(page.getByText(/Cloudmoji’s own app lock, not an iPhone or iPad lock/i)).toBeVisible();
});
