import SwiftUI

/// FAQ, privacy and version history.
///
/// Pushed from `SettingsView`, so it is already behind the parental gate. The web
/// reaches its About panel from the header and hides a five-tap gesture for the
/// grown-ups bits; here the whole thing sits behind the arithmetic question, which
/// is one fewer secret to remember and one fewer way for a toddler to land on a wall
/// of text.
///
/// The only outbound action is a `mailto:` link to Cloudmoji support. About is
/// already behind the parental gate, the link opens the grown-up's own mail app,
/// and Cloudmoji attaches no diagnostics or child data. The web's Ko-fi button
/// remains deliberately absent.
///
/// The privacy text is **rewritten, not ported**. Every sentence in it is a claim
/// about this binary and was checked against the source before it was written: the
/// app has no analytics or advertising SDK, there is no `URLSession` anywhere in
/// the target, and `SettingsStore` writes exactly seven `UserDefaults` keys.
/// StoreKit is the one network-backed system service, used only when a grown-up
/// checks the purchase price, buys, or restores. Shipping the web's
/// wording instead would attach an inaccurate disclosure to a listing whose
/// nutrition label says "Data Not Collected".
struct AboutView: View {

    static let supportEmail = "kevin.chan@sproutlearn.co"
    static let supportURL = URL(
        string: "mailto:\(supportEmail)?subject=Cloudmoji%20Support"
    )!

    struct Entry: Identifiable {
        /// Explicit rather than derived from `question`, so the accessibility
        /// identifier a UI test looks up is stable when the wording is edited —
        /// and so it stays plain ASCII, which a slug of "v1.0 — Cloudmoji for
        /// iPhone and iPad" would not.
        let id: String
        let question: String
        let answer: String
    }

    static let faq: [Entry] = [
        Entry(
            id: "how-to-use",
            question: "How do we use Cloudmoji?",
            answer: """
                The free version opens with Words and Count in English. Tap one to go in; tap the big cloud along \
                the bottom of the screen to come back out.

                🗣️ Words — tap any emoji to hear the word spoken aloud. The row along the top \
                keeps what your little one has tapped, and the 🔊 button replays the lot.

                🧮 Count — tap the emojis one at a time to count them. Cloudmoji says the \
                running number out loud and the dots track how far along the round is.

                ⚡ Flash Cards — Cloudmoji says a word and your child finds it among three.

                🎹 Music — eight pads, eight notes, no wrong ones.

                🔊 Animals — tap a creature to hear it, then hear its name.

                📷 Photos — your child takes pictures inside the app. A grown-up can save copies \
                to Apple Photos from the grown-ups screen.

                🌙 Sleepy Cloud — a breathing exercise with a soft calming sound for winding \
                down. You or your child can pick two, five or ten minutes; the screen dims as it goes.

                Music, Flash Cards, Animals, Photos and Sleepy Cloud are part of Full Cloudmoji.

                We run it in Guided Access, which locks the phone to Cloudmoji so a small \
                person can tap freely without leaving the app.
                """
        ),
        Entry(
            id: "guided-access",
            question: "How do I turn on Guided Access?",
            answer: """
                1. Settings → Accessibility → Guided Access → turn it on.
                2. Set a passcode.
                3. Open Cloudmoji, then triple-click the side button.
                4. Tap Start in the top right.
                5. To leave: triple-click the side button again and enter your passcode.
                """
        ),
        Entry(
            id: "languages",
            question: "Which languages are supported?",
            answer: """
                The free version includes English. Full Cloudmoji adds Mandarin Chinese (中文), \
                Bahasa Melayu (BM), Japanese (日本語) and Tagalog (TL). With Full, choose the \
                starting language in the grown-ups screen; Words and Count also have a quick \
                language button in their top bar.

                Japanese uses hiragana and katakana only — no kanji — which matches what \
                Japanese children learn first. Counting uses the ～つ counter (ひとつ, ふたつ, \
                みっつ), the first counting system Japanese children are taught.

                In the grown-ups screen you can switch off the languages your family does not \
                use, so the picker only offers the ones you want.
                """
        ),
        Entry(
            id: "make-it-simpler",
            question: "Can I make it simpler for a younger child?",
            answer: """
                Yes. Tap the locked Grown-ups button on Home, answer the grown-ups question, and you \
                can switch off whole categories of emoji and choose how high Count mode goes. \
                Narrowing it to two categories and counting to three is a good place to start \
                with a toddler; widen it as they get older.
                """
        ),
        Entry(
            id: "tagalog-voice",
            question: "The Tagalog voice sounds wrong. Can I fix it?",
            answer: """
                Most devices ship without a Filipino voice, so Cloudmoji falls back to the Malay \
                one — the two languages share the same five vowels and the same "ng" sound, so \
                it is much closer than English would be.

                To get the real thing: Settings → Accessibility → Spoken Content → Voices → \
                Filipino. Cloudmoji uses it the next time you open the app.
                """
        ),
        Entry(
            id: "offline",
            question: "Does it work offline?",
            answer: """
                Play works offline, and there is no "first time online" for the free activities \
                or anything already unlocked. Every word in all five languages is inside the app \
                you downloaded, and speech comes from your device's own voices. Only checking the \
                price, buying or restoring Full Cloudmoji may need the App Store connection.
                """
        ),
        Entry(
            id: "full-cloudmoji",
            question: "What does Full Cloudmoji unlock?",
            answer: """
                Full Cloudmoji is a one-time purchase, not a subscription. It adds Music, Flash \
                Cards, Animals, Photos, Sleepy Cloud, Mandarin Chinese, Bahasa Melayu, Japanese, \
                Tagalog and the Apple Watch experience, including short voice notes. The free \
                version includes Words and Count in English.
                """
        ),
        Entry(
            id: "data",
            question: "Is my child's data collected?",
            answer: """
                Cloudmoji does not automatically collect personal data. There are no accounts, no \
                tracking and no Cloudmoji servers. Apple handles an optional purchase through the \
                App Store.

                If a grown-up chooses to email support, their own mail app sends the address and \
                message they choose to provide. Cloudmoji attaches nothing. See the privacy policy \
                below for the details.
                """
        ),
    ]

