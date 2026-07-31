package app.cloudmoji.android.ui.parents

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.BuildConfig
import app.cloudmoji.android.model.MascotMood
import app.cloudmoji.android.ui.mascot.CloudMascot
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary
import app.cloudmoji.android.ui.theme.TextTertiary

/**
 * FAQ, privacy and support. Ported from iOS `AboutView.swift`, rewritten
 * (not translated line for line) for what this Android binary actually does
 * today: all seven mini-apps are real as of Task 14; the camera is used in
 * exactly one of them and the microphone in none (unlike iOS, which carries a
 * watch-only microphone); Full Cloudmoji has no Google Play purchase yet
 * ([FullCloudmojiScreen] covers that); and settings are six values in
 * Preferences DataStore, not seven `UserDefaults` keys. Every claim below was
 * checked against what is
 * actually built rather than copied from the iPhone app's disclosure —
 * `AboutView.swift`'s own doc explains why that distinction matters: a
 * privacy screen is a claim about *this* binary.
 *
 * Reached only from [GrownUpsScreen]'s "About Cloudmoji" row, so it is
 * already behind the parental gate.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundPrimary, BackgroundMid, BackgroundEdge)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("about-panel"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ParentBackBar(title = "About Cloudmoji", onBack = onBack, backTag = "about-back")

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { Intro() }
                item { Spacer(Modifier.height(4.dp)) }

                item { AboutSectionHeader("FAQ") }
                items(faq) { entry -> Disclosure(entry) }
                item { Spacer(Modifier.height(4.dp)) }

                item { AboutSectionHeader("Support") }
                item {
                    SupportEmailRow(
                        onClick = {
                            openMailto(context, SupportLinks.SUPPORT_MAILTO)
                        },
                    )
                }
                item {
                    Text(
                        text = "Your mail app opens with no Cloudmoji data attached. Please do not include personal information about your child.",
                        color = TextTertiary,
                        fontFamily = CloudmojiBodyFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }

                item { AboutSectionHeader("Legal") }
                item {
                    NavLinkRow(
                        icon = "🔏",
                        label = "Open the full Privacy Policy",
                        testTag = "about-privacy-link",
                        onClick = { openUrl(context, SupportLinks.PRIVACY_URL) },
                    )
                }
                items(legal) { entry -> Disclosure(entry) }
                item { Spacer(Modifier.height(4.dp)) }

                item { AboutSectionHeader("Version") }
                item {
                    Text(
                        text = "Cloudmoji for Android v${BuildConfig.VERSION_NAME}",
                        color = TextSecondary,
                        fontFamily = CloudmojiBodyFont,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier.testTag("about-version"),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun Intro() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        CloudMascot(mood = MascotMood.Happy, size = 96.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Made with love by Kevin and PQ for our son Cloud.",
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "One day Cloud picked up a locked iPhone, started typing emojis, and said the " +
                "words out loud — all on his own. This is that idea, now on Android too.",
            color = TextTertiary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AboutSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = TextTertiary,
        fontFamily = CloudmojiBodyFont,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun Disclosure(entry: AboutEntry) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface, RoundedCornerShape(12.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { expanded = !expanded },
            )
            .semantics { role = Role.Button }
            .testTag("about-${entry.id}")
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.question,
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(text = if (expanded) "▲" else "▼", color = TextSecondary, fontSize = 12.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.answer,
                color = TextTertiary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SupportEmailRow(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface, RoundedCornerShape(12.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { role = Role.Button; contentDescription = "Email Cloudmoji Support at ${SupportLinks.SUPPORT_EMAIL}" }
            .testTag("about-support-email")
            .padding(14.dp),
    ) {
        Text(text = "✉️", fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Email Cloudmoji Support",
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                text = SupportLinks.SUPPORT_EMAIL,
                color = TextSecondary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        Text(text = "↗", color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun NavLinkRow(icon: String, label: String, testTag: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { role = Role.Button; contentDescription = label }
            .testTag(testTag),
    ) {
        Text(text = icon, fontSize = 16.sp, modifier = Modifier.padding(end = 10.dp))
        Text(
            text = label,
            color = Teal,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

/** Shared by [AboutScreen], [FullCloudmojiScreen] and [TutorialScreen] — the
 * "‹ Back" control every sub-screen of the Grown-ups area uses to return to
 * [GrownUpsScreen], since none of them are reachable except from there. */
