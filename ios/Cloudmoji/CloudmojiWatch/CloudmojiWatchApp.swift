import SwiftUI

@main
struct CloudmojiWatchApp: App {
    /// Assigned in `init` so the connection is up before the first view appears
    /// and any queued context from the phone is applied on launch.
    @State private var model: WatchModel

    init() {
        let model = WatchModel()
        _model = State(initialValue: model)
        model.activate()
    }

    var body: some Scene {
        WindowGroup {
            PocketCloudView().environment(model)
        }
    }
}
