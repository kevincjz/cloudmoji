package app.cloudmoji.android.model

/**
 * Pure product policy for the one Full Cloudmoji entitlement. Every
 * accessibility question — which mini-apps show, which language a child
 * hears — funnels through here, mirroring iOS's `AppAccessPolicy`: "Views
 * never decide accessibility — only the central access policy does."
 */
data class AppAccessPolicy(
    val hasFullAccess: Boolean,
) {
    fun canUse(app: MiniApp): Boolean = hasFullAccess || !app.requiresFull

    fun visibleMiniApps(
        animalsEnabled: Boolean = true,
    ): List<MiniApp> = MiniApp.entries.filter { app ->
        canUse(app) && (app != MiniApp.Animals || animalsEnabled)
    }

    /**
     * The only language child-facing code may render or speak. Mirrors iOS
     * `AppAccessPolicy.effectiveLanguage(preferred:)`: locked access resolves
     * to English regardless of what the parent has saved, and — critically —
     * that resolution is read-only. It never writes back through the stored
     * preference, so a locked or not-yet-verified entitlement does not
     * overwrite a parent's saved language; it only masks it until the
     * entitlement is unlocked again.
     */
    fun effectiveLanguage(preferred: Language): Language =
        if (hasFullAccess) preferred else Language.English

    /**
     * The languages a picker may offer. Mirrors iOS `AppModel.availableLanguages`:
     * locked access narrows to English even if [enabled] (the parent's saved
     * multi-language selection) includes others — unlocking, not the
     * enabled-set toggle, is what turns the rest back on.
     */
    fun allowedLanguages(enabled: Set<Language>): Set<Language> =
        if (hasFullAccess) enabled else setOf(Language.English)
}

