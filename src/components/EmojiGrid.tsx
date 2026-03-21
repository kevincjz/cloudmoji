import type { EmojiEntry } from "../types";
import { EmojiButton } from "./EmojiButton";

interface EmojiGridProps {
  emojis: EmojiEntry[];
  bounceIdx: number | null;
  onTap: (item: EmojiEntry, idx: number) => void;
}

export function EmojiGrid({ emojis, bounceIdx, onTap }: EmojiGridProps) {
  return (
    <div
      data-testid="emoji-grid"
      className="flex-1 overflow-y-auto"
      style={{
        padding: "2px 10px 24px",
        WebkitOverflowScrolling: "touch",
      }}
    >
      <div
        className="grid gap-2"
        style={{
          gridTemplateColumns: "repeat(auto-fill, minmax(72px, 1fr))",
        }}
      >
        {emojis.map((item, i) => (
          <EmojiButton
            key={item.emoji + item.cat}
            emoji={item.emoji}
            isBouncing={bounceIdx === i}
            onClick={() => onTap(item, i)}
          />
        ))}
      </div>
    </div>
  );
}
