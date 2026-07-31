package app.cloudmoji.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.LanguageMeta
import app.cloudmoji.android.model.MascotMood
import app.cloudmoji.android.ui.mascot.CloudMascot
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.CloudmojiDisplayFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary

/** Every number [ModeHeader] is drawn from. Mirrors iOS `ModeHeader.swift`'s
 * `ModeHeaderMetrics`, dp for pt. Parent-only chrome, so the 44dp HIG-style
 * floor rather than the 64dp child-facing one — CLAUDE.md rule 1's own carve-out. */
object ModeHeaderMetrics {
    val controlSide = 44.dp
    val padControlSide = 54.dp
    val spacing = 8.dp
    val controlCornerRadius = 14.dp
    val controlBorderWidth = 2.dp
    val controlGlyphSize = 18.sp
    val padControlGlyphSize = 22.sp
    val titleSize = 21.sp
    val compactTitleSize = 17.sp
    val subtitleSize = 10.sp
    val padTitleSize = 29.sp
    val padSubtitleSize = 13.sp
    val mascotSize = 64.dp
    val compactMascotSize = 42.dp
    val padMascotSize = 86.dp
    val languageLabelSize = 14.sp
    val padLanguageLabelSize = 17.sp

    /** Fixed, not intrinsic — cycling EN -> 中文 -> BM -> 日本語 -> TL through an
     * intrinsically-sized button would shuffle the whole header sideways
     * under the child's finger on every tap. */
    val languageControlWidth = 62.dp
    val padLanguageControlWidth = 78.dp

    const val pressedScale = 0.88f
}

/**
 * The strip at the top of every screen: the mascot, the wordmark, and the
 * parent's controls. One component, shared by every mode — Task 7's Count
 * reuses this exactly. Ported from iOS `ModeHeader.swift`.
 *
 * Every piece of state here is already resolved by the caller — [language]
 * is the effective (access-policy-filtered) language, [availableLanguages]
 * is already narrowed to what the parent left enabled *and* what the
 * entitlement allows. This view makes no accessibility decision of its own.
 */
@Composable
fun ModeHeader(
    mood: MascotMood,
    title: String,
    subtitle: String,
    isCompact: Boolean,
    isExpandedPad: Boolean,
    muted: Boolean,
    language: Language,
    availableLanguages: List<LanguageMeta>,
    onParent: () -> Unit,
    onToggleMute: () -> Unit,
    onCycleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = availableLanguages.firstOrNull { it.id == language }
    val canCycle = availableLanguages.size > 1

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isExpandedPad) 12.dp else ModeHeaderMetrics.spacing),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isExpandedPad) 24.dp else if (isCompact) 12.dp else 14.dp,
                vertical = if (isExpandedPad) 14.dp else if (isCompact) 3.dp else 8.dp,
            ),
    ) {
        CloudMascot(
            mood = mood,
            size = if (isExpandedPad) {
                ModeHeaderMetrics.padMascotSize
            } else if (isCompact) {
                ModeHeaderMetrics.compactMascotSize
            } else {
                ModeHeaderMetrics.mascotSize
            },
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Teal,
                fontFamily = CloudmojiDisplayFont,
                fontSize = if (isExpandedPad) {
                    ModeHeaderMetrics.padTitleSize
                } else if (isCompact) {
                    ModeHeaderMetrics.compactTitleSize
                } else {
                    ModeHeaderMetrics.titleSize
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isCompact) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontFamily = CloudmojiBodyFont,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isExpandedPad) ModeHeaderMetrics.padSubtitleSize else ModeHeaderMetrics.subtitleSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        ModeHeaderControl(
            glyph = "⚙️",
            label = "Grown-ups only",
            identifier = "parent-btn",
            tint = Teal,
            isOn = false,
            isExpandedPad = isExpandedPad,
            action = onParent,
        )
        ModeHeaderControl(
            glyph = if (muted) "🔇" else "🔊",
            label = if (muted) "Unmute" else "Mute",
            identifier = "mute-btn",
            tint = Coral,
            isOn = muted,
            isExpandedPad = isExpandedPad,
            action = onToggleMute,
        )
        LanguageToggle(
            label = current?.short ?: language.code.uppercase(),
            voiceOverLabel = "Language: ${current?.name ?: language.code}",
            voiceOverValue = current?.short ?: "",
            voiceOverHint = if (canCycle) "Switches to the next language" else "The only language switched on in Settings",
            isEnabled = canCycle,
            isExpandedPad = isExpandedPad,
            action = onCycleLanguage,
        )
    }
}

/** The language button: the current language's own short name, tapped to
 * advance to the next one the parent left enabled. Ported from iOS's
 * `LanguageToggle`. */
@Composable
private fun LanguageToggle(
    label: String,
    voiceOverLabel: String,
    voiceOverValue: String,
    voiceOverHint: String,
    isEnabled: Boolean,
    isExpandedPad: Boolean,
    action: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(if (isExpandedPad) 18.dp else ModeHeaderMetrics.controlCornerRadius)
    val width = if (isExpandedPad) ModeHeaderMetrics.padLanguageControlWidth else ModeHeaderMetrics.languageControlWidth
    val height = if (isExpandedPad) ModeHeaderMetrics.padControlSide else ModeHeaderMetrics.controlSide

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(height)
            .pressScale(interactionSource, ModeHeaderMetrics.pressedScale)
            .clip(shape)
            .background(Surface, shape)
            .border(ModeHeaderMetrics.controlBorderWidth, SurfaceBorder, shape)
            .then(
                if (isEnabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = action,
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = "$voiceOverLabel. $voiceOverValue. $voiceOverHint"
                // Always a button — the disabled state is that it does not
                // respond to a tap, not that it stops being one, the same
                // distinction iOS's `.disabled(!isEnabled)` (rather than
                // removing the button trait) draws.
                role = Role.Button
            }
            .testTag("lang-picker"),
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = if (isExpandedPad) ModeHeaderMetrics.padLanguageLabelSize else ModeHeaderMetrics.languageLabelSize,
            maxLines = 1,
        )
    }
}

/** One parent-chrome button. 44dp — the platform-norm floor for grown-up
 * chrome, **not** the 64dp child-facing floor. Ported from iOS's `ModeHeaderControl`. */
@Composable
private fun ModeHeaderControl(
    glyph: String,
    label: String,
    identifier: String,
    tint: Color,
    isOn: Boolean,
    isExpandedPad: Boolean,
    action: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(if (isExpandedPad) 18.dp else ModeHeaderMetrics.controlCornerRadius)
    val side = if (isExpandedPad) ModeHeaderMetrics.padControlSide else ModeHeaderMetrics.controlSide

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(side)
            .pressScale(interactionSource, ModeHeaderMetrics.pressedScale)
            .clip(shape)
            .background(if (isOn) tint.copy(alpha = 0.2f) else Surface, shape)
            .border(
                ModeHeaderMetrics.controlBorderWidth,
                if (isOn) tint.copy(alpha = 0.3f) else SurfaceBorder,
                shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = action,
            )
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .testTag(identifier),
    ) {
        Text(
            text = glyph,
            color = TextPrimary,
            fontSize = if (isExpandedPad) ModeHeaderMetrics.padControlGlyphSize else ModeHeaderMetrics.controlGlyphSize,
        )
    }
}
