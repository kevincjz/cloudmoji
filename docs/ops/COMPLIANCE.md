# Compliance — Children's Privacy

> **This document is not legal advice, and it does not assert that Cloudmoji complies with
> COPPA, GDPR, PDPA, or any store policy.** Its job is to record the data flows that
> actually exist in the shipped code, accurately enough that counsel can assess them.
> Where an earlier version of this file claimed compliance, that claim has been replaced
> with *what would need to be true* for the claim to hold.
>
> Data flows below were verified against source on 2026-07-26. Re-verify after any change
> to `src/main.tsx`, `index.html`, or `vite.config.ts`.

## What Ships Today (Verified Against Source)

Cloudmoji is a web PWA — Vite + React 19 + TypeScript, Tailwind v4, service worker via
`vite-plugin-pwa`, hosted on Vercel at cloudmoji.app. There is no native app, no backend,
no database, no accounts, and no payments. Content is 200 emojis (`src/data/emojis.ts`)
and 84 countables (`src/data/countables.ts`) in five languages — English, Simplified
Chinese, Malay, Japanese, Tagalog (`src/data/languages.ts`, `src/types.ts`).

### On-device state (never leaves the device)

`localStorage` is the only storage the app writes. Two keys:

| Key | Written by | Contents |
|---|---|---|
| `cm_lang` | `src/hooks/useLocalStorage.ts`, via `src/App.tsx` | Selected language, e.g. `"en"` |
| `cm_events` | `src/lib/measurement.ts` | Rolling buffer, max 500 records of `{timestamp, event, data}` |

The events recorded in `cm_events` are `session_start`, `lang` (language chosen), `tab`
(Words/Count), `tap` (emoji tapped), `count_tap` (emoji plus number reached), `cat`
(category chosen), `replay`, and `clear`. All are interaction counters. None carries text
the child or parent authored, and none carries a device or user identifier.

`cm_events` is read back only by the in-app stats panel (`src/components/StatsPanel.tsx`),
which can export it as JSON on a deliberate user action. No app code transmits either key.
There is no MMKV, no PostHog, no analytics SDK holding this data, and no sync.

### Third parties the browser actually contacts

**Vercel Analytics** — `@vercel/analytics`, rendered unconditionally in `src/main.tsx`.
Sends a beacon per pageview to a Vercel endpoint served from the app's own origin
(`/_vercel/insights/*`). Transmitted or derived from that request: page path, referrer,
and request-derived metadata — approximate geography (country level), operating system,
browser, device type. As with any HTTP request, the visitor's IP address and User-Agent
reach Vercel's servers. Vercel's product documentation describes deriving a rotating
hashed visitor identifier rather than storing raw IPs, and not setting cookies —
**confirm that against Vercel's current documentation and DPA rather than relying on this
file.**

**Vercel Speed Insights** — `@vercel/speed-insights`, also rendered unconditionally in
`src/main.tsx`. Sends real-user page performance timings (Core Web Vitals class metrics)
measured in the browser, associated with the page/route, plus the same class of
request-derived metadata. It does not carry app content: no emoji taps, no spoken words,
no `cm_events` data.

**Google Fonts** — `index.html` preconnects to `fonts.googleapis.com` and
`fonts.gstatic.com` and loads a stylesheet from Google. On first load, before any user
interaction and before any notice could be shown, the browser issues requests to
Google-operated servers, which necessarily expose the visitor's IP address and User-Agent
to Google. The service worker caches those responses (`CacheFirst`, ~1 year, configured in
`vite.config.ts`), so repeat visits on the same device are served locally — but the first
load on every new device, and every load after a cache clear, hits Google.

**Vercel hosting** — every request for the app itself terminates at Vercel's edge, which
produces standard server-side request logs (IP, User-Agent, path, timestamp) outside the
app's control.

### Speech

