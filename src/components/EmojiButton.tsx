interface EmojiButtonProps {
  emoji: string;
  isBouncing: boolean;
  onClick: () => void;
}

export function EmojiButton({ emoji, isBouncing, onClick }: EmojiButtonProps) {
  return (
    <button
      data-testid={`emoji-${emoji}`}
      onClick={onClick}
      className="active:scale-85"
      style={{
        background: "rgba(255,255,255,0.04)",
        border: "1.5px solid rgba(255,255,255,0.06)",
        borderRadius: 18,
        padding: 0,
        height: 72,
        fontSize: 38,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        transition: "transform 0.1s",
        animation: isBouncing ? "bounceEmoji 0.35s ease" : "none",
        WebkitTapHighlightColor: "transparent",
      }}
    >
      {emoji}
    </button>
  );
}
