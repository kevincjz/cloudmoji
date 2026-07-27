# CloudmojiCore + Content Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the UI-free Swift package that holds Cloudmoji's data models, counting grammars, voice selection, settings and speech queue — plus the generator that keeps its content in lockstep with the web app.

**Architecture:** A standalone SwiftPM package (`ios/CloudmojiCore`) with no Xcode project, so `swift test` runs from the CLI in seconds. Content is generated from `src/data/*.ts` into a bundled `EmojiData.json` by a Node script in the web repo, with a CI check that fails if the committed JSON drifts from freshly generated output.

**Tech Stack:** Swift 6, SwiftPM, Swift Testing (`import Testing`), AVFoundation, Node + tsx for the generator.

This is **Stage 1 of 3** from [the design spec](../specs/2026-07-27-ios-watchos-app-design.md). Stage 2 (iOS app) and Stage 3 (watchOS) get their own plans once this lands.

## Global Constraints

- Swift tools version 6.0; package platforms `.iOS(.v17)`, `.watchOS(.v10)`, `.macOS(.v14)` (macOS only so `swift test` runs on the CLI).
- `CloudmojiCore` imports only non-UI system frameworks — Foundation, AVFoundation and
  Observation. **Never** SwiftUI, UIKit or WatchKit.
- All public API is `Sendable` where it crosses a concurrency boundary.
- Speech rate `0.85`, pitch `1.1` — matching `src/hooks/useTTS.ts`.
- Five languages exactly: `en`, `zh`, `ms`, `ja`, `tl`. Never Spanish, never Thai.
- Japanese uses hiragana for native words, katakana for loanwords, **no kanji**. 🦷 is katakana `ハ` deliberately — a standalone `は` is voiced "wa". Do not "correct" it.
- Japanese counting is noun-first: `りんご みっつ`, never `みっつのりんご`.
- Tagalog and Japanese countables are **bare nouns**; only `zh` and `ms` bake in the classifier.
- Content is never hand-edited in Swift. `src/data/*.ts` is the only source of truth.
- Commit after every task. Repo commit style: lowercase `type: subject`, body explains why.

---

## File Structure

**Created in the web repo:**

| File | Responsibility |
|---|---|
| `tools/generate-ios-data/index.ts` | Reads `src/data/*.ts`, emits `EmojiData.json` |
| `tools/generate-ios-data/schema.ts` | The JSON shape, shared by generator and its test |
| `tests/ios-data-parity.spec.ts` | Playwright-run check that committed JSON matches source |

**Created under `ios/CloudmojiCore/`:**

| File | Responsibility |
|---|---|
| `Package.swift` | Package manifest |
| `Sources/CloudmojiCore/Models.swift` | `Language`, `Category`, `EmojiEntry`, `Countable`, `CategoryTab`, `LanguageMeta` |
| `Sources/CloudmojiCore/EmojiData.swift` | Top-level decodable container |
| `Sources/CloudmojiCore/EmojiRepository.swift` | Loads and exposes the bundled JSON |
| `Sources/CloudmojiCore/CountingGrammar.swift` | Per-language count phrases |
| `Sources/CloudmojiCore/VoiceResolver.swift` | Prefix-chain voice selection |
| `Sources/CloudmojiCore/SettingsStore.swift` | Persisted, validated parent settings |
| `Sources/CloudmojiCore/SpeechController.swift` | Cancellable speech queue |
| `Sources/CloudmojiCore/Resources/EmojiData.json` | Generated; committed |
| `Tests/CloudmojiCoreTests/*.swift` | One test file per source file above |

---

### Task 1: Package scaffolding

**Files:**
- Create: `ios/CloudmojiCore/Package.swift`
- Create: `ios/CloudmojiCore/Sources/CloudmojiCore/CloudmojiCore.swift`
- Test: `ios/CloudmojiCore/Tests/CloudmojiCoreTests/PackageSmokeTests.swift`

**Interfaces:**
- Consumes: nothing
- Produces: a package named `CloudmojiCore` whose library product other targets link against; `CloudmojiCore.version` (`String`)

- [ ] **Step 1: Write the failing test**

Create `ios/CloudmojiCore/Tests/CloudmojiCoreTests/PackageSmokeTests.swift`:

```swift
import Testing
@testable import CloudmojiCore

@Test("package exposes its version")
func packageVersion() {
    #expect(CloudmojiCore.version == "1.0.0")
}
```

- [ ] **Step 2: Create the manifest**

Create `ios/CloudmojiCore/Package.swift`:

```swift
// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "CloudmojiCore",
    // macOS is listed only so `swift test` runs on the command line.
    platforms: [.iOS(.v17), .watchOS(.v10), .macOS(.v14)],
    products: [
        .library(name: "CloudmojiCore", targets: ["CloudmojiCore"])
    ],
    targets: [
        .target(
            name: "CloudmojiCore",
            resources: [.process("Resources")]
        ),
        .testTarget(
            name: "CloudmojiCoreTests",
            dependencies: ["CloudmojiCore"]
        )
    ]
)
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd ios/CloudmojiCore && swift test`
Expected: FAIL — `cannot find 'CloudmojiCore' in scope`, and a warning that `Sources/CloudmojiCore/Resources` does not exist.

- [ ] **Step 4: Write minimal implementation**

Create `ios/CloudmojiCore/Sources/CloudmojiCore/CloudmojiCore.swift`:

```swift
/// Namespace for package-level metadata.
public enum CloudmojiCore {
    public static let version = "1.0.0"
}
```

Create the resources directory so SwiftPM stops warning:

```bash
mkdir -p ios/CloudmojiCore/Sources/CloudmojiCore/Resources
touch ios/CloudmojiCore/Sources/CloudmojiCore/Resources/.gitkeep
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd ios/CloudmojiCore && swift test`
Expected: PASS — `1 test passed`

- [ ] **Step 6: Ignore build artifacts**

`swift test` writes a `.build` directory that must never be committed —
`git add ios/CloudmojiCore` would otherwise sweep in ~940KB of object files.

Create `ios/CloudmojiCore/.gitignore`:

```gitignore
# SwiftPM build artifacts — regenerated by `swift build` / `swift test`.
.build/
.swiftpm/
Package.resolved
```

- [ ] **Step 7: Commit**

```bash
git add ios/CloudmojiCore
git commit -m "feat(ios): scaffold CloudmojiCore package

Pure SwiftPM with no Xcode project, so the grammar tests run from the CLI in
seconds. The Xcode project arrives in stage 2 when there is a UI to host."
```

---

### Task 2: Content generator

**Files:**
- Create: `tools/generate-ios-data/schema.ts`
- Create: `tools/generate-ios-data/index.ts` (pure — no side effects on import)
- Create: `tools/generate-ios-data/cli.ts` (writes the file)
- Modify: `package.json` (add `tsx` devDependency and a `generate:ios` script)
- Create: `ios/CloudmojiCore/Sources/CloudmojiCore/Resources/EmojiData.json` (generated)

**Interfaces:**
- Consumes: `src/data/emojis.ts` (`EMOJIS`, `CATEGORIES`), `src/data/countables.ts` (`COUNTABLES`, `NUMBER_WORDS`), `src/data/languages.ts` (`LANGUAGES`)
- Produces: `EmojiData.json` with top-level keys `version`, `languages`, `categories`, `emojis`, `countables`, `numberWords`

- [ ] **Step 1: Define the schema**

Create `tools/generate-ios-data/schema.ts`:

