import SwiftUI
import CloudmojiCore

/// Pocket Cloud — the entire watch app.
///
/// One emoji fills the screen, its word beneath. The Digital Crown pages through
/// the catalogue; a tap sends the current emoji to the child's phone (and speaks
/// it here). When the child taps something on the phone, it flashes over the top
/// with a haptic. That is the whole interface — one target, one gesture pair, no
/// failure states, exactly what a 40mm screen and a distracted parent can use.
struct PocketCloudView: View {
    @Environment(WatchModel.self) private var model
    @State private var selection: String?
    @State private var isRecording = false

    var body: some View {
        // The mic is a real row **below** the pager, not an overlay — a paging
        // TabView ignores a parent's safe-area inset, so an overlaid button lands
        // on top of the word. Stacking gives the pager the space above and the
        // mic its own strip, and they can never collide.
        VStack(spacing: 0) {
            TabView(selection: $selection) {
                ForEach(model.entries) { entry in
                    emojiPage(entry).tag(Optional(entry.id))
                }
            }
            .tabViewStyle(.verticalPage)

            micButton.padding(.vertical, 3)
        }
        .background(WatchTheme.background.ignoresSafeArea())
        .overlay { incomingFlash }
        .sheet(isPresented: $isRecording) {
            RecordView().environment(model)
        }
    }

    /// The one way to reach the microphone — small, out of the emoji's way, and
    /// a parent's target rather than a child's.
    private var micButton: some View {
        Button {
            isRecording = true
        } label: {
            Image(systemName: "mic.fill")
                .font(.system(size: 15, weight: .black))
                .foregroundStyle(.white)
                .padding(9)
                .background(WatchTheme.teal.opacity(0.85), in: Circle())
        }
        .buttonStyle(.plain)
        .padding(.bottom, 2)
    }

    private func emojiPage(_ entry: EmojiEntry) -> some View {
        VStack(spacing: 6) {
            Text(entry.emoji)
                .font(.system(size: WatchTheme.emojiSize))
            Text(model.word(for: entry))
                .font(.system(.headline, design: .rounded).weight(.heavy))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // The whole page is the target — a wrist tap lands anywhere.
        .contentShape(Rectangle())
        .onTapGesture { model.tap(entry) }
    }

    /// What Cloud just tapped, dropped over the browser for a beat.
    @ViewBuilder private var incomingFlash: some View {
        if let flash = model.flash {
            VStack(spacing: 6) {
                Text(flash.emoji).font(.system(size: WatchTheme.emojiSize))
                if !flash.word.isEmpty {
                    Text(flash.word)
                        .font(.system(.headline, design: .rounded).weight(.heavy))
                        .foregroundStyle(WatchTheme.teal)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(WatchTheme.bgPrimary.opacity(0.92).ignoresSafeArea())
            .id(flash.id)
            .transition(.opacity)
        }
    }
}
