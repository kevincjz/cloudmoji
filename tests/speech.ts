import type { Page } from "@playwright/test";

export interface SpokenUtterance {
  text: string;
  lang: string;
  /** name of the SpeechSynthesisVoice actually assigned, or null if none matched */
  voice: string | null;
  voiceLang: string | null;
  rate: number;
  pitch: number;
}

declare global {
  interface Window {
    __spoken: SpokenUtterance[];
  }
}

/**
 * Records every speechSynthesis.speak() call. Headless Chromium exposes the API
 * but produces no audible output, so we assert on what the app HANDED to the
 * speech engine — the text, the BCP-47 lang, and the resolved voice.
 */
export async function instrumentSpeech(page: Page) {
  await page.addInitScript(() => {
    window.__spoken = [];
    // Patch the PROTOTYPE, not the instance: WebKit hands back a fresh
    // SpeechSynthesis wrapper on each `window.speechSynthesis` access, so an
    // own-property override is silently discarded.
    const proto = Object.getPrototypeOf(window.speechSynthesis) as SpeechSynthesis;
    const orig = proto.speak;
    proto.speak = function (this: SpeechSynthesis, u: SpeechSynthesisUtterance) {
      if (u && u.text) {
        window.__spoken.push({
          text: u.text,
          lang: u.lang,
          voice: u.voice ? u.voice.name : null,
          voiceLang: u.voice ? u.voice.lang : null,
          rate: u.rate,
          pitch: u.pitch,
        });
      }
      return orig.call(this, u);
    };
  });
}

/**
 * Freeze CSS animations. The count tiles run an infinite `gentleFloat`, so
 * Playwright's actionability check never sees them settle; and the language
 * menu's `popIn` scales from 0, so boundingBox() catches it mid-scale.
 * Neither is an app defect — both make measurement non-deterministic.
 */
export async function freezeAnimations(page: Page) {
  await page.addStyleTag({
    content: `*, *::before, *::after {
      animation: none !important;
      transition: none !important;
    }`,
  });
}

export const spoken = (page: Page) => page.evaluate(() => window.__spoken);
export const clearSpoken = (page: Page) =>
  page.evaluate(() => {
    window.__spoken = [];
  });

export async function lastSpoken(page: Page): Promise<SpokenUtterance | undefined> {
  const all = await spoken(page);
  return all[all.length - 1];
}

/** Switch language via the picker. */
export async function selectLanguage(page: Page, id: string) {
  const menu = page.getByTestId("lang-menu");
  if (!(await menu.isVisible().catch(() => false))) {
    await page.getByTestId("lang-toggle").click();
  }
  await page.getByTestId(`lang-${id}`).click();
  await menu.waitFor({ state: "hidden" });
}

export const EXPECTED_SPEECH_LANG: Record<string, string> = {
  en: "en-US",
  zh: "zh-CN",
  ms: "ms-MY",
  ja: "ja-JP",
  tl: "fil-PH",
};
