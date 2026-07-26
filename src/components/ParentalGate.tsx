import { useState } from "react";

const CHALLENGES: ReadonlyArray<readonly [number, number]> = [
  [7, 8], [9, 6], [12, 7], [6, 11], [8, 9], [11, 8], [7, 12], [9, 7],
];
let challengeIndex = 0;

interface ParentalGateProps {
  /** What the parent is about to do, shown so they know why they were asked. */
  action: string;
  onPass: () => void;
  onCancel: () => void;
}

/**
 * A real gate, not a gesture. Five rapid taps is something a toddler produces by
 * accident; an arithmetic question they cannot read is not. Deliberately boring:
 * no timer, no penalty, no "wrong!" — a parent who misreads just tries again.
 */
export function ParentalGate({ action, onPass, onCancel }: ParentalGateProps) {
  // Rotate through a fixed set rather than randomising. The gate does not need
  // unpredictability — a 2-year-old cannot do arithmetic at all — and calling
  // Math.random() during render is impure.
  const [[a, b]] = useState(() => {
    const pair = CHALLENGES[challengeIndex % CHALLENGES.length];
    challengeIndex += 1;
    return pair;
  });
  const [entry, setEntry] = useState("");
  const [wrong, setWrong] = useState(false);

  const submit = () => {
    if (Number(entry) === a * b) onPass();
    else {
      setWrong(true);
      setEntry("");
    }
  };

  return (
    <div
      data-testid="parental-gate"
      className="fixed inset-0 z-[70] flex items-center justify-center"
      style={{ background: "rgba(0,0,0,0.82)", padding: 20 }}
      onClick={onCancel}
    >
      <div
        className="rounded-2xl"
        style={{
          background: "linear-gradient(160deg, #1A1145, #0D2137)",
          border: "1.5px solid rgba(255,255,255,0.12)",
          padding: 24,
          maxWidth: 340,
          width: "100%",
          fontFamily: "'Nunito', sans-serif",
          color: "#fff",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ fontSize: 16, fontWeight: 900, marginBottom: 6 }}>
          Grown-ups only
        </div>
        <div
          style={{
            fontSize: 13,
            fontWeight: 700,
            color: "rgba(255,255,255,0.5)",
            marginBottom: 16,
            lineHeight: 1.5,
          }}
        >
          {action}
        </div>

        <div style={{ fontSize: 22, fontWeight: 900, marginBottom: 10 }}>
          What is {a} × {b}?
        </div>

        <input
          data-testid="gate-input"
          inputMode="numeric"
          pattern="[0-9]*"
          value={entry}
          autoFocus
          onChange={(e) => {
            setEntry(e.target.value.replace(/\D/g, ""));
            setWrong(false);
          }}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          style={{
            width: "100%",
            minHeight: 52,
            borderRadius: 12,
            border: wrong
              ? "2px solid rgba(255,107,107,0.6)"
              : "2px solid rgba(255,255,255,0.15)",
            background: "rgba(255,255,255,0.06)",
            color: "#fff",
            fontSize: 20,
            fontWeight: 900,
            textAlign: "center",
            fontFamily: "'Nunito', sans-serif",
            marginBottom: wrong ? 6 : 14,
            boxSizing: "border-box",
          }}
        />
        {wrong && (
          <div
            data-testid="gate-error"
            style={{
              fontSize: 12,
              fontWeight: 800,
              color: "#FF6B6B",
              marginBottom: 10,
            }}
          >
            Not quite — have another go.
          </div>
        )}

        <div className="flex gap-2">
          <button
            data-testid="gate-cancel"
            onClick={onCancel}
            style={{
              flex: 1,
              minHeight: 48,
              borderRadius: 12,
              border: "2px solid rgba(255,255,255,0.12)",
              background: "none",
              color: "rgba(255,255,255,0.6)",
              fontSize: 14,
              fontWeight: 900,
              cursor: "pointer",
              fontFamily: "'Nunito', sans-serif",
            }}
          >
            Cancel
          </button>
          <button
            data-testid="gate-submit"
            onClick={submit}
            style={{
              flex: 1,
              minHeight: 48,
              borderRadius: 12,
              border: "2px solid rgba(78,205,196,0.4)",
              background: "rgba(78,205,196,0.2)",
              color: "#4ECDC4",
              fontSize: 14,
              fontWeight: 900,
              cursor: "pointer",
              fontFamily: "'Nunito', sans-serif",
            }}
          >
            Continue
          </button>
        </div>
      </div>
    </div>
  );
}