    static let legal: [Entry] = [
        Entry(
            id: "privacy",
            question: "Privacy Policy",
            answer: """
                Cloudmoji for iPhone and iPad collects nothing, and its App Store privacy label \
                says so: Data Not Collected.

                NO CLOUDMOJI SERVERS OR TRACKING
                There are no analytics, advertising, crash-reporting services or Cloudmoji \
                servers. Nothing about your child is uploaded to us. The word lists for all five \
                languages are inside the download, the emoji are drawn by iOS itself, and the two \
                typefaces we ship travel with the app. If a grown-up buys or restores Full \
                Cloudmoji, Apple's StoreKit handles that transaction with the App Store.

                WHAT IS STORED, AND WHERE
                Seven small settings are kept on this device by iOS: the chosen language, which \
                languages are switched on, which categories are switched on, the lowest and the \
                highest number Count mode uses, whether Cloudmoji is muted, whether the welcome \
                tour has been dismissed. Purchase entitlement information is maintained by Apple, \
                not stored as a Cloudmoji setting. The settings never leave the device, and \
                deleting the app deletes them.

                CAMERA
                Photos your child takes are written inside the app's own storage on this device. \
                They are excluded from iCloud backup and are not added to your photo library \
                automatically. In the grown-ups screen you can save copies to Apple Photos, or \
                delete the Cloudmoji originals — one at a time or all at once. Cloudmoji asks only \
                for permission to add photos; it never reads your library. Deleting Cloudmoji \
                deletes any originals that remain inside the app. The camera is only ever running \
                while the camera screen is open.

                APPLE WATCH
                If you pair an Apple Watch, Cloudmoji on the watch and Cloudmoji on the iPhone \
                pass things between them using Apple's own device-to-device connection — the same \
                Bluetooth-or-shared-Wi-Fi link a watch uses to talk to its phone. Everything stays \
                between your own two devices and never touches the internet.

                You can tap emoji on the watch to send to the phone, and your child's taps come \
                back to the watch. You can also record a short voice message on the watch for your \
                child to hear on the phone. That recording is the one place Cloudmoji uses a \
                microphone, and it is on the watch — the grown-up's device — not the phone. A \
                message plays on the paired phone and is kept only in memory so your child can \
                replay it during that session; it is never saved to the phone, never added to any \
                recordings, and never sent anywhere else. Closing the app forgets it.

                SPEECH
                Words are spoken by your device's own text-to-speech. Cloudmoji hands iOS a word \
                and iOS makes a sound; nothing is recorded, nothing is saved and nothing is sent \
                anywhere. On the iPhone, Cloudmoji never asks for the microphone — the camera is \
                used for still pictures only, with no sound recorded (recording a voice message is \
                the watch's job, described above). If you add a voice from iOS Settings, iOS \
                fetches that voice — that is the system doing it, not this app.

                OPTIONAL SUPPORT EMAIL
                The Support row opens your own mail app with the Cloudmoji support address and a \
                subject line. Cloudmoji does not attach diagnostics, settings, photos, voice \
                messages or any other app data. If you choose to send an email, Kevin receives \
                your email address and whatever you write. Please do not include your child's \
                name, photos, voice recordings or other personal information.

                NO ACCOUNTS, ONE APPLE PURCHASE, NO CHILD LINKS OUT
                There is nothing to sign in to. A grown-up can make one optional, one-time \
                purchase for Full Cloudmoji after passing the grown-ups question; there is no \
                subscription. Nothing your child can reach opens a browser, another app or a \
                purchase screen. The grown-ups area can also open iOS Settings after camera \
                access has been refused.

                We are parents, not lawyers. This describes what the app actually does rather \
                than being a legal certification, and we have aimed at what COPPA and \
                Singapore's PDPA ask for.
                """
        ),
        Entry(
            id: "terms",
            question: "Terms of Use",
            answer: """
                • Cloudmoji is provided as is, without warranty of any kind.
                • It is intended for use by children under adult supervision.
                • We recommend Guided Access to keep a small person safely inside the app.
                • Speech quality depends on the voices installed on your device and varies \
                between them.
                • We may update or discontinue Cloudmoji at any time.
                • Full Cloudmoji is sold as a one-time In-App Purchase handled by Apple.

                Last updated: July 2026.
                """
        ),
    ]

