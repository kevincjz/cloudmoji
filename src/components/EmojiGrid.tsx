import { useCallback, useEffect, useRef } from "react";
import type { EmojiEntry, Category } from "../types";
import { EmojiButton } from "./EmojiButton";
import type { EmojiSection } from "../lib/sections";

/** A chip tap. The token is what makes tapping the same chip twice jump again. */
export interface SectionJump {
  id: Category;
  token: number;
}

interface EmojiGridProps {
  sections: EmojiSection[];
  /** `emoji + cat` of the tile that is mid-bounce, or null. */
  bounceKey: string | null;
  jumpTo: SectionJump | null;
  /** Fires when scrolling moves the list into a different section. */
  onActiveSection: (id: Category) => void;
  onTap: (item: EmojiEntry) => void;
}

/** `padding: 2px 10px 24px` on the scroll container, split out because the jump
 *  arithmetic has to subtract the top inset to land the header flush. */
const PAD_TOP = 2;

/** How far past the top edge a header counts as "crossed". Absorbs the
 *  container's own top padding and sub-pixel scroll offsets. */
const CROSSED = 8;

/** How long the list takes to glide to a tapped section. */
const SCROLL_MS = 320;

/**
 * One long scrollable list of every emoji, grouped into category sections.
 *
 * It used to be a filtered grid: tapping Animals replaced the contents with
 * animals. A 27-month-old tried to scroll past the end of that grid looking for
 * the other categories — his model was that the categories are *places in one
 * list*, which is better than the one we shipped. So the list is continuous, the
 * headers mark the sections, and the chips scroll rather than filter.
 *
 * That also deletes a failure state rather than guarding it. When a chip
 * filtered, a parent switching that category off in Settings left the child on a
 * permanently blank grid — `CLAUDE.md` rule 4 — and the fix was a fallback
 * handler. A disabled category simply has no section here, and the rest of the
 * list is untouched, so there is nothing left to fall back from.
 */
