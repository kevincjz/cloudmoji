package app.cloudmoji.android.ui.parents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.MascotMood
import app.cloudmoji.android.ui.mascot.CloudMascot
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.CloudmojiDisplayFont
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary
import app.cloudmoji.android.ui.theme.TextTertiary

/**
 * The one-screen tour, replayable from the Grown-ups panel. Ported from iOS
 * `TutorialView.swift`'s six steps, dropping the Apple Watch mention (there
 * is no watch companion on Android — see `FullCloudmojiScreen`'s doc) and
 * adapting "the row along the top" step, which is Words-mode-specific detail
 * both apps already share via [app.cloudmoji.android.ui.words.TypingRow].
 *
 * Only the Settings-triggered replay exists in this task — unlike iOS there
 * is no first-launch sheet here yet, so every reachable route to this screen
 * is already behind the parental gate. Reached only from [GrownUpsScreen].
 */
@Composable
fun TutorialScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundPrimary, BackgroundMid, BackgroundEdge)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("tutorial-panel"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ParentBackBar(title = "How to use Cloudmoji", onBack = onBack, backTag = "tutorial-back")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Heading()
                Spacer(Modifier.height(18.dp))
                steps.forEach { step ->
                    StepRow(step)
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun Heading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CloudMascot(mood = MascotMood.Happy, size = 56.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Welcome to Cloudmoji",
                color = Teal,
                fontFamily = CloudmojiDisplayFont,
                fontSize = 20.sp,
            )
            Text(
                text = "Tap. Listen. Learn!",
                color = TextSecondary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Thirty seconds, and you will know everything there is to know.",
        color = TextTertiary,
        fontFamily = CloudmojiBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
}

@Composable
private fun StepRow(step: TutorialStep) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("tutorial-step-${step.id}"),
    ) {
        Text(text = step.glyph, fontSize = 24.sp, modifier = Modifier.width(34.dp))
        Column {
            Text(
                text = step.title,
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = step.detail,
                color = TextTertiary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

private data class TutorialStep(val id: String, val glyph: String, val title: String, val detail: String)

private val steps: List<TutorialStep> = listOf(
    TutorialStep(
        id = "tap",
        glyph = "👆",
        title = "Tap an emoji, hear the word",
        detail = "Start with Words and Count in English. Tap anything in the grid and Cloudmoji " +
            "says it out loud. A grown-up can find plan details and every parent control in " +
            "Grown-ups.",
    ),
    TutorialStep(
        id = "full",
        glyph = "🗣️",
        title = "More worlds with Full Cloudmoji",
        detail = "Full Cloudmoji adds Music, Flash Cards, Animals, Photos and Sleepy Cloud, plus " +
            "four more languages. Details live inside the gated Grown-ups screen.",
    ),
    TutorialStep(
        id = "home",
        glyph = "☁️",
        title = "The cloud brings you home",
        detail = "Inside a mini-app there is a big cloud centred along the bottom. Tapping it " +
            "goes home. It is always in the same place, and it is the only way out.",
    ),
    TutorialStep(
        id = "typing-row",
        glyph = "⌨️",
        title = "The row along the top",
        detail = "Everything your child taps in Words mode collects there. Tap any one of them " +
            "to hear it again, 🔊 replays the lot, ⌫ removes the last one and ✕ clears the row.",
    ),
    TutorialStep(
        id = "mute",
        glyph = "🔊",
        title = "Sound lives in the app",
        detail = "Sound is controlled in the grown-ups screen, not your phone's silent switch. " +
            "If it is off, sound-based mini-apps show a large 🔊 button to turn it back on.",
    ),
    TutorialStep(
        id = "settings",
        glyph = "⚙️",
        title = "Grown-ups settings",
        detail = "The locked Grown-ups button opens them, behind a simple sum to keep small " +
            "fingers out. Inside you can choose sound, categories, how high Count mode goes and " +
            "which languages are available with your plan.",
    ),
)
