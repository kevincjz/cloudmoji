# Cloudmoji iOS — Project, Speech and Words Mode

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A runnable iOS app where tapping an emoji speaks the word in any of five languages, with the mascot reacting — the core loop, on a real simulator.

**Architecture:** A SwiftUI app target consuming the already-merged `CloudmojiCore` package. Views adapt through size classes rather than branching into parallel trees. `AVSpeechSynthesizer` is bound behind Stage 1's `SpeechEngine` protocol, so queue behaviour stays testable without audio.

**Tech Stack:** Swift 6, SwiftUI, AVFoundation, XCTest/XCUITest, Xcode 16+.

This is **Stage 2a of 3** from [the design spec](../specs/2026-07-27-ios-watchos-app-design.md).
Stage 1 (`CloudmojiCore`) is merged. **Stage 2b** adds Count mode, the parental gate,
Settings and About. **Stage 3** is watchOS.

## Global Constraints

- Minimum iOS 17.0. Universal — iPhone and iPad.
- Bundle identifier `app.cloudmoji.Cloudmoji`. Permanent once submitted; Stage 3's
  watch target must be prefixed by it.
- Speech rate `0.85`, pitch `1.1`.
- Five languages exactly: `en`, `zh`, `ms`, `ja`, `tl`.
- **Touch targets: 64pt minimum, 72pt preferred, for anything a CHILD taps** — emoji
  tiles, typed emojis, replay/delete/clear, category chips. Parent-only chrome (About,
  mute, language) follows the 44pt iOS HIG minimum instead.
- **No failure state ever reaches the child.** Errors degrade silently; the mascot still
  reacts so a tap is always rewarded.
- **Kids Category:** no third-party analytics or ad SDKs, ever. Fonts are bundled, not
  fetched. Nothing in this plan may add a network call.
- Content comes from `CloudmojiCore`; never hand-write emoji data in Swift.
- Commit after every task.

---

## Carried over from Stage 1

Five items were deferred out of `CloudmojiCore` with a note to land here. Each has a task:

| Deferred item | Task |
|---|---|
| `SpeechController` has no watchdog; a dropped `didFinish` stalls a replay | Task 3 |
| `speak()` has no completion callback, so the mascot cannot return to happy | Task 3 |
| `engine.voices()` is called per utterance; must be cached | Task 2 |
| `SettingsStore` isolation undecided | Task 4 |
| Spec setup steps 6/8 place `EmojiData.json` in the app target; it lives in the package | Task 1 |

---

## File Structure

| File | Responsibility |
|---|---|
| `ios/Cloudmoji/Cloudmoji.xcodeproj` | App project (created in Task 1) |
| `ios/Cloudmoji/Cloudmoji/CloudmojiApp.swift` | `@main`, audio session setup |
| `ios/Cloudmoji/Cloudmoji/SystemSpeechEngine.swift` | `AVSpeechSynthesizer` bound to `SpeechEngine` |
| `ios/Cloudmoji/Cloudmoji/AppModel.swift` | `@Observable` — repository, settings, filtered content |
| `ios/Cloudmoji/Cloudmoji/Theme.swift` | Colours and fonts, one place |
| `ios/Cloudmoji/Cloudmoji/Views/CloudMascot.swift` | The mascot, four moods |
| `ios/Cloudmoji/Cloudmoji/Views/EmojiTile.swift` | One 72pt tile |
| `ios/Cloudmoji/Cloudmoji/Views/EmojiGrid.swift` | Scrollable grid |
| `ios/Cloudmoji/Cloudmoji/Views/TypingRow.swift` | Tapped emojis, capped at 50 |
| `ios/Cloudmoji/Cloudmoji/Views/WordBubble.swift` | Floating word label |
| `ios/Cloudmoji/Cloudmoji/Views/CategorySource.swift` | Category list — one component, two layouts |
| `ios/Cloudmoji/Cloudmoji/Views/WordsView.swift` | Assembles Words mode |
| `ios/Cloudmoji/Cloudmoji/Views/AdaptiveShell.swift` | Chooses rail vs bars from size class |
| `ios/Cloudmoji/CloudmojiTests/` | Unit tests for the adapter and model |
| `ios/Cloudmoji/CloudmojiUITests/` | XCUITest for behaviour and touch targets |

---

### Task 1: Xcode project — **you do this part**

This is the one task a subagent cannot do: Xcode's project wizard is GUI-only. Follow the
steps, then run the verification command. Everything after this is automatable.

**Files:**
- Create: `ios/Cloudmoji/Cloudmoji.xcodeproj`, `ios/Cloudmoji/Cloudmoji/CloudmojiApp.swift`, `ios/Cloudmoji/Cloudmoji/ContentView.swift`
- Create: `ios/Cloudmoji/Resources/Fonts/` (two font files)
- Modify: `docs/superpowers/specs/2026-07-27-ios-watchos-app-design.md` (setup steps 6 and 8)

**Interfaces:**
- Consumes: the `CloudmojiCore` package at `ios/CloudmojiCore`
- Produces: a scheme named `Cloudmoji` that builds for the iOS Simulator

- [ ] **Step 1: Create the project**

Open Xcode → **File → New → Project**. Choose the **iOS** tab, then **App** —
**not** the Multiplatform tab. Multiplatform pre-ticks Mac as a destination and
still would not cover watchOS (that is a separate target either way), so it buys
nothing here and drags in Mac decisions this design does not want yet. Mac can be
added later as a checkbox under Supported Destinations if it is ever wanted.

Then:

| Field | Value |
|---|---|
| Product Name | `Cloudmoji` |
| Team | your Apple ID (Personal Team is fine) |
| Organization Identifier | `app.cloudmoji` |
| Bundle Identifier | should read `app.cloudmoji.Cloudmoji` — leave it |
| Interface | SwiftUI |
| Language | Swift |
| Testing System | **Swift Testing with XCTest UI Tests** |
| Storage | None |

Save it into the existing `ios/` directory — **not** a new subfolder. When the save
sheet appears, navigate to `/Users/kevincjz/Programming/cloudmoji/ios` and untick
"Create Git repository".

- [ ] **Step 2: Set the identifier and deployment target**

Select the project in the navigator → **Cloudmoji** target → **General**:
- Minimum Deployments → iOS **17.0**. Xcode defaults this to whatever OS you are
  running (26.5 on this machine), which would restrict the app to devices on that
  release — effectively nobody. This is the easiest setting to leave wrong.
- iPhone and iPad both ticked, Mac unticked

Leave the bundle identifier as `app.cloudmoji.Cloudmoji`. It is the reverse-DNS of
the domain you own, and it is permanent once an App Store Connect record exists —
so it is worth being right now rather than later.

- [ ] **Step 3: Add the local package**

**File → Add Package Dependencies…** → **Add Local…** → choose
`/Users/kevincjz/Programming/cloudmoji/ios/CloudmojiCore` → **Add Package**.

When asked which target to add it to, choose **Cloudmoji**.

Confirm it worked: the project navigator should show a `CloudmojiCore` package, and
**Cloudmoji → General → Frameworks, Libraries, and Embedded Content** should list
`CloudmojiCore`.

