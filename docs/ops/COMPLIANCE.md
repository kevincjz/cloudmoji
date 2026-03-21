# Compliance — Children's Privacy

## Applicable Regulations

### COPPA (US — Children's Online Privacy Protection Act)
Applies because the app will be available in the US App Store/Play Store and targets children under 13.

**Requirements**:
- No collection of personal information from children without verifiable parental consent
- Must post a clear, comprehensive privacy policy
- Must provide parents with notice and choice regarding data collection
- Must not condition a child's participation on disclosing more info than necessary

**Our approach**: Collect NO personal information. Anonymous analytics only. No accounts in free tier.

### PDPA (Singapore — Personal Data Protection Act)
Applies because the company is based in Singapore.

**Requirements**:
- Consent required for data collection
- Purpose limitation
- Data minimization
- Access and correction rights

**Our approach**: Minimal data collection, clear privacy policy, opt-out for analytics.

### Apple App Store — Kids Category Requirements
**Requirements**:
- No third-party analytics, advertising, or tracking (unless approved)
- No links out of the app without parental gate
- No behavioral advertising
- Must comply with COPPA
- Age rating must be accurate
- Privacy Nutrition Labels must be accurate

**Our approach**: PostHog is first-party analytics (we control the instance). No ads ever. Parental gate for external links and settings.

### Google Play — Families Policy
**Requirements**:
- Must participate in Designed for Families program
- No interest-based advertising
- Comply with COPPA
- Teacher Approved badge (optional, apply after launch)
- Privacy policy must be linked in store listing

**Our approach**: Full compliance, apply for Teacher Approved badge in Phase 2.

## Privacy Policy (Key Points)

### What We Collect
- Anonymous usage data (emoji taps, session length) — no PII
- Device type and OS version (for bug fixing)
- Country-level location (from App Store, not GPS)

### What We DO NOT Collect
- Names, emails, photos of children
- Precise location
- Contacts or address book
- Voice recordings (stored locally only, never uploaded in MVP)
- Cross-app tracking data

### Data Storage
- All user preferences stored locally on device (MMKV)
- Analytics data processed by PostHog (EU-hosted or self-hosted)
- No data sold to third parties, ever

## Implementation Checklist
- [ ] Privacy policy page: cloudmoji.app/privacy
- [ ] Terms of service: cloudmoji.app/terms
- [ ] Apple Privacy Nutrition Labels filled accurately
- [ ] Google Play Data Safety section filled accurately
- [ ] Parental gate implemented for settings/purchases
- [ ] Analytics opt-out toggle in settings
- [ ] No external links without parental gate
- [ ] No push notification opt-in for children
- [ ] Age gate if required by platform
- [ ] COPPA compliance review with legal counsel before launch

## Parental Consent Flow (Phase 2 — Accounts)
When we add accounts/sync:
1. Parent creates account (behind parental gate)
2. Parent provides email (their own, not child's)
3. Parent explicitly consents to data collection
4. Parent can delete all data at any time
5. Parent can export all data (GDPR/PDPA right)

## Legal Review
Before launch, get a 1-hour legal review covering:
- Privacy policy adequacy
- COPPA compliance confirmation
- Apple/Google policy alignment
- Singapore PDPA compliance
Budget: ~$500 SGD for a tech lawyer review
