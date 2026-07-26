import { useState } from "react";
import { ParentalGate } from "./ParentalGate";

interface AboutPanelProps {
  onClose: () => void;
}

const FAQ_ITEMS = [
  {
    q: "How do we use Cloudmoji?",
    a: "Cloudmoji has two modes — switch between them using the tabs at the bottom:\n\n🗣️ Words — Tap any emoji to hear the word spoken aloud. Build sentences in the typing row and replay them!\n\n🧮 Count — Tap emojis one by one to count them. The app says the number out loud and tracks your progress with dots.\n\nWe save Cloudmoji as an app on our home screen and run it in Guided Access mode — this locks the phone to just Cloudmoji so your little one can tap freely without accidentally switching apps or pressing buttons they shouldn't!",
  },
  {
    q: "How do I save Cloudmoji to my home screen?",
    a: "On iPhone/iPad: Open Cloudmoji in Safari → tap the Share button (square with arrow) → scroll down and tap \"Add to Home Screen\" → tap \"Add\". On Android: Open in Chrome → tap the three-dot menu (⋮) → tap \"Add to Home Screen\" → tap \"Add\". It will appear as an app icon!",
  },
  {
    q: "How do I turn on Guided Access? (iPhone/iPad)",
    a: "1. Go to Settings → Accessibility → Guided Access → turn it ON.\n2. Set a passcode.\n3. Open Cloudmoji, then triple-click the side button (or home button).\n4. Tap \"Start\" in the top right.\n5. To exit: triple-click the side button again and enter your passcode.",
  },
  {
    q: "How do I lock the screen to one app? (Android)",
    a: "1. Go to Settings → Security → App Pinning (or Screen Pinning) → turn it ON.\n2. Open Cloudmoji, then open Recent Apps (square button).\n3. Tap the Cloudmoji app icon at the top of its card → tap \"Pin this app\".\n4. To exit: hold Back and Overview buttons together.",
  },
  {
    q: "Which languages are supported?",
    a: "Five languages: English, Mandarin Chinese (中文), Bahasa Melayu (BM), Japanese (日本語), and Tagalog (TL).\n\nTap the language button in the top right to open the picker, then tap the language you want.\n\nJapanese uses hiragana and katakana — no kanji — so it matches what Japanese children learn first. Counting uses the ～つ counter (ひとつ, ふたつ, みっつ), the first counting system Japanese kids are taught.",
  },
  {
    q: "Does it work offline?",
    a: "Yes! After your first visit, Cloudmoji works without an internet connection. The text-to-speech uses your device's built-in voices.",
  },
  {
    q: "Is my child's data collected?",
    a: "No. Cloudmoji has no accounts, no tracking of children, and no personal data collection. Usage stats are stored only on your device and never sent anywhere.",
  },
];

function FAQItem({ q, a }: { q: string; a: string }) {
  const [open, setOpen] = useState(false);

  return (
    <div
      style={{
        background: "rgba(255,255,255,0.04)",
        border: "1px solid rgba(255,255,255,0.06)",
        borderRadius: 12,
        overflow: "hidden",
      }}
    >
      <button
        onClick={() => setOpen(!open)}
        style={{
          width: "100%",
          background: "none",
          border: "none",
          padding: "10px 12px",
          color: "#fff",
          fontSize: 13,
          fontWeight: 800,
          cursor: "pointer",
          textAlign: "left",
          fontFamily: "'Nunito', sans-serif",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: 8,
        }}
      >
        <span>{q}</span>
        <span style={{
          fontSize: 12,
          color: "rgba(255,255,255,0.3)",
          flexShrink: 0,
          transition: "transform 0.2s",
          transform: open ? "rotate(180deg)" : "rotate(0deg)",
        }}>
          ▼
        </span>
      </button>
      {open && (
        <div style={{
          padding: "0 12px 10px",
          fontSize: 12,
          fontWeight: 700,
          lineHeight: 1.6,
          color: "rgba(255,255,255,0.45)",
          whiteSpace: "pre-line",
        }}>
          {a}
        </div>
      )}
    </div>
  );
}

