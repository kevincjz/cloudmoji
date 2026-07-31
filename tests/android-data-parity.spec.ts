import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { build, serialise } from "../tools/generate-ios-data/index";

const BUNDLED = resolve(
  import.meta.dirname,
  "../android/app/src/main/assets/EmojiData.json",
);

test.describe("Android data parity", () => {
  test("the bundled JSON matches the shared TypeScript content", () => {
    const committed = readFileSync(BUNDLED, "utf8");
    expect(committed).toBe(serialise(build()));
  });

  test("the Android bundle carries the complete content set", () => {
    const data = JSON.parse(readFileSync(BUNDLED, "utf8"));

    expect(data.emojis).toHaveLength(200);
    expect(data.countables).toHaveLength(84);
    expect(data.languages).toHaveLength(5);
    expect(data.categories).toHaveLength(9);
    expect(Object.keys(data.animalSounds)).toHaveLength(20);
  });
});

