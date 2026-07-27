//
//  ContentView.swift
//  Cloudmoji
//
//  Created by Kevin Chan on 27/7/26.
//

import SwiftUI
import CloudmojiCore

/// A scaffold, not the app. It exists so the theme and the mascot can be seen
/// moving on a device, and it keeps the Task 1 proof that the package's bundled
/// content actually loads — a plain build passes without either. Task 9 replaces
/// all of this with the real screen.
struct ContentView: View {
    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()

            VStack(spacing: 32) {
                wordmark

                HStack(alignment: .top, spacing: 8) {
                    ForEach(MascotMood.allCases, id: \.self) { mood in
                        VStack(spacing: 10) {
                            CloudMascot(mood: mood, size: 72)
                                .frame(height: 72) // room for the beaming lift
                            Text(label(for: mood))
                                .font(Theme.body(11, .bold))
                                .foregroundStyle(Theme.textSecondary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }

                Text(contentSummary)
                    .font(Theme.body(12, .bold))
                    .foregroundStyle(Theme.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .padding(24)
        }
    }

    private var wordmark: some View {
        VStack(spacing: 6) {
            CloudMascot(mood: .happy, size: 120)
            Text("Cloudmoji")
                .font(Theme.display(34))
                .foregroundStyle(
                    LinearGradient(
                        colors: [Theme.coral, Theme.gold, Theme.teal],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
            Text("Tap. Listen. Learn!")
                .font(Theme.body(10, .heavy))
                .tracking(0.5)
                .foregroundStyle(Theme.textSecondary)
        }
    }

    private func label(for mood: MascotMood) -> String {
        switch mood {
        case .happy: "happy"
        case .excited: "excited"
        case .speaking: "speaking"
        case .beaming: "beaming"
        }
    }

    private var contentSummary: String {
        guard let repo = try? EmojiRepository(),
              let apple = repo.emojis.first(where: { $0.emoji == "🍎" })
        else { return "no content" }
        // Also proves the five-language columns survived generation.
        let words = Language.allCases.map { apple.word($0) }.joined(separator: " · ")
        return "\(repo.emojis.count) emojis · \(repo.languages.count) languages\n\(words)"
    }
}

#Preview {
    ContentView()
}