```typescript
import type { Language } from "../../src/types";

export interface IosLanguage {
  id: Language;
  short: string;
  name: string;
  speech: string;
  voicePrefixes: string[];
}

export interface IosCategory {
  id: string; // "all" or a Category
  icon: string;
  labels: Record<string, string>; // keyed by Language
}

export interface IosEmoji {
  emoji: string;
  cat: string;
  en: string;
  zh: string;
  ms: string;
  ja: string;
  tl: string;
}

export interface IosCountable extends IosEmoji {
  /** Present only where the regular pluraliser is wrong (teeth, mice). */
  enPlural?: string;
}

export interface IosEmojiData {
  version: number;
  languages: IosLanguage[];
  categories: IosCategory[];
  emojis: IosEmoji[];
  countables: Omit<IosCountable, "cat">[];
  numberWords: Record<string, string[]>;
}
```

- [ ] **Step 2: Write the generator**

Create `tools/generate-ios-data/index.ts`:

```typescript
import { EMOJIS, CATEGORIES } from "../../src/data/emojis";
import { COUNTABLES, NUMBER_WORDS } from "../../src/data/countables";
import { LANGUAGES } from "../../src/data/languages";
import type { IosEmojiData } from "./schema";

export function build(): IosEmojiData {
  return {
    version: 1,
    languages: LANGUAGES.map((l) => ({
      id: l.id,
      short: l.short,
      name: l.name,
      speech: l.speech,
      voicePrefixes: l.voicePrefixes,
    })),
    categories: CATEGORIES.map((c) => ({
      id: c.id,
      icon: c.icon,
      labels: { ...c.labels },
    })),
    emojis: EMOJIS.map((e) => ({
      emoji: e.emoji,
      cat: e.cat,
      en: e.en,
      zh: e.zh,
      ms: e.ms,
      ja: e.ja,
      tl: e.tl,
    })),
    countables: COUNTABLES.map((c) => ({
      emoji: c.emoji,
      en: c.en,
      ...(c.enPlural ? { enPlural: c.enPlural } : {}),
      zh: c.zh,
      ms: c.ms,
      ja: c.ja,
      tl: c.tl,
    })),
    numberWords: { ...NUMBER_WORDS },
  };
}

/** Stable formatting so the committed file only changes when the data does. */
export function serialise(data: IosEmojiData): string {
  return JSON.stringify(data, null, 2) + "\n";
}

```

This module has **no side effects on import**. The parity test in Task 10 imports
`build` and `serialise`; if importing it also rewrote the JSON, the test would
regenerate the file before comparing and could never fail.

- [ ] **Step 2b: Write the CLI entry point**

Create `tools/generate-ios-data/cli.ts`:

```typescript
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { build, serialise } from "./index";

const OUT = resolve(
  import.meta.dirname,
  "../../ios/CloudmojiCore/Sources/CloudmojiCore/Resources/EmojiData.json",
);

const data = build();
mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, serialise(data), "utf8");
console.log(
  `wrote ${OUT}\n  ${data.emojis.length} emojis, ${data.countables.length} countables, ` +
    `${data.languages.length} languages, ${data.categories.length} categories`,
);
```

- [ ] **Step 3: Add the tooling**

Run:

```bash
npm i -D tsx
```

Then add to `package.json` scripts:

```json
"generate:ios": "tsx tools/generate-ios-data/cli.ts"
```

- [ ] **Step 4: Run the generator**

Run: `npm run generate:ios`
Expected: `200 emojis, 84 countables, 5 languages, 9 categories`

- [ ] **Step 5: Verify the output shape**

Run:

```bash
node -e "const d=require('./ios/CloudmojiCore/Sources/CloudmojiCore/Resources/EmojiData.json');
console.log(d.emojis.length, d.countables.length, d.languages.length);
console.log(d.countables.find(c=>c.en==='tooth'));
console.log(d.emojis.find(e=>e.emoji==='🦷').ja);"
```

Expected:
```
200 84 5
{ emoji: '🦷', en: 'tooth', enPlural: 'teeth', zh: '颗牙齿', ms: 'batang gigi', ja: 'ハ', tl: 'ngipin' }
ハ
```

- [ ] **Step 6: Commit**

```bash
git add tools/generate-ios-data package.json package-lock.json ios/CloudmojiCore/Sources/CloudmojiCore/Resources/EmojiData.json
git commit -m "feat(ios): generate EmojiData.json from the web data

src/data/*.ts stays the single source of truth. Word and Count mode already
drifted apart inside one codebase; hand-porting 200 emojis in five languages
into Swift would drift faster."
```

---

### Task 3: Models and repository

**Files:**
- Create: `ios/CloudmojiCore/Sources/CloudmojiCore/Models.swift`
- Create: `ios/CloudmojiCore/Sources/CloudmojiCore/EmojiData.swift`
- Create: `ios/CloudmojiCore/Sources/CloudmojiCore/EmojiRepository.swift`
- Test: `ios/CloudmojiCore/Tests/CloudmojiCoreTests/EmojiRepositoryTests.swift`

**Interfaces:**
- Consumes: `EmojiData.json` from Task 2
- Produces:
  - `Language` (`enum String`: `en, zh, ms, ja, tl`)
  - `Category` (`enum String`: `fruits, food, animals, vehicles, nature, objects, people, faces`)
  - `EmojiEntry.word(_ language: Language) -> String`
  - `Countable.noun(_ language: Language) -> String`, `Countable.enPlural: String?`
  - `CategoryTab.label(_ language: Language) -> String`
  - `EmojiRepository(bundle:) throws`, `.emojis`, `.countables`, `.categories`, `.languages`
  - `EmojiRepository.numberWord(_ language: Language, count: Int) -> String?`

- [ ] **Step 1: Write the failing test**

Create `ios/CloudmojiCore/Tests/CloudmojiCoreTests/EmojiRepositoryTests.swift`:

```swift
import Testing
@testable import CloudmojiCore

@Suite("EmojiRepository")
struct EmojiRepositoryTests {
    let repo = try! EmojiRepository()

    @Test("loads the full content set")
    func counts() {
        #expect(repo.emojis.count == 200)
        #expect(repo.countables.count == 84)
        #expect(repo.languages.count == 5)
        #expect(repo.categories.count == 9)
    }

    @Test("every emoji has a non-empty word in all five languages")
    func allLanguagesPopulated() {
        for entry in repo.emojis {
            for language in Language.allCases {
                #expect(!entry.word(language).isEmpty, "\(entry.emoji) missing \(language)")
            }
        }
    }

    @Test("tooth keeps its deliberate katakana spelling")
    func toothIsKatakana() throws {
        let tooth = try #require(repo.emojis.first { $0.emoji == "🦷" })
        // Hiragana は is parsed as the topic particle and voiced "wa".
        #expect(tooth.word(.ja) == "ハ")
    }

    @Test("number words run one through ten in every language")
    func numberWords() {
        for language in Language.allCases {
            #expect(repo.numberWord(language, count: 1) != nil)
            #expect(repo.numberWord(language, count: 10) != nil)
            #expect(repo.numberWord(language, count: 11) == nil)
            #expect(repo.numberWord(language, count: 0) == nil)
        }
    }

    @Test("category labels are translated in every language")
    func categoryLabels() {
        for tab in repo.categories {
            for language in Language.allCases {
                #expect(!tab.label(language).isEmpty, "\(tab.id) missing \(language)")
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios/CloudmojiCore && swift test`
Expected: FAIL — `cannot find 'EmojiRepository' in scope`

- [ ] **Step 3: Write the models**

Create `ios/CloudmojiCore/Sources/CloudmojiCore/Models.swift`:

