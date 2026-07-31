package app.cloudmoji.android.ui.parents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary
import app.cloudmoji.android.ui.theme.TextTertiary

/** Every number [ParentalGate] is drawn from. Mirrors iOS `ParentalGate.swift`'s
 * own constants, pt for dp. Parent-only chrome, so the field/buttons clear the
 * 44dp HIG-style floor rather than the 64dp child-facing one. */
private object GateMetrics {
    val cardWidth = 340.dp
    val fieldHeight = 52.dp
    val buttonHeight = 48.dp
    const val pressedScale = 0.88f
}

/**
 * A real gate, not a gesture. Ported from iOS `ParentalGate.swift`.
 *
 * Deliberately boring: no timer, no penalty, no lock-out — a parent who
 * misreads the question just tries again (see [GateAttempt.submit]). Drawn as
 * a full-screen overlay by the caller (see `CloudmojiApp.kt`) rather than a
 * dialog, so it sits over the launcher tiles *and* whatever mini-app was open
 * when the gear was tapped — a gate a child can tap around underneath is not
 * a gate.
 *
 * [challengeIndex] is owned by the caller and only ever advances by one, on
 * every close (pass or cancel) — this composable's own [GateAttempt] state
 * resets fresh whenever [challengeIndex] changes, exactly like iOS's
 * `ParentalGate` view being torn down and recreated each time the sheet
 * reopens over a new `gateIndex`.
 */
@Composable
fun ParentalGate(
    challengeIndex: Int,
    action: String,
    onPass: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Plain `remember`, not `rememberSaveable` — `GateAttempt` is not
    // `Parcelable`/`Serializable`, and the default Saver would crash trying
    // to put it in a Bundle. Losing an in-progress, unsubmitted digit on a
    // rotation is a fair trade: [challengeIndex] itself lives in the caller's
    // `rememberSaveable` `Int`, so a rotation mid-gate resumes on the same
    // question with a blank field rather than losing the gate outright.
    var attempt by remember(challengeIndex) {
        mutableStateOf(GateAttempt(index = challengeIndex))
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(challengeIndex) {
        focusRequester.requestFocus()
    }

    fun submit() {
        when (val outcome = attempt.submit()) {
            GateOutcome.Passed -> onPass()
            is GateOutcome.Failed -> attempt = outcome.attempt
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            // Tapping outside the card is Cancel — a toddler who reached this
            // screen needs a way out that is not the right answer. A
            // `pointerInput` rather than `.clickable` so this carries no
            // button semantics of its own; the card beneath swallows its own
            // taps the same way (see below), matching iOS's
            // `.onTapGesture(perform: onCancel)` + the card's own
            // `stopPropagation`-equivalent.
            .pointerInput(onCancel) { detectTapGestures(onTap = { onCancel() }) }
            .semantics { contentDescription = "Grown-ups only" }
            .testTag("parental-gate"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = GateMetrics.cardWidth)
                .padding(20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(listOf(BackgroundMid, BackgroundEdge)),
                    RoundedCornerShape(16.dp),
                )
                .border(1.5.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(24.dp),
        ) {
            Text(
                text = "Grown-ups only",
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(6.dp))

            Text(
                text = action,
                color = TextSecondary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "What is ${attempt.challenge.a} × ${attempt.challenge.b}?",
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.testTag("gate-question"),
            )
            Spacer(Modifier.height(10.dp))

            val fieldBorderColor = if (attempt.wasWrong) Coral.copy(alpha = 0.6f) else SurfaceBorder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GateMetrics.fieldHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface, RoundedCornerShape(12.dp))
                    .border(2.dp, fieldBorderColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicTextField(
                    value = attempt.entry,
                    onValueChange = { attempt = attempt.withEntry(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = CloudmojiBodyFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(Teal),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .focusRequester(focusRequester)
                        .testTag("gate-input"),
                )
            }
            Spacer(Modifier.height(if (attempt.wasWrong) 6.dp else 14.dp))

            if (attempt.wasWrong) {
                Text(
                    text = "Not quite — have another go.",
                    color = Coral,
                    fontFamily = CloudmojiBodyFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("gate-error"),
                )
                Spacer(Modifier.height(10.dp))
            }

            Row {
                GateButton(
                    title = "Cancel",
                    testTag = "gate-cancel",
                    tint = TextTertiary,
                    filled = false,
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                GateButton(
                    title = "Continue",
                    testTag = "gate-submit",
                    tint = Teal,
                    filled = true,
                    onClick = ::submit,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GateButton(
    title: String,
    testTag: String,
    tint: Color,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(GateMetrics.buttonHeight)
            .pressScale(interactionSource, GateMetrics.pressedScale)
            .clip(shape)
            .background(if (filled) tint.copy(alpha = 0.2f) else Color.Transparent, shape)
            .border(2.dp, if (filled) tint.copy(alpha = 0.4f) else SurfaceBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { role = Role.Button; contentDescription = title }
            .testTag(testTag),
    ) {
        Text(
            text = title,
            color = tint,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
        )
    }
}
