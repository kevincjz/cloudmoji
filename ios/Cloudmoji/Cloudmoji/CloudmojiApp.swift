//
//  CloudmojiApp.swift
//  Cloudmoji
//
//  Created by Kevin Chan on 27/7/26.
//

import SwiftUI

@main
struct CloudmojiApp: App {
    /// Proof that launch registered the bundled fonts, so a test can assert it
    /// without registering them itself and passing regardless.
    ///
    /// `BundledFonts` shipped with its registration wired up and nothing calling
    /// it: `Font.custom` falls back to the system face in silence, so the
    /// wordmark rendered in SF Rounded and looked merely a little plain. Its own
    /// tests call `register()` before asserting, so they passed throughout.
    private(set) static var didRegisterFontsAtLaunch = false

    init() {
        BundledFonts.register()
        Self.didRegisterFontsAtLaunch = BundledFonts.logoFontIsAvailable
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