> `EmojiData.json` lives inside the package and reaches the app through `Bundle.module`.
> Do **not** add it to the app target — that would bundle it twice.

- [ ] **Step 4: Add the fonts** — *deferrable; see the note below*

Download Lilita One and Nunito from Google Fonts as `.ttf`. Create a group
`Resources/Fonts` in the app target and drag both in, with **Copy items if needed**
ticked and the **Cloudmoji** target checked.

Then in **Info** (the target's Info tab), add a row:
- Key: `Fonts provided by application` (`UIAppFonts`)
- Item 0: `LilitaOne-Regular.ttf`
- Item 1: `Nunito-VariableFont_wght.ttf`

Use whatever the files are actually named — the names must match exactly.

> **This step can be deferred to any point before Task 5.** `Font.custom` falls back to
> the system font when a name is not registered: no error, no warning, just the wrong
> typeface. So the app builds and runs without fonts — you simply cannot tell by looking
> whether they are working, which is why Task 5 adds an explicit check.
>
> **The name that matters is the PostScript name, not the filename.** `Nunito-VariableFont_wght.ttf`
> does not mean `Font.custom("Nunito-VariableFont_wght")` works. After adding the files,
> find the real names:
>
> ```swift
> // Drop into any #Preview or the app's init, temporarily.
> for family in UIFont.familyNames.sorted() where family.contains("Nunito") || family.contains("Lilita") {
>     print(family, UIFont.fontNames(forFamilyName: family))
> }
> ```
>
> Whatever that prints is what `Theme` must use.

- [ ] **Step 5: Verify it builds from the command line**

Run from the repo root:

```bash
xcodebuild -project ios/Cloudmoji/Cloudmoji.xcodeproj -scheme Cloudmoji \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max' \
  build 2>&1 | tail -5
```

Expected: `** BUILD SUCCEEDED **`

If the destination name is wrong, list what you have:

```bash
xcrun simctl list devices available | grep iPhone
```

- [ ] **Step 6: Verify the package is actually linked**

Replace the body of `ios/Cloudmoji/Cloudmoji/ContentView.swift` with:

```swift
import SwiftUI
import CloudmojiCore

struct ContentView: View {
    var body: some View {
        // Temporary: proves the package is linked and its resources load.
        // Task 5 replaces this with the real UI.
        Text(verbatim: {
            guard let repo = try? EmojiRepository() else { return "no content" }
            return "\(repo.emojis.count) emojis, \(repo.languages.count) languages"
        }())
        .font(.title)
    }
}

#Preview { ContentView() }
```

Build again with the command from Step 5. Expected: `** BUILD SUCCEEDED **`.

Then run it in the simulator (**Cmd+R** in Xcode) — the screen should read
**200 emojis, 5 languages**. If it says "no content", the package resource is not
reaching the app and nothing later will work; stop and fix that first.

- [ ] **Step 7: Correct the spec**

The spec's project-setup steps 6 and 8 say to add `EmojiData.json` to the app target's
Copy Bundle Resources and to the watch target's. That is wrong — it lives in the package
and both targets reach it via `Bundle.module`. Edit
`docs/superpowers/specs/2026-07-27-ios-watchos-app-design.md` so step 6 reads:

```
6. `EmojiData.json` lives inside CloudmojiCore's resources and reaches every
   target through `Bundle.module`. Do not add it to any target's Copy Bundle
   Resources — that would bundle it twice.
```

and renumber the remaining steps.

- [ ] **Step 8: Commit**

```bash
git add ios/Cloudmoji/Cloudmoji.xcodeproj ios/Cloudmoji docs/superpowers/specs
git commit -m "feat(ios): add the Cloudmoji app target

Universal iOS 17 app linking the CloudmojiCore package. EmojiData.json stays
in the package and reaches the app via Bundle.module rather than being copied
into the target, which the spec's setup steps had wrong."
```

---

### Task 2: Speech engine adapter

**Files:**
- Create: `ios/Cloudmoji/Cloudmoji/SystemSpeechEngine.swift`
- Test: `ios/Cloudmoji/CloudmojiTests/SystemSpeechEngineTests.swift`

**Interfaces:**
- Consumes: `SpeechEngine`, `SpeechUtterance`, `VoiceDescribing` from `CloudmojiCore`
- Produces: `SystemSpeechEngine()`, conforming to `SpeechEngine`; `.invalidateVoiceCache()`

- [ ] **Step 1: Write the failing test**

Create `ios/Cloudmoji/CloudmojiTests/SystemSpeechEngineTests.swift`:

```swift
import Testing
import AVFoundation
@testable import Cloudmoji
import CloudmojiCore

@MainActor
@Suite("SystemSpeechEngine")
struct SystemSpeechEngineTests {
    @Test("installed voices are enumerated once and cached")
    func voicesAreCached() {
        let engine = SystemSpeechEngine()
        let first = engine.voices()
        let second = engine.voices()
        // Enumerating installed voices is not free, and this sits on the
        // tap-to-speech path with a sub-200ms budget.
        #expect(engine.voiceLookupCount == 1)
        #expect(first.count == second.count)
    }

    @Test("invalidating the cache forces a fresh lookup")
    func invalidateForcesLookup() {
        let engine = SystemSpeechEngine()
        _ = engine.voices()
        engine.invalidateVoiceCache()
        _ = engine.voices()
        #expect(engine.voiceLookupCount == 2)
    }

    @Test("stop drops the pending finish callback")
    func stopDropsCallback() {
        let engine = SystemSpeechEngine()
        var finished = false
        engine.speak(
            SpeechUtterance(text: "hello", languageTag: "en-US", voice: nil) { finished = true }
        )
        engine.stop()
        // A late delegate callback after stop must not resume a cancelled queue.
        engine.simulateFinish()
        #expect(finished == false)
    }

    @Test("finishing invokes the callback exactly once")
    func finishInvokesOnce() {
        let engine = SystemSpeechEngine()
        var count = 0
        engine.speak(
            SpeechUtterance(text: "hello", languageTag: "en-US", voice: nil) { count += 1 }
        )
        engine.simulateFinish()
        engine.simulateFinish()
        #expect(count == 1)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run:

```bash
xcodebuild -project ios/Cloudmoji/Cloudmoji.xcodeproj -scheme Cloudmoji \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max' \
  -only-testing:CloudmojiTests -parallel-testing-enabled NO \
  test 2>&1 | grep -E "error:|Testing failed" | head -5
```

Expected: `cannot find 'SystemSpeechEngine' in scope`

- [ ] **Step 3: Write the implementation**

Create `ios/Cloudmoji/Cloudmoji/SystemSpeechEngine.swift`:

```swift
import AVFoundation
import CloudmojiCore

/// Binds `AVSpeechSynthesizer` to the queue protocol `CloudmojiCore` defines.
///
/// The synthesiser's delegate methods are not main-actor isolated, so they hop
/// back explicitly. Only one utterance is ever in flight — `SpeechController`
/// always stops before speaking — so a single pending callback is sufficient.
@MainActor
final class SystemSpeechEngine: NSObject, SpeechEngine {
    private let synthesizer = AVSpeechSynthesizer()
    private var pendingFinish: (() -> Void)?
    private var cachedVoices: [AVSpeechSynthesisVoice]?

    /// Test seam: how many times the system voice list was actually enumerated.
    private(set) var voiceLookupCount = 0

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    func voices() -> [any VoiceDescribing] {
        if let cachedVoices { return cachedVoices }
        voiceLookupCount += 1
        let fresh = AVSpeechSynthesisVoice.speechVoices()
        cachedVoices = fresh
        return fresh
    }

    /// Call when the app returns to the foreground — a parent may have installed
    /// a voice in Settings while the app was backgrounded.
    func invalidateVoiceCache() {
        cachedVoices = nil
    }

    func speak(_ utterance: SpeechUtterance) {
        pendingFinish = utterance.onFinish
        let u = AVSpeechUtterance(string: utterance.text)
        u.rate = SpeechController.rate
        u.pitchMultiplier = SpeechController.pitch
        u.voice = utterance.voice as? AVSpeechSynthesisVoice
            ?? AVSpeechSynthesisVoice(language: utterance.languageTag)
        // Setting a voice overrides this, but it is the fallback when no voice
        // resolved and the engine picks for itself.
        synthesizer.speak(u)
    }

    func stop() {
        // Drop the callback before stopping: a delegate call can still arrive
        // for the utterance being cancelled, and it must not resume the queue.
        pendingFinish = nil
        synthesizer.stopSpeaking(at: .immediate)
    }

    /// Invoked by the delegate, and directly by tests.
    func simulateFinish() {
        let callback = pendingFinish
        pendingFinish = nil
        callback?()
    }
}

extension SystemSpeechEngine: AVSpeechSynthesizerDelegate {
    nonisolated func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        Task { @MainActor in self.simulateFinish() }
    }

    nonisolated func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didCancel utterance: AVSpeechUtterance
    ) {
        // Cancellation already cleared the callback in `stop()`; nothing to do.
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same `xcodebuild … test` command from Step 2.
Expected: `** TEST SUCCEEDED **`, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add ios/Cloudmoji
git commit -m "feat(ios): bind AVSpeechSynthesizer to the SpeechEngine protocol

Voices are cached — enumerating them is not free and this sits on the
tap-to-speech path. stop() drops the pending callback before stopping, because
a delegate call can still arrive for the utterance being cancelled and must not
resume a queue that was just cancelled."
```

---

### Task 3: Speech watchdog and completion callback

Two gaps the Stage 1 review recorded. Both live in `CloudmojiCore`.

**Files:**
- Modify: `ios/CloudmojiCore/Sources/CloudmojiCore/SpeechController.swift`
- Test: `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SpeechControllerTests.swift`

**Interfaces:**
- Consumes: `SpeechController`, `SpeechEngine` from Stage 1
- Produces: `speak(_ text: String, in language: Language, onFinish: (() -> Void)?)` —
  the parameter defaults to `nil`, so existing call sites are unchanged;
  `SpeechController.watchdogInterval: Duration`

- [ ] **Step 1: Write the failing test**

Add to `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SpeechControllerTests.swift`:

```swift
    @Test("a single word reports completion")
    func speakReportsCompletion() {
        let (controller, engine) = makeController()
        var finished = false
        controller.speak("apple", in: .en) { finished = true }
        #expect(finished == false)
        engine.finishCurrent()
        #expect(finished, "the mascot cannot return to happy without this")
    }

    @Test("a cancelled single word does not report completion")
    func cancelledSpeakDoesNotReportCompletion() {
        let (controller, engine) = makeController()
        var finished = false
        controller.speak("apple", in: .en) { finished = true }
        controller.cancelAll()
        engine.finishLate()
        #expect(finished == false)
    }

    @Test("a sequence advances when the engine never reports finishing")
    func watchdogAdvancesAStalledSequence() async throws {
        let (controller, engine) = makeController()
        controller.watchdogInterval = .milliseconds(50)
        controller.speakSequence(
            [SpeechItem(text: "one"), SpeechItem(text: "two")],
            in: .en
        )
        #expect(engine.spoken.map(\.text) == ["one"])
        // The engine never calls back — a real synthesiser can drop didFinish on
        // a route change or session interruption.
        try await Task.sleep(for: .milliseconds(200))
        #expect(engine.spoken.map(\.text) == ["one", "two"],
                "a dropped didFinish must not strand the rest of the sequence")
    }

    @Test("the watchdog does not double-advance when the engine does report")
    func watchdogDoesNotDoubleAdvance() async throws {
        let (controller, engine) = makeController()
        controller.watchdogInterval = .milliseconds(50)
        controller.speakSequence(
            [SpeechItem(text: "one"), SpeechItem(text: "two"), SpeechItem(text: "three")],
            in: .en
        )
        engine.finishCurrent()
        try await Task.sleep(for: .milliseconds(200))
        #expect(engine.spoken.map(\.text) == ["one", "two", "three"])
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios/CloudmojiCore && swift test --filter SpeechController`
Expected: FAIL — `extra argument 'onFinish' in call`, and `watchdogInterval` not found.

- [ ] **Step 3: Add the completion callback**

In `SpeechController.swift`, replace `speak`:

```swift
    /// Speaks one word. `onFinish` runs when the engine reports completion, and
    /// is dropped if the utterance is cancelled first — the mascot uses it to
    /// return from speaking to happy.
    public func speak(
        _ text: String,
        in language: Language,
        onFinish: (() -> Void)? = nil
    ) {
        cancelAll()
        guard !text.isEmpty else { return }
        let token = generation
        emit(text, in: language) {
            guard token == self.generation else { return }
            onFinish?()
        }
    }
```

- [ ] **Step 4: Add the watchdog**

In `SpeechController.swift`, add the property:

```swift
    /// How long to wait for the engine to report finishing before advancing
    /// anyway. A real synthesiser can drop `didFinish` on a route change or an
    /// interruption, which would otherwise strand the rest of a sequence.
    public var watchdogInterval: Duration = .seconds(6)

    private var watchdog: Task<Void, Never>?
```

In `cancelAll()`, add before `engine.stop()`:

```swift
        watchdog?.cancel()
        watchdog = nil
```

In `speakSequence`'s `step()`, replace the `emit` call with:

```swift
            var advanced = false
            let advance = { [weak self] in
                guard let self, !advanced, token == self.generation else { return }
                advanced = true
                self.watchdog?.cancel()
                self.watchdog = nil
                step()
            }

            emit(item.text, in: language, onFinish: advance)

            watchdog?.cancel()
            watchdog = Task { @MainActor [weak self] in
                guard let self else { return }
                try? await Task.sleep(for: self.watchdogInterval)
                guard !Task.isCancelled else { return }
                advance()
            }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd ios/CloudmojiCore && swift test`
Expected: PASS — 68 tests.

- [ ] **Step 6: Commit**

```bash
git add ios/CloudmojiCore
git commit -m "feat(core): speech completion callback and stall watchdog

Both were recorded as Stage 1 gaps. The mascot cannot return from speaking to
happy without a completion signal, and a synthesiser that drops didFinish on a
route change would otherwise strand the rest of a replay until the next tap."
```

---

### Task 4: App model

**Files:**
- Create: `ios/Cloudmoji/Cloudmoji/AppModel.swift`
- Test: `ios/Cloudmoji/CloudmojiTests/AppModelTests.swift`

**Interfaces:**
- Consumes: `EmojiRepository`, `SettingsStore`, `CountingGrammar`, `VoiceResolver`,
  `SpeechController` from `CloudmojiCore`; `SystemSpeechEngine` from Task 2
- Produces: `@Observable final class AppModel`, `@MainActor`, with
  `.emojis(in category: Category?) -> [EmojiEntry]`, `.availableLanguages: [LanguageMeta]`,
  `.categories: [CategoryTab]`, `.settings`, `.speech`, `.word(for: EmojiEntry) -> String`

- [ ] **Step 1: Give the package an empty repository**

`AppModel` needs a degraded fallback if the bundled resource ever fails to load, but
`EmojiData`'s memberwise initialiser is internal — Swift does not synthesise a public one
for a public struct — so the app target cannot construct one. Rather than expose the whole
memberwise surface, add a named empty value.

Append to `ios/CloudmojiCore/Sources/CloudmojiCore/EmojiRepository.swift`:

```swift
extension EmojiRepository {
    /// A repository with no content. The degraded case when the bundled resource
    /// cannot be loaded — the app shows an empty grid rather than crashing in
    /// front of a child. Reaching this in production means the build is broken.
    public static let empty = EmojiRepository(
        data: EmojiData(
            version: 0, languages: [], categories: [],
            emojis: [], countables: [], numberWords: [:]
        )
    )
}
```

Verify: `cd ios/CloudmojiCore && swift test` still passes, then commit it with the app
model in Step 5.

- [ ] **Step 2: Write the failing test**

Create `ios/Cloudmoji/CloudmojiTests/AppModelTests.swift`:

```swift
import Foundation
import Testing
@testable import Cloudmoji
import CloudmojiCore

@MainActor
@Suite("AppModel")
struct AppModelTests {
    /// Isolated defaults per test, so cases cannot leak into each other.
    func makeModel() -> AppModel {
        let suite = UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return AppModel(settings: SettingsStore(defaults: defaults))
    }

    @Test("exposes all content by default")
    func allContentByDefault() {
        let model = makeModel()
        #expect(model.emojis(in: nil).count == 200)
        #expect(model.availableLanguages.count == 5)
        #expect(model.categories.count == 9)
    }

    @Test("disabling a category narrows both the grid and the tabs")
    func disablingACategory() {
        let model = makeModel()
        let before = model.emojis(in: nil).count
        model.settings.enabledCategories.remove(.fruits)
        #expect(model.emojis(in: nil).count < before)
        #expect(!model.categories.contains { $0.id == "fruits" })
        // Views must never have to filter for themselves.
        #expect(model.emojis(in: nil).allSatisfy { $0.cat != .fruits })
    }

    @Test("disabling a language narrows the picker")
    func disablingALanguage() {
        let model = makeModel()
        model.settings.enabledLanguages = [.en, .zh]
        #expect(model.availableLanguages.map(\.id) == [.en, .zh])
    }

    @Test("the word follows the selected language")
    func wordFollowsLanguage() throws {
        let model = makeModel()
        let apple = try #require(model.emojis(in: .fruits).first { $0.emoji == "🍎" })
        model.settings.language = .en
        #expect(model.word(for: apple) == "apple")
        model.settings.language = .ja
        #expect(model.word(for: apple) == "りんご")
    }

    @Test("filtering by category returns only that category")
    func filterByCategory() {
        let model = makeModel()
        let fruits = model.emojis(in: .fruits)
        #expect(!fruits.isEmpty)
        #expect(fruits.allSatisfy { $0.cat == .fruits })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run the `xcodebuild … test` command from Task 2 Step 2 (unit tests only, one simulator).
Expected: `cannot find 'AppModel' in scope`

- [ ] **Step 4: Write the implementation**

Create `ios/Cloudmoji/Cloudmoji/AppModel.swift`:

```swift
import Foundation
import Observation
import CloudmojiCore

/// Everything the views read. Settings filtering happens here, so a view never
/// branches on a setting — it consumes an already-narrowed list.
///
/// `@MainActor` because SwiftUI reads it and `SpeechController` is main-actor
/// isolated. Stage 3's WatchConnectivity callbacks arrive off-main and will
/// need to hop here; that is the isolation decision Stage 1 left open.
@MainActor
@Observable
final class AppModel {
    let settings: SettingsStore
    let speech: SpeechController
    let grammar: CountingGrammar

    private let repository: EmojiRepository
    private let allEmojis: [EmojiEntry]

    init(settings: SettingsStore = SettingsStore()) {
        self.settings = settings
        // A missing or malformed bundled resource is a build error, not a
        // runtime path — but the child must never see a crash, so an empty
        // repository is the degraded case rather than a trap.
        let repo = (try? EmojiRepository()) ?? .empty
        self.repository = repo
        self.allEmojis = repo.emojis
        self.grammar = CountingGrammar(repository: repo)
        self.speech = SpeechController(
            resolver: VoiceResolver(languages: repo.languages),
            engine: SystemSpeechEngine()
        )
    }

    var availableLanguages: [LanguageMeta] {
        repository.languages.filter { settings.enabledLanguages.contains($0.id) }
    }

    var categories: [CategoryTab] {
        repository.categories.filter { tab in
            guard let category = tab.category else { return true } // "all"
            return settings.enabledCategories.contains(category)
        }
    }

    /// `nil` means the "all" tab.
    func emojis(in category: Category?) -> [EmojiEntry] {
        allEmojis.filter { entry in
            guard settings.enabledCategories.contains(entry.cat) else { return false }
            guard let category else { return true }
            return entry.cat == category
        }
    }

    func word(for entry: EmojiEntry) -> String {
        entry.word(settings.language)
    }

    func label(for tab: CategoryTab) -> String {
        tab.label(settings.language)
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run the `xcodebuild … test` command (unit tests only, one simulator).
Expected: `** TEST SUCCEEDED **`, 9 tests.

- [ ] **Step 6: Commit**

```bash
git add ios/Cloudmoji ios/CloudmojiCore
git commit -m "feat(ios): add AppModel

Settings filtering lives here so views consume an already-narrowed list and
never branch on a setting themselves. Main-actor isolated, which also settles
the SettingsStore isolation question Stage 1 left open."
```

---

### Task 5: Theme and mascot

**Files:**
- Create: `ios/Cloudmoji/Cloudmoji/Theme.swift`, `ios/Cloudmoji/Cloudmoji/Views/CloudMascot.swift`
- Modify: `ios/Cloudmoji/Cloudmoji/ContentView.swift` (replace the Task 1 placeholder)

**Interfaces:**
- Consumes: nothing from earlier tasks
- Produces: `Theme.background`, `.coral`, `.teal`, `.gold`, `.cloudWhite`, `.blush`,
  `.textPrimary`, `.textSecondary`, `Theme.display(_ size:)`, `Theme.body(_ size:_ weight:)`;
  `CloudMascot(mood: MascotMood, size: CGFloat)` where
  `enum MascotMood { case happy, excited, speaking, beaming }`

- [ ] **Step 1: Write the theme**

Create `ios/Cloudmoji/Cloudmoji/Theme.swift`:

```swift
import SwiftUI

/// One place for colour and type, mirroring docs/design/DESIGN_SYSTEM.md.
enum Theme {
    static let background = LinearGradient(
        colors: [
            Color(red: 0.059, green: 0.055, blue: 0.165), // #0F0E2A
            Color(red: 0.102, green: 0.067, blue: 0.271), // #1A1145
            Color(red: 0.051, green: 0.129, blue: 0.216), // #0D2137
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    static let coral = Color(red: 1.0, green: 0.420, blue: 0.420)      // #FF6B6B
    static let teal = Color(red: 0.306, green: 0.804, blue: 0.769)     // #4ECDC4
    static let gold = Color(red: 1.0, green: 0.902, blue: 0.427)       // #FFE66D
    static let cloudWhite = Color.white
    static let blush = Color(red: 1.0, green: 0.710, blue: 0.710)      // #FFB5B5

    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.4)
    static let surface = Color.white.opacity(0.04)
    static let surfaceBorder = Color.white.opacity(0.06)

    static func display(_ size: CGFloat) -> Font {
        .custom("LilitaOne-Regular", size: size)
    }

    static func body(_ size: CGFloat, _ weight: Font.Weight = .heavy) -> Font {
        .custom("Nunito", size: size).weight(weight)
    }
}
```

- [ ] **Step 2: Write the mascot**

Create `ios/Cloudmoji/Cloudmoji/Views/CloudMascot.swift`:

```swift
import SwiftUI

enum MascotMood {
    case happy, excited, speaking, beaming
}

/// The cloud character. Drawn with shapes rather than a ported SVG so it scales
/// cleanly from a 42pt landscape header to a 64pt portrait one.
struct CloudMascot: View {
    let mood: MascotMood
    var size: CGFloat = 64

    @State private var float = false

    var body: some View {
        ZStack {
            if mood == .beaming {
                Circle()
                    .fill(Theme.gold.opacity(0.35))
                    .blur(radius: size * 0.18)
                    .frame(width: size * 1.25, height: size * 1.25)
            }
            body_
        }
        .frame(width: size, height: size * 0.78)
        .offset(y: float ? -3 : 0)
        .animation(
            .easeInOut(duration: mood == .beaming ? 1.2 : 3)
                .repeatForever(autoreverses: true),
            value: float
        )
        .onAppear { float = true }
        .accessibilityHidden(true)
    }

    private var body_: some View {
        ZStack {
            // Cloud silhouette: three bumps over a rounded base.
            Circle().frame(width: size * 0.46, height: size * 0.46)
                .offset(x: -size * 0.22, y: -size * 0.04)
            Circle().frame(width: size * 0.56, height: size * 0.56)
                .offset(y: -size * 0.14)
            Circle().frame(width: size * 0.42, height: size * 0.42)
                .offset(x: size * 0.24, y: -size * 0.02)
            RoundedRectangle(cornerRadius: size * 0.22)
                .frame(width: size * 0.92, height: size * 0.42)
                .offset(y: size * 0.14)
        }
        .foregroundStyle(Theme.cloudWhite)
        .overlay(face)
    }

    private var face: some View {
        VStack(spacing: size * 0.05) {
            HStack(spacing: size * 0.20) {
                eye
                eye
            }
            mouth
        }
        .offset(y: size * 0.12)
    }

    @ViewBuilder private var eye: some View {
        switch mood {
        case .excited:
            Image(systemName: "star.fill")
                .resizable().scaledToFit()
                .frame(width: size * 0.15)
                .foregroundStyle(Theme.gold)
        case .beaming:
            // Squinting: a happy arc rather than a round eye.
            Capsule()
                .frame(width: size * 0.14, height: size * 0.05)
                .foregroundStyle(.black.opacity(0.75))
        default:
            Circle()
                .frame(width: size * 0.09, height: size * 0.09)
                .foregroundStyle(.black.opacity(0.75))
        }
    }

    @ViewBuilder private var mouth: some View {
        switch mood {
        case .speaking:
            Ellipse()
                .frame(width: size * 0.16, height: size * 0.18)
                .foregroundStyle(Theme.coral)
        case .excited, .beaming:
            Capsule()
                .frame(width: size * 0.26, height: size * 0.12)
                .foregroundStyle(Theme.coral)
        case .happy:
            Capsule()
                .frame(width: size * 0.18, height: size * 0.05)
                .foregroundStyle(.black.opacity(0.6))
        }
    }
}

#Preview {
    HStack(spacing: 24) {
        CloudMascot(mood: .happy)
        CloudMascot(mood: .excited)
        CloudMascot(mood: .speaking)
        CloudMascot(mood: .beaming)
    }
    .padding(40)
    .background(Theme.background)
}
```

- [ ] **Step 3: Confirm whether the custom fonts are registered**

`Font.custom` fails silently, so check explicitly rather than by eye. Add this temporarily
to `CloudmojiApp.init()`, run once, then remove it:

```swift
let wanted = ["LilitaOne-Regular", "Nunito"]
for name in wanted {
    let registered = UIFont(name: name, size: 12) != nil
    print("font \(name): \(registered ? "registered" : "MISSING — falling back to system")")
}
```

If either says MISSING, the app still works — it just uses the system font. Either finish
Task 1 Step 4 now, or carry on and come back to it; nothing else depends on fonts.

- [ ] **Step 4: Verify it renders**

Open `ios/Cloudmoji/Cloudmoji/Views/CloudMascot.swift` in Xcode and resume the preview
(**Cmd+Option+P**). All four moods should render as recognisable cloud faces: round eyes
and a small smile for happy, gold stars for excited, an open coral mouth for speaking, and
squinting eyes with a golden glow for beaming.

This is a visual check — there is no assertion that can substitute for looking at it.

- [ ] **Step 5: Build**

Run the `xcodebuild … build` command from Task 1 Step 5.
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 6: Commit**

```bash
git add ios/Cloudmoji
git commit -m "feat(ios): add theme and cloud mascot

Mascot is drawn with shapes rather than a ported SVG, so it scales from the
42pt landscape header to the 64pt portrait one without redrawing."
```

---

### Task 6: Emoji tile and grid

**Files:**
- Create: `ios/Cloudmoji/Cloudmoji/Views/EmojiTile.swift`, `ios/Cloudmoji/Cloudmoji/Views/EmojiGrid.swift`

**Interfaces:**
- Consumes: `Theme` from Task 5; `EmojiEntry` from `CloudmojiCore`
- Produces: `EmojiTile(entry:isBouncing:onTap:)`, `EmojiGrid(entries:bouncingID:onTap:)`

- [ ] **Step 1: Write the tile**

Create `ios/Cloudmoji/Cloudmoji/Views/EmojiTile.swift`:

```swift
import SwiftUI
import CloudmojiCore

/// One emoji. 72pt is the project's preferred child-facing target; the rule is
/// 64pt minimum, and this is the surface a toddler taps most.
struct EmojiTile: View {
    let entry: EmojiEntry
    var isBouncing: Bool = false
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(entry.emoji)
                .font(.system(size: 40))
                .frame(minWidth: 72, minHeight: 72)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 18))
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(Theme.surfaceBorder, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .scaleEffect(isBouncing ? 1.3 : 1.0)
        .animation(.spring(duration: 0.4), value: isBouncing)
        .accessibilityLabel(entry.en)
        .accessibilityIdentifier("emoji-\(entry.emoji)")
    }
}
```

- [ ] **Step 2: Write the grid**

Create `ios/Cloudmoji/Cloudmoji/Views/EmojiGrid.swift`:

```swift
import SwiftUI
import CloudmojiCore

struct EmojiGrid: View {
    let entries: [EmojiEntry]
    var bouncingID: String?
    let onTap: (EmojiEntry) -> Void

    private let columns = [GridItem(.adaptive(minimum: 72), spacing: 8)]

    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(entries) { entry in
                    EmojiTile(
                        entry: entry,
                        isBouncing: bouncingID == entry.id,
                        onTap: { onTap(entry) }
                    )
                }
            }
            .padding(.horizontal, 10)
            .padding(.bottom, 24)
        }
        .accessibilityIdentifier("emoji-grid")
    }
}