```swift
import Foundation

public enum Language: String, Codable, CaseIterable, Sendable, Hashable {
    case en, zh, ms, ja, tl
}

public enum Category: String, Codable, CaseIterable, Sendable, Hashable {
    case fruits, food, animals, vehicles, nature, objects, people, faces
}

public struct LanguageMeta: Codable, Sendable, Hashable, Identifiable {
    public let id: Language
    /// The language's own short name, shown on the toggle: EN, 中文, BM, 日本語, TL.
    public let short: String
    /// English name, shown in the picker so a parent can find it.
    public let name: String
    /// BCP-47 code handed to AVSpeechSynthesizer.
    public let speech: String
    /// Ordered voice-language prefixes to try. See VoiceResolver.
    public let voicePrefixes: [String]
}

public struct EmojiEntry: Codable, Sendable, Hashable, Identifiable {
    public let emoji: String
    public let cat: Category
    public let en, zh, ms, ja, tl: String

    /// Stable across categories, since an emoji may appear in more than one.
    public var id: String { "\(emoji)|\(cat.rawValue)" }

    public func word(_ language: Language) -> String {
        switch language {
        case .en: en
        case .zh: zh
        case .ms: ms
        case .ja: ja
        case .tl: tl
        }
    }
}

public struct Countable: Codable, Sendable, Hashable, Identifiable {
    public let emoji: String
    public let en: String
    /// Set only where the regular pluraliser is wrong: teeth, mice.
    public let enPlural: String?
    /// zh and ms bake the classifier into the noun (只狗, ekor anjing).
    /// ja and tl stay bare — their morphology lives on the number.
    public let zh, ms, ja, tl: String

    public var id: String { emoji }

    public func noun(_ language: Language) -> String {
        switch language {
        case .en: en
        case .zh: zh
        case .ms: ms
        case .ja: ja
        case .tl: tl
        }
    }
}

public struct CategoryTab: Codable, Sendable, Hashable, Identifiable {
    /// "all", or a Category raw value.
    public let id: String
    public let icon: String
    /// Keyed by Language raw value. Stored as [String: String] because Swift
    /// encodes dictionaries with non-String keys as arrays, which would not
    /// round-trip against the generated JSON.
    public let labels: [String: String]

    public func label(_ language: Language) -> String {
        labels[language.rawValue] ?? labels["en"] ?? id
    }

    /// nil for the "all" tab.
    public var category: Category? { Category(rawValue: id) }
}
```

- [ ] **Step 4: Write the container and repository**

Create `ios/CloudmojiCore/Sources/CloudmojiCore/EmojiData.swift`:

```swift
import Foundation

public struct EmojiData: Codable, Sendable {
    public let version: Int
    public let languages: [LanguageMeta]
    public let categories: [CategoryTab]
    public let emojis: [EmojiEntry]
    public let countables: [Countable]
    /// Keyed by Language raw value; ten entries each, for counts 1...10.
    public let numberWords: [String: [String]]
}
```

Create `ios/CloudmojiCore/Sources/CloudmojiCore/EmojiRepository.swift`:

```swift
import Foundation

public enum EmojiRepositoryError: Error, CustomStringConvertible {
    case resourceMissing(String)
    case decodeFailed(any Error)

    public var description: String {
        switch self {
        case .resourceMissing(let name):
            "EmojiData resource '\(name).json' is missing from the bundle"
        case .decodeFailed(let error):
            "EmojiData.json could not be decoded: \(error)"
        }
    }
}

/// Loads the generated content. The only type that knows the file format.
public struct EmojiRepository: Sendable {
    public let data: EmojiData

    public var emojis: [EmojiEntry] { data.emojis }
    public var countables: [Countable] { data.countables }
    public var categories: [CategoryTab] { data.categories }
    public var languages: [LanguageMeta] { data.languages }

    public init(data: EmojiData) {
        self.data = data
    }

    // Bundle.module is generated as internal, so it cannot be a default argument
    // on a public initialiser. Take an optional and fall back inside instead.
    public init(bundle: Bundle? = nil, resource: String = "EmojiData") throws {
        let bundle = bundle ?? .module
        guard let url = bundle.url(forResource: resource, withExtension: "json") else {
            throw EmojiRepositoryError.resourceMissing(resource)
        }
        do {
            let raw = try Data(contentsOf: url)
            self.data = try JSONDecoder().decode(EmojiData.self, from: raw)
        } catch {
            throw EmojiRepositoryError.decodeFailed(error)
        }
    }

    /// Number word for a count, or nil when the count is out of range.
    /// Japanese has no ～つ form past ten, so callers must handle nil rather
    /// than fabricating a counter.
    public func numberWord(_ language: Language, count: Int) -> String? {
        guard let words = data.numberWords[language.rawValue],
              count >= 1, count <= words.count else { return nil }
        return words[count - 1]
    }

    public func meta(for language: Language) -> LanguageMeta? {
        languages.first { $0.id == language }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd ios/CloudmojiCore && swift test --filter EmojiRepository`
Expected: PASS — 5 tests passed

- [ ] **Step 6: Commit**

```bash
git add ios/CloudmojiCore
git commit -m "feat(ios): add content models and repository

CategoryTab.labels is [String: String] rather than [Language: String]: Swift
encodes dictionaries with non-String keys as arrays, which would not
round-trip against the generated JSON."
```

---

### Task 4: English counting grammar

**Files:**
- Create: `ios/CloudmojiCore/Sources/CloudmojiCore/CountingGrammar.swift`
- Test: `ios/CloudmojiCore/Tests/CloudmojiCoreTests/CountingGrammarEnglishTests.swift`

**Interfaces:**
- Consumes: `Countable`, `Language`, `EmojiRepository` from Task 3
- Produces: `CountingGrammar(repository:)`, `.phrase(_ item: Countable, count: Int, in: Language) -> String`

- [ ] **Step 1: Write the failing test**

Create `ios/CloudmojiCore/Tests/CloudmojiCoreTests/CountingGrammarEnglishTests.swift`:

```swift
import Testing
@testable import CloudmojiCore

@Suite("CountingGrammar: English")
struct CountingGrammarEnglishTests {
    let repo = try! EmojiRepository()
    var grammar: CountingGrammar { CountingGrammar(repository: repo) }

    func item(_ en: String) throws -> Countable {
        try #require(repo.countables.first { $0.en == en })
    }

    @Test("singular keeps the bare noun")
    func singular() throws {
        #expect(grammar.phrase(try item("dog"), count: 1, in: .en) == "one dog")
    }

    @Test("regular plurals add s")
    func regular() throws {
        #expect(grammar.phrase(try item("dog"), count: 2, in: .en) == "two dogs")
    }

    @Test("irregular plurals come from the data, not the rule")
    func irregular() throws {
        #expect(grammar.phrase(try item("tooth"), count: 2, in: .en) == "two teeth")
        #expect(grammar.phrase(try item("mouse"), count: 2, in: .en) == "two mice")
    }

    @Test("fish does not gain an s")
    func fish() throws {
        #expect(grammar.phrase(try item("fish"), count: 3, in: .en) == "three fish")
    }

    @Test("sibilant endings take es")
    func sibilants() {
        let bus = Countable(emoji: "🚌", en: "bus", enPlural: nil,
                            zh: "辆巴士", ms: "buah bas", ja: "バス", tl: "bus")
        #expect(grammar.phrase(bus, count: 2, in: .en) == "two buses")
    }

    @Test("consonant-y becomes ies, vowel-y does not")
    func yEndings() {
        let berry = Countable(emoji: "🫐", en: "berry", enPlural: nil,
                              zh: "颗蓝莓", ms: "biji beri", ja: "ベリー", tl: "berry")
        let toy = Countable(emoji: "🧸", en: "toy", enPlural: nil,
                            zh: "个玩具", ms: "buah mainan", ja: "おもちゃ", tl: "laruan")
        #expect(grammar.phrase(berry, count: 2, in: .en) == "two berries")
        #expect(grammar.phrase(toy, count: 2, in: .en) == "two toys")
    }

    @Test("every shipped countable pluralises without a doubled s")
    func noDoubleS() {
        for item in repo.countables {
            let phrase = grammar.phrase(item, count: 2, in: .en)
            #expect(!phrase.hasSuffix("ss"), "\(item.en) -> \(phrase)")
            }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios/CloudmojiCore && swift test --filter CountingGrammarEnglish`
