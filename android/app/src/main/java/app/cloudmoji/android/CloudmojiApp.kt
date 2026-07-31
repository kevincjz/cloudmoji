package app.cloudmoji.android

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.cloudmoji.android.model.AppAccessPolicy
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.ui.MiniAppPlaceholder
import app.cloudmoji.android.ui.ParentPlaceholder
import app.cloudmoji.android.ui.launcher.LauncherScreen

private const val LauncherRoute = "launcher"
private const val ParentRoute = "parents"

@Composable
fun CloudmojiApp() {
    var route by rememberSaveable { mutableStateOf(LauncherRoute) }
    val language = Language.English
    val accessPolicy = AppAccessPolicy(hasFullAccess = false)

    BackHandler(enabled = route != LauncherRoute) {
        route = LauncherRoute
    }

    when {
        route == LauncherRoute -> LauncherScreen(
            apps = accessPolicy.visibleMiniApps(),
            language = language,
            onOpen = { route = it.route },
            onParent = { route = ParentRoute },
        )

        route == ParentRoute -> ParentPlaceholder(
            onHome = { route = LauncherRoute },
        )

        else -> {
            val app = MiniApp.fromRoute(route)
            if (app != null && accessPolicy.canUse(app)) {
                MiniAppPlaceholder(
                    app = app,
                    language = language,
                    onHome = { route = LauncherRoute },
                )
            } else {
                LauncherScreen(
                    apps = accessPolicy.visibleMiniApps(),
                    language = language,
                    onOpen = { route = it.route },
                    onParent = { route = ParentRoute },
                )
            }
        }
    }
}