#Preview {
    let repo = try! EmojiRepository()
    return EmojiGrid(entries: Array(repo.emojis.prefix(24))) { _ in }
        .background(Theme.background)
}
```

- [ ] **Step 3: Build and check the preview**

Run the `xcodebuild … build` command.
Expected: `** BUILD SUCCEEDED **`

Then resume the preview in `EmojiGrid.swift` — you should see a grid of fruit tiles that
reflows with the pane width.

- [ ] **Step 4: Commit**

```bash
git add ios/Cloudmoji
git commit -m "feat(ios): add emoji tile and grid

Adaptive columns at a 72pt minimum, so the same grid reflows from iPhone SE to
iPad without a breakpoint."
```

---

### Task 7: Typing row and word bubble

**Files:**
- Create: `ios/Cloudmoji/Cloudmoji/Views/TypingRow.swift`, `ios/Cloudmoji/Cloudmoji/Views/WordBubble.swift`

**Interfaces:**
- Consumes: `Theme` from Task 5
- Produces: `TypedEmoji(id: UUID, emoji: String, word: String)`;
  `TypingRow(typed:muted:onReplay:onDelete:onClear:onTapTyped:)`;
  `WordBubble(emoji: String, word: String)`;
  `TypingRow.maxTyped: Int` (50)

- [ ] **Step 1: Write the typing row**

Create `ios/Cloudmoji/Cloudmoji/Views/TypingRow.swift`:

```swift
import SwiftUI