Expected: FAIL — `cannot find 'CountingGrammar' in scope`

- [ ] **Step 3: Write the implementation**

Create `ios/CloudmojiCore/Sources/CloudmojiCore/CountingGrammar.swift`:

```swift
import Foundation

/// Builds the spoken phrase for "N of this thing", per language.
///
/// The rules differ structurally, not just lexically:
/// zh and ms bake the classifier into the noun, ja fuses the counter into the
/// number and puts the noun first, tl attaches a linker to the numeral.
public struct CountingGrammar: Sendable {
    private let repository: EmojiRepository

    public init(repository: EmojiRepository) {
        self.repository = repository
    }

    public func phrase(_ item: Countable, count: Int, in language: Language) -> String {
        guard let number = repository.numberWord(language, count: count) else {
            // No number word for this count — speak the bare noun rather than
            // fabricate a counter.
            return item.noun(language)
        }

        switch language {
        case .en:
            return "\(number) \(englishPlural(item, count: count))"
        default:
            return item.noun(language)
        }
    }

    // MARK: - English

    func englishPlural(_ item: Countable, count: Int) -> String {
        guard count > 1 else { return item.en }
        if let irregular = item.enPlural { return irregular }
        return Self.regularPlural(item.en)
    }

    static func regularPlural(_ noun: String) -> String {
        if noun == "fish" { return "fish" }
        if noun.hasSuffix("y"), let beforeY = noun.dropLast().last,
           !"aeiou".contains(beforeY) {
            return noun.dropLast() + "ies"
        }
        for suffix in ["s", "sh", "ch", "x", "z"] where noun.hasSuffix(suffix) {
            return noun + "es"
        }
        return noun + "s"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ios/CloudmojiCore && swift test --filter CountingGrammarEnglish`
Expected: PASS — 7 tests passed

- [ ] **Step 5: Commit**

```bash
git add ios/CloudmojiCore
git commit -m "feat(ios): English counting grammar

Irregular plurals come from the data rather than a rule table, so teeth and
mice stay correct without the Swift side knowing which nouns are irregular."
```

---

### Task 5: Chinese and Malay counting grammar

**Files:**
- Modify: `ios/CloudmojiCore/Sources/CloudmojiCore/CountingGrammar.swift`
- Test: `ios/CloudmojiCore/Tests/CloudmojiCoreTests/CountingGrammarClassifierTests.swift`

**Interfaces:**
- Consumes: `CountingGrammar.phrase` from Task 4
- Produces: no new API — extends `phrase` to handle `.zh` and `.ms`

- [ ] **Step 1: Write the failing test**

Create `ios/CloudmojiCore/Tests/CloudmojiCoreTests/CountingGrammarClassifierTests.swift`:

```swift
import Testing
@testable import CloudmojiCore

@Suite("CountingGrammar: classifier languages")
struct CountingGrammarClassifierTests {
    let repo = try! EmojiRepository()
    var grammar: CountingGrammar { CountingGrammar(repository: repo) }

    func item(_ en: String) throws -> Countable {
        try #require(repo.countables.first { $0.en == en })
    }

    @Test("Chinese joins with no space, classifier already in the noun")
    func chinese() throws {
        #expect(grammar.phrase(try item("dog"), count: 3, in: .zh) == "三只狗")
        #expect(grammar.phrase(try item("apple"), count: 1, in: .zh) == "一个苹果")
    }

    @Test("Chinese uses 两 not 二 for two")
    func chineseTwo() throws {
        let phrase = grammar.phrase(try item("dog"), count: 2, in: .zh)
        #expect(phrase.hasPrefix("两"))
        #expect(!phrase.hasPrefix("二"))
    }

    @Test("Malay joins with a space, penjodoh already in the noun")
    func malay() throws {
        #expect(grammar.phrase(try item("dog"), count: 3, in: .ms) == "tiga ekor anjing")
        #expect(grammar.phrase(try item("apple"), count: 1, in: .ms) == "satu biji epal")
    }

    @Test("no classifier language ever emits a double space")
    func noDoubleSpace() throws {
        for item in repo.countables {
            for count in 1...10 {
                for language in [Language.zh, .ms] {
                    let phrase = grammar.phrase(item, count: count, in: language)
                    #expect(!phrase.contains("  "), "\(language) \(item.en): \(phrase)")
                    #expect(phrase == phrase.trimmingCharacters(in: .whitespaces))
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios/CloudmojiCore && swift test --filter CountingGrammarClassifier`
Expected: FAIL — Chinese returns the bare noun `只狗` instead of `三只狗`

- [ ] **Step 3: Extend the implementation**

In `CountingGrammar.swift`, replace the `switch language` block inside `phrase` with:

```swift
        switch language {
        case .en:
            return "\(number) \(englishPlural(item, count: count))"
        case .zh:
            // The measure word is already part of the noun (只狗), and Chinese
            // takes no space between numeral and classifier.
            return "\(number)\(item.zh)"
        case .ms:
            // Likewise the penjodoh bilangan (ekor anjing), space-separated.
            return "\(number) \(item.ms)"
        default:
            return item.noun(language)
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ios/CloudmojiCore && swift test --filter CountingGrammarClassifier`
Expected: PASS — 4 tests passed

- [ ] **Step 5: Commit**

```bash
git add ios/CloudmojiCore
git commit -m "feat(ios): Chinese and Malay counting grammar

Both bake the classifier into the countable noun, so the grammar only chooses
the separator: none for zh, a space for ms."
```

---

### Task 6: Japanese and Tagalog counting grammar

**Files:**
- Modify: `ios/CloudmojiCore/Sources/CloudmojiCore/CountingGrammar.swift`
- Test: `ios/CloudmojiCore/Tests/CloudmojiCoreTests/CountingGrammarJaTlTests.swift`

**Interfaces:**
- Consumes: `CountingGrammar.phrase` from Task 5
- Produces: `CountingGrammar.tagalogLinked(_ number: String) -> String` (internal, tested directly)

- [ ] **Step 1: Write the failing test**

Create `ios/CloudmojiCore/Tests/CloudmojiCoreTests/CountingGrammarJaTlTests.swift`:

```swift
import Testing
@testable import CloudmojiCore

@Suite("CountingGrammar: Japanese and Tagalog")
struct CountingGrammarJaTlTests {
    let repo = try! EmojiRepository()
    var grammar: CountingGrammar { CountingGrammar(repository: repo) }

    func item(_ en: String) throws -> Countable {
        try #require(repo.countables.first { $0.en == en })
    }

    // MARK: Japanese

    @Test("Japanese puts the noun first and the counter last")
    func japaneseOrder() throws {
        #expect(grammar.phrase(try item("apple"), count: 3, in: .ja) == "りんご みっつ")
        #expect(grammar.phrase(try item("dog"), count: 1, in: .ja) == "いぬ ひとつ")
    }

    @Test("Japanese inserts no particle between noun and counter")
    func japaneseNoParticle() throws {
        let phrase = grammar.phrase(try item("dog"), count: 2, in: .ja)
        // Exactly two space-separated tokens. Checked as a shape because some
        // nouns legitimately contain の (やしのき = palm tree).
        #expect(phrase.split(separator: " ").count == 2)
        #expect(!phrase.hasPrefix("ふたつ"))
    }

    @Test("Japanese counts one through ten with the ～つ series")
    func japaneseSeries() throws {
        let expected = ["ひとつ", "ふたつ", "みっつ", "よっつ", "いつつ",
                        "むっつ", "ななつ", "やっつ", "ここのつ", "とお"]
        let dog = try item("dog")
        for (index, counter) in expected.enumerated() {
            #expect(grammar.phrase(dog, count: index + 1, in: .ja).hasSuffix(" \(counter)"))
        }
    }

    // MARK: Tagalog

    @Test("vowel-final numerals take the -ng linker")
    func tagalogNg() throws {
        #expect(grammar.phrase(try item("dog"), count: 3, in: .tl) == "tatlong aso")
        #expect(grammar.phrase(try item("dog"), count: 1, in: .tl) == "isang aso")
        #expect(grammar.phrase(try item("dog"), count: 10, in: .tl) == "sampung aso")
    }

    @Test("consonant-final numerals take a separate na")
    func tagalogNa() throws {
        #expect(grammar.phrase(try item("dog"), count: 4, in: .tl) == "apat na aso")
        #expect(grammar.phrase(try item("dog"), count: 6, in: .tl) == "anim na aso")
        #expect(grammar.phrase(try item("dog"), count: 9, in: .tl) == "siyam na aso")
    }

    @Test("n-final numerals take -g")
    func tagalogG() {
        #expect(CountingGrammar.tagalogLinked("roon") == "roong")
    }

    @Test("Tagalog nouns are never pluralised after a numeral")
    func tagalogNoPlural() throws {
        let aso = try item("dog")
        for count in 1...10 {
            #expect(grammar.phrase(aso, count: count, in: .tl).hasSuffix(" aso"))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios/CloudmojiCore && swift test --filter CountingGrammarJaTl`
