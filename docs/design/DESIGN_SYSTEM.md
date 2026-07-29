# Design System — Cloudmoji

## Design Philosophy
**"Bold playground at night"** — A dark sky full of colorful emojis, presided over by a friendly cloud character. High energy but not chaotic. The dark background makes emojis the visual stars. The mascot provides warmth and personality. Everything is rounded, chunky, and inviting for tiny fingers.

## Color Palette

### Background
```css
--bg-primary:    #0F0E2A;  /* Deep indigo-black */
--bg-mid:        #1A1145;  /* Mid gradient */
--bg-edge:       #0D2137;  /* Dark blue edge */
--bg-gradient:   linear-gradient(160deg, #0F0E2A 0%, #1A1145 40%, #0D2137 100%);
```

### Brand Colors
```css
--coral:         #FF6B6B;  /* Primary — taps, mascot mouth, energy */
--teal:          #4ECDC4;  /* Secondary — active states, replay, mascot glow */
--gold:          #FFE66D;  /* Accent — celebrations, sparkles, beaming glow */
--teal-deep:     #44B8AC;  /* far stop of the count badge gradient */
```

### Surface
```css
--surface-card:     rgba(255, 255, 255, 0.04);
--surface-hover:    rgba(255, 255, 255, 0.06);
--surface-border:   rgba(255, 255, 255, 0.06);
--surface-border-strong: rgba(255, 255, 255, 0.12);  /* count tiles, header buttons, gate card */
--surface-active:   rgba(78, 205, 196, 0.20);   /* teal tint for active tabs */
--border-active:    rgba(78, 205, 196, 0.40);
```

### Text
```css
--text-primary:    #FFFFFF;
--text-tertiary:   rgba(255, 255, 255, 0.6);
--text-secondary:  rgba(255, 255, 255, 0.4);
--text-muted:      rgba(255, 255, 255, 0.2);
--text-disabled:   rgba(255, 255, 255, 0.12);
```

### Mascot
```css
--cloud-body:      #FFFFFF;
--cloud-highlight:  #F8FCFF;
--cloud-shadow:    #E8EEF4;
--blush:           #FFB5B5;
--blush-beaming:   #FF9E9E;
--mouth:           #FF6B6B;
--mouth-stroke:    #E55555;
--eyes:            #2D3436;
```

### Control Buttons
```css
--btn-replay:     rgba(78, 205, 196, 0.20);   /* teal */
--btn-replay-bdr: rgba(78, 205, 196, 0.30);
--btn-delete:     rgba(255, 179, 71, 0.20);    /* orange */
--btn-delete-bdr: rgba(255, 179, 71, 0.30);
--btn-clear:      rgba(255, 107, 107, 0.20);   /* coral */
--btn-clear-bdr:  rgba(255, 107, 107, 0.30);
```

### Sleepy Cloud (iOS only)
Two tints used by the wind-down mini-app and by nothing else. They sit
deliberately outside the brand trio: coral, teal and gold are the colours of a
reward, and a screen whose whole job is to wind a child down must not use the
palette they have learned means "well done".
```
--moonlight: #A8D6FF   /* the breathing cloud's halo, the phase labels, the stars */
--lavender:  #C4B5FD   /* the far stop of the title and progress-line gradients */
```

### Background Glow Orbs
Three fixed-position radial gradients that animate with `bgGlow`:
- Coral orb: top-right, 300px, 8% opacity
- Teal orb: bottom-left, 250px, 8% opacity
- Gold orb: mid-right, 200px, 6% opacity

## Typography

### Fonts
- **Display / Logo**: `Lilita One` — chunky, rounded, unmistakably playful
- **Body / UI**: `Nunito` — weights 700 (Bold), 800 (ExtraBold), 900 (Black)
- Load via Google Fonts: `https://fonts.googleapis.com/css2?family=Lilita+One&family=Nunito:wght@700;800;900`

### Scale
| Element | Font | Size | Weight | Notes |
|---------|------|------|--------|-------|
| Logo "Cloudmoji" | Lilita One | 21px | 400 | Gradient text (coral→gold→teal) |
| Tagline | Nunito | 10px | 800 | rgba white 0.3, letter-spacing 0.5 |
| Word bubble | Nunito | 18px | 900 | White, letter-spacing 0.5 |
| Category label | Nunito | 14px | 800 | Teal when active, muted when inactive |
| Language toggle | Nunito | 14px | 900 | White |
| Placeholder text | Nunito | 13px | 800 | rgba white 0.2 |
| Tap counter | Nunito | 10px | 800 | rgba white 0.12 |

## Spacing
```
4px   — xs (gap between tiny elements)
7px   — category tab padding vertical
6px   — between glow orbs
8px   — sm (grid gap, gap between category tabs, minimum gap between touch targets)
10px  — grid horizontal padding
16px  — category tab padding horizontal
12px  — card margin horizontal
14px  — header padding horizontal
16px  — md
18px  — word bubble padding horizontal
20px  — lg
24px  — grid bottom padding
```

## Touch Targets (Non-negotiable)

Two different rules apply, and which one applies depends on **who taps it**:

- **A child taps it** → 64px minimum, 72px preferred. A toddler's aim is poor and
  every miss is a small failure in a product whose whole premise is that there are
  none.
- **Only a parent taps it** → 44px, the iOS HIG minimum. Forcing 64px on parent
  chrome swallows the header on a 375px screen.

Either way, minimum 8px between adjacent targets.