struct TypedEmoji: Identifiable, Equatable {
    let id = UUID()
    let emoji: String
    let word: String
}

struct TypingRow: View {
    /// PRD: at most 50 emojis in the row, oldest dropped first.
    static let maxTyped = 50

    let typed: [TypedEmoji]
    let muted: Bool
    let onReplay: () -> Void
    let onDelete: () -> Void
    let onClear: () -> Void
    let onTapTyped: (TypedEmoji) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    if typed.isEmpty {
                        Text("Tap emojis below! 👇")
                            .font(Theme.body(15))
                            .foregroundStyle(Theme.textSecondary)
                            .padding(.leading, 4)
                    } else {
                        ForEach(typed) { item in
                            Button { onTapTyped(item) } label: {
                                Text(item.emoji)
                                    .font(.system(size: 32))
                                    .frame(minWidth: 64, minHeight: 64)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(item.word)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if !typed.isEmpty {
                if !muted { control("🔊", "replay-btn", Theme.teal, onReplay) }
                control("⌫", "delete-btn", Color(red: 1, green: 0.7, blue: 0.28), onDelete)
                control("✕", "clear-btn", Theme.coral, onClear)
            }
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 72)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 18))
        .accessibilityIdentifier("typing-row")
    }

    private func control(
        _ glyph: String, _ id: String, _ tint: Color, _ action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(glyph)
                .font(.system(size: 22))
                // Child-facing, so 64pt — not the 44pt parent-chrome minimum.
                .frame(width: 64, height: 64)
                .background(tint.opacity(0.2), in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}
```

- [ ] **Step 2: Write the word bubble**

Create `ios/Cloudmoji/Cloudmoji/Views/WordBubble.swift`:

```swift
import SwiftUI

/// The floating label shown for ~2.2s after a tap.
struct WordBubble: View {
    let emoji: String
    let word: String

    @State private var shown = false

    var body: some View {
        HStack(spacing: 8) {
            Text(emoji).font(.system(size: 22))
            Text(word)
                .font(Theme.body(20, .black))
                .foregroundStyle(Theme.textPrimary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().stroke(Color.white.opacity(0.18), lineWidth: 1))
        .opacity(shown ? 1 : 0)
        .scaleEffect(shown ? 1 : 0.7)
        .offset(y: shown ? 0 : 12)
        .onAppear {
            withAnimation(.spring(duration: 0.3)) { shown = true }
        }
        .accessibilityIdentifier("word-bubble")
    }
}
```

- [ ] **Step 3: Build**

Run the `xcodebuild … build` command.
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 4: Commit**

```bash
git add ios/Cloudmoji
git commit -m "feat(ios): add typing row and word bubble

Typed emojis and the replay/delete/clear controls are 64pt — a child taps
them, so they follow the child-facing rule rather than the 44pt HIG minimum."
```

---

### Task 8: Category source and adaptive shell

**Files:**
- Create: `ios/Cloudmoji/Cloudmoji/Views/CategorySource.swift`, `ios/Cloudmoji/Cloudmoji/Views/AdaptiveShell.swift`

**Interfaces:**
- Consumes: `Theme`, `AppModel`, `CategoryTab`
- Produces: `CategorySource(tabs:selected:label:layout:onSelect:)` with
  `enum CategoryLayout { case horizontal, rail }`;
  `AdaptiveShell(content:)` exposing `\.cloudmojiIsCompact` in the environment

- [ ] **Step 1: Write the category source**

Create `ios/Cloudmoji/Cloudmoji/Views/CategorySource.swift`:

```swift
import SwiftUI
import CloudmojiCore

enum CategoryLayout {
    case horizontal   // portrait: a scrolling strip
    case rail         // landscape: a vertical rail of icons
}

/// One component, two layouts.
///
/// The web app kept two copies of this list — one for each orientation — and
/// three separate edits landed on the dead copy before anyone noticed. There is
/// deliberately only one here.
struct CategorySource: View {
    let tabs: [CategoryTab]
    let selected: String
    let label: (CategoryTab) -> String
    let layout: CategoryLayout
    let onSelect: (CategoryTab) -> Void

    var body: some View {
        switch layout {
        case .horizontal:
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(tabs) { tab in chip(tab, showsLabel: true) }
                }
                .padding(.horizontal, 12)
            }
            .accessibilityIdentifier("category-bar")
        case .rail:
            ScrollView(showsIndicators: false) {
                LazyVGrid(columns: [GridItem(.fixed(64)), GridItem(.fixed(64))], spacing: 8) {
                    ForEach(tabs) { tab in chip(tab, showsLabel: false) }
                }
                .padding(.vertical, 8)
            }
            .accessibilityIdentifier("category-rail")
        }
    }

    private func chip(_ tab: CategoryTab, showsLabel: Bool) -> some View {
        let isActive = tab.id == selected
        return Button { onSelect(tab) } label: {
            HStack(spacing: 6) {
                Text(tab.icon).font(.system(size: showsLabel ? 18 : 28))
                if showsLabel {
                    Text(label(tab))
                        .font(Theme.body(15, .bold))
                        .fixedSize()
                }
            }
            .frame(minWidth: showsLabel ? 0 : 64, minHeight: 64)
            .padding(.horizontal, showsLabel ? 16 : 0)
            .background(
                isActive ? Theme.teal.opacity(0.2) : Theme.surface,
                in: RoundedRectangle(cornerRadius: 16)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isActive ? Theme.teal.opacity(0.4) : Theme.surfaceBorder,
                            lineWidth: 1.5)
            )
            .foregroundStyle(isActive ? Theme.teal : Theme.textSecondary)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label(tab))
        .accessibilityIdentifier("cat-\(tab.id)")
    }
}
```

- [ ] **Step 2: Write the adaptive shell**

Create `ios/Cloudmoji/Cloudmoji/Views/AdaptiveShell.swift`:

```swift
import SwiftUI

