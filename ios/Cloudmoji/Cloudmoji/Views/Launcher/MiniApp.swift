import SwiftUI
import CloudmojiCore

/// The seven things Cloudmoji can be.
///
/// This replaces `AppMode`, which named two. The raw values are a contract in
/// three directions and must not be renamed casually: they are the accessibility
/// identifiers `launcher-tile-<raw>`, the `-cm_open <raw>` debug deep link every
/// UI suite launches through, and the order they appear on the launcher.
///
/// Words and Count keep their old raw values so the deep link, and the suites
/// that use it, read the same before and after the launcher landed.
enum MiniApp: String, CaseIterable, Identifiable {
    case words
    case count
    case flashCards = "flashcards"
    case instrument
    case animalSounds = "animalsounds"
    case photos
    case sleepy

    var id: String { rawValue }

    /// The tile's face. A colour emoji in every case, so it keeps its own
    /// colours rather than taking the button's accent tint.
    var icon: String {
        switch self {
        case .words: "🗣️"
        // 🧮 rather than 🔢: the web changed it because 🔢 renders as a grey box
        // on several platforms, and the two listings are worth keeping identical.
        case .count: "🧮"
        case .flashCards: "⚡"
        case .instrument: "🎹"
        case .animalSounds: "🔊"
        case .photos: "📷"
        case .sleepy: "🌙"
        }
    }

    /// Whether this one is behind the single unlock.
    ///
    /// A property of the mini-app, not of the person holding the phone — whether
    /// it *opens* is `EntitlementProviding.isUnlocked`, which is a different
    /// question and lives somewhere else on purpose.
    var isPremium: Bool {
        switch self {
        case .flashCards, .animalSounds, .photos: true
        case .words, .count, .instrument, .sleepy: false
        }
    }

    /// The tile's caption, in the five languages.
    ///
    /// `AppMode` kept its two captions in English, the way the web does. This
    /// does not, and the reason is the launcher itself: two tabs with familiar
    /// glyphs could be navigated by icon alone, but seven tiles is a list a
    /// parent reads — and a Chinese-speaking family reading `Flash Cards` on a
    /// screen where everything else is 中文 is a worse first impression than a
    /// translation is a risk. Copy, not content: it lives beside the markup on
    /// the web too, so there is nothing in `src/data/` to generate it from.
    /// `CountView.uiText` is the precedent.
    func label(_ language: Language) -> String {
        Self.labels[self]?[language] ?? Self.labels[self]?[.en] ?? rawValue
    }

    private static let labels: [MiniApp: [Language: String]] = [
        .words: [.en: "Words", .zh: "词语", .ms: "Perkataan", .ja: "ことば", .tl: "Mga Salita"],
        .count: [.en: "Count", .zh: "数数", .ms: "Kira", .ja: "かぞえる", .tl: "Bilang"],
        .flashCards: [.en: "Flash Cards", .zh: "闪卡", .ms: "Kad Kilat", .ja: "カード", .tl: "Flash Card"],
        .instrument: [.en: "Music", .zh: "音乐", .ms: "Muzik", .ja: "おんがく", .tl: "Musika"],
        .animalSounds: [.en: "Animals", .zh: "动物", .ms: "Haiwan", .ja: "どうぶつ", .tl: "Hayop"],
        .photos: [.en: "Photos", .zh: "照片", .ms: "Gambar", .ja: "しゃしん", .tl: "Mga Litrato"],
        .sleepy: [.en: "Sleepy Cloud", .zh: "瞌睡云", .ms: "Awan Mengantuk", .ja: "ねむいくも", .tl: "Inaantok na Ulap"],
    ]
}

/// The visual identity shared by a mini-app's launcher icon, background and
/// floating home control.
///
/// The colours are composed exclusively from the established Cloudmoji palette.
/// A mini-app gets its own atmosphere without becoming a second brand, and
/// Words / Count can keep the exact shell they already shipped.
struct MiniAppVisualTheme {
    let accent: Color
    let secondary: Color
    let background: LinearGradient
}

