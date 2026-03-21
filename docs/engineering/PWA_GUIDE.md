# PWA Implementation Guide

## Why PWA
- No App Store review process (ship instantly)
- Share via URL (WhatsApp link = zero friction)
- Works on any device with a browser
- "Add to Home Screen" = feels like a native app
- Offline via service worker

## Setup: vite-plugin-pwa

```bash
npm install -D vite-plugin-pwa
```

```typescript
// vite.config.ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'icons/*.png'],
      manifest: {
        name: 'Cloudmoji',
        short_name: 'Cloudmoji',
        description: 'Tap emojis, learn words in English and Mandarin',
        theme_color: '#1a1a2e',
        background_color: '#1a1a2e',
        display: 'standalone',
        orientation: 'portrait',
        start_url: '/',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        runtimeCaching: [
          {
            // Cache Google Fonts
            urlPattern: /^https:\/\/fonts\.googleapis\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'google-fonts-cache',
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 365 },
            },
          },
          {
            urlPattern: /^https:\/\/fonts\.gstatic\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'gstatic-fonts-cache',
              expiration: { maxEntries: 10, maxAgeSeconds: 60 * 60 * 24 * 365 },
            },
          },
        ],
      },
    }),
  ],
});
```

## App Icons

Need two icons minimum:
- `public/icons/icon-192.png` (192×192)
- `public/icons/icon-512.png` (512×512)

Design: emoji-style icon on dark background. Could be a simple 🗣️ or custom design.

Quick generation approach:
```bash
# Use a simple emoji as the icon for MVP
# Can be replaced with proper design later
```

For MVP, create a simple icon with an emoji on the dark navy background. Don't block on icon design.

## iOS-Specific PWA Gotchas

### 1. Apple Touch Icon
iOS uses `apple-touch-icon` meta tag, not the manifest icons:
```html
<!-- index.html -->
<link rel="apple-touch-icon" href="/icons/icon-192.png" />
<meta name="apple-mobile-web-app-capable" content="yes" />
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
<meta name="apple-mobile-web-app-title" content="Cloudmoji" />
```

### 2. Viewport
Prevent zoom (toddlers will accidentally pinch-zoom):
```html
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover" />
```

### 3. Safe Area (iPhone Notch)
```css
/* Handle notch/Dynamic Island */
body {
  padding-top: env(safe-area-inset-top);
  padding-bottom: env(safe-area-inset-bottom);
  padding-left: env(safe-area-inset-left);
  padding-right: env(safe-area-inset-right);
}
```

### 4. Standalone Mode Detection
```typescript
const isStandalone = window.matchMedia('(display-mode: standalone)').matches
  || (window.navigator as any).standalone === true;
```

### 5. iOS PWA Limitations
- No push notifications (iOS 16.4+ supports, but unreliable)
- No background sync
- No badge API
- Storage limit: ~50MB (plenty for our use case)
- Service worker re-registers every few weeks (may need to re-cache)

These limitations don't affect our MVP at all.

## Android-Specific Notes
- Chrome shows "Add to Home Screen" banner automatically after 2 visits
- Full manifest support
- Service worker works reliably
- No special meta tags needed beyond the manifest

## Offline Testing Checklist
1. [ ] Load app with internet → all emojis visible
2. [ ] Enable airplane mode
3. [ ] Refresh the page → app still loads
4. [ ] Tap emojis → TTS still works (platform native, no network needed)
5. [ ] Close and reopen from home screen → still works
6. [ ] Language toggle still works offline
7. [ ] Category filtering works offline

## Lighthouse PWA Audit
Run before deploying:
```bash
# In Chrome DevTools → Lighthouse → check "Progressive Web App"
# Target: 90+ score
```

Common issues to fix:
- Missing maskable icon → add `purpose: 'maskable'` to a 512px icon
- Missing meta theme-color → add `<meta name="theme-color" content="#1a1a2e" />`
- Missing offline fallback → service worker should cache index.html

## Social Sharing Meta Tags
```html
<!-- Open Graph (WhatsApp, LinkedIn, Facebook) -->
<meta property="og:title" content="Cloudmoji — Tap emojis, learn words" />
<meta property="og:description" content="Your toddler taps emojis and hears words in English and Mandarin. Free, no download needed." />
<meta property="og:image" content="https://cloudmoji.app/og-image.png" />
<meta property="og:url" content="https://cloudmoji.app" />
<meta property="og:type" content="website" />

<!-- Twitter Card -->
<meta name="twitter:card" content="summary_large_image" />
<meta name="twitter:title" content="Cloudmoji — Tap emojis, learn words" />
<meta name="twitter:description" content="Built because my 2-year-old was already doing this on a locked iPhone." />
<meta name="twitter:image" content="https://cloudmoji.app/og-image.png" />
```

Create `public/og-image.png` (1200×630): dark background, a few large emojis, the word "Cloudmoji" in Nunito. Keep it simple.