    /// What **this** app has shipped, not the web app's changelog. The web's own
    /// history is summarised in one entry below rather than reprinted release by
    /// release: a parent holding the iPhone app does not need five dated bullets
    /// about a website, and half of them describe things that were never in this
    /// build.
    static let history: [Entry] = [
        Entry(
            id: "v1-0-ios",
            question: "v1.0 — Cloudmoji for iPhone and iPad",
            answer: """
                • The first native release: 200 emojis across 8 categories, in English, \
                Mandarin, Malay, Japanese and Tagalog
                • Count mode with 84 things to count, rounds of 2 to 10, and the correct \
                counting grammar in every language — Chinese measure words, Malay penjodoh \
                bilangan, the Japanese ～つ counter with the noun first, and the Tagalog number \
                linker
                • A grown-ups screen behind an arithmetic gate: choose which languages appear, \
                which categories your child sees, and how high Count mode goes — and it is \
                remembered between sessions
                • A landscape layout that moves the tabs into a side rail instead of squeezing \
                the grid
                • Tagalog falls back to the Malay voice rather than the English one on the many \
                devices with no Filipino voice installed
                • Everything ships inside the app — no network connection is ever made
                """
        ),
        Entry(
            id: "web-history",
            question: "The web app so far — cloudmoji.app",
            answer: """
                v1.4 (July 2026) — 40 new emojis, taking the app from 160 to 200, and 30 new \
                things to count, taking Count mode from 54 to 84. Every new word had a native \
                speaker pass in all five languages.

                v1.3 (July 2026) — Japanese and Tagalog added, hiragana and katakana only, with \
                the ～つ counter and the correct Tagalog linker. The language button became a \
                picker. Landscape stopped squeezing the grid to under two rows.

                v1.2 (April 2026) — 27 new emojis and 27 new countables. Chinese counting fixed \
                to use 两 rather than 二 with a measure word. English irregular plurals fixed. \
                Malay classifiers added.

                v1.1 (March 2026) — Count mode, and the tab bar to reach it.

                v1.0 (March 2026) — 121 emojis, three languages, the cloud mascot, the typing \
                row and milestone celebrations.
                """
        ),
    ]

    /// Read from the bundle rather than written down, so the panel cannot drift from
    /// the build a parent is actually holding.
    static func versionText(from info: [String: Any]?) -> String {
        guard let version = info?["CFBundleShortVersionString"] as? String else { return "Cloudmoji" }
        return "Cloudmoji v\(version)"
    }

