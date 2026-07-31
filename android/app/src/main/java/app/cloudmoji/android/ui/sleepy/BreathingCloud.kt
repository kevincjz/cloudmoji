package app.cloudmoji.android.ui.sleepy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.BreathPhase
import app.cloudmoji.android.ui.theme.Blush
import app.cloudmoji.android.ui.theme.BlushBeaming
import app.cloudmoji.android.ui.theme.CloudHighlight
import app.cloudmoji.android.ui.theme.CloudShadow
import app.cloudmoji.android.ui.theme.CloudWhite
import app.cloudmoji.android.ui.theme.MascotEyes
import app.cloudmoji.android.ui.theme.Moonlight

/**
 * The sleeping cloud. Ported from iOS `BreathingCloud`
 * (`Views/Sleepy/BreathingCloud.swift`).
 *
 * Its own composable rather than a fifth [app.cloudmoji.android.model.MascotMood]
 * — the same reason [BreathPhase]'s own doc gives: a sleeping face is one
 * screen's animation state, not product law about which mood outranks
 * which. The body geometry below is drawn from the same 120 x 78
 * coordinates [app.cloudmoji.android.ui.mascot.CloudMascot] uses — same
 * circles, same rounded base, same underside shadow — so the two clouds
 * read as the same character; only the face and the glow differ. The shapes
 * are duplicated here rather than shared with `CloudMascot.kt`'s private
 * draw functions, mirroring iOS's own choice: `BreathingCloud.swift` does
 * not call into `CloudMascot.swift` either — see that file's class doc.
 *
 * No ambient/cast shadow behind the art, matching
 * [app.cloudmoji.android.ui.mascot.CloudMascot]'s own documented gap: a
 * dynamic-radius glow tied to [scale] needs `RenderEffect`, API 31, and this
 * app's `minSdk` is 26.
 */
@Composable
fun BreathingCloud(
    scale: Double,
    phase: BreathPhase,
    modifier: Modifier = Modifier,
    /** 0 at the start of a session, 0.55 at the end. Everything that glows
     * fades against it, so the screen gets quieter as the child does. */
    dim: Double = 0.0,
    width: Dp = BreathingCloudMetrics.renderedWidth,
) {
    val isAsleep = phase == BreathPhase.Asleep
    // Eyes close on the hold as well as in sleep — the pause at the top of a
    // breath is where a drowsy face settles.
    val eyesClosed = isAsleep || phase == BreathPhase.Hold
    val height = width * (BreathingCloudMetrics.artHeight / BreathingCloudMetrics.artWidth)
    val scaleFactorDp = width / BreathingCloudMetrics.artWidth

    Box(
        modifier = modifier
            .size(width, height)
            .testTag("sleepy-cloud-${phase.name.lowercase()}")
            // Deliberately not `mascot-<mood>`: a second element answering
            // to that identifier would make every celebration lookup in
            // other screens' UI tests ambiguous — mirrors iOS's own
            // deliberate identifier choice.
            .clearAndSetSemantics { contentDescription = "Cloudmoji" },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The breath itself. Driven every frame by the caller
                    // (`SleepyCloudScreen`'s own wall-clock loop), so — like
                    // iOS's `TimelineView`-driven scale — it carries no
                    // `animateFloatAsState` of its own.
                    scaleX = scale.toFloat()
                    scaleY = scale.toFloat()
                },
        ) {
            val scaleFactor = this.size.width / BreathingCloudMetrics.artWidth
            val yOffset = (this.size.height - BreathingCloudMetrics.artHeight * scaleFactor) / 2f
            withTransform({
                translate(0f, yOffset)
                scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
            }) {
                drawHalo(dim)
                drawCloudBody()
                drawCloudHighlights()
                drawUndersideShadow()
                drawBlush()
                drawEyes(eyesClosed)
                drawMouth(phase)
            }
        }

        if (isAsleep) {
            ZzzGlyph(x = 101f, y = 14f, fontSizePx = 12f, alpha = 0.8f, scaleFactorDp = scaleFactorDp)
            ZzzGlyph(x = 109f, y = 5f, fontSizePx = 9f, alpha = 0.6f, scaleFactorDp = scaleFactorDp)
        }
    }
}

/** Every number [BreathingCloud] is drawn from. Mirrors iOS
 * `BreathingCloud`'s own static properties. */
object BreathingCloudMetrics {
    /** The web viewBox. Every coordinate in this file's drawing functions is
     * in these units. */
    const val artWidth: Float = 120f
    const val artHeight: Float = 78f

    /** Drawn at 200 x 130 in the prototype, which is this viewBox at
     * 1.667x. */
    val renderedWidth: Dp = 200.dp

    /** Sideways. A landscape phone gives about 400pt of height, and 130 of
     * it for the cloud plus a title, three buttons and a caption overflowed
     * — the cloud came out clipped against the top edge of the screen. */
    val compactRenderedWidth: Dp = 132.dp
}

// MARK: - Drawing (viewBox-local coordinates; the caller has already applied
// the scale + letterbox transform)

/** A quiet crescent-adjacent halo behind the cloud — mirrors iOS's
 * `RadialGradient` `Ellipse`, approximated here with a circular
 * [Brush.radialGradient] filled into the same oval bounds (Compose has no
 * elliptical-gradient primitive either; iOS's own `RadialGradient` is
 * circular too, just clipped by the `Ellipse()` shape it fills). */
