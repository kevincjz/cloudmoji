package app.cloudmoji.android.ui.mascot

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.MascotMood
import app.cloudmoji.android.ui.theme.Blush
import app.cloudmoji.android.ui.theme.BlushBeaming
import app.cloudmoji.android.ui.theme.CloudHighlight
import app.cloudmoji.android.ui.theme.CloudShadow
import app.cloudmoji.android.ui.theme.CloudWhite
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Gold
import app.cloudmoji.android.ui.theme.MascotEyes
import app.cloudmoji.android.ui.theme.MouthStroke
import kotlin.math.cos
import kotlin.math.sin

// MARK: - Pure style model
//
// Deliberately free of any Compose/Android import (no `Color`, no `Dp`): the
// "which face goes with which mood" lookup table is exactly the part of a
// mascot that can silently rot without a screenshot catching it — see
// `ios/Cloudmoji/CloudmojiTests/CloudMascotTests.swift`'s own framing — so it
// stays plain-JVM-testable here too, in `CloudMascotStyleTest`.

/** The three `@keyframes` the web mascot cycles between, ported from
 * `src/index.css` via `ios/Cloudmoji/Cloudmoji/Views/CloudMascot.swift`'s
 * `MascotMotion`. */
enum class MascotMotion {
    /** `mascotFloat` — idle drift. */
    Float,

    /** `mascotBounce` — while a word is being spoken. */
    Bounce,

    /** `mascotBeam` — milestone celebration; already scaled up at rest. */
    Beam,
    ;

    /** The CSS `animation-duration`, covering a whole 0% -> 100% round trip. */
    val cssDurationMs: Int
        get() = when (this) {
            Float -> 3000
            Bounce -> 400
            Beam -> 600
        }

    /** `repeatForever(autoreverses: true)` / Compose's `RepeatMode.Reverse`
     * both treat the animation duration as one leg of the round trip, so the
     * CSS duration (a full round trip) is halved here — forgetting this runs
     * the mascot at half speed, which still looks like a working animation. */
    val halfCycleMs: Int get() = cssDurationMs / 2

    /** Peak `translateY`, in points, at the mascot's reference size of 64. */
    val referenceLift: Float
        get() = when (this) {
            Float -> 4f
            Bounce -> 3f
            Beam -> 6f
        }

    /** `transform: scale()` at 0%. */
    val restScale: Float
        get() = when (this) {
            Float, Bounce -> 1f
            Beam -> 1.08f
        }

    /** `transform: scale()` at 50%. */
    val peakScale: Float
        get() = when (this) {
            Float -> 1f
            Bounce -> 1.06f
            Beam -> 1.15f
        }

    /** The web keyframes are absolute pixels against a 64px mascot; this
     * scales [referenceLift] to whatever [sizeDp] the mascot is actually
     * drawn at. */
    fun lift(sizeDp: Float): Float = referenceLift * sizeDp / 64f
}

/** How the eyes are drawn for a given mood. */
enum class MascotEyeShape {
    /** A gentle upward arc — the resting face. */
    Arc,

    /** Round open eyes, paired with the speaking mouth. */
    Dot,

    /** Five-pointed stars. */
    Star,

    /** A wider, flatter arc: squinting with delight. */
    Squint,
}

/** How the mouth is drawn for a given mood. */
enum class MascotMouthShape {
    /** An outlined curve, no fill. */
    Smile,

    /** A small filled curve. */
    Grin,

    /** A filled ellipse — mid-word. */
    OpenRound,

    /** A wide filled curve with an outline. */
    WideGrin,
}

/** A twinkle drawn beside the cloud. Coordinates are the SVG text origin
 * (left edge, baseline) in the 120 x 78 viewBox. */
data class MascotSparkle(
    val id: Int,
    val glyph: String,
    val x: Float,
    val y: Float,
    val fontSizePx: Float,
    /** CSS `animation-duration` for the shared `sparkle` keyframe. */
    val cssDurationMs: Int,
    val delayMs: Int,
)

/** Every appearance decision the mood drives, resolved in one place — the
 * view then draws exactly what it is told. Ported from iOS's `MascotStyle`. */
