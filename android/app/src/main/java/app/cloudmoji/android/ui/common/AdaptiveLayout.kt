package app.cloudmoji.android.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The measured canvas shared by every Cloudmoji screen. Mirrors iOS
 * `AdaptiveShell.swift`'s `CloudmojiLayout`.
 *
 * Android has no `UIDevice.userInterfaceIdiom`; [isPad] uses the platform's
 * own tablet convention instead — `smallestScreenWidthDp >= 600` — which is
 * stable across rotation, unlike comparing the current width and height.
 */
data class CloudmojiLayout(
    val isPad: Boolean,
    val isLandscape: Boolean,
    val isExpandedPad: Boolean,
    val isCompactPhone: Boolean,
)

object AdaptiveLayoutMetrics {
    /** Full-screen iPad mini landscape is 744dp tall before insets and can
     * measure below 700dp inside a shell; the threshold stays below that so
     * rotating a full-screen tablet never falls back to phone sizing. A
     * narrow split-screen window still remains below this floor. Mirrors iOS
     * `CloudmojiLayout.expandedPadMinimumSide`. */
    val expandedPadMinimumSide: Dp = 640.dp

    /** A phone in landscape gives about 320–420dp of usable height; the
     * shortest 7" tablet is well above this. Anything at or under this has
     * no height to spend on a horizontal category strip, and moves it into
     * the side rail. Mirrors iOS `AdaptiveShell.compactHeight`. */
    val compactPhoneMaxHeight: Dp = 560.dp

    /** Google's own smallest-tablet convention. */
    const val TABLET_SMALLEST_WIDTH_DP = 600
}

/** No screen has measured anything yet — never rendered, only a safe fallback
 * for `LocalCloudmojiLayout.current` read outside an [AdaptiveShell]. */
val LocalCloudmojiLayout = compositionLocalOf {
    CloudmojiLayout(isPad = false, isLandscape = false, isExpandedPad = false, isCompactPhone = false)
}

/**
 * The chrome every screen sits in: the one measurement of how much room there
 * is, published so descendants read it rather than each re-deriving their own
 * breakpoint. Mirrors iOS `AdaptiveShell.swift` — but, unlike iOS, draws no
 * background and applies no inset padding of its own: those stay with each
 * screen (`MiniAppScaffold`, `LauncherScreen`) exactly as before, so wrapping
 * the app's route switch in this cannot double-apply either.
 */
@Composable
fun AdaptiveShell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val configuration = LocalConfiguration.current
        val isPad = configuration.smallestScreenWidthDp >= AdaptiveLayoutMetrics.TABLET_SMALLEST_WIDTH_DP
        val isLandscape = maxWidth > maxHeight
        val isExpandedPad = isPad &&
            minOf(maxWidth, maxHeight) >= AdaptiveLayoutMetrics.expandedPadMinimumSide
        val isCompactPhone = !isPad && isLandscape && maxHeight <= AdaptiveLayoutMetrics.compactPhoneMaxHeight

        val layout = remember(isPad, isLandscape, isExpandedPad, isCompactPhone) {
            CloudmojiLayout(
                isPad = isPad,
                isLandscape = isLandscape,
                isExpandedPad = isExpandedPad,
                isCompactPhone = isCompactPhone,
            )
        }

        CompositionLocalProvider(LocalCloudmojiLayout provides layout) {
            content()
        }
    }
}
