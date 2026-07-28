import SwiftUI
import CloudmojiCore

/// The parent's panel, behind the gate.
///
/// Everything here narrows what `AppModel` publishes. No view anywhere else branches
/// on a setting — a family that uses only English and 中文 gets a two-item language
/// picker, not a five-item one with three of them ignored, so Cloud cannot land in
/// Tagalog by accident.
///
/// This is the only screen in the app whose targets are 44pt rather than 64: a child
/// never reaches it, and a 64pt row in a `Form` looks broken.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    /// The 44pt iOS HIG floor for parent chrome, named so the rows below say why
    /// they are 44 and not the app's usual 64.
    private static let rowHeight: CGFloat = 44

    var body: some View {
        @Bindable var settings = model.settings

        Form {
            Section {
                ForEach(model.allLanguages) { meta in
                    Toggle(isOn: languageBinding(meta.id)) {
                        HStack(spacing: 10) {
                            Text(meta.short)
                                .font(Theme.body(15, .black))
                                .frame(minWidth: 52, alignment: .leading)
                            Text(meta.name)
                                .font(Theme.body(13, .bold))
                                .foregroundStyle(Theme.textSecondary)
                        }
                    }
                    .tint(Theme.teal)
                    .disabled(!model.canDisableLanguage(meta.id)
                              && settings.enabledLanguages.contains(meta.id))
                    .frame(minHeight: Self.rowHeight)
                    .accessibilityIdentifier("settings-lang-\(meta.id.rawValue)")
                }
            } header: {
                Text("Languages")
            } footer: {
                Text("Only the languages you leave on appear in the picker Cloudmoji shows.")
            }

            Section("Starting language") {
                Picker("Starting language", selection: $settings.language) {
                    // Only the enabled ones: choosing a default you have switched
                    // off is a state `SettingsStore` would immediately undo.
                    ForEach(model.availableLanguages) { meta in
                        Text(meta.name).tag(meta.id)
                    }
                }
                .pickerStyle(.menu)
                .tint(Theme.teal)
                .frame(minHeight: Self.rowHeight)
                // The frame alone does NOT grow a menu picker's hit area — it lays
                // out at 34pt tall and only the text is tappable, which is how the
                // header's picker shipped once as a 34pt control claiming 44.
                .contentShape(Rectangle())
                .accessibilityIdentifier("settings-default-lang")
            }

            Section {
                ForEach(model.allCategories) { tab in
                    Toggle(isOn: categoryBinding(tab)) {
                        HStack(spacing: 10) {
                            Text(tab.icon)
                            Text(tab.label(settings.language))
                                .font(Theme.body(15, .bold))
                        }
                    }
                    .tint(Theme.teal)
                    .disabled(tab.category.map {
                        !model.canDisableCategory($0) && settings.enabledCategories.contains($0)
                    } ?? true)
                    .frame(minHeight: Self.rowHeight)
                    .accessibilityIdentifier("settings-cat-\(tab.id)")
                }
            } header: {
                Text("Categories")
            } footer: {
                Text("Narrows both the emoji grid and what Count mode picks from.")
            }

            Section {
                Stepper(
                    "Count from \(settings.countRange.lowerBound)",
                    value: lowerBinding,
                    in: SettingsStore.countBounds.lowerBound...settings.countRange.upperBound
                )
                .frame(minHeight: Self.rowHeight)
                .accessibilityIdentifier("settings-count-lower")

                Stepper(
                    "Count up to \(settings.countRange.upperBound)",
                    value: upperBinding,
                    in: settings.countRange.lowerBound...SettingsStore.countBounds.upperBound
                )
                .frame(minHeight: Self.rowHeight)
                .accessibilityIdentifier("settings-count-upper")
            } header: {
                Text("Count mode")
            } footer: {
                Text("Rounds step up through this range. Two is the smallest group that reads as counting, and ten is as far as the Japanese ～つ counter goes.")
            }

            Section {
                // Pushed rather than presented: About is a second page of the same
                // parent panel, and a sheet on top of a sheet loses the Done button
                // this stack already has.
                NavigationLink {
                    AboutView()
                } label: {
                    Label("About Cloudmoji", systemImage: "info.circle")
                        .font(Theme.body(15, .bold))
                        .frame(minHeight: Self.rowHeight)
                }
                .accessibilityIdentifier("settings-about-row")
            } footer: {
                Text("How to use Cloudmoji, what it does with your data, and what has changed.")
            }
        }
        .font(Theme.body(15, .bold))
        .tint(Theme.teal)
        .scrollContentBackground(.hidden)
        .background(Theme.background.ignoresSafeArea())
        // A `Form` otherwise renders in the system light palette on a light device
        // and the whole panel comes out white.
        .preferredColorScheme(.dark)
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Done") { dismiss() }
                    .accessibilityIdentifier("settings-done")
            }
        }
        // Without this the identifier propagates down and overwrites every row's
        // own — `settings-lang-en`, `settings-cat-fruits` and the rest all become
        // "settings-panel", which is exactly how the typing row's three controls
        // went missing in stage 2a.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("settings-panel")
    }

    // MARK: Bindings
    //
    // Set-membership toggles, written out rather than derived: `Binding` has no
    // spelling for "is this element in the set" and inlining four closures in the
    // body makes the sections unreadable.

    private func languageBinding(_ id: Language) -> Binding<Bool> {
        Binding(
            get: { model.settings.enabledLanguages.contains(id) },
            set: { isOn in
                var next = model.settings.enabledLanguages
                if isOn { next.insert(id) } else { next.remove(id) }
                // One assignment, not an insert into the live set: `SettingsStore`
                // validates and persists in `didSet`, and mutating in place would
                // fire it once per element.
                model.settings.enabledLanguages = next
            }
        )
    }

    private func categoryBinding(_ tab: CategoryTab) -> Binding<Bool> {
        Binding(
            get: { tab.category.map { model.settings.enabledCategories.contains($0) } ?? true },
            set: { isOn in
                guard let category = tab.category else { return }
                var next = model.settings.enabledCategories
                if isOn { next.insert(category) } else { next.remove(category) }
                model.settings.enabledCategories = next
            }
        )
    }

    private var lowerBinding: Binding<Int> {
        Binding(
            get: { model.settings.countRange.lowerBound },
            set: { model.settings.countRange = $0...model.settings.countRange.upperBound }
        )
    }

    private var upperBinding: Binding<Int> {
        Binding(
            get: { model.settings.countRange.upperBound },
            set: { model.settings.countRange = model.settings.countRange.lowerBound...$0 }
        )
    }
}

#Preview("Settings") {
    NavigationStack { SettingsView() }
        .environment(AppModel())
}