export function EmojiGrid({
  sections,
  bounceKey,
  jumpTo,
  onActiveSection,
  onTap,
}: EmojiGridProps) {
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const headers = useRef(new Map<string, HTMLElement>());
  const activeRef = useRef<Category | null>(null);

  /**
   * Which section the child is looking at: the last header to have crossed the
   * top of the viewport.
   *
   * Deliberately the top edge and not, say, the middle. A half-viewport rule
   * reads well on a phone and is wrong on an iPad, where nine columns fit the
   * whole of Fruits into two rows — the Food header is already past the middle
   * before anything has been scrolled at all, so the app opened with the second
   * chip lit while the child was looking at the first.
   */
  const recompute = useCallback(() => {
    const container = scrollRef.current;
    if (!container) return;
    const top = container.getBoundingClientRect().top;
    let current: Category | null = null;
    for (const section of sections) {
      const el = headers.current.get(section.id);
      if (!el) continue;
      if (el.getBoundingClientRect().top - top <= CROSSED) current = section.id;
    }
    // The end of the list is the last section, whatever the arithmetic above
    // says. On a wide screen the final section is short — twelve faces across
    // nine columns is two rows — so its header can never get past the middle,
    // and without this the last chip could not be lit by scrolling *or* by
    // tapping it. "You are at the end" and "you are in the last section" are
    // the same statement.
    if (container.scrollTop + container.clientHeight >= container.scrollHeight - 2) {
      current = sections[sections.length - 1]?.id ?? current;
    }
    // The first section owns the top of the list even before its header has
    // crossed anything.
    if (!current) current = sections[0]?.id ?? null;
    if (current && current !== activeRef.current) {
      activeRef.current = current;
      onActiveSection(current);
    }
  }, [sections, onActiveSection]);

  // Held in a ref as well so the jump below can call it without taking it as a
  // dependency — `recompute` changes identity when the language does, and a
  // jump effect that re-ran on that would fling the child back to whichever
  // chip they last tapped every time a parent switched language.
  const recomputeRef = useRef(recompute);

  useEffect(() => {
    recomputeRef.current = recompute;
    const el = scrollRef.current;
    if (!el) return;
    el.addEventListener("scroll", recompute, { passive: true });
    // The observer, not a one-off call: it fires once on observe — which is the
    // real first reading, after the flex column has resolved a height — and
    // again on rotation.
    const ro = new ResizeObserver(recompute);
    ro.observe(el);
    return () => {
      el.removeEventListener("scroll", recompute);
      ro.disconnect();
    };
  }, [recompute]);

  // Read out as primitives so the effect can depend on them directly — see the
  // dependency comment at the foot of it.
  const jumpId = jumpTo?.id;
  const jumpToken = jumpTo?.token;

  useEffect(() => {
    if (!jumpId) return;
    const target = headers.current.get(jumpId);
    const container = scrollRef.current;
    if (!target || !container) return;
    const delta =
      target.getBoundingClientRect().top - container.getBoundingClientRect().top;
    const from = container.scrollTop;
    const to = from + delta - PAD_TOP;

    // Hand-rolled rather than `scrollTo({ behavior: "smooth" })`.
    //
    // Native smooth scrolling is a no-op in more environments than is
    // comfortable — it does nothing under the automation browser here, and
    // "Reduce Motion" turns it off on a real phone. Every one of those cases
    // would leave the chips looking tappable and doing *nothing*, which is the
    // failure state this whole change exists to remove. A glide rather than a
    // jump because the point being taught is that it is the same list, moving.
    let raf = 0;
    let done = false;
    const start = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / SCROLL_MS);
      // ease-out cubic: quick off the mark, settles rather than slams.
      container.scrollTop = from + (to - from) * (1 - Math.pow(1 - t, 3));
      if (t < 1) raf = requestAnimationFrame(step);
      else done = true;
      // The scroll event is what normally drives the chips, and it is not
      // guaranteed to be delivered for a programmatic scroll in a tab that is
      // not painting. Recomputing here means the chip follows the jump even
      // then — a lit chip pointing at a section the child is not on is worse
      // than no chip at all.
      recomputeRef.current();
    };
    raf = requestAnimationFrame(step);

    // The animation is a courtesy; arriving is not. `requestAnimationFrame`
    // never fires in a tab that is not being painted — a backgrounded PWA, an
    // automation browser — and a chip that quietly does nothing is exactly the
    // failure this change exists to delete. So the destination is guaranteed on
    // a timer regardless of whether a single frame was ever drawn.
    const guard = setTimeout(() => {
      if (!done) {
        container.scrollTop = to;
        recomputeRef.current();
      }
    }, SCROLL_MS + 60);

    return () => {
      cancelAnimationFrame(raf);
      clearTimeout(guard);
    };
    // Depends on the two primitives rather than on the object, so the token is
    // genuinely load-bearing: a fresh object per tap works today only because
    // nothing memoises it, and the day something does, a child tapping Animals
    // after scrolling away would get nothing at all.
  }, [jumpId, jumpToken]);

  // Deliberately no scroll chevron on this list, in either orientation.
  //
  // The affordance exists for the *horizontal* category strip, where a chip is
  // clipped mid-word and nothing says there is more sideways. Scrolling a
  // vertical list of emojis is a gesture a toddler already has, and the rows are
  // visibly cut off at the bottom of the screen — the owner watched his son do
  // it without a prompt. A hint here would be decoration over a solved problem.
  return (
    <div
      ref={scrollRef}
      data-testid="emoji-grid"
      className="flex-1 overflow-y-auto"
      style={{
        padding: `${PAD_TOP}px 10px 24px`,
        WebkitOverflowScrolling: "touch",
      }}
    >
      {sections.map((section) => (
        <section key={section.id} data-testid={`section-${section.id}`}>
          <h2
            ref={(el) => {
              if (el) headers.current.set(section.id, el);
              else headers.current.delete(section.id);
            }}
            data-testid={`section-header-${section.id}`}
            className="flex items-center gap-2"
            style={{ padding: "10px 2px 6px", margin: 0 }}
          >
            <span style={{ fontSize: 18, lineHeight: 1 }}>{section.icon}</span>
            <span
              style={{
                fontFamily: "'Nunito', sans-serif",
                fontSize: 14,
                fontWeight: 800,
                color: "#4ECDC4",
                letterSpacing: 0.5,
              }}
            >
              {section.label}
            </span>
            {/* Runs the label out to the edge, so a section reads as a band
                across the list rather than as a floating word. */}
            <span
              aria-hidden
              style={{ flex: 1, height: 1, background: "rgba(255,255,255,0.06)" }}
            />
          </h2>
          <div
            className="grid gap-2"
            style={{ gridTemplateColumns: "repeat(auto-fill, minmax(72px, 1fr))" }}
          >
            {section.emojis.map((item) => (
              <EmojiButton
                key={item.emoji + item.cat}
                emoji={item.emoji}
                isBouncing={bounceKey === item.emoji + item.cat}
                onClick={() => onTap(item)}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