private struct CompactLayoutKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    /// True when the screen is short and wide — a phone held sideways. Keyed on
    /// height, not orientation alone, so a tall iPad in landscape keeps the
    /// roomy portrait layout.
    var cloudmojiIsCompact: Bool {
        get { self[CompactLayoutKey.self] }
        set { self[CompactLayoutKey.self] = newValue }
    }
}

struct AdaptiveShell<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        GeometryReader { proxy in
            content
                .environment(\.cloudmojiIsCompact, proxy.size.height <= 560
                             && proxy.size.width > proxy.size.height)
                .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .background(Theme.background.ignoresSafeArea())
    }
}
```

- [ ] **Step 3: Build**

Run the `xcodebuild … build` command.
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 4: Commit**

```bash
git add ios/Cloudmoji
git commit -m "feat(ios): add category source and adaptive shell

One CategorySource renders either the portrait strip or the landscape rail.
The web app kept two copies and three edits landed on the dead one."
```

---

### Task 9: Words mode

**Files:**
- Create: `ios/Cloudmoji/Cloudmoji/Views/WordsView.swift`
- Modify: `ios/Cloudmoji/Cloudmoji/ContentView.swift`, `ios/Cloudmoji/Cloudmoji/CloudmojiApp.swift`

**Interfaces:**
- Consumes: everything from Tasks 4–8
- Produces: `WordsView()`, reading `AppModel` from the environment

- [ ] **Step 1: Write Words mode**

Create `ios/Cloudmoji/Cloudmoji/Views/WordsView.swift`:

```swift
import SwiftUI
import CloudmojiCore