data class MascotStyle(
    val eyes: MascotEyeShape,
    val mouth: MascotMouthShape,
    val motion: MascotMotion,
    val blushRadiusX: Float,
    val blushRadiusY: Float,
    val isBlushBeaming: Boolean,
    val blushOpacity: Float,
    /** True only while beaming — the celebration glow, gold cast shadow, and
     * extra sparkles all key off this alone. */
    val showsGlow: Boolean,
    val sparkles: List<MascotSparkle>,
) {
    companion object {
        private val baseSparkles = listOf(
            MascotSparkle(id = 0, glyph = "✨", x = 102f, y = 24f, fontSizePx = 10f, cssDurationMs = 600, delayMs = 0),
            MascotSparkle(id = 1, glyph = "✨", x = 12f, y = 28f, fontSizePx = 8f, cssDurationMs = 800, delayMs = 200),
        )

        private val beamingSparkles = listOf(
            MascotSparkle(id = 2, glyph = "⭐", x = 4f, y = 50f, fontSizePx = 9f, cssDurationMs = 700, delayMs = 100),
            MascotSparkle(id = 3, glyph = "⭐", x = 110f, y = 48f, fontSizePx = 9f, cssDurationMs = 900, delayMs = 400),
            MascotSparkle(id = 4, glyph = "🌟", x = 58f, y = 12f, fontSizePx = 11f, cssDurationMs = 500, delayMs = 300),
        )

        fun forMood(mood: MascotMood): MascotStyle {
            val isBeaming = mood == MascotMood.Beaming
            return MascotStyle(
                eyes = when (mood) {
                    MascotMood.Happy -> MascotEyeShape.Arc
                    MascotMood.Speaking -> MascotEyeShape.Dot
                    MascotMood.Excited -> MascotEyeShape.Star
                    MascotMood.Beaming -> MascotEyeShape.Squint
                },
                mouth = when (mood) {
                    MascotMood.Happy -> MascotMouthShape.Smile
                    MascotMood.Speaking -> MascotMouthShape.OpenRound
                    MascotMood.Excited -> MascotMouthShape.Grin
                    MascotMood.Beaming -> MascotMouthShape.WideGrin
                },
                motion = when (mood) {
                    MascotMood.Happy, MascotMood.Excited -> MascotMotion.Float
                    MascotMood.Speaking -> MascotMotion.Bounce
                    MascotMood.Beaming -> MascotMotion.Beam
                },
                // Rosier and fuller while beaming.
                blushRadiusX = if (isBeaming) 10f else 8f,
                blushRadiusY = if (isBeaming) 5.5f else 4.5f,
                isBlushBeaming = isBeaming,
                blushOpacity = if (isBeaming) 0.7f else 0.55f,
                showsGlow = isBeaming,
                sparkles = when (mood) {
                    MascotMood.Happy, MascotMood.Speaking -> emptyList()
                    MascotMood.Excited -> baseSparkles
                    MascotMood.Beaming -> baseSparkles + beamingSparkles
                },
            )
        }
    }
}

// MARK: - Composable

private const val ArtWidth = 120f
private const val ArtHeight = 78f

/** CSS `ease` — cubic-bezier(0.25, 0.1, 0.25, 1) — for the two energetic
 * cycles (bounce, beam) and every twinkle. */
private val EaseCurve = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** CSS `ease-in-out` for the idle float. */
private val EaseInOutCurve = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

/**
 * The cloud mascot — Cloudmoji's emotional centerpiece.
 *
 * Drawn as vector shapes in the same 120 x 78 coordinate space as the web
 * SVG (`src/components/CloudMascot.tsx`) and ported from
 * `ios/Cloudmoji/Cloudmoji/Views/CloudMascot.swift`. [mood] drives every
 * visual decision through [MascotStyle.forMood]; this composable owns no
 * mood logic of its own — that lives in `MascotMoodMachine`, so a caller
 * simply does `val mood by machine.mood.collectAsState()`.
 *
 * `testTag("cloud-mascot")` and a `"Cloudmoji"` content description mirror
 * iOS's non-hidden `accessibilityElement` — a hidden mascot is a mascot
 * whose celebration no test, and no TalkBack user, can ever observe.
 */
