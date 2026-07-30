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
                NavigationLink {
                    FullCloudmojiView()
                } label: {
                    VStack(alignment: .leading, spacing: 7) {
                        HStack {
                            Label(
                                model.entitlements.isUnlocked ? "Full Cloudmoji" : "Cloudmoji Free",
                                systemImage: model.entitlements.isUnlocked
                                    ? "checkmark.seal.fill"
                                    : "cloud.fill"
                            )
                            .font(Theme.body(15, .black))

                            Spacer()

                            Text("CURRENT")
                                .font(Theme.body(10, .black))
                                .foregroundStyle(model.entitlements.isUnlocked
                                                 ? Theme.bgEdge
                                                 : Theme.textSecondary)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(
                                    model.entitlements.isUnlocked
                                        ? Theme.teal
                                        : Theme.surface,
                                    in: Capsule()
                                )
                        }

                        Text(model.entitlements.isUnlocked
                             ? "You own the paid Full version. Everything is unlocked."
                             : "You’re using the free version.")
                            .font(Theme.body(13, .bold))
                            .foregroundStyle(Theme.textSecondary)

                        if !model.entitlements.isUnlocked {
                            Text("Included: Words and Count in English.")
                                .font(Theme.body(13, .bold))
                                .foregroundStyle(Theme.textSecondary)

                            Label(fullPlanCallToAction, systemImage: "sparkles")
                                .font(Theme.body(14, .black))
                                .foregroundStyle(Theme.teal)
                                .padding(.top, 3)
                                .accessibilityIdentifier("settings-full-call-to-action")
                        }
                    }
                    .padding(.vertical, 6)
                    .frame(minHeight: Self.rowHeight, alignment: .leading)
                }
                .accessibilityIdentifier("settings-plan-row")
            } header: {
                Text("Your Cloudmoji plan")
            } footer: {
                if model.entitlements.isUnlocked {
                    Text("Full Cloudmoji is a lifetime purchase. There is no subscription.")
                } else {
                    Text("Full Cloudmoji is the paid version. One purchase unlocks five more mini-apps, four more languages and Apple Watch. There is no subscription.")
                }
            }

            Section {
                Toggle(isOn: soundBinding) {
                    Label(
                        settings.muted ? "Sound is off" : "Sound is on",
                        systemImage: settings.muted ? "speaker.slash.fill" : "speaker.wave.2.fill"
                    )
                    .font(Theme.body(15, .bold))
                }
                .tint(Theme.teal)
                .frame(minHeight: Self.rowHeight)
                .accessibilityIdentifier("settings-sound")
            } header: {
                Text("Sound")
            } footer: {
                Text("Controls speech, Music, animal sounds and the calming sound in Sleepy Cloud. Sound-based mini-apps also show a large way to turn sound back on.")
            }

            if model.entitlements.isUnlocked {
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
                        ForEach(model.availableLanguages) { meta in
                            Text(meta.name).tag(meta.id)
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(Theme.teal)
                    .frame(minHeight: Self.rowHeight)
                    .contentShape(Rectangle())
                    .accessibilityIdentifier("settings-default-lang")
                }
            } else {
                Section {
                    HStack {
                        Text("English")
                            .font(Theme.body(15, .black))
                        Spacer()
                        Text("Included")
                            .font(Theme.body(13, .bold))
                            .foregroundStyle(Theme.teal)
                    }
                    .frame(minHeight: Self.rowHeight)
                    .accessibilityIdentifier("settings-lang-en")

                    NavigationLink {
                        FullCloudmojiView()
                    } label: {
                        Label("4 more languages with Full", systemImage: "lock.fill")
                            .font(Theme.body(15, .bold))
                            .frame(minHeight: Self.rowHeight)
                    }
                    .accessibilityIdentifier("settings-full-languages")
                } header: {
                    Text("Language")
                } footer: {
                    Text("The free version speaks and displays child content in English.")
                }
            }

            Section {
                ForEach(model.allCategories) { tab in
                    Toggle(isOn: categoryBinding(tab)) {
                        HStack(spacing: 10) {
                            Text(tab.icon)
                            Text(tab.label(model.effectiveLanguage))
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
                NavigationLink {
                    ManagePhotosView()
                } label: {
                    Label("Manage photos", systemImage: "photo.on.rectangle")
                        .font(Theme.body(15, .bold))
                        .frame(minHeight: Self.rowHeight)
                }
                .accessibilityIdentifier("settings-manage-photos-row")
            } header: {
                Text("Photos")
            } footer: {
                Text(model.entitlements.isUnlocked
                     ? "Save copies to Apple Photos or delete Cloudmoji originals."
                     : "If Full access ends, stored photos remain here so you can save or delete them.")
            }

            Section {
                // The whole point of the tour being reopenable. It shows itself
                // once, on first launch, and the person holding the phone then
                // may well be the toddler — so there has to be a way back to it,
                // and this is it. Pushed rather than presented, for the same
                // reason About is: a sheet on top of a sheet loses the Done
                // button this stack already has.
                //
                // No `onDone` — the navigation bar's back control is the way out
                // here, and reaching the tour this way deliberately does not
                // touch `seenTutorial`. That flag records a first run, not a
                // reading.
                NavigationLink {
                    TutorialView()
                } label: {
                    Label("How to use Cloudmoji", systemImage: "questionmark.circle")
                        .font(Theme.body(15, .bold))
                        .frame(minHeight: Self.rowHeight)
                }
                .accessibilityIdentifier("settings-tutorial-row")

                NavigationLink {
                    AboutView()
                } label: {
                    Label("About Cloudmoji", systemImage: "info.circle")
                        .font(Theme.body(15, .bold))
                        .frame(minHeight: Self.rowHeight)
                }
                .accessibilityIdentifier("settings-about-row")
            } footer: {
                Text("The tour Cloudmoji showed you the first time, plus what the app does with your data and what has changed.")
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

    private var soundBinding: Binding<Bool> {
        Binding(
            get: { !model.settings.muted },
            set: { model.settings.muted = !$0 }
        )
    }

    private var fullPlanCallToAction: String {
        guard let price = model.entitlements.priceText else {
            return "See the paid Full version"
        }
        return "See the paid Full version — \(price)"
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