struct WordsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.cloudmojiIsCompact) private var isCompact

    @State private var category: String = "all"
    @State private var typed: [TypedEmoji] = []
    @State private var bubble: TypedEmoji?
    @State private var bouncingID: String?
    @State private var mood: MascotMood = .happy
    @State private var tapCount = 0
    @State private var bubbleTask: Task<Void, Never>?

    private var entries: [EmojiEntry] {
        model.emojis(in: Category(rawValue: category))
    }

    var body: some View {
        Group {
            if isCompact { landscape } else { portrait }
        }
        .onDisappear { model.speech.cancelAll() }
    }

    // MARK: Layouts

    private var portrait: some View {
        VStack(spacing: 6) {
            header
            typingRow
            bubbleSlot.frame(height: 44)
            CategorySource(
                tabs: model.categories, selected: category,
                label: model.label, layout: .horizontal, onSelect: select
            )
            grid
        }
    }

    private var landscape: some View {
        HStack(spacing: 0) {
            CategorySource(
                tabs: model.categories, selected: category,
                label: model.label, layout: .rail, onSelect: select
            )
            .frame(width: 156)

            VStack(spacing: 4) {
                header
                typingRow
                grid
            }
            .overlay(alignment: .bottom) { bubbleSlot.padding(.bottom, 6) }
        }
    }

    // MARK: Pieces

    private var header: some View {
        HStack {
            CloudMascot(mood: mood, size: isCompact ? 42 : 64)
            VStack(alignment: .leading, spacing: 1) {
                Text("Cloudmoji")
                    .font(Theme.display(isCompact ? 17 : 21))
                    .foregroundStyle(Theme.teal)
                if !isCompact {
                    Text("Tap. Listen. Learn!")
                        .font(Theme.body(10, .heavy))
                        .foregroundStyle(Theme.textSecondary)
                }
            }
            Spacer()
            languagePicker
        }
        .padding(.horizontal, 14)
    }

    private var languagePicker: some View {
        @Bindable var settings = model.settings
        return Picker("Language", selection: $settings.language) {
            ForEach(model.availableLanguages) { meta in
                Text(meta.short).tag(meta.id)
            }
        }
        .pickerStyle(.menu)
        .tint(Theme.textPrimary)
        // Parent-facing chrome, so the 44pt HIG minimum rather than 64pt.
        .frame(minWidth: 44, minHeight: 44)
        .accessibilityIdentifier("lang-picker")
    }

    private var typingRow: some View {
        TypingRow(
            typed: typed,
            muted: model.settings.muted,
            onReplay: replayAll,
            onDelete: { model.speech.cancelAll(); if !typed.isEmpty { typed.removeLast() } },
            onClear: { model.speech.cancelAll(); typed.removeAll(); bubble = nil },
            onTapTyped: { speak($0.word, emoji: $0.emoji) }
        )
        .padding(.horizontal, 12)
    }

    @ViewBuilder private var bubbleSlot: some View {
        if let bubble {
            WordBubble(emoji: bubble.emoji, word: bubble.word).id(bubble.id)
        }
    }

    private var grid: some View {
        EmojiGrid(entries: entries, bouncingID: bouncingID, onTap: tap)
    }

    // MARK: Behaviour

    private func select(_ tab: CategoryTab) {
        category = tab.id
        speak(model.label(for: tab), emoji: tab.icon)
    }

    private func tap(_ entry: EmojiEntry) {
        let word = model.word(for: entry)
        typed = (typed + [TypedEmoji(emoji: entry.emoji, word: word)])
            .suffix(TypingRow.maxTyped)
        bouncingID = entry.id
        speak(word, emoji: entry.emoji)

        tapCount += 1
        if [10, 25, 50, 100].contains(tapCount) { celebrate() }

        Task {
            try? await Task.sleep(for: .milliseconds(400))
            bouncingID = nil
        }
    }

    private func speak(_ word: String, emoji: String) {
        showBubble(TypedEmoji(emoji: emoji, word: word))
        guard !model.settings.muted else { return }
        if mood != .beaming { mood = .excited }
        model.speech.speak(word, in: model.settings.language) {
            if mood != .beaming { mood = .happy }
        }
    }

    private func replayAll() {
        guard !typed.isEmpty, !model.settings.muted else { return }
        model.speech.speakSequence(
            typed.map { item in
                SpeechItem(text: item.word) { showBubble(item) }
            },
            in: model.settings.language
        )
    }

    private func showBubble(_ item: TypedEmoji) {
        bubble = item
        bubbleTask?.cancel()
        bubbleTask = Task {
            try? await Task.sleep(for: .milliseconds(2200))
            guard !Task.isCancelled else { return }
            bubble = nil
        }
    }

    private func celebrate() {
        Task {
            try? await Task.sleep(for: .milliseconds(500))
            mood = .beaming
            try? await Task.sleep(for: .seconds(3))
            mood = .happy
        }
    }
}
```

- [ ] **Step 2: Wire it up**

Replace `ios/Cloudmoji/Cloudmoji/ContentView.swift` entirely:

```swift
import SwiftUI