Speech uses the browser's Web Speech API (`speechSynthesis`) in `src/hooks/useTTS.ts`. The
app enumerates locally available voices, picks one, and hands it a single dictionary word.
The app never records audio and never requests microphone access. **Whether synthesis
happens on the device or on the browser/OS vendor's servers is decided by the platform's
voice engine, not by Cloudmoji** — some engines use cloud voices, which would mean the
word text is sent to that vendor. Verify per target platform before making any
"all audio stays on the device" claim.

### Capabilities the app does not use

No microphone, camera, geolocation, contacts, or push notifications. No cookies set by app
code. No `fetch`, `XMLHttpRequest`, or `sendBeacon` anywhere in `src/` — the only network
egress from app code is the two Vercel SDKs above. No ads, no in-app purchases, no social
media links. One external link exists: a Ko-fi support link in the About panel
(`src/components/AboutPanel.tsx`) — see Open Issues.

## Applicable Regulations

### COPPA (US — Children's Online Privacy Protection Act)
**Applies if** the service is directed to children under 13 and reachable by US users.
Cloudmoji is a toddler-facing product on the open web with no geo-restriction, so treat
COPPA as applying unless counsel advises otherwise. (The earlier version of this section
premised applicability on App Store / Play Store distribution — that premise is not yet
true, there is no native app, but web distribution is sufficient on its own.)

**Requirements**:
- No collection of personal information from children without verifiable parental consent
- Must post a clear, comprehensive privacy policy
- Must provide parents with notice and choice regarding data collection
- Must not condition a child's participation on disclosing more info than necessary

**What would need to be true** to claim COPPA compliance:
- That the pageview beacons, performance beacons, and Google Fonts requests either fall
  outside COPPA's definition of personal information (which, under some readings, reaches
  IP addresses and persistent identifiers used for tracking), or are covered by an
  applicable exception such as support for internal operations — as determined by counsel,
  not assumed here.
- That a privacy policy is published and linked where a parent will see it.
- That parents have actual notice and a real choice. Today there is neither: the
  collectors fire on load, unconditionally, with no opt-out. See Open Issues.

### PDPA (Singapore — Personal Data Protection Act)
Applies because the operator is based in Singapore.

**Requirements**:
- Consent required for data collection
- Purpose limitation
- Data minimization
- Access and correction rights

**What would need to be true**:
- That the analytics and performance transfers to Vercel have a valid basis under PDPA's
  consent/notification rules, with the purpose documented and limited.
- That a parent has a workable route to ask what is held and to have it corrected or
  stopped. Today the only contact channel is a Ko-fi link in the About panel, and there is
  no opt-out mechanism at all.

### GDPR / UK GDPR
**Applies if** the service is offered to users in the EU/UK. It is on the open web with no
geo-restriction, so assume it can be reached from there.

**What would need to be true**:
- A lawful basis for the Vercel Analytics and Speed Insights transfers, established before
  they fire.
- A position on the Google Fonts embed. Loading fonts from Google's CDN transfers the
  visitor's IP address to Google; EU courts have treated that as a transfer requiring a
  basis. **Self-hosting the font files removes the question entirely** and is the cheapest
  fix available here.
- A privacy notice served before those requests are made — which the current load order
  does not allow.
- Data processing agreements on file with Vercel and any other processor, plus a record of
  retention periods and processing locations.
- Heightened care given that the data subjects are children.

### Apple App Store — Kids Category Requirements
**Status: planned, not applicable today.** Cloudmoji ships as a web PWA; there is no App
Store submission. Retained for when/if a native or wrapped build is submitted.

**Requirements**:
- No third-party analytics, advertising, or tracking (unless approved)
- No links out of the app without parental gate
- No behavioral advertising
- Must comply with COPPA
- Age rating must be accurate
- Privacy Nutrition Labels must be accurate