Expected: FAIL — Japanese returns `いぬ` instead of `いぬ ひとつ`

- [ ] **Step 3: Extend the implementation**

In `CountingGrammar.swift`, replace the `default:` case in `phrase` with:

```swift
        case .ja:
            // Noun first, counter last: "りんご みっつ". The number-の-noun order
            // is grammatical but bookish, and the ～つ counter is already fused
            // into the number word, so the noun never changes form.
            return "\(item.ja) \(number)"
        case .tl:
            // The linker attaches to the NUMERAL, not the noun, and the noun is
            // never pluralised after a numeral.
            return "\(Self.tagalogLinked(number)) \(item.tl)"
        }
```

Then add to the type:

```swift
    // MARK: - Tagalog

    /// Attaches the Tagalog linker to a numeral.
    /// Vowel-final takes -ng (tatlo → tatlong); n-final takes -g;
    /// any other consonant takes a separate "na" (apat → apat na).
    static func tagalogLinked(_ number: String) -> String {
        guard let last = number.lowercased().last else { return number }
        if "aeiou".contains(last) { return number + "ng" }
        if last == "n" { return number + "g" }
        return number + " na"
    }
```

Replacing `default:` with these two cases makes the switch exhaustive over `Language`, so a sixth language would become a compile error rather than silently falling through to a bare noun.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ios/CloudmojiCore && swift test --filter CountingGrammarJaTl`
Expected: PASS — 7 tests passed

- [ ] **Step 5: Run the whole suite**

Run: `cd ios/CloudmojiCore && swift test`
Expected: PASS — all tests from Tasks 1, 3, 4, 5, 6

- [ ] **Step 6: Commit**

```bash
git add ios/CloudmojiCore
git commit -m "feat(ios): Japanese and Tagalog counting grammar

Japanese is noun-first because that is how it is said aloud; the number-の-noun
order is bookish. Tagalog's linker attaches to the numeral, so apat na aso but
tatlong aso — verified across all ten numbers."
```

---

### Task 7: Voice resolution

**Files:**
- Create: `ios/CloudmojiCore/Sources/CloudmojiCore/VoiceResolver.swift`
- Test: `ios/CloudmojiCore/Tests/CloudmojiCoreTests/VoiceResolverTests.swift`

**Interfaces:**
- Consumes: `LanguageMeta.voicePrefixes`, `EmojiRepository.languages` from Task 3
- Produces:
  - `protocol VoiceDescribing { var lang: String { get }; var name: String { get } }`
  - `VoiceResolver(languages: [LanguageMeta])`
  - `.pick(from: [any VoiceDescribing], for: Language) -> (any VoiceDescribing)?`
  - `AVSpeechSynthesisVoice: VoiceDescribing` conformance

- [ ] **Step 1: Write the failing test**

Create `ios/CloudmojiCore/Tests/CloudmojiCoreTests/VoiceResolverTests.swift`:

```swift
import Testing
@testable import CloudmojiCore

private struct FakeVoice: VoiceDescribing, Equatable {
    let lang: String
    let name: String
}

@Suite("VoiceResolver")
struct VoiceResolverTests {
    let resolver = VoiceResolver(languages: try! EmojiRepository().languages)

    /// A plausible notched-iPhone voice set: no Filipino, which is the norm.
    let appleish = [
        FakeVoice(lang: "en-US", name: "Samantha"),
        FakeVoice(lang: "en-GB", name: "Daniel"),
        FakeVoice(lang: "zh-CN", name: "Tingting"),
        FakeVoice(lang: "ja-JP", name: "Kyoko"),
        FakeVoice(lang: "ms-MY", name: "Amira"),
        FakeVoice(lang: "id-ID", name: "Damayanti"),
        FakeVoice(lang: "es-ES", name: "Monica"),
    ]

    @Test("with no Filipino voice, Tagalog falls to Malay and never to English")
    func tagalogFallsToMalay() throws {
        let picked = try #require(resolver.pick(from: appleish, for: .tl))
        #expect(picked.lang == "ms-MY")
        #expect(!picked.lang.hasPrefix("en"))
    }

    @Test("a real Filipino voice wins over the fallback")
    func filipinoWins() throws {
        let voices = appleish + [FakeVoice(lang: "fil-PH", name: "Rosa")]
        #expect(try #require(resolver.pick(from: voices, for: .tl)).name == "Rosa")
    }

    @Test("tl-PH tagging is accepted as well as fil-PH")
    func tlTagAccepted() throws {
        let voices = appleish + [FakeVoice(lang: "tl-PH", name: "Angelo")]
        #expect(try #require(resolver.pick(from: voices, for: .tl)).name == "Angelo")
    }

    @Test("Malay falls back to Indonesian")
    func malayFallsToIndonesian() throws {
        let voices = appleish.filter { !$0.lang.hasPrefix("ms") }
        #expect(try #require(resolver.pick(from: voices, for: .ms)).lang == "id-ID")
        // and Tagalog then lands on Indonesian too, still not English
        #expect(try #require(resolver.pick(from: voices, for: .tl)).lang == "id-ID")
    }

    @Test("the other four languages are unaffected")
    func othersUnaffected() throws {
        #expect(try #require(resolver.pick(from: appleish, for: .en)).name == "Samantha")
        #expect(try #require(resolver.pick(from: appleish, for: .zh)).name == "Tingting")
        #expect(try #require(resolver.pick(from: appleish, for: .ja)).name == "Kyoko")
        #expect(try #require(resolver.pick(from: appleish, for: .ms)).name == "Amira")
    }

    @Test("an English-only device resolves to nothing rather than mislabelling")
    func englishOnlyDevice() {
        let voices = [FakeVoice(lang: "en-US", name: "Samantha")]
        #expect(resolver.pick(from: voices, for: .tl) == nil)
        #expect(resolver.pick(from: voices, for: .ja) == nil)
        #expect(resolver.pick(from: voices, for: .zh) == nil)
        #expect(resolver.pick(from: voices, for: .en)?.name == "Samantha")
    }