extension MiniApp {
    var visualTheme: MiniAppVisualTheme {
        switch self {
        case .words:
            MiniAppVisualTheme(
                accent: Theme.teal,
                secondary: Theme.coral,
                background: Theme.background
            )
        case .count:
            MiniAppVisualTheme(
                accent: Theme.gold,
                secondary: Theme.amber,
                background: Theme.background
            )
        case .flashCards:
            MiniAppVisualTheme(
                accent: Theme.gold,
                secondary: Theme.lavender,
                background: LinearGradient(
                    colors: [
                        Theme.bgPrimary,
                        Theme.bgMid,
                        Theme.coral.opacity(0.28),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
        case .instrument:
            MiniAppVisualTheme(
                accent: Theme.coral,
                secondary: Theme.moonlight,
                background: LinearGradient(
                    colors: [
                        Theme.bgPrimary,
                        Theme.bgMid,
                        Theme.teal.opacity(0.22),
                    ],
                    startPoint: .top,
                    endPoint: .bottomTrailing
                )
            )
        case .animalSounds:
            MiniAppVisualTheme(
                accent: Theme.teal,
                secondary: Theme.gold,
                background: LinearGradient(
                    colors: [
                        Theme.bgEdge,
                        Theme.bgMid,
                        Theme.teal.opacity(0.30),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
        case .photos:
            MiniAppVisualTheme(
                accent: Theme.coral,
                secondary: Theme.moonlight,
                background: LinearGradient(
                    colors: [
                        Theme.bgPrimary,
                        Theme.coral.opacity(0.24),
                        Theme.amber.opacity(0.20),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
        case .sleepy:
            MiniAppVisualTheme(
                accent: Theme.moonlight,
                secondary: Theme.lavender,
                background: LinearGradient(
                    colors: [
                        Theme.bgPrimary,
                        Theme.bgMid,
                        Theme.moonlight.opacity(0.12),
                    ],
                    startPoint: .top,
                    endPoint: .bottomTrailing
                )
            )
        }
    }

    /// The original two experiences keep the original `AdaptiveShell`
    /// background. The five newer apps paint an atmosphere of their own.
    var hasThemedBackdrop: Bool {
        self != .words && self != .count
    }

    /// `nil` preserves the original neutral home button in Words and Count.
    var homeAccent: Color? {
        hasThemedBackdrop ? visualTheme.accent : nil
    }

    /// The original two modes already carry a visible mute control in their
    /// headers. These newer experiences rely on audio but otherwise have no way
    /// to recover when sound was switched off on another screen.
    var showsSoundRecovery: Bool {
        switch self {
        case .flashCards, .instrument, .animalSounds, .sleepy: true
        case .words, .count, .photos: false
        }
    }
}

/// Full-bleed atmosphere behind a hosted mini-app.
///
/// These are a few large, quiet shapes rather than particles. They stay out of
/// the interaction layer, cost almost nothing to render, and give each app a
/// recognisable world before the child has touched anything.
struct MiniAppBackdrop: View {
    let app: MiniApp

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                app.visualTheme.background

                switch app {
                case .flashCards:
                    flashCards(in: proxy.size)
                case .instrument:
                    musicStage(in: proxy.size)
                case .animalSounds:
                    habitat(in: proxy.size)
                case .photos:
                    scrapbook(in: proxy.size)
                case .sleepy:
                    nightSky(in: proxy.size)
                case .words, .count:
                    EmptyView()
                }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func flashCards(in size: CGSize) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 42, style: .continuous)
                .stroke(Theme.gold.opacity(0.10), lineWidth: 5)
                .frame(width: min(size.width * 0.68, 340), height: min(size.height * 0.38, 300))
                .rotationEffect(.degrees(-12))
                .offset(x: size.width * 0.30, y: -size.height * 0.34)
            Circle()
                .fill(Theme.coral.opacity(0.12))
                .frame(width: min(size.width * 0.85, 430))
                .blur(radius: 30)
                .offset(x: -size.width * 0.38, y: size.height * 0.38)
        }
    }

    private func musicStage(in size: CGSize) -> some View {
        ZStack {
            LinearGradient(
                colors: [Theme.coral.opacity(0.22), .clear],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: size.width * 0.52, height: size.height * 0.82)
            .rotationEffect(.degrees(18))
            .offset(x: -size.width * 0.30, y: -size.height * 0.18)

            LinearGradient(
                colors: [Theme.moonlight.opacity(0.18), .clear],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: size.width * 0.52, height: size.height * 0.82)
            .rotationEffect(.degrees(-18))
            .offset(x: size.width * 0.30, y: -size.height * 0.18)
        }
        .blur(radius: 18)
    }

    private func habitat(in size: CGSize) -> some View {
        ZStack {
            Circle()
                .fill(Theme.gold.opacity(0.14))
                .frame(width: min(size.width * 0.62, 270))
                .offset(x: size.width * 0.38, y: -size.height * 0.33)
                .blur(radius: 2)
            Ellipse()
                .fill(Theme.teal.opacity(0.10))
                .frame(width: size.width * 1.45, height: size.height * 0.34)
                .offset(x: -size.width * 0.16, y: size.height * 0.43)
        }
    }

    private func scrapbook(in size: CGSize) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(Theme.moonlight.opacity(0.09), lineWidth: 4)
                .frame(width: min(size.width * 0.56, 260), height: min(size.height * 0.34, 260))
                .rotationEffect(.degrees(9))
                .offset(x: size.width * 0.36, y: -size.height * 0.34)
            Circle()
                .fill(Theme.amber.opacity(0.12))
                .frame(width: min(size.width * 0.90, 420))
                .blur(radius: 34)
                .offset(x: -size.width * 0.40, y: size.height * 0.38)
        }
    }

    private func nightSky(in size: CGSize) -> some View {
        ZStack {
            Circle()
                .fill(Theme.moonlight.opacity(0.08))
                .frame(width: min(size.width * 0.78, 350))
                .blur(radius: 28)
                .offset(x: size.width * 0.34, y: -size.height * 0.34)
            Circle()
                .fill(Theme.lavender.opacity(0.07))
                .frame(width: min(size.width * 0.94, 440))
                .blur(radius: 38)
                .offset(x: -size.width * 0.42, y: size.height * 0.42)
        }
    }
}