**What would need to be true**: the previous claim here — "PostHog is first-party
analytics (we control the instance)" — is false. There is no PostHog and never was. What
ships is Vercel Analytics and Vercel Speed Insights: third-party SDKs reporting to a
third-party processor. A Kids Category submission would need those SDKs removed or
replaced, or an explicit determination with Apple that they fall inside the narrow
analytics exception. The Ko-fi link would need a parental gate or removal. Nutrition
Labels would need to reflect the flows in "What Ships Today", not this section's
aspirations.

### Google Play — Families Policy
**Status: planned, not applicable today.** No Play Store submission exists.

**Requirements**:
- Must participate in Designed for Families program
- No interest-based advertising
- Comply with COPPA
- Teacher Approved badge (optional, apply after launch)
- Privacy policy must be linked in store listing

**What would need to be true**: "Full compliance" is not something this doc can assert.
Designed for Families requires every bundled SDK to be appropriate for a child or mixed
audience and disclosed in the Data Safety form. That means an accurate Data Safety
declaration covering the Vercel collectors, a published and linked privacy policy, and a
decision on whether the analytics SDKs stay at all. Teacher Approved remains a Phase 2
aspiration.

## Privacy Policy (Key Points)

### What Is Collected
- **Nothing by us directly** — there is no server we operate and no account system.
- **Vercel Analytics**: pageviews plus request-derived metadata — referrer, country-level
  geography, OS, browser, device type.
- **Vercel Speed Insights**: real-user page performance timings plus the same class of
  metadata.
- **Vercel hosting**: standard edge request logs (IP, User-Agent, path, timestamp).
- **Google (Fonts CDN)**: IP address and User-Agent, as a byproduct of fetching font files
  on first load.

### What Is NOT Collected
- Names, emails, or photos of children or parents
- Precise location — the app never calls the geolocation API
- Contacts or address book
- Voice recordings — there is no microphone access and no recording code anywhere
- Cross-app tracking data or advertising identifiers
- Emoji taps, spoken words, or usage statistics sent off-device — `cm_events` stays in
  `localStorage` and is only ever exported by a deliberate user action

### Data Storage
- Preferences and usage events: browser `localStorage` only (`cm_lang`, `cm_events`).
  Erased when the user clears site data. No MMKV — that was a React Native library and
  this is not a React Native app.
- Analytics and performance data: processed by Vercel as a third-party processor.
  Retention, storage region, and sub-processors are Vercel's, not ours — confirm against
  Vercel's current DPA before describing them to a parent or a store reviewer.
- Fonts: served by Google; the service-worker cache of them is local.
- No data is sold to third parties. This is a commitment we control; the terms attached to
  the processors above are not.

### Where the policy is published
There is no `cloudmoji.app/privacy` and no `cloudmoji.app/terms`. The only user-facing
privacy text lives inside the About panel (`src/components/AboutPanel.tsx`), and that text
is currently inaccurate. See Open Issues.

## Open Issues

Gaps between this document and the shipped implementation. These cannot be closed by
editing docs — each needs a code, content, or process change.

1. **No telemetry opt-out.** `src/main.tsx` renders `<Analytics />` and `<SpeedInsights />`
   unconditionally. There is no consent gate, no settings toggle, and no way for a parent
   to decline. The checklist item "Analytics opt-out toggle in settings" is unimplemented.

2. **Analytics load before any notice could be shown.** Both beacons and the Google Fonts
   requests fire during initial page load. Consent-before-collection is not achievable
   without restructuring how and when these are mounted.

