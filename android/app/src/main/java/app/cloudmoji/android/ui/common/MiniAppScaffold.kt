package app.cloudmoji.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary

/**
 * The chrome every mini-app screen sits in: the app's background, a band
 * reserved at the bottom so [CloudHomeButton] never floats over content a
 * child might still be tapping, and the button itself. Task 7+ mini-apps
 * reuse this exactly as Words does — see the Task 6 brief.
 *
 * Ported from iOS `ContentView.swift`'s `HostedMiniApp`: a real reserved
 * band, not safe-area padding, because padding only insets where a scroll
 * view's content *ends* — everything in the middle still travels underneath
 * the cloud. [content] is laid out full width/height inside the remaining
 * space, so it never has to know the button is there.
 */
@Composable
fun MiniAppScaffold(
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    homeAccent: Color? = null,
    screenTag: String = "",
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BackgroundPrimary, BackgroundMid, BackgroundEdge)),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .then(if (screenTag.isNotEmpty()) Modifier.testTag(screenTag) else Modifier),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
            Spacer(Modifier.height(HomeButtonMetrics.reservedHeight))
        }

        CloudHomeButton(
            onClick = onHome,
            accent = homeAccent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = HomeButtonMetrics.inset),
        )
    }
}