@Composable
fun CloudMascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val style = remember(mood) { MascotStyle.forMood(mood) }
    val density = LocalDensity.current

    // Re-keyed on the motion *family* (not the mood) so happy <-> excited —
    // which share `.float` — never restarts the cycle, while a genuine
    // family change (e.g. into `.bounce`) starts fresh from rest, exactly
    // like iOS's `.id(style.motion)`.
    val phase = key(style.motion) {
        val transition = rememberInfiniteTransition()
        val animated = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = style.motion.halfCycleMs,
                    easing = if (style.motion == MascotMotion.Float) EaseInOutCurve else EaseCurve,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        animated.value
    }

    val breathScale = lerp(style.motion.restScale, style.motion.peakScale, phase)
    val liftDp = style.motion.referenceLift.dp * (size / 64.dp)
    val liftPx = with(density) { liftDp.toPx() }

    // Fixed call sites (never inside a loop) so Compose's positional
    // memoization stays stable regardless of how many sparkles the current
    // mood actually shows — see `twinklePhase`'s doc.
    val glowPhase = twinklePhase(cssDurationMs = 1200, delayMs = 0)
    val sparklePhase0 = twinklePhase(cssDurationMs = 600, delayMs = 0)
    val sparklePhase1 = twinklePhase(cssDurationMs = 800, delayMs = 200)
    val sparklePhase2 = twinklePhase(cssDurationMs = 700, delayMs = 100)
    val sparklePhase3 = twinklePhase(cssDurationMs = 900, delayMs = 400)
    val sparklePhase4 = twinklePhase(cssDurationMs = 500, delayMs = 300)
    val sparklePhasesById = remember(sparklePhase0, sparklePhase1, sparklePhase2, sparklePhase3, sparklePhase4) {
        mapOf(0 to sparklePhase0, 1 to sparklePhase1, 2 to sparklePhase2, 3 to sparklePhase3, 4 to sparklePhase4)
    }

    // The SVG letterboxes: `preserveAspectRatio` fits the 120 x 78 viewBox
    // inside a `size x size*0.78` box. Sparkles are real `Text` overlays (as
    // they are on both iOS and web) positioned with the same math the Canvas
    // transform below uses, so they land in the same place as the shapes.
    val scaleFactorDp = size / ArtWidth
    val yOffsetDp = (size * 0.78f - scaleFactorDp * ArtHeight) / 2f

    Box(
        modifier = modifier
            .size(size, size * 0.78f)
            .testTag("cloud-mascot")
            // `clearAndSetSemantics`, not plain `semantics {}`: the latter
            // defaults `mergeDescendants = false`, which *adds* a labeled
            // node rather than collapsing this element's descendants —
            // during excited/beaming, up to 5 real `Text` sparkle glyphs
            // below would each surface their own unlabeled node to TalkBack
            // alongside this one. `clearAndSetSemantics` discards whatever
            // the subtree would have reported and substitutes this single
            // node, the direct analogue of iOS's
            // `.accessibilityElement(children: .ignore)`.
            .clearAndSetSemantics { contentDescription = "Cloudmoji" },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = breathScale
                    scaleY = breathScale
                    translationY = -liftPx
                },
        ) {
            // Explicit `this.size`: `size` alone resolves to the outer
            // composable's `size: Dp` parameter captured in this closure,
            // not `DrawScope.size` (the canvas's actual pixel dimensions).
            val scaleFactor = this.size.width / ArtWidth
            val yOffset = (this.size.height - ArtHeight * scaleFactor) / 2f
            withTransform({
                translate(0f, yOffset)
                scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
            }) {
                if (style.showsGlow) drawBeamGlow(glowPhase)
                drawCloudBody()
                drawCloudHighlights()
                drawUndersideShadow()
                drawBlush(style)
                drawEyes(style.eyes)
                drawMouth(style.mouth)
            }
        }

        for (sparkle in style.sparkles) {
            val sparkleTwinkle = sparklePhasesById[sparkle.id] ?: 0f
            val twinkleScale = lerp(0.7f, 1.3f, sparkleTwinkle)
            val twinkleAlpha = lerp(0.2f, 1f, sparkleTwinkle)
            // A dp-scaled magnitude (the viewBox's own `fontSizePx` number
            // times the mascot's dp-per-viewbox-unit ratio), not a pixel
            // count — used below as both `.sp` and `.dp`.
            val fontSizeDpValue = sparkle.fontSizePx * scaleFactorDp.value
            Text(
                text = sparkle.glyph,
                fontSize = fontSizeDpValue.sp,
                modifier = Modifier
                    .offset(
                        x = scaleFactorDp * sparkle.x,
                        y = yOffsetDp + scaleFactorDp * sparkle.y - fontSizeDpValue.dp,
                    )
                    .graphicsLayer {
                        alpha = twinkleAlpha
                        scaleX = twinkleScale
                        scaleY = twinkleScale
                    },
            )
        }
    }
}

/** One sparkle's (or the beam glow's) twinkle: opacity 0.2 -> 1, scale
 * 0.7 -> 1.3, ported from iOS's `Twinkle` modifier / the shared `sparkle`
 * CSS keyframe. Always called from the same fixed call sites in
 * [CloudMascot] — never from inside a loop over the mood-dependent sparkle
 * list — so Compose's positional state keeps every twinkle's animation
 * running continuously even while its glyph is not currently shown. */
@Composable
private fun twinklePhase(cssDurationMs: Int, delayMs: Int): Float {
    val transition = rememberInfiniteTransition()
    val animated = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cssDurationMs / 2, easing = EaseCurve),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMs),
        ),
    )
    return animated.value
}

// MARK: - Drawing (viewBox-local coordinates; the caller has already applied
// the scale + letterbox transform)

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

private fun DrawScope.drawBlush(style: MascotStyle) {
    val color = (if (style.isBlushBeaming) BlushBeaming else Blush).copy(alpha = style.blushOpacity)
    for (cx in floatArrayOf(34f, 86f)) {
        drawOval(
            color = color,
            topLeft = Offset(cx - style.blushRadiusX, 58f - style.blushRadiusY),
            size = Size(style.blushRadiusX * 2, style.blushRadiusY * 2),
        )
    }
}

