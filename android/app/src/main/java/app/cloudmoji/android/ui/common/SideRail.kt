package app.cloudmoji.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.SurfaceBorder

/** Two 64dp columns and their 8dp gap need 136; the rest is breathing room.
 * Mirrors iOS `CategorySourceMetrics.railWidth`. */
val SideRailWidth = 156.dp

/**
 * The left-hand column in landscape: a darker plate down the edge, holding
 * whatever the screen has to offer — Words puts the category chips here.
 * Ported from iOS `SideRail.swift`/`RailPlate`.
 */
@Composable
fun SideRail(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .width(SideRailWidth)
            .fillMaxHeight()
            .background(BackgroundPrimary.copy(alpha = 0.5f)),
    ) {
        content()

        // The hairline separating the rail from the content, run down the
        // trailing edge — the direct analogue of iOS `RailPlate`'s
        // `.overlay(alignment: .trailing) { Rectangle()... }`.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(SurfaceBorder),
        )
    }
}
