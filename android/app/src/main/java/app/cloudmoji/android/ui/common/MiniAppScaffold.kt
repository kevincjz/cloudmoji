package app.cloudmoji.android.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.Coral

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
 *
 * [showsSoundRecovery]/[muted]/[onUnmute] port iOS `HostedMiniApp`'s other
 * overlay: `if app.showsSoundRecovery && model.settings.muted { SoundRecoveryButton { ... } }`.
 * Words and Count already carry their own header mute control
 * ([app.cloudmoji.android.ui.common.ModeHeader]), so both pass
 * [showsSoundRecovery] `false` (the default) and never render this. A
 * mini-app with no header of its own — Music today, FlashCards/Animals/
 * Sleepy once built, per [app.cloudmoji.android.model.MiniApp.showsSoundRecovery] —
 * would otherwise be a dead end the moment the phone is muted from another
 * screen: taps still land (haptic, press animation) but never make a sound,
 * with no route back except the gated Grown-ups panel. That is exactly the
 * "no failure states" rule `CLAUDE.md` rule 4 exists to rule out, and why
 * this lives in the shared scaffold rather than duplicated per screen —
 * every future `showsSoundRecovery` mini-app gets it for free just by
 * passing the flag, the same way [homeAccent] already works.
 */
@Composable
fun MiniAppScaffold(
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    homeAccent: Color? = null,
    screenTag: String = "",
    showsSoundRecovery: Boolean = false,
    muted: Boolean = false,
    onUnmute: () -> Unit = {},
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

        AnimatedVisibility(
            visible = showsSoundRecovery && muted,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            SoundRecoveryButton(onClick = onUnmute)
        }
    }
}

/**
 * A child-readable recovery from global mute. Ported from iOS
 * `ContentView.swift`'s private `SoundRecoveryButton`.
 *
 * Audio-driven mini-apps must never look broken because a parent muted the
 * app on another screen. Words and Count already expose their own header
 * control; this large speaker appears only where there is otherwise no
 * route back to sound — see [MiniAppScaffold]'s own doc.
 *
 * 64dp — the child-facing floor, `CLAUDE.md` rule 1 — since the child who
 * taps this is the same one who just tapped a silent pad, not a parent.
 */
@Composable
private fun SoundRecoveryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val side = 64.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(side)
            .pressScale(interactionSource, 0.88f)
            .shadow(12.dp, CircleShape)
            .background(BackgroundPrimary.copy(alpha = 0.94f), CircleShape)
            .background(Coral.copy(alpha = 0.16f), CircleShape)
            .border(2.dp, Coral.copy(alpha = 0.52f), CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = "Turn sound on"
            }
            .testTag("sound-recovery-btn"),
    ) {
        Text(text = "🔇", fontSize = 24.sp)
    }
}