struct ContentView: View {
    var body: some View {
        AdaptiveShell { WordsView() }
    }
}
```

Replace `ios/Cloudmoji/Cloudmoji/CloudmojiApp.swift` entirely:

```swift
import SwiftUI
import AVFoundation

@main
struct CloudmojiApp: App {
    @State private var model = AppModel()

    init() {
        // .playback so Cloudmoji speaks even with the ringer switch off — what a
        // parent expects when handing over the phone. A deliberate override of a
        // system setting, recorded as such in the design spec.
        try? AVAudioSession.sharedInstance().setCategory(.playback, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    var body: some Scene {
        WindowGroup {
            ContentView().environment(model)
        }
    }
}
```

- [ ] **Step 3: Build and run**

Run the `xcodebuild … build` command. Expected: `** BUILD SUCCEEDED **`

Then **Cmd+R** in Xcode. Tap 🍎 — you should hear "apple", see the word bubble, and see
the mascot's eyes turn to stars and back. Switch the language picker to 日本語 and tap it
again: "りんご".

Rotate the simulator (**Cmd+←**) and the category strip should become a rail on the left.

- [ ] **Step 4: Commit**

```bash
git add ios/Cloudmoji
git commit -m "feat(ios): Words mode

The core loop: tap, hear, see the word, mascot reacts. Portrait and landscape
share one view body and differ only in how the pieces are arranged."
```

---

### Task 10: UI tests

**Files:**
- Create: `ios/Cloudmoji/CloudmojiUITests/WordsModeUITests.swift`

**Interfaces:**
- Consumes: accessibility identifiers set in Tasks 6–9
- Produces: nothing consumed by later tasks

- [ ] **Step 1: Write the tests**

Create `ios/Cloudmoji/CloudmojiUITests/WordsModeUITests.swift`:

```swift
import XCTest

final class WordsModeUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
        return app
    }