| Element | Who taps | Size | Border Radius |
|---------|----------|------|---------------|
| Emoji button | child | 72 × 72px | 18px |
| Category tab | child | auto × 64px | 16px |
| Control button (replay/delete/clear) | child | 64 × 64px | 12px |
| Typed emoji (re-speak) | child | 64 × 64px | — (inline text) |
| Language toggle | parent | auto × 44px | 16px |
| About / mute | parent | 44 × 44px | — |

**iOS only — the launcher and the mini-apps** (the web app remains the two-mode
product; see the scope note in `CLAUDE.md`):

| Element | Who taps | Size | Border Radius |
|---------|----------|------|---------------|
| Launcher icon cell | child | flexible width × 112pt (94 sideways); visible icon 76pt (68 sideways) | icon squircle = 25% of side |
| Cloud home button | child | 64 × 64pt circle | — (circle) |
| Instrument pad | child | ≥72 × 72pt, fills its grid | 20pt |
| Flash-card choice | child | 110 × 110pt (96 sideways) | 22pt |
| Animal habitat card | child | flexible width × 138pt (106 sideways) | 26pt |
| Sleepy Cloud duration | child | 96 × 64pt | 20pt |
| Photo scrapbook thumbnail | child | ≥92 × 112pt | 15pt frame / 12pt image |
| Camera shutter | child | 88pt circle | — (circle) |
| Manage-photos row | parent | auto × 44pt | — |

## Border Radius
Everything is rounded. No sharp corners anywhere.
```
12px — control buttons, language dropdown
16px — category tabs
16px — language toggle, typing row inner
18px — emoji buttons
20px — typing row container, word bubble, instrument pad, Sleepy Cloud duration
22px — flash-card choice (iOS)
22px — 76pt launcher icon (iOS); computed as 25% of the icon side
26px — animal habitat card (iOS)
28px — app preview card (if ever wrapped)
```

## iOS Launcher and Mini-app Identity

The launcher follows the iPhone Home Screen metaphor:

- four fixed columns in both Cloudmoji layouts;
- a 76pt layered squircle above a one-line localized caption;
- the whole icon-and-label cell remains a child-sized target;
- partial rows stay left-aligned, preserving empty icon positions;
- app icons sit directly on the wallpaper;
- the brand and one labelled Grown-ups door share the launcher's single header
  widget; sound and language configuration stay behind the parental gate;
- purchase markers never appear on an app the child can already open.

The glass/material layer is navigation chrome only. App icons and mini-app
content use opaque or standard translucent surfaces, not nested glass cards.

Words and Count preserve the original Cloudmoji background and components. The
five newer apps reuse the same palette with distinct compositions:

| Mini-app | Atmosphere | Signature shape |
|----------|------------|-----------------|
| Flash Cards | indigo, coral and lavender card table | dealt card stack |
| Music | dark stage with coral and moonlight spotlights | illuminated toy pads |
| Animals | teal habitat with a gold sun and rolling ground | large creature portrait card |
| Photos | coral-and-amber scrapbook | white instant-photo frame and camera lens |
| Sleepy Cloud | moonlight-and-lavender night sky with synthesized soft-rain ambience | crescent halo and breathing cloud |

## Mascot Design Reference
The Cloudmoji mascot is defined as an SVG in `reference/prototype.jsx` (CloudMascot component). Key specs:

**Shape** (viewBox 0 0 120 78):
- Flat base: rect x=12 y=48 w=96 h=24 rx=12
- Left bump: circle cx=30 cy=46 r=20
- Center-left: circle cx=52 cy=36 r=23
- Center-right (tallest): circle cx=72 cy=30 r=26
- Right bump: circle cx=94 cy=42 r=19
- Connector: circle cx=42 cy=44 r=16

**Face** (centered in lower body):
- Eyes at y≈54, x=46 and x=74
- Mouth at y≈61, cx=60
- Blush at y=58, cx=34 and cx=86

**Moods**: happy, excited, speaking, beaming, love (see CLAUDE.md for behavior rules)

## Animations (CSS @keyframes Only)
| Name | Duration | Use |
|------|----------|-----|
| popIn | 0.3s ease-out | Emoji appearing in typing row |
| bounceEmoji | 0.35s ease | Emoji button on tap |
| wordFloat | 2.2s ease-in-out | Word bubble appear and fade |
| mascotFloat | 3s ease-in-out infinite | Idle mascot drift |
| mascotBounce | 0.4s ease infinite | Speaking mascot bounce |
| mascotBeam | 0.6s ease infinite | Beaming mascot (bigger bounce + scale) |
| sparkle | variable | Star/sparkle twinkle |
| bgGlow | 6-8s ease-in-out infinite | Background orb pulse |

Full keyframe definitions are in `reference/prototype.jsx` style block.

## Active States
Every tappable element must have an `:active` transform for tactile feedback:
- Emoji buttons: `scale(0.85)`
- Category tabs: `scale(0.9)`
- Launcher icon cells: `scale(0.92)`; cloud home button: `scale(0.9)` (iOS)
- Instrument pads and flash-card choices: `scale(0.85)` (iOS)
- Control buttons: `scale(0.88)`
- Language toggle: standard button feedback

## App Icon
Cloud mascot (happy face) on a gradient background (coral → teal diagonal).
Sizes needed: 192×192, 512×512, and apple-touch-icon.
Simple: render the SVG mascot centered on the gradient. Can be generated programmatically.
