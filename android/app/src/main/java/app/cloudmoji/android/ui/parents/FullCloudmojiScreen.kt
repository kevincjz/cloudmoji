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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.CloudmojiDisplayFont
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary
import app.cloudmoji.android.ui.theme.TextTertiary

/**
 * The only place Full Cloudmoji is explained. Ported from iOS
 * `FullCloudmojiView.swift`, adapted for what the plan doc actually promises
 * on Android (`docs/superpowers/plans/2026-07-30-android-app.md`'s "Full
 * tier" row): five more mini-apps and four more languages — there is no
 * Apple Watch companion on Android, and Wear OS is explicitly "a later,
 * separately approved project", so that benefit is dropped rather than
 * copied over.
 *
 * **No purchase button.** Google Play Billing is Phase 5 of the Android
 * plan and is not built — this screen shows what Full unlocks and says
 * plainly that buying it is not available on Android yet, with no price
 * anywhere (there is no product to price). Reached only from
 * [GrownUpsScreen], so it is already behind the parental gate; nothing here
 * is reachable by a child.
 */
@Composable
fun FullCloudmojiScreen(isUnlocked: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundPrimary, BackgroundMid, BackgroundEdge)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("full-cloudmoji-panel"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ParentBackBar(title = "Full Cloudmoji", onBack = onBack, backTag = "full-back")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 620.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Hero()
                Spacer(Modifier.height(22.dp))
                FreeBaseline()
                Spacer(Modifier.height(22.dp))
                Benefits()
                Spacer(Modifier.height(22.dp))
                PurchaseArea(isUnlocked = isUnlocked)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Hero() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "☁️", fontSize = 48.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Upgrade to Full Cloudmoji",
            color = Teal,
            fontFamily = CloudmojiDisplayFont,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Full Cloudmoji is the paid version.",
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "One purchase, when it launches. No subscription.",
            color = TextSecondary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FreeBaseline() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("full-free-includes")
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = "Your free version includes",
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
        )
        Text(
            text = "Words and Count in English.",
            color = TextSecondary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "You can keep using the free version for as long as you like.",
            color = TextTertiary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Benefits() {
    Column(modifier = Modifier.fillMaxWidth().testTag("full-benefits")) {
        Text(
            text = "Full Cloudmoji adds",
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(14.dp))
        Benefit(
            icon = "🎹",
            title = "Five more mini-apps",
            detail = "Music, Flash Cards, Animals, Photos and Sleepy Cloud",
        )
        Spacer(Modifier.height(14.dp))
        Benefit(
            icon = "🌏",
            title = "Four more languages",
            detail = "Mandarin Chinese, Bahasa Melayu, Japanese and Tagalog",
        )
    }
}

@Composable
private fun Benefit(icon: String, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(text = icon, fontSize = 20.sp, modifier = Modifier.width(30.dp))
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
            Text(
                text = detail,
                color = TextSecondary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PurchaseArea(isUnlocked: Boolean) {
    if (isUnlocked) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Teal.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                .testTag("full-unlocked")
                .padding(18.dp),
        ) {
            Text(text = "✅", fontSize = 18.sp, modifier = Modifier.width(28.dp))
            Text(
                text = "Full Cloudmoji is unlocked on this device.",
                color = Teal,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface, RoundedCornerShape(16.dp))
                .testTag("full-not-yet-available")
                .padding(18.dp),
        ) {
            Text(
                text = "Not yet available to purchase on Android",
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A Google Play purchase for Full Cloudmoji is being built. This screen " +
                    "will show its price here once it launches — there is nothing to buy yet.",
                color = TextSecondary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}
