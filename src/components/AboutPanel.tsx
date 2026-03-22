interface AboutPanelProps {
  onClose: () => void;
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
          <div style={{ fontSize: 36, textAlign: "center" }}>
            ☁️
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

          <div style={{
            fontSize: 10,
            fontWeight: 800,
            color: "rgba(255,255,255,0.15)",
            textAlign: "center",
          }}>
            Cloudmoji v1.0 — cloudmoji.app
          </div>
        </div>
      </div>
    </div>
  );
}
