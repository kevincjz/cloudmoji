# Claude Code — Development Guide

## The Golden Rule
Every session starts with:
```
Read CLAUDE.md then [your task]
```

## The Prototype-First Workflow
`reference/prototype.jsx` is a **fully working app**. It has all 121 emojis, TTS, the mascot, animations, and interactions. Your job is to PORT it to a proper project structure — not rewrite from scratch.

### Session 1: Scaffold + Port Core
```
Read CLAUDE.md. Scaffold a Vite + React + TypeScript + Tailwind project.
Read reference/prototype.jsx for the exact implementation.
Port the emoji data to src/data/emojis.ts (typed).
Port the CloudMascot component to src/components/CloudMascot.tsx.
Port the TTS logic to src/hooks/useTTS.ts (follow docs/engineering/WEB_TTS_GUIDE.md).
Build the main app with EmojiGrid, EmojiButton, tap-to-speak, mascot reactions.
Match the prototype's exact colors, fonts, layout, and animations.
```

### Session 2: Complete Features
```
Read CLAUDE.md. Continue from where Session 1 left off.
Port: TypingRow (replay, delete, clear), CategoryBar, LangToggle, WordBubble.
Add: localStorage persistence for language preference.
Add: Milestone mascot beaming at 10/25/50/100 taps.
Add: Measurement system from docs/ops/MEASUREMENT.md (hidden stats panel).
Responsive: test at 375px and 1024px.
```

### Session 3: PWA + Deploy
```
Read CLAUDE.md and docs/engineering/PWA_GUIDE.md.
Add vite-plugin-pwa with manifest, service worker, icons.
Add iOS meta tags (apple-touch-icon, web-app-capable, no-zoom viewport).
Add Open Graph meta tags for WhatsApp/LinkedIn sharing.
Generate app icons: cloud mascot on coral→teal gradient, 192px and 512px.
Create og-image.png (1200×630): dark bg, mascot, "Cloudmoji" text, a few emojis.
Test offline. Run Lighthouse. Deploy to Vercel.
```

### Session 4: Fix + Polish
```
Read CLAUDE.md. I tested and found: [list issues].
These Mandarin words sound wrong: [list].
Fix issues, adjust TTS, polish any rough edges.
```

## Prompting Tips

### Be Specific About Files
```
# Good
"Port the CloudMascot SVG from reference/prototype.jsx into src/components/CloudMascot.tsx.
Keep the exact same SVG paths, mood states, and animations."

# Bad
"Make a cloud mascot"
```

### Reference the Prototype
```
# Good
"The emoji data shape is in reference/prototype.jsx — the EMOJIS array.
Port it to src/data/emojis.ts with proper TypeScript types."

# Bad
"Create the emoji data"
```

### Don't Reinvent
```
# Good
"The word bubble animation is defined as @keyframes wordFloat in the prototype.
Use the same timing and easing."

# Bad
"Add a floating word animation"
```

## Code Recipes (From the Prototype)

### TTS Hook Pattern
```typescript
// src/hooks/useTTS.ts — see reference/prototype.jsx speak() function
// Key: cancel → set voice → speak, with iOS init workaround
```

### localStorage Hook
```typescript
export function useLocalStorage<T>(key: string, init: T): [T, (v: T) => void] {
  const [val, setVal] = useState<T>(() => {
    try { const s = localStorage.getItem(key); return s ? JSON.parse(s) : init; }
    catch { return init; }
  });
  useEffect(() => { localStorage.setItem(key, JSON.stringify(val)); }, [key, val]);
  return [val, setVal];
}
```

### CSS Animations (Copy from Prototype)
All keyframes are defined in the `<style>` block of `reference/prototype.jsx`. Copy them verbatim into a Tailwind-compatible CSS file or a `<style>` tag.

## Component Checklist
When porting each component, verify:
- [ ] TypeScript types for all props
- [ ] `data-testid` on interactive elements
- [ ] Touch target ≥ 72px on emoji buttons
- [ ] Tailwind classes (convert inline styles from prototype)
- [ ] Dark theme colors match the design system
- [ ] `:active` scale transform on all buttons
- [ ] Responsive at 375px and 1024px

## Deployment Checklist
Before `npx vercel`:
- [ ] `npm run build` — no errors
- [ ] `npm run preview` — test at localhost:4173
- [ ] All 121 emojis render in grid
- [ ] TTS works in EN and ZH on iPhone Safari
- [ ] Mascot reacts to taps (excited → speaking → happy)
- [ ] Mascot beams at 10th tap
- [ ] Language toggle persists after refresh
- [ ] Offline works (airplane mode after first load)
- [ ] "Add to Home Screen" works on iOS
- [ ] Lighthouse: PWA 90+, Performance 90+
- [ ] og-image.png and all meta tags present