    @Test("an exact language match beats a looser one in the same tier")
    func exactMatchPreferred() throws {
        let voices = [
            FakeVoice(lang: "en-GB", name: "Daniel"),
            FakeVoice(lang: "en-US", name: "Alex"),
        ]
        #expect(try #require(resolver.pick(from: voices, for: .en)).lang == "en-US")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios/CloudmojiCore && swift test --filter VoiceResolver`
Expected: FAIL — `cannot find type 'VoiceDescribing' in scope`

- [ ] **Step 3: Write the implementation**

Create `ios/CloudmojiCore/Sources/CloudmojiCore/VoiceResolver.swift`:

```swift
import Foundation
import AVFoundation

/// Structural view of a voice, so selection can be tested without AVFoundation.
public protocol VoiceDescribing {
    var lang: String { get }
    var name: String { get }
}

extension AVSpeechSynthesisVoice: VoiceDescribing {
    public var lang: String { language }
}

/// Picks the best available voice for a language.
///
/// Walks the language's prefix chain in order and takes the first tier with any
/// voice. That is what stops a device with no Filipino voice falling through to
/// the engine's English default, which mispronounces Tagalog badly — it lands on
/// Malay instead, which shares Tagalog's vowels and its "ng".
public struct VoiceResolver: Sendable {
    private let prefixes: [Language: [String]]
    private let speechTags: [Language: String]

    private static let femaleHints = [
        "female", "samantha", "karen", "tessa",
        "tingting", "sinji", "amira", "kyoko", "o-ren", "rosa",
    ]

    public init(languages: [LanguageMeta]) {
        var prefixes: [Language: [String]] = [:]
        var tags: [Language: String] = [:]
        for meta in languages {
            prefixes[meta.id] = meta.voicePrefixes
            tags[meta.id] = meta.speech
        }
        self.prefixes = prefixes
        self.speechTags = tags
    }

    public func speechTag(for language: Language) -> String {
        speechTags[language] ?? language.rawValue
    }

    // Takes an existential array rather than a generic: SpeechEngine hands back
    // [any VoiceDescribing], and Swift will not satisfy `V: VoiceDescribing`
    // with an existential. A concrete [FakeVoice] still upcasts implicitly.
    public func pick(
        from voices: [any VoiceDescribing],
        for language: Language
    ) -> (any VoiceDescribing)? {
        let chain = prefixes[language] ?? [language.rawValue]

        var tier: [any VoiceDescribing] = []
        for prefix in chain {
            tier = voices.filter { $0.lang.hasPrefix(prefix) }
            if !tier.isEmpty { break }
        }
        guard !tier.isEmpty else { return nil }

        // Prefer an exact tag match, then a female-sounding name, then the first.
        let exact = tier.filter { $0.lang == speechTag(for: language) }
        let pool = exact.isEmpty ? tier : exact
        return pool.first { voice in
            let name = voice.name.lowercased()
            return Self.femaleHints.contains { name.contains($0) }
        } ?? pool.first
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ios/CloudmojiCore && swift test --filter VoiceResolver`
Expected: PASS — 7 tests passed

- [ ] **Step 5: Commit**

```bash
git add ios/CloudmojiCore
git commit -m "feat(ios): voice resolution with a phonetic fallback chain

Tests run against synthetic voice lists rather than the host's installed
voices, because CI machines and simulators have different sets — and the case
that matters most (no Filipino installed) cannot be reproduced on demand."
```

---

### Task 8: Settings store

**Files:**
- Create: `ios/CloudmojiCore/Sources/CloudmojiCore/SettingsStore.swift`
- Test: `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SettingsStoreTests.swift`

**Interfaces:**
- Consumes: `Language`, `Category` from Task 3
- Produces:
  - `@Observable final class SettingsStore`
  - `.language: Language`, `.enabledLanguages: Set<Language>`, `.enabledCategories: Set<Category>`, `.countRange: ClosedRange<Int>`, `.muted: Bool`
  - `init(defaults: UserDefaults)`

- [ ] **Step 1: Write the failing test**

Create `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SettingsStoreTests.swift`:

```swift
import Foundation
import Testing
@testable import CloudmojiCore

@Suite("SettingsStore")
struct SettingsStoreTests {
    /// Isolated defaults per test, so cases cannot leak into each other.
    func makeDefaults(_ name: String = UUID().uuidString) -> UserDefaults {
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    @Test("fresh install starts in English with everything enabled")
    func defaultsOnFreshInstall() {
        let store = SettingsStore(defaults: makeDefaults())
        #expect(store.language == .en)
        #expect(store.enabledLanguages == Set(Language.allCases))
        #expect(store.enabledCategories == Set(Category.allCases))
        #expect(store.countRange == 2...9)
        #expect(store.muted == false)
    }

    @Test("a stored language survives a reload")
    func persistence() {
        let defaults = makeDefaults()
        SettingsStore(defaults: defaults).language = .ja
        #expect(SettingsStore(defaults: defaults).language == .ja)
    }

    @Test("an unknown stored language recovers to English")
    func recoversFromCorruptLanguage() {
        let defaults = makeDefaults()
        // "es" was never a Cloudmoji language. On the web this exact value
        // reached NUMBER_WORDS[lang][n-1] and crashed Count mode on first tap.
        defaults.set("es", forKey: "cm_lang")
        #expect(SettingsStore(defaults: defaults).language == .en)
    }

    @Test("garbage in the enabled-languages set is filtered out")
    func filtersUnknownLanguages() {
        let defaults = makeDefaults()
        defaults.set(["en", "th", "zh"], forKey: "cm_enabled_langs")
        #expect(SettingsStore(defaults: defaults).enabledLanguages == [.en, .zh])
    }

    @Test("the enabled set is never empty")
    func neverEmpty() {
        let defaults = makeDefaults()
        defaults.set([String](), forKey: "cm_enabled_langs")
        #expect(SettingsStore(defaults: defaults).enabledLanguages == Set(Language.allCases))

        defaults.set([String](), forKey: "cm_enabled_cats")
        #expect(SettingsStore(defaults: defaults).enabledCategories == Set(Category.allCases))
    }

    @Test("the active language is forced back into the enabled set")
    func activeLanguageMustBeEnabled() {
        let defaults = makeDefaults()
        defaults.set("ja", forKey: "cm_lang")
        defaults.set(["en", "zh"], forKey: "cm_enabled_langs")
        // Disabling the active language would leave the picker with no valid
        // selection, so it recovers rather than showing an impossible state.
        #expect(SettingsStore(defaults: defaults).language == .en)
    }

    @Test("an inverted or out-of-bounds count range is clamped")
    func clampsCountRange() {
        let defaults = makeDefaults()
        defaults.set(9, forKey: "cm_count_lower")
        defaults.set(2, forKey: "cm_count_upper")
        #expect(SettingsStore(defaults: defaults).countRange == 2...9)

        defaults.set(0, forKey: "cm_count_lower")
        defaults.set(99, forKey: "cm_count_upper")
        #expect(SettingsStore(defaults: defaults).countRange == 2...10)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios/CloudmojiCore && swift test --filter SettingsStore`
Expected: FAIL — `cannot find 'SettingsStore' in scope`

- [ ] **Step 3: Write the implementation**

Create `ios/CloudmojiCore/Sources/CloudmojiCore/SettingsStore.swift`:

```swift
import Foundation
import Observation

/// Parent-facing settings, persisted and validated.
///
/// Every value is validated on read. Without that, a stale or hand-edited value
/// flows straight through — on the web a leftover language of "es" reached
/// NUMBER_WORDS[lang][n-1] and crashed Count mode on the first tap.
@Observable
public final class SettingsStore {
    private let defaults: UserDefaults

    private enum Key {
        static let language = "cm_lang"
        static let enabledLanguages = "cm_enabled_langs"
        static let enabledCategories = "cm_enabled_cats"
        static let countLower = "cm_count_lower"
        static let countUpper = "cm_count_upper"
        static let muted = "cm_muted"
    }

    /// Count mode never goes below two (one is not counting) or above ten
    /// (Japanese has no ～つ form past とお).
    public static let countBounds = 2...10

    public var language: Language {
        didSet { defaults.set(language.rawValue, forKey: Key.language) }
    }

    public var enabledLanguages: Set<Language> {
        didSet {
            let cleaned = enabledLanguages.isEmpty ? Set(Language.allCases) : enabledLanguages
            if cleaned != enabledLanguages { enabledLanguages = cleaned; return }
            defaults.set(cleaned.map(\.rawValue).sorted(), forKey: Key.enabledLanguages)
            if !cleaned.contains(language) { language = cleaned.sorted { $0.rawValue < $1.rawValue }.first! }
        }
    }

    public var enabledCategories: Set<Category> {
        didSet {
            let cleaned = enabledCategories.isEmpty ? Set(Category.allCases) : enabledCategories
            if cleaned != enabledCategories { enabledCategories = cleaned; return }
            defaults.set(cleaned.map(\.rawValue).sorted(), forKey: Key.enabledCategories)
        }
    }

    public var countRange: ClosedRange<Int> {
        didSet {
            defaults.set(countRange.lowerBound, forKey: Key.countLower)
            defaults.set(countRange.upperBound, forKey: Key.countUpper)
        }
    }

    public var muted: Bool {
        didSet { defaults.set(muted, forKey: Key.muted) }
    }

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults

        let languages = Self.readSet(defaults, Key.enabledLanguages, Language.init(rawValue:))
        self.enabledLanguages = languages.isEmpty ? Set(Language.allCases) : languages

        let categories = Self.readSet(defaults, Key.enabledCategories, Category.init(rawValue:))
        self.enabledCategories = categories.isEmpty ? Set(Category.allCases) : categories

        // didSet does not run during init, so the "active language must be
        // enabled" rule is applied explicitly here as well.
        let stored = defaults.string(forKey: Key.language).flatMap(Language.init(rawValue:))
        if let stored, self.enabledLanguages.contains(stored) {
            self.language = stored
        } else {
            self.language = .en
        }

        self.countRange = Self.readRange(defaults)
        self.muted = defaults.bool(forKey: Key.muted)
    }

    private static func readSet<T: Hashable>(
        _ defaults: UserDefaults,
        _ key: String,
        _ make: (String) -> T?
    ) -> Set<T> {
        guard defaults.object(forKey: key) != nil else { return [] }
        let raw = defaults.stringArray(forKey: key) ?? []
        return Set(raw.compactMap(make))
    }

    private static func readRange(_ defaults: UserDefaults) -> ClosedRange<Int> {
        guard defaults.object(forKey: Key.countLower) != nil,
              defaults.object(forKey: Key.countUpper) != nil else { return 2...9 }
        let lower = max(countBounds.lowerBound, defaults.integer(forKey: Key.countLower))
        let upper = min(countBounds.upperBound, defaults.integer(forKey: Key.countUpper))
        guard lower < upper else { return 2...9 }
        return lower...upper
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ios/CloudmojiCore && swift test --filter SettingsStore`
Expected: PASS — 7 tests passed

- [ ] **Step 5: Commit**

```bash
git add ios/CloudmojiCore
git commit -m "feat(ios): settings store with validation on read

Recovers rather than propagating bad values. The web app crashed on exactly
this: a stale language of 'es' reached NUMBER_WORDS[lang][n-1] and threw on
the first tap in Count mode."
```

---

### Task 9: Cancellable speech controller

**Files:**
- Create: `ios/CloudmojiCore/Sources/CloudmojiCore/SpeechController.swift`
- Test: `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SpeechControllerTests.swift`

**Interfaces:**
- Consumes: `VoiceResolver` from Task 7, `Language` from Task 3
- Produces:
  - `protocol SpeechEngine` with `speak(_:)`, `stop()`, `voices()`
  - `SpeechController(resolver:engine:)`
  - `.speak(_ text: String, in: Language)`, `.speakSequence(_ items: [SpeechItem], in: Language)`, `.cancelAll()`
  - `struct SpeechItem { let text: String; let onSpeak: (@Sendable () -> Void)? }`

- [ ] **Step 1: Write the failing test**

Create `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SpeechControllerTests.swift`:

```swift
import Testing
@testable import CloudmojiCore

/// Records what would have been spoken, and lets a test decide when an
/// utterance "finishes" — so queue behaviour is deterministic.
@MainActor
private final class FakeEngine: SpeechEngine {
    var spoken: [(text: String, lang: String)] = []
    var stopCount = 0
    var onFinish: (() -> Void)?

    func voices() -> [any VoiceDescribing] {
        [FakeVoice(lang: "en-US", name: "Samantha")]
    }

    func speak(_ utterance: SpeechUtterance) {
        spoken.append((utterance.text, utterance.languageTag))
        onFinish = utterance.onFinish
    }

    func stop() {
        stopCount += 1
        onFinish = nil
    }

    /// Simulates the engine finishing the current utterance.
    func finishCurrent() {
        let callback = onFinish
        onFinish = nil
        callback?()
    }

    private struct FakeVoice: VoiceDescribing {
        let lang: String
        let name: String
    }
}

@MainActor
@Suite("SpeechController")
struct SpeechControllerTests {
    func makeController() -> (SpeechController, FakeEngine) {
        let engine = FakeEngine()
        let resolver = VoiceResolver(languages: try! EmojiRepository().languages)
        return (SpeechController(resolver: resolver, engine: engine), engine)
    }

    @Test("speaking cancels whatever came before")
    func speakCancelsPrevious() {
        let (controller, engine) = makeController()
        controller.speak("apple", in: .en)
        controller.speak("banana", in: .en)
        #expect(engine.spoken.map(\.text) == ["apple", "banana"])
        #expect(engine.stopCount == 2)
    }

    @Test("a sequence advances only when the engine reports finished")
    func sequenceChainsOnFinish() {
        let (controller, engine) = makeController()
        controller.speakSequence(
            [SpeechItem(text: "one"), SpeechItem(text: "two"), SpeechItem(text: "three")],
            in: .en
        )
        #expect(engine.spoken.map(\.text) == ["one"])
        engine.finishCurrent()
        #expect(engine.spoken.map(\.text) == ["one", "two"])
        engine.finishCurrent()
        #expect(engine.spoken.map(\.text) == ["one", "two", "three"])
    }

    @Test("cancelAll stops a sequence already in flight")
    func cancelStopsSequence() {
        let (controller, engine) = makeController()
        controller.speakSequence(
            [SpeechItem(text: "one"), SpeechItem(text: "two"), SpeechItem(text: "three")],
            in: .en
        )
        controller.cancelAll()
        engine.finishCurrent() // a late callback from the cancelled utterance
        #expect(engine.spoken.map(\.text) == ["one"], "nothing may speak after cancelAll")
    }

    @Test("onSpeak fires for each item as it starts")
    func onSpeakCallbacks() {
        let (controller, engine) = makeController()
        var seen: [String] = []
        controller.speakSequence(
            [
                SpeechItem(text: "one") { seen.append("one") },
                SpeechItem(text: "two") { seen.append("two") },
            ],
            in: .en
        )
        #expect(seen == ["one"])
        engine.finishCurrent()
        #expect(seen == ["one", "two"])
    }

    @Test("an empty sequence speaks nothing")
    func emptySequence() {
        let (controller, engine) = makeController()
        controller.speakSequence([], in: .en)
        #expect(engine.spoken.isEmpty)
    }

    @Test("rate and pitch match the web app")
    func rateAndPitch() {
        #expect(SpeechController.rate == 0.85)
        #expect(SpeechController.pitch == 1.1)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ios/CloudmojiCore && swift test --filter SpeechController`
Expected: FAIL — `cannot find type 'SpeechEngine' in scope`

- [ ] **Step 3: Write the implementation**

Create `ios/CloudmojiCore/Sources/CloudmojiCore/SpeechController.swift`:

```swift
import Foundation
import AVFoundation

public struct SpeechUtterance {
    public let text: String
    public let languageTag: String
    public let voice: (any VoiceDescribing)?
    public let onFinish: () -> Void
}

/// Seam over AVSpeechSynthesizer so queue behaviour is testable without audio.
@MainActor
public protocol SpeechEngine: AnyObject {
    func voices() -> [any VoiceDescribing]
    func speak(_ utterance: SpeechUtterance)
    func stop()
}

public struct SpeechItem {
    public let text: String
    public let onSpeak: (() -> Void)?

    public init(text: String, onSpeak: (() -> Void)? = nil) {
        self.text = text
        self.onSpeak = onSpeak
    }
}

/// Speaks single words and sequences, and can genuinely cancel either.
///
/// Sequences chain on the engine's finish callback rather than on timers. A
/// timer-based queue keeps firing after mute, after a language change and after
/// the round is replaced, because each callback holds stale state and nothing
/// can call it back — which is exactly what happened on the web.
@MainActor
public final class SpeechController {
    public static let rate: Float = 0.85
    public static let pitch: Float = 1.1

    private let resolver: VoiceResolver
    private let engine: any SpeechEngine
    /// Bumped on every cancel. Queued work compares against it and bails.
    private var generation = 0

    public init(resolver: VoiceResolver, engine: any SpeechEngine) {
        self.resolver = resolver
        self.engine = engine
    }

    public func cancelAll() {
        generation += 1
        engine.stop()
    }

    public func speak(_ text: String, in language: Language) {
        guard !text.isEmpty else { return }
        cancelAll()
        emit(text, in: language, onFinish: {})
    }

    public func speakSequence(_ items: [SpeechItem], in language: Language) {
        guard !items.isEmpty else { return }
        cancelAll()
        let token = generation
        var index = 0

        func step() {
            guard token == generation, index < items.count else { return }
            let item = items[index]
            index += 1
            item.onSpeak?()
            emit(item.text, in: language) {
                guard token == self.generation else { return }
                step()
            }
        }
        step()
    }

    private func emit(_ text: String, in language: Language, onFinish: @escaping () -> Void) {
        let tag = resolver.speechTag(for: language)
        let voice = resolver.pick(from: engine.voices(), for: language)
        engine.speak(
            SpeechUtterance(
                text: text,
                // Keep the tag consistent with the chosen voice, or some engines
                // re-resolve and ignore the explicit voice.
                languageTag: voice?.lang ?? tag,
                voice: voice,
                onFinish: onFinish
            )
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ios/CloudmojiCore && swift test --filter SpeechController`
Expected: PASS — 6 tests passed

- [ ] **Step 5: Run the whole suite**

Run: `cd ios/CloudmojiCore && swift test`
Expected: PASS — all tests

- [ ] **Step 6: Commit**

```bash
git add ios/CloudmojiCore
git commit -m "feat(ios): cancellable speech controller

Sequences chain on the engine's finish callback behind a generation token, so
mute, a language change or a new round genuinely stop queued speech. The web
app's timer-based version kept talking after all three.

The AVSpeechSynthesizer binding lands in stage 2; SpeechEngine is the seam that
keeps queue behaviour testable without audio."
```

---

### Task 10: CI parity check

**Files:**
- Create: `tests/ios-data-parity.spec.ts`
- Modify: `package.json` (add `verify:ios` script)

**Interfaces:**
- Consumes: `tools/generate-ios-data/index.ts` exports `build()` and `serialise()` from Task 2
- Produces: a failing test whenever the committed JSON differs from freshly generated output

- [ ] **Step 1: Write the failing test**

Create `tests/ios-data-parity.spec.ts`:

```typescript
import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { build, serialise } from "../tools/generate-ios-data/index";

const BUNDLED = resolve(
  import.meta.dirname,
  "../ios/CloudmojiCore/Sources/CloudmojiCore/Resources/EmojiData.json",
);

test.describe("iOS data parity", () => {
  test("the bundled JSON matches the web data", () => {
    const committed = readFileSync(BUNDLED, "utf8");
    // Regenerating is the fix: npm run generate:ios
    expect(committed).toBe(serialise(build()));
  });

  test("the bundled JSON carries the full content set", () => {
    const data = JSON.parse(readFileSync(BUNDLED, "utf8"));
    expect(data.emojis).toHaveLength(200);
    expect(data.countables).toHaveLength(84);
    expect(data.languages).toHaveLength(5);
    expect(data.categories).toHaveLength(9);
    for (const [lang, words] of Object.entries(data.numberWords)) {
      expect(words, `numberWords.${lang}`).toHaveLength(10);
    }
  });

  test("Word mode and Count mode name the same thing in zh and ms", () => {
    const data = JSON.parse(readFileSync(BUNDLED, "utf8"));
    const byEmoji = new Map(data.emojis.map((e: never) => [e["emoji"], e]));
    const mismatches: string[] = [];
    for (const item of data.countables) {
      const word = byEmoji.get(item.emoji) as Record<string, string> | undefined;
      if (!word) continue; // count-only entries such as 🌟 are legitimate
      for (const lang of ["zh", "ms"]) {
        if (!item[lang].endsWith(word[lang])) {
          mismatches.push(`${item.emoji} ${lang}: "${item[lang]}" vs "${word[lang]}"`);
        }
      }
    }
    expect(mismatches).toEqual([]);
  });
});
```

- [ ] **Step 2: Verify it passes against current data**

Run: `npx playwright test ios-data-parity --project=iphone-15-pro-max`
Expected: PASS — 3 tests passed

- [ ] **Step 3: Verify it actually catches drift**

Run:

```bash
node -e "const f='ios/CloudmojiCore/Sources/CloudmojiCore/Resources/EmojiData.json';
const fs=require('fs');const d=JSON.parse(fs.readFileSync(f));
d.emojis[0].en='APPLE';fs.writeFileSync(f,JSON.stringify(d,null,2)+'\n');"
npx playwright test ios-data-parity --project=iphone-15-pro-max
```

Expected: FAIL on "the bundled JSON matches the web data"

Then restore:

```bash
npm run generate:ios
npx playwright test ios-data-parity --project=iphone-15-pro-max
```

Expected: PASS

- [ ] **Step 4: Add the convenience script**

Add to `package.json` scripts:

```json
"verify:ios": "npm run generate:ios && git diff --exit-code ios/CloudmojiCore/Sources/CloudmojiCore/Resources/EmojiData.json"
```

- [ ] **Step 5: Run the full web suite**

Run: `npm run typecheck && npm run lint && npx playwright test`
Expected: PASS — no regressions in the existing 274 tests

- [ ] **Step 6: Commit**

```bash
git add tests/ios-data-parity.spec.ts package.json
git commit -m "test(ios): fail the build when bundled data drifts from source

Verified the check catches drift by corrupting the JSON and watching it fail,
rather than trusting that it would."
```

---

## Definition of done

- [ ] `cd ios/CloudmojiCore && swift test` passes with no warnings
- [ ] `npm run generate:ios` is idempotent — running it twice leaves git clean
- [ ] `npx playwright test ios-data-parity` passes, and fails when JSON is edited by hand
- [ ] The existing web suite still passes
- [ ] No SwiftUI, UIKit or WatchKit import anywhere in `CloudmojiCore`

Verify the last one:

```bash
! grep -rn "import SwiftUI\|import UIKit\|import WatchKit" ios/CloudmojiCore/Sources
```

Expected: no output, exit 0.

## What this stage deliberately does not do

- No Xcode project. Stage 2 creates it when there is a UI to host.
- No `AVSpeechSynthesizer` binding. `SpeechEngine` is the seam; the concrete
  implementation lands in stage 2 where an audio session exists to configure.
- No usage logging. `UsageLog` moves to stage 2 alongside the parent stats screen.
- No watch code. Stage 3.
