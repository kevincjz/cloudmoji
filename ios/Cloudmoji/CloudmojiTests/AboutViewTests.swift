import SwiftUI
import Testing
import UIKit
@testable import Cloudmoji

@Suite("AboutView")
@MainActor
struct AboutViewTests {

    private var everyEntry: [AboutView.Entry] {
        AboutView.faq + AboutView.legal + AboutView.history
    }

    /// The web's privacy text describes four things that leave the device, and
    /// **none of them exists in this build**. Copying it across is the obvious
    /// shortcut and it would attach an inaccurate disclosure to a listing whose
    /// nutrition label says "Data Not Collected" — which is a review rejection at
    /// best and a false statement at worst.
    ///
    /// Swept over *every* entry rather than only the privacy one, because the
    /// paste this guards against is from `AboutPanel.tsx` as a whole: its Ko-fi
    /// line, its "Add to Home Screen" answer and its localStorage sentence sit in
    /// three different items, and a check aimed only at the privacy entry would
    /// wave the other two through.
    ///
    /// Mutation: paste `AboutPanel.tsx`'s privacy string in. This fails and names
    /// both the entry and the collector that came across with it.
    @Test("the privacy text does not describe the web app's collectors")
    func privacyTextIsTheIosOne() {
        let privacy = AboutView.legal
            .first { $0.question.contains("Privacy") }?.answer ?? ""
        #expect(!privacy.isEmpty, "there is no privacy disclosure at all")

        // The thing that *is* true, in the words the nutrition label uses.
        #expect(privacy.contains("no network connections"))

        for absent in ["Vercel", "Speed Insights", "Google Fonts", "localStorage",
                       "fonts.googleapis.com", "pageview", "ko-fi", "http"] {
            for entry in everyEntry {
                let text = entry.question + "\n" + entry.answer
                #expect(
                    text.range(of: absent, options: .caseInsensitive) == nil,
                    "\"\(entry.question)\" mentions \(absent), which does not exist in the iOS build"
                )
            }
        }
    }

    /// Mutation: return a hardcoded "1.0". The second and third fail, and every
    /// release after the first tells the parent the wrong version.
    @Test("the version shown is the bundle's own")
    func versionComesFromTheBundle() {
        #expect(AboutView.versionText(from: ["CFBundleShortVersionString": "1.0"]) == "Cloudmoji v1.0")
        #expect(AboutView.versionText(from: ["CFBundleShortVersionString": "2.3"]) == "Cloudmoji v2.3")
        #expect(AboutView.versionText(from: [:]) == "Cloudmoji", "a missing key must not print 'nil'")

        // The dictionary the panel actually passes. Without this the three above
        // are three assertions about a function that could be fed nothing useful
        // at its only call site and still pass — the "derived value asserted
        // against its own definition" shape this project keeps finding.
        #expect(
            AboutView.versionText(from: Bundle.main.infoDictionary) != "Cloudmoji",
            "the app bundle carries no CFBundleShortVersionString, so the panel prints no version at all"
        )
    }

    /// A disclosure group with an empty answer looks like a bug and reads like one.
    ///
    /// Mutation: leave any answer as "". The entry is named and this fails.
    @Test("every entry has both a question and an answer")
    func everyEntryIsComplete() {
        let all = everyEntry
        #expect(all.count >= 10, "only \(all.count) entries — the panel is missing sections")
        for entry in all {
            #expect(!entry.question.isEmpty)
            #expect(entry.answer.count > 20, "'\(entry.question)' has a \(entry.answer.count)-character answer")
        }
    }

    /// Each row carries its own accessibility identifier, built from `id`. Two
    /// rows sharing one would make `AboutUITests`' lookups ambiguous — it would
    /// silently measure whichever matched first — and `ForEach` over a duplicated
    /// `Identifiable` id drops rows on screen as well.
    ///
    /// Mutation: give two entries the same `id`. This fails.
    @Test("every entry has a unique, identifier-safe id")
    func idsAreUniqueAndSafe() {
        let ids = everyEntry.map(\.id)
        #expect(Set(ids).count == ids.count, "duplicate ids in \(ids)")
        for id in ids {
            #expect(!id.isEmpty)
            #expect(
                id.allSatisfy { $0.isASCII && ($0.isLowercase || $0.isNumber || $0 == "-") },
                "\"\(id)\" is not a plain lowercase-and-hyphen identifier"
            )
        }
    }

    /// Guided Access is the single most useful thing a parent of a toddler can be
    /// told about an iPad, and it is the one FAQ item that is *more* relevant here
    /// than on the web.
    ///
    /// Mutation: drop the Guided Access entry while trimming the web's PWA items.
    /// This fails.
    @Test("the FAQ explains Guided Access")
    func faqCoversGuidedAccess() {
        #expect(AboutView.faq.contains { $0.question.contains("Guided Access") })
        #expect(!AboutView.faq.contains { $0.question.contains("Home Screen") },
                "the Add-to-Home-Screen item describes installing a PWA")
        #expect(!everyEntry.contains { $0.question.contains("Android") || $0.answer.contains("Screen Pinning") },
                "the Android screen-pinning item came across from the web")
    }

    /// If it draws nothing, none of the above means anything.
    ///
    /// Two measurements, because the mascot alone is a ~120pt white shape and
    /// clears any whole-window threshold on its own: a panel whose three sections
    /// had been dropped would still light 8,000-odd pixels and pass a
    /// whole-window count. The second band starts well below the header block, so
    /// only the FAQ rows can light it.
    ///
    /// Mutation: replace `List`'s three `section(...)` calls with nothing. The
    /// band assertion fails; the whole-window one does not.
    @Test("the panel draws, sections and all")
    func panelDraws() async {
        let bitmap = await Bitmap.of(
            NavigationStack { AboutView() }, width: 390, height: 844,
            settling: .milliseconds(300), fillsWindow: true
        )
        #expect(bitmap.litPixels(threshold: 150) > 5000, "the About panel drew almost nothing")

        var band = 0
        for y in 560..<844 {
            for x in 0..<390 where bitmap.rgb(x: x, y: y).sum > 150 {
                band += 1
            }
        }
        #expect(band > 500, "only \(band) lit pixels below the header block — the sections did not draw")
    }
}