@Composable
internal fun ParentBackBar(title: String, onBack: () -> Unit, backTag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                )
                .semantics { role = Role.Button; contentDescription = "Back to Grown-ups" }
                .testTag(backTag)
                .padding(horizontal = 4.dp),
        ) {
            Text(
                text = "‹ Back",
                color = Teal,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
        )
    }
}

/** One FAQ or legal entry — mirrors iOS `AboutView.Entry`. The [id] is
 * explicit rather than slugged from [question], so a test's identifier stays
 * stable when the wording is edited. */
internal data class AboutEntry(val id: String, val question: String, val answer: String)

private val faq: List<AboutEntry> = listOf(
    AboutEntry(
        id = "how-to-use",
        question = "How do we use Cloudmoji?",
        answer = "The free version opens with Words and Count in English. Tap one to go in; tap " +
            "the big cloud along the bottom of the screen to come back out.\n\n" +
            "🗣️ Words — tap any emoji to hear the word spoken aloud.\n\n" +
            "🧮 Count — tap the emojis one at a time to count them, out loud, in order.\n\n" +
            "⚡ Flash Cards, 🎹 Music, 🔊 Animals, 📷 Photos and 🌙 Sleepy Cloud are part of " +
            "Full Cloudmoji.",
    ),
    AboutEntry(
        id = "screen-pinning",
        question = "How do I keep my child inside Cloudmoji?",
        answer = "Most Android phones have a screen-pinning feature built in. Settings → Security " +
            "(sometimes under \"Additional settings\" or \"Lock screen\") → Screen pinning → turn " +
            "it on. Then open Cloudmoji, open Recents, and tap the pin icon on the Cloudmoji card. " +
            "To unpin, hold Back and Recents together — the exact gesture depends on your device.",
    ),
    AboutEntry(
        id = "languages",
        question = "Which languages are supported?",
        answer = "The free version includes English. Full Cloudmoji adds Mandarin Chinese (中文), " +
            "Bahasa Melayu (BM), Japanese (日本語) and Tagalog (TL) — Japanese uses hiragana and " +
            "katakana only, no kanji, matching what Japanese children learn first. In the " +
            "grown-ups screen you can switch off the languages your family does not use.",
    ),
    AboutEntry(
        id = "make-it-simpler",
        question = "Can I make it simpler for a younger child?",
        answer = "Yes. Tap the locked Grown-ups button, answer the grown-ups question, and you can " +
            "switch off whole categories of emoji and choose how high Count mode goes. Narrowing " +
            "it to two categories and counting to three is a good place to start with a toddler.",
    ),
    AboutEntry(
        id = "offline",
        question = "Does it work offline?",
        answer = "Yes. Every word in every language already on this device is inside the app you " +
            "downloaded, and speech comes from Android's own text-to-speech voices. Cloudmoji " +
            "makes no network calls of its own.",
    ),
    AboutEntry(
        id = "full-cloudmoji",
        question = "What does Full Cloudmoji unlock?",
        answer = "Music, Flash Cards, Animals, Photos, Sleepy Cloud, and four more languages: " +
            "Mandarin Chinese, Bahasa Melayu, Japanese and Tagalog. A Google Play purchase for " +
            "Full Cloudmoji is not available yet — see the Full Cloudmoji row in Grown-ups for " +
            "what is unlocked on this build today and what changes when it launches.",
    ),
    AboutEntry(
        id = "data",
        question = "Is my child's data collected?",
        answer = "Cloudmoji does not collect personal data. There are no accounts, no tracking and " +
            "no Cloudmoji servers. If a grown-up chooses to email support, their own mail app " +
            "sends the address and message they choose to write — Cloudmoji attaches nothing.",
    ),
)