export function AboutPanel({ onClose }: AboutPanelProps) {
  const [gate, setGate] = useState(false);

  return (
    <div
      data-testid="about-panel"
      className="fixed inset-0 z-50 flex items-center justify-center"
      style={{ background: "rgba(0,0,0,0.7)" }}
      onClick={onClose}
    >
      <div
        className="rounded-2xl"
        style={{
          background: "linear-gradient(160deg, #1A1145, #0D2137)",
          border: "1.5px solid rgba(255,255,255,0.1)",
          padding: "24px",
          maxWidth: 340,
          width: "90%",
          maxHeight: "85dvh",
          overflowY: "auto",
          fontFamily: "'Nunito', sans-serif",
          color: "#fff",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between" style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 16, fontWeight: 900 }}>About Cloudmoji</div>
          <button
            data-testid="about-close"
            onClick={onClose}
            style={{
              background: "rgba(255,255,255,0.1)",
              border: "none",
              borderRadius: 10,
              width: 32,
              height: 32,
              fontSize: 14,
              cursor: "pointer",
              color: "#fff",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            ✕
          </button>
        </div>

        <div className="flex flex-col gap-4">
          <div className="flex justify-center">
            <img
              src="/icons/icon-192.png"
              alt="Cloudmoji"
              width={160}
              height={160}
              style={{ borderRadius: 20 }}
            />
          </div>

          <p style={{
            fontSize: 14,
            fontWeight: 700,
            lineHeight: 1.6,
            color: "rgba(255,255,255,0.7)",
            textAlign: "center",
            margin: 0,
          }}>
            Made with love by <span style={{ color: "#fff", fontWeight: 900 }}>Kevin</span> and <span style={{ color: "#fff", fontWeight: 900 }}>PQ</span> for our son <span style={{ color: "#fff", fontWeight: 900 }}>Cloud</span>.
          </p>

          <p style={{
            fontSize: 13,
            fontWeight: 700,
            lineHeight: 1.6,
            color: "rgba(255,255,255,0.45)",
            textAlign: "center",
            margin: 0,
          }}>
            One day, Cloud picked up a locked iPhone and started typing emojis — then said the words out loud, all on his own. We thought: what if we could turn that into a safe space that helps him (and every toddler) learn new words in multiple languages?
          </p>

          <p style={{
            fontSize: 13,
            fontWeight: 700,
            lineHeight: 1.6,
            color: "rgba(255,255,255,0.45)",
            textAlign: "center",
            margin: 0,
          }}>
            That's how Cloudmoji was born. Tap an emoji, hear the word — in English, Mandarin, Malay, Japanese, or Tagalog.
          </p>

          <div
            style={{
              background: "rgba(255,255,255,0.04)",
              border: "1px solid rgba(255,255,255,0.06)",
              borderRadius: 16,
              padding: "14px 16px",
              textAlign: "center",
            }}
          >
            <p style={{
              fontSize: 13,
              fontWeight: 800,
              color: "rgba(255,255,255,0.5)",
              margin: "0 0 10px",
            }}>
              If your little one enjoys Cloudmoji, consider supporting us so we can keep making it better!
            </p>
            <button
              data-testid="kofi-btn"
              onClick={() => setGate(true)}
              className="active:scale-95"
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 8,
                minHeight: 48,
                background: "rgba(78,205,196,0.2)",
                border: "1.5px solid rgba(78,205,196,0.3)",
                borderRadius: 14,
                padding: "10px 20px",
                color: "#4ECDC4",
                fontSize: 14,
                fontWeight: 900,
                cursor: "pointer",
                fontFamily: "'Nunito', sans-serif",
                transition: "all 0.2s",
              }}
            >
              <span style={{ fontSize: 18 }}>☕</span>
              Buy us a coffee
            </button>
          </div>

          {/* FAQ Section */}
          <div>
            <div style={{
              fontSize: 14,
              fontWeight: 900,
              marginBottom: 8,
            }}>
              FAQ
            </div>
            <div className="flex flex-col gap-2">
              {FAQ_ITEMS.map((item) => (
                <FAQItem key={item.q} q={item.q} a={item.a} />
              ))}
            </div>
          </div>

          {/* Privacy & Terms */}
          <div>
            <div style={{
              fontSize: 14,
              fontWeight: 900,
              marginBottom: 8,
            }}>
              Legal
            </div>
            <div className="flex flex-col gap-2">
              <FAQItem
                q="Privacy Policy"
                a={"Cloudmoji is designed with your child's privacy in mind. Here is exactly what happens.\n\nSTAYS ON YOUR DEVICE\n• Usage statistics (tap counts, favourite emojis) are stored only on your device in localStorage and are never sent to a server.\n• Your language choice is stored the same way.\n• No accounts, logins, or registration. No cookies or tracking pixels. No ads or in-app purchases.\n\nLEAVES YOUR DEVICE\n• Vercel Web Analytics receives a pageview when Cloudmoji loads, along with metadata such as referrer, approximate geography, operating system, browser and device type.\n• Vercel Speed Insights receives real-user performance measurements (how quickly pages render and respond).\n• Google Fonts is used for the app's typefaces, so your browser requests files from fonts.googleapis.com and fonts.gstatic.com on first load. Once cached, the app runs offline.\n• Neither collector is given a name, email, or any information you type. Neither is used to build a profile of your child.\n\nWHAT WE DO NOT YET OFFER\n• There is currently no in-app switch to turn analytics off. If you would prefer none of it, use the app offline after the first load, or block those domains at the network level. We are looking at adding a proper opt-out.\n\nYOUR DATA\n• Everything stored locally can be exported or deleted from the stats screen, behind the grown-ups-only gate.\n\nCloudmoji is built to be safe for children, and we have aimed at what COPPA and Singapore's PDPA ask for. We are parents, not lawyers, and this is a description of our data flows rather than a legal certification.\n\nFor questions, reach out to Kevin via ko-fi.com/kevincjz."}
              />
              <FAQItem
                q="Terms of Use"
                a={"By using Cloudmoji, you agree to the following:\n\n• Cloudmoji is provided free of charge, \"as is\", without warranty of any kind.\n• Cloudmoji is intended for use by children under parental supervision.\n• We recommend using Guided Access (iOS) or Screen Pinning (Android) to keep your child safely within the app.\n• Text-to-speech quality depends on your device and may vary.\n• We reserve the right to update or discontinue Cloudmoji at any time.\n• Cloudmoji is a personal project by Kevin and PQ, not a commercial product.\n\nLast updated: July 2026."}
              />
            </div>
          </div>

          {/* Version History */}
          <div>
            <div style={{
              fontSize: 14,
              fontWeight: 900,
              marginBottom: 8,
            }}>
              Version History
            </div>
            <div className="flex flex-col gap-2">
              <FAQItem
                q="v1.4 — 26 July 2026"
                a={"• Added 40 new emojis (160 → 200 total), in 9 themed groups: My Family, All About Me, Getting Dressed, Around the House, Mealtime & Treats, On the Road, More Animal Friends, Outside & Water, and Playtime & Music\n• Biggest gap closed: the People category had no caregivers at all and was the smallest in the app — it goes from 11 to 20 emojis with mommy, daddy, grandma, grandpa, boy and girl\n• 30 of the new emojis also work in Count mode (54 → 84 countables), each with the correct Chinese measure word and Malay penjodoh bilangan\n• Every new word was reviewed by an independent native-speaker pass in all 5 languages\n• Better Tagalog speech on devices with no Filipino voice installed (most of them): instead of falling back to an English voice, which mispronounces Tagalog, Cloudmoji now uses the Malay voice — both languages share the same vowels and the same \"ng\" sound. Install a Filipino voice under Settings → Accessibility → Spoken Content → Voices and Cloudmoji will use it automatically."}
              />
              <FAQItem
                q="v1.3 — 26 July 2026"
                a={"• Added Japanese (日本語) and Tagalog (TL) — all 160 emojis and 54 countables, plus category names and counting phrases\n• Japanese uses hiragana and katakana only (no kanji), with the ～つ counter children learn first: りんご みっつ\n• Tagalog uses the correct number linker: tatlong aso, but apat na aso\n• Language button now opens a picker instead of cycling — one tap to any of the 5 languages\n• 🤫 is now \"quiet\" instead of \"shh\", in every language\n• Fixed landscape mode: the grid was squeezed to under 2 rows and took 14 screenfuls to scroll. It now uses the full width (11 columns instead of 6) and needs 6\n• Fixed the bottom tab bar on notched iPhones — the home-indicator inset was eating 21px out of the tap target, leaving it at 42px instead of the intended 64px\n• Count tab icon changed from 🔢 to 🧮, which renders as a proper icon rather than a grey box"}
              />
              <FAQItem
                q="v1.2 — 2 April 2026"
                a={"• Added 27 new emojis to Words mode (133 → 160 total)\n• Added 27 new countables to Count mode (27 → 54 total)\n• Fixed Chinese counting grammar: use 两 (liǎng) instead of 二 (èr) for counting with measure words\n• Fixed English irregular plurals (fish, butterfly, strawberry, bus, cherry)\n• Added Malay classifiers (penjodoh bilangan): ekor, biji, keping, buah, kuntum, batang, pasang, tangkai"}
              />
              <FAQItem
                q="v1.1 — 25 March 2026"
                a={"• New Cloudculator counting game mode (🔢 Count tab)\n• Bottom tab bar to switch between Words and Count modes"}
              />
              <FAQItem
                q="v1.0 — 22 March 2026"
                a={"• 121 emojis across 8 categories\n• Text-to-speech in English, Mandarin, and Bahasa Melayu\n• Cloud mascot with 4 mood states\n• Typing row with replay, delete, and clear\n• Language toggle (EN / 中文 / BM)\n• Tap categories to hear the category name spoken\n• Milestone celebrations at 10, 25, 50, 100 taps\n• PWA with offline support\n• About page with FAQ, privacy policy, and Ko-fi support link\n• Deployed at cloudmoji.app"}
              />
            </div>
          </div>

          <div style={{
            fontSize: 10,
            fontWeight: 800,
            color: "rgba(255,255,255,0.15)",
            textAlign: "center",
          }}>
            Cloudmoji v1.4 — cloudmoji.app
          </div>
        </div>
      </div>

      {gate && (
        <ParentalGate
          action="This opens Ko-fi in your browser, outside Cloudmoji."
          onCancel={() => setGate(false)}
          onPass={() => {
            setGate(false);
            window.open("https://ko-fi.com/kevincjz", "_blank", "noopener,noreferrer");
          }}
        />
      )}
    </div>
  );
}
