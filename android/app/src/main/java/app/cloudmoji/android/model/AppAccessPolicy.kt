package app.cloudmoji.android.model

data class AppAccessPolicy(
    val hasFullAccess: Boolean,
) {
    fun canUse(app: MiniApp): Boolean = hasFullAccess || !app.requiresFull

    fun visibleMiniApps(
        animalsEnabled: Boolean = true,
    ): List<MiniApp> = MiniApp.entries.filter { app ->
        canUse(app) && (app != MiniApp.Animals || animalsEnabled)
    }
}

