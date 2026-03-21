interface WordBubbleProps {
  emoji: string;
  word: string;
  id: number;
}

export function WordBubble({ emoji, word, id }: WordBubbleProps) {
  return (
    <div key={id} style={{ animation: "wordFloat 2.2s ease-in-out forwards" }}>
      <span
        data-testid="word-bubble"
        style={{
          background:
            "linear-gradient(135deg, rgba(255,107,107,0.2), rgba(78,205,196,0.2))",
          padding: "5px 18px",
          borderRadius: 18,
          fontWeight: 900,
          fontSize: 18,
          letterSpacing: 0.5,
          border: "1.5px solid rgba(255,255,255,0.1)",
          color: "#fff",
          display: "inline-flex",
          alignItems: "center",
          gap: 6,
        }}
      >
        <span style={{ fontSize: 22 }}>{emoji}</span>
        {word}
      </span>
    </div>
  );
}