    /// Parent-facing chrome throughout, so the 44pt iOS HIG floor rather than the
    /// app's usual 64pt child minimum. A 64pt row in a `List` looks broken, and no
    /// child ever reaches this screen.
    private static let rowHeight: CGFloat = 44

    var body: some View {
        // A `List`, not a `ScrollView`: a bare `ScrollView` top-pins its content and
        // would leave the sections stacked against the nav bar with dead space
        // below on an iPad, and `List` gives the disclosure rows their separators
        // and their scroll recycling for free.
        List {
            Section {
                VStack(spacing: 14) {
                    CloudMascot(mood: .happy, size: 120)
                        .padding(.top, 8)

                    Text("Made with love by Kevin and PQ for our son Cloud.")
                        .font(Theme.body(14, .bold))
                        .foregroundStyle(Theme.textPrimary)

                    Text("""
                        One day Cloud picked up a locked iPhone, started typing emojis, and said \
                        the words out loud — all on his own. We wondered what would happen if we \
                        turned that into a safe place to learn words in more than one language.
                        """)
                        .font(Theme.body(13, .bold))
                        .foregroundStyle(Theme.textTertiary)
                }
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
            }

            section("FAQ", Self.faq)

            Section("Support") {
                Link(destination: Self.supportURL) {
                    HStack(spacing: 12) {
                        Image(systemName: "envelope.fill")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(Theme.teal)
                            .accessibilityHidden(true)

                        VStack(alignment: .leading, spacing: 3) {
                            Text("Email Cloudmoji Support")
                                .font(Theme.body(14, .bold))
                                .foregroundStyle(Theme.textPrimary)
                            Text(Self.supportEmail)
                                .font(Theme.body(12, .bold))
                                .foregroundStyle(Theme.textSecondary)
                        }

                        Spacer(minLength: 8)

                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Theme.textSecondary)
                            .accessibilityHidden(true)
                    }
                    .frame(minHeight: Self.rowHeight)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Email Cloudmoji Support at \(Self.supportEmail)")
                .accessibilityIdentifier("about-support-email")

                Text("Your mail app opens with no Cloudmoji data attached. Please do not include personal information about your child.")
                    .font(Theme.body(11, .bold))
                    .foregroundStyle(Theme.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            section("Legal", Self.legal)
            section("Version history", Self.history)

            Section {
                Text(Self.versionText(from: Bundle.main.infoDictionary))
                    .font(Theme.body(11, .heavy))
                    .foregroundStyle(Theme.textSecondary)
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
                    .accessibilityIdentifier("about-version")
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(Theme.background.ignoresSafeArea())
        // A `List` otherwise renders in the system light palette on a light device
        // and the whole panel comes out white — the same reason `SettingsView` sets
        // it.
        .preferredColorScheme(.dark)
        .navigationTitle("About Cloudmoji")
        .navigationBarTitleDisplayMode(.inline)
        // Insurance, and honestly labelled as such. An identifier on a plain
        // container (a `VStack`, an `HStack`, a `ZStack`) propagates down and
        // overwrites its children's — that is what made the typing row's three
        // controls unreachable in stage 2a and the gate's six UI tests red in the
        // task before this one. A `List` is **not** that: its rows are table cells
        // and each publishes its own element, so `about-privacy` and the rest
        // survive without this line. That was measured, not assumed — removing it
        // and re-running `AboutUITests` leaves both tests green. It stays because
        // the cost is nothing and the day this `List` becomes a `ScrollView` of
        // `VStack`s the trap is live again.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("about-panel")
    }

    private func section(_ title: String, _ entries: [Entry]) -> some View {
        Section(title) {
            ForEach(entries) { entry in
                DisclosureGroup {
                    Text(entry.answer)
                        .font(Theme.body(13, .bold))
                        .foregroundStyle(Theme.textTertiary)
                        // Long copy in a list row otherwise truncates to one line
                        // and the disclosure looks broken.
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.vertical, 4)
                } label: {
                    Text(entry.question)
                        .font(Theme.body(14, .bold))
                        .foregroundStyle(Theme.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(minHeight: Self.rowHeight, alignment: .leading)
                }
                .tint(Theme.teal)
                .accessibilityIdentifier("about-\(entry.id)")
            }
        }
    }
}

#Preview("About") {
    NavigationStack { AboutView() }
}
