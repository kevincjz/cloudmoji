import { useState } from "react";

interface AboutPanelProps {
  onClose: () => void;
}

const FAQ_ITEMS = [
  {
    q: "How do we use Cloudmoji?",
    a: "Cloudmoji has two modes — switch between them using the tabs at the bottom:\n\n🗣️ Words — Tap any emoji to hear the word spoken aloud. Build sentences in the typing row and replay them!\n\n🔢 Count — Tap emojis one by one to count them. The app says the number out loud and tracks your progress with dots.\n\nWe save Cloudmoji as an app on our home screen and run it in Guided Access mode — this locks the phone to just Cloudmoji so your little one can tap freely without accidentally switching apps or pressing buttons they shouldn't!",
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
    a: "English, Mandarin Chinese, and Bahasa Melayu. Tap the language button (EN / 中文 / BM) in the top right to switch.",
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
            That's how Cloudmoji was born. Tap an emoji, hear the word — in English, Mandarin, or Malay.
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
            <a
              href="https://ko-fi.com/kevincjz"
              target="_blank"
              rel="noopener noreferrer"
              className="active:scale-95"
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 8,
                background: "rgba(78,205,196,0.2)",
                border: "1.5px solid rgba(78,205,196,0.3)",
                borderRadius: 14,
                padding: "10px 20px",
                color: "#4ECDC4",
                fontSize: 14,
                fontWeight: 900,
                cursor: "pointer",
                textDecoration: "none",
                fontFamily: "'Nunito', sans-serif",
                transition: "all 0.2s",
              }}
            >
              <span style={{ fontSize: 18 }}>☕</span>
              Buy us a coffee
            </a>
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
                a={"Cloudmoji is designed with your child's privacy in mind.\n\n• We do not collect any personal information from children or parents.\n• We do not require accounts, logins, or registration.\n• We do not use cookies or tracking pixels.\n• Usage statistics (tap counts, favourite emojis) are stored only on your device in localStorage and are never sent to any server.\n• We use Vercel Analytics, which collects anonymous, aggregated page view data (visitor count, country, device type). No individual users are identified.\n• Cloudmoji does not contain ads, in-app purchases, or links to social media.\n• We comply with COPPA (Children's Online Privacy Protection Act) and Singapore's PDPA.\n\nFor questions, reach out to Kevin via ko-fi.com/kevincjz."}
              />
              <FAQItem
                q="Terms of Use"
                a={"By using Cloudmoji, you agree to the following:\n\n• Cloudmoji is provided free of charge, \"as is\", without warranty of any kind.\n• Cloudmoji is intended for use by children under parental supervision.\n• We recommend using Guided Access (iOS) or Screen Pinning (Android) to keep your child safely within the app.\n• Text-to-speech quality depends on your device and may vary.\n• We reserve the right to update or discontinue Cloudmoji at any time.\n• Cloudmoji is a personal project by Kevin and PQ, not a commercial product.\n\nLast updated: March 2026."}
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
                q="v1.2 — 2 April 2026"
                a={"• Added 27 new emojis to Words mode (133 → 160 total)\n• Added 27 new countables to Count mode (28 → 55 total)\n• Fixed Chinese counting grammar: use 两 (liǎng) instead of 二 (èr) for counting with measure words\n• Fixed English irregular plurals (fish, butterfly, strawberry, bus, cherry)\n• Added Malay classifiers (penjodoh bilangan): ekor, biji, keping, buah, kuntum, batang, pasang, tangkai"}
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
            Cloudmoji v1.2 — cloudmoji.app
          </div>
        </div>
      </div>
    </div>
  );
}
