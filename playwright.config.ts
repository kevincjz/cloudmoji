import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:5173",
    trace: "retain-on-failure",
  },
  // iPhone/iPad descriptors carry defaultBrowserType "webkit", so these run on
  // WebKit — the engine iOS Safari actually uses. Pixel carries "chromium".
  projects: [
    { name: "iphone-15-pro-max", use: { ...devices["iPhone 15 Pro Max"] } },
    {
      name: "iphone-15-pro-max-landscape",
      use: { ...devices["iPhone 15 Pro Max landscape"] },
    },
    // Smallest screen we support — where cramped layouts show up first.
    { name: "iphone-se", use: { ...devices["iPhone SE"] } },
    { name: "ipad", use: { ...devices["iPad (gen 7)"] } },
    // Android/Chromium, to catch engine-specific behaviour WebKit hides.
    { name: "pixel-7", use: { ...devices["Pixel 7"] } },
  ],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