private fun DrawScope.drawEyes(eyes: MascotEyeShape) {
    when (eyes) {
        MascotEyeShape.Arc -> drawPath(
            path = arcEyesPath(halfWidth = 3.5f, baseline = 52f, apex = 49f),
            color = MascotEyes,
            style = Stroke(width = 1.8f, cap = StrokeCap.Round),
        )
        MascotEyeShape.Squint -> drawPath(
            path = arcEyesPath(halfWidth = 7f, baseline = 51f, apex = 48f),
            color = MascotEyes,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round),
        )
        MascotEyeShape.Dot -> {
            drawCircle(color = MascotEyes, radius = 4.2f, center = Offset(46f, 50f))
            drawCircle(color = MascotEyes, radius = 4.2f, center = Offset(74f, 50f))
        }
        MascotEyeShape.Star -> {
            drawPath(path = starPath(cx = 46f, cy = 50f, radius = 5.5f), color = MascotEyes)
            drawPath(path = starPath(cx = 74f, cy = 50f, radius = 5.5f), color = MascotEyes)
        }
    }
}

private fun DrawScope.drawMouth(mouth: MascotMouthShape) {
    when (mouth) {
        MascotMouthShape.Smile -> drawPath(
            path = quadPath(fromX = 54f, fromY = 61f, toX = 66f, toY = 61f, controlX = 60f, controlY = 66f),
            color = MascotEyes,
            style = Stroke(width = 1.8f, cap = StrokeCap.Round),
        )
        MascotMouthShape.Grin -> drawPath(
            path = quadPath(fromX = 53f, fromY = 60f, toX = 67f, toY = 60f, controlX = 60f, controlY = 69f),
            color = Coral,
        )
        MascotMouthShape.WideGrin -> {
            // Fill auto-closes the curve, as SVG does; the stroke stays open,
            // so the top of the grin is a clean edge rather than a drawn line.
            val grin = quadPath(fromX = 46f, fromY = 59f, toX = 74f, toY = 59f, controlX = 60f, controlY = 74f)
            drawPath(path = grin, color = Coral)
            drawPath(path = grin, color = MouthStroke, style = Stroke(width = 0.5f))
        }
        MascotMouthShape.OpenRound -> {
            val topLeft = Offset(60f - 5.5f, 62f - 4.5f)
            val ovalSize = Size(11f, 9f)
            drawOval(color = Coral, topLeft = topLeft, size = ovalSize)
            drawOval(color = MouthStroke, topLeft = topLeft, size = ovalSize, style = Stroke(width = 0.5f))
        }
    }
}

private fun DrawScope.drawBeamGlow(phase: Float) {
    val glowScale = lerp(0.7f, 1.3f, phase)
    val glowAlpha = lerp(0.2f, 1f, phase)
    val radius = 52f * glowScale
    val center = Offset(60f, 45f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Gold.copy(alpha = 0.6f), Gold.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        alpha = glowAlpha,
    )
}

/** Both eyes as one upward arc each, mirrored about the face centre. A
 * quadratic curve only reaches halfway to its control point, so the control
 * sits twice as far above the endpoints as the visible apex — ported from
 * iOS's `arcEyes`. */
private fun arcEyesPath(halfWidth: Float, baseline: Float, apex: Float): Path {
    val controlY = 2 * apex - baseline
    val path = Path()
    for (cx in floatArrayOf(46f, 74f)) {
        path.moveTo(cx - halfWidth, baseline)
        path.quadraticTo(cx, controlY, cx + halfWidth, baseline)
    }
    return path
}

private fun quadPath(fromX: Float, fromY: Float, toX: Float, toY: Float, controlX: Float, controlY: Float): Path {
    val path = Path()
    path.moveTo(fromX, fromY)
    path.quadraticTo(controlX, controlY, toX, toY)
    return path
}

/** A regular five-pointed star, drawn rather than typed: the `★` glyph web
 * uses is a font glyph, and Android has no more guarantee about its metrics
 * than iOS does — ported from iOS's `CloudMascot.star`. */
private fun starPath(cx: Float, cy: Float, radius: Float): Path {
    val path = Path()
    val inner = radius * 0.382f // the waist of a regular pentagram
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else inner
        val angle = (-90 + i * 36) * (kotlin.math.PI / 180f).toFloat()
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

// MARK: - Preview

@Preview(showBackground = true, backgroundColor = 0xFF0F0E2A)
@Composable
private fun CloudMascotMoodsPreview() {
    Column(modifier = Modifier.size(120.dp, 320.dp)) {
        for (mood in MascotMood.entries) {
            CloudMascot(mood = mood, size = 64.dp)
        }
    }
}