3. **The in-app privacy policy asserts compliance and is factually wrong.** The "Privacy
   Policy" entry in `src/components/AboutPanel.tsx` states "We comply with COPPA
   (Children's Online Privacy Protection Act) and Singapore's PDPA" — an assertion this
   document no longer makes and that no one has verified. The same text says "We do not
   use cookies or tracking pixels" and names only Vercel Analytics: it omits Vercel Speed
   Insights and omits the third-party requests to Google. This is the highest-priority
   fix, because it is the only privacy statement a parent actually sees.

4. **Third-party font CDN on first load.** `index.html` loads Google Fonts from
   `fonts.googleapis.com` / `fonts.gstatic.com`. Self-hosting the two font families
   (Lilita One, Nunito) would remove a third-party data flow entirely and also drop the
   runtime-caching rules in `vite.config.ts`.

5. **No privacy or terms page.** The checklist lists `cloudmoji.app/privacy` and
   `/terms`; neither exists. A static support page now exists at `/support`, but the
   privacy and terms documents still need their own public routes.

6. **External link with no parental gate.** `src/components/AboutPanel.tsx` links to
   `ko-fi.com/kevincjz` in a new tab with no gate. The checklist item "No external links
   without parental gate" is unmet. Ko-fi is also a payment destination, which matters if
   a store submission is ever attempted.

7. **Speech pathway unverified per platform.** Whether `speechSynthesis` sends word text
   to a vendor's cloud depends on the platform's voice engine. Untested across iOS Safari,
   Android Chrome, and desktop. Needed before claiming audio never leaves the device.

8. **Stale feature list in the About panel.** The same panel that holds the privacy policy
   advertises "121 emojis" and text-to-speech in three languages; the app ships 200 emojis
   and five languages. Not a compliance defect on its own, but inaccurate copy adjacent to
   a privacy statement undermines the credibility of both.

9. **No processor documentation on file.** No Vercel DPA reviewed, no recorded retention
   period, no recorded processing region. These are prerequisites for answering any Data
   Safety form, Nutrition Label, or parent inquiry honestly.

## Implementation Checklist

Reality-checked against source. Unchecked means not implemented.

- [ ] Privacy policy page at cloudmoji.app/privacy — does not exist
- [ ] Terms of service at cloudmoji.app/terms — does not exist
- [x] Support page at cloudmoji.app/support with a direct support email
- [ ] Correct the in-app privacy text in `AboutPanel.tsx` (Open Issue 3) — remove the
      compliance assertion, add Speed Insights and Google Fonts
- [ ] Analytics opt-out toggle, and a mechanism that prevents the beacons firing before a
      choice is made (Open Issues 1, 2)
- [ ] Self-host Lilita One and Nunito (Open Issue 4)
- [ ] Parental gate on the Ko-fi link, or removal (Open Issue 6)
- [ ] Verify the `speechSynthesis` pathway on each target platform (Open Issue 7)
- [ ] Obtain and file the Vercel DPA; record retention and region (Open Issue 9)
- [ ] Legal review of the data flows in "What Ships Today" — see below
- [x] No accounts, no login, no PII collected by app code
- [x] No ads, no behavioral advertising, no cross-app tracking
- [x] No microphone, camera, geolocation, or contacts access
- [x] No push notifications
- [ ] *Planned, store-submission only*: Apple Privacy Nutrition Labels
- [ ] *Planned, store-submission only*: Google Play Data Safety section
- [ ] *Planned, store-submission only*: age gate if required by platform

## Parental Consent Flow (Phase 2 — Accounts) — Planned

Not built. Applies only if accounts/sync are ever added:
1. Parent creates account (behind parental gate)
2. Parent provides email (their own, not the child's)
3. Parent explicitly consents to data collection
4. Parent can delete all data at any time
5. Parent can export all data (GDPR/PDPA right)

## Legal Review

Not yet done. Before any launch push, get a review from a qualified lawyer covering:
- Adequacy of the privacy notice, once one exists
- Assessment of COPPA applicability and exposure, given the flows in "What Ships Today"
- Assessment of Singapore PDPA obligations
- Assessment of GDPR/UK GDPR exposure, including the Google Fonts embed
- Apple/Google policy alignment, if a store submission is planned

Give the reviewer the "What Ships Today" and "Open Issues" sections verbatim — they are
the factual record. Budget: ~$500 SGD for a tech lawyer review.
