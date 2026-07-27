//
//  ContentView.swift
//  Cloudmoji
//
//  Created by Kevin Chan on 27/7/26.
//

import SwiftUI
import CloudmojiCore

struct ContentView: View {
    var body: some View {
        // Temporary: proves the package is linked AND its bundled resource loads.
        // A plain build passes without either. Task 9 replaces this with the real UI.
        VStack(spacing: 12) {
            Text(summary)
                .font(.title2)
                .multilineTextAlignment(.center)
            Text(sample)
                .font(.title3)
                .foregroundStyle(.secondary)
        }
        .padding()
    }

    private var summary: String {
        guard let repo = try? EmojiRepository() else { return "no content" }
        return "\(repo.emojis.count) emojis, \(repo.languages.count) languages"
    }

    private var sample: String {
        guard let repo = try? EmojiRepository(),
              let apple = repo.emojis.first(where: { $0.emoji == "🍎" })
        else { return "" }
        // Also proves the five-language columns survived generation.
        return Language.allCases.map { apple.word($0) }.joined(separator: " · ")
    }
}

#Preview {
    ContentView()
}