private fun DrawScope.drawHalo(dim: Double) {
    val center = Offset(60f, 46f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Moonlight.copy(alpha = 0.5f), Moonlight.copy(alpha = 0f)),
            center = center,
            radius = 54f,
        ),
        topLeft = Offset(60f - 54f, 46f - 36f),
        size = Size(108f, 72f),
        alpha = (0.35 * (1 - dim)).toFloat().coerceIn(0f, 1f),
    )
}

private fun DrawScope.drawCloudBody() {
    val circles = listOf(
        Triple(30f, 46f, 20f),
        Triple(52f, 36f, 23f),
        Triple(72f, 30f, 26f),
        Triple(94f, 42f, 19f),
        Triple(42f, 44f, 16f),
    )
    for ((cx, cy, r) in circles) drawCircle(color = CloudWhite, radius = r, center = Offset(cx, cy))
    drawRoundRect(
        color = CloudWhite,
        topLeft = Offset(12f, 48f),
        size = Size(96f, 24f),
        cornerRadius = CornerRadius(12f, 12f),
    )
}

private fun DrawScope.drawCloudHighlights() {
    drawCircle(color = CloudHighlight, radius = 12f, center = Offset(72f, 22f))
    drawCircle(color = CloudHighlight.copy(alpha = 0.7f), radius = 8f, center = Offset(50f, 30f))
}

private fun DrawScope.drawUndersideShadow() {
    drawOval(
        color = CloudShadow.copy(alpha = 0.4f),
        topLeft = Offset(60f - 44f, 68f - 6f),
        size = Size(88f, 12f),
    )
}

/** Fixed opacity, unlike [app.cloudmoji.android.ui.mascot.CloudMascot]'s own
 * mood-dependent blush — a sleepy cloud has one steady, quiet expression. */
private fun DrawScope.drawBlush() {
    val color = Blush.copy(alpha = 0.42f)
    for (cx in floatArrayOf(34f, 86f)) {
        drawOval(color = color, topLeft = Offset(cx - 8f, 58f - 4.5f), size = Size(16f, 9f))
    }
}

private fun DrawScope.drawEyes(closed: Boolean) {
    if (closed) {
        val path = Path()
        for (cx in floatArrayOf(46f, 74f)) {
            path.moveTo(cx - 6f, 52f)
            path.quadraticTo(cx, 42f, cx + 6f, 52f)
        }
        drawPath(path = path, color = MascotEyes, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
    } else {
        drawOval(color = MascotEyes, topLeft = Offset(46f - 2.4f, 51f - 2.8f), size = Size(4.8f, 5.6f))
        drawOval(color = MascotEyes, topLeft = Offset(74f - 2.4f, 51f - 2.8f), size = Size(4.8f, 5.6f))
    }
}

private fun DrawScope.drawMouth(phase: BreathPhase) {
    when (phase) {
        BreathPhase.Asleep ->
            drawOval(color = BlushBeaming.copy(alpha = 0.75f), topLeft = Offset(60f - 4f, 62f - 3f), size = Size(8f, 6f))

        BreathPhase.Inhale ->
            // A small round mouth, the way a person taking a breath in has
            // one.
            drawOval(color = BlushBeaming.copy(alpha = 0.7f), topLeft = Offset(60f - 4.5f, 62f - 4f), size = Size(9f, 8f))

        BreathPhase.Hold, BreathPhase.Exhale -> {
            val path = Path().apply {
                moveTo(55f, 61f)
                quadraticTo(60f, 69f, 65f, 61f)
            }
            drawPath(
                path = path,
                color = MascotEyes.copy(alpha = 0.75f),
                style = Stroke(width = 1.6f, cap = StrokeCap.Round),
            )
        }
    }
}

/** One "z", drawn as a real `Text` overlay positioned in the same viewBox
 * units the [Canvas] above uses — mirrors
 * [app.cloudmoji.android.ui.mascot.CloudMascot]'s own sparkle-overlay
 * technique, and the reason: Compose's `DrawScope` has no built-in text
 * primitive as convenient as SwiftUI's `Text` inside a `ZStack`.
 *
 * An approximation, not a pixel match: iOS's `.position(x:y:)` centres the
 * glyph on the given point; `Modifier.offset` here places its top-left
 * corner there instead, since Compose has no equivalent single-call
 * "position the center" for a `Text` sized to its own content without an
 * extra measurement pass. Two small "z"s a few points off-position during
 * the asleep phase is not worth that pass — the same category of trade-off
 * [app.cloudmoji.android.ui.mascot.CloudMascot]'s own sparkle placement
 * already documents. */
@Composable
private fun ZzzGlyph(x: Float, y: Float, fontSizePx: Float, alpha: Float, scaleFactorDp: Dp) {
    val fontSizeDpValue = fontSizePx * scaleFactorDp.value
    Text(
        text = "z",
        color = Moonlight.copy(alpha = alpha),
        fontSize = fontSizeDpValue.sp,
        modifier = Modifier.offset(x = scaleFactorDp * x, y = scaleFactorDp * y),
    )
}