private val legal: List<AboutEntry> = listOf(
    AboutEntry(
        id = "privacy",
        question = "Privacy Policy",
        answer = "Cloudmoji for Android collects nothing on its own.\n\n" +
            "NO SERVERS OR TRACKING\nThere are no analytics, advertising or crash-reporting SDKs, " +
            "and no Cloudmoji servers. Nothing about your child is uploaded anywhere. The word " +
            "lists for every language are bundled inside the app, and the two typefaces Cloudmoji " +
            "uses travel with it.\n\n" +
            "WHAT IS STORED, AND WHERE\nSix settings are kept on this device, in Android's own " +
            "Preferences DataStore: the chosen language, which languages are switched on, which " +
            "categories are switched on, the lowest and highest number Count mode uses, whether " +
            "Cloudmoji is muted, and whether the tour has been seen. None of it is sent to us. " +
            "Photographs your child takes are the one other thing stored — see the next " +
            "section.\n\n" +
            "CAMERA AND PHOTOS\nThe camera is used in one place: the Photos mini-app, where your " +
            "child takes pictures. Cloudmoji asks for camera permission there and nowhere else, " +
            "and only after a grown-up has answered the grown-ups question. The pictures are " +
            "written inside the app's own private storage: they are not added to your phone's " +
            "gallery, they are excluded from Android backup and phone-to-phone transfer, and " +
            "uninstalling Cloudmoji deletes them. Each picture is re-saved before it is written, " +
            "which removes the camera's own hidden information — there is no location in a " +
            "Cloudmoji photo, and Cloudmoji never asks for location permission. A grown-up can " +
            "delete them, or save copies to your gallery, under Photos on this device in the " +
            "grown-ups screen.\n\n" +
            "MICROPHONE\nThis build of Cloudmoji does not use the microphone anywhere.\n\n" +
            "SPEECH\nWords are spoken by Android's own text-to-speech. Cloudmoji hands the system " +
            "a word and Android makes a sound; nothing is recorded, saved or sent anywhere.\n\n" +
            "OPTIONAL SUPPORT EMAIL\nThe Support row opens your own mail app with the Cloudmoji " +
            "support address and a subject line already filled in. Cloudmoji attaches no " +
            "diagnostics, settings or app data. Please do not include your child's name or other " +
            "personal information.\n\n" +
            "NO ACCOUNTS, NO PURCHASE YET\nThere is nothing to sign in to. Full Cloudmoji has no " +
            "Google Play purchase in this build — every experience currently in the app is free " +
            "to use while that is being built. Nothing your child can reach opens a browser, " +
            "another app, or a purchase screen.\n\n" +
            "We are parents, not lawyers. This describes what the app actually does rather than " +
            "serving as a legal certification. The full policy, covering every Cloudmoji " +
            "platform, is linked above.",
    ),
    AboutEntry(
        id = "terms",
        question = "Terms of Use",
        answer = "• Cloudmoji is provided as is, without warranty of any kind.\n" +
            "• It is intended for use by children under adult supervision.\n" +
            "• We recommend Android's screen-pinning feature to keep a small person safely inside " +
            "the app.\n" +
            "• Speech quality depends on the voices installed on your device and varies between " +
            "them.\n" +
            "• We may update or discontinue Cloudmoji at any time.\n" +
            "• When Full Cloudmoji reaches Google Play, it will be sold as a one-time purchase, " +
            "not a subscription.",
    ),
)

private fun openMailto(context: android.content.Context, mailto: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailto))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No mail app on this device — there is nothing else this row can do,
        // and CLAUDE.md rule 4's "no failure states" is a child-facing rule;
        // a parent-chrome no-op is the acceptable degraded case here.
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser available — see openMailto's note.
    }
}