    func testTappingAnEmojiShowsItsWord() {
        let app = launch()
        let apple = app.buttons["emoji-🍎"]
        XCTAssertTrue(apple.waitForExistence(timeout: 5))
        apple.tap()
        XCTAssertTrue(app.otherElements["word-bubble"].waitForExistence(timeout: 2))
    }

    func testTappedEmojiJoinsTheTypingRow() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()
        app.buttons["emoji-🍌"].tap()
        let row = app.otherElements["typing-row"]
        XCTAssertTrue(row.buttons["apple"].exists)
        XCTAssertTrue(row.buttons["banana"].exists)
    }

    func testClearEmptiesTheTypingRow() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()
        app.buttons["clear-btn"].tap()
        XCTAssertFalse(app.otherElements["typing-row"].buttons["apple"].exists)
    }

    func testEveryChildFacingControlMeetsTheSixtyFourPointRule() {
        let app = launch()
        app.buttons["emoji-🍎"].tap()

        var checked = 0
        for id in ["emoji-🍎", "replay-btn", "delete-btn", "clear-btn", "cat-all"] {
            let element = app.buttons[id]
            guard element.exists else { continue }
            XCTAssertGreaterThanOrEqual(
                element.frame.height, 64,
                "\(id) is \(element.frame.height)pt — child-facing controls must be 64pt+"
            )
            checked += 1
        }
        XCTAssertGreaterThan(checked, 3, "too few controls found to be a real check")
    }

    func testCategorySelectionFiltersTheGrid() {
        let app = launch()
        let all = app.buttons.matching(identifier: "emoji-🍎").count
        XCTAssertGreaterThan(all, 0)
        app.buttons["cat-animals"].tap()
        XCTAssertFalse(app.buttons["emoji-🍎"].exists, "fruit should be filtered out")
        XCTAssertTrue(app.buttons["emoji-🐶"].exists)
    }
}
```

- [ ] **Step 2: Run the tests**

Run:

```bash
xcodebuild -project ios/Cloudmoji/Cloudmoji.xcodeproj -scheme Cloudmoji \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max' \
  -parallel-testing-enabled NO \
  test 2>&1 | tail -8
```

> `-parallel-testing-enabled NO` matters on a memory-constrained machine. Without it
> Xcode clones the simulator once per test target and leaves the clones running if the
> run is interrupted — four booted iPhones is easy to reach and slow to notice.

Expected: `** TEST SUCCEEDED **` — the unit tests from Tasks 2 and 4 plus these 5.

- [ ] **Step 3: Commit**

```bash
git add ios/Cloudmoji
git commit -m "test(ios): UI tests for Words mode

Covers the behaviours regression-tested on web: the tap loop, the typing row,
clear, category filtering, and the 64pt child-facing touch-target rule."
```

---

## Definition of done

- [ ] `xcodebuild … build` succeeds
- [ ] `xcodebuild … test` succeeds — unit and UI tests
- [ ] `cd ios/CloudmojiCore && swift test` still passes (68 after Task 3)
- [ ] The app runs on the simulator: tapping an emoji speaks it in the selected language,
      the word bubble appears, the mascot reacts, and rotating swaps the strip for the rail
- [ ] `npm run typecheck && npm run lint && npx playwright test` still pass — this plan
      touches no web code, so any failure means something leaked

## What this plan deliberately does not do

- **Count mode.** Stage 2b.
- **The parental gate, Settings and About screens.** Stage 2b. Until then the language
  picker is unprotected, which is acceptable while nothing behind a gate exists.
- **`UsageLog`.** Stage 2b, with the stats screen that consumes it.
- **Voice-availability guidance.** The spec defers prompting a parent to install a
  Filipino voice; `VoiceResolver` keeps the seam.
- **App Store submission.** Icons, launch screen, privacy nutrition labels and the Kids
  Category declaration all come after parity.
