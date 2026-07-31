package app.cloudmoji.android.ui.parents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.AppAccessPolicy
import app.cloudmoji.android.model.Category
import app.cloudmoji.android.model.CategoryTab
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.LanguageMeta
import app.cloudmoji.android.model.Settings
import app.cloudmoji.android.ui.common.pressScale
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.CloudmojiDisplayFont
import app.cloudmoji.android.ui.theme.Surface
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary
import app.cloudmoji.android.ui.theme.TextTertiary

/** Parent-only chrome throughout this screen, so the 44dp HIG-style floor —
 * `CLAUDE.md` rule 1's own carve-out — rather than the app's 64dp
 * child-facing minimum. Mirrors iOS `SettingsView.rowHeight`. */
private val RowHeight = 44.dp

/**
 * The parent's panel, behind the gate. Ported from iOS `SettingsView.swift`.
 *
 * Every control here writes straight through
 * [app.cloudmoji.android.data.SettingsRepository] (via the `onSet*`
 * callbacks the caller wires up) and reads only already-resolved state — this
 * screen makes no accessibility decision of its own; [accessPolicy] is
 * consulted only to decide *which* language controls to show, mirroring iOS
 * `SettingsView`'s own `model.entitlements.isUnlocked` branch.
 *
 * [allLanguages] is the full five-language catalogue, shown as a toggle list
 * only when [accessPolicy] allows it — a parent with Full access needs to be
 * able to turn any of the five on or off. [availableLanguages] is already
 * narrowed to what [accessPolicy] allows *and* what the parent left enabled
 * (`CloudmojiApp.kt`'s existing `availableLanguages` val), and is what the
 * starting-language picker offers — this is the "only access-policy-allowed
 * languages listed" the Task 8 brief asks for.
 */
@Composable
fun GrownUpsScreen(
    settings: Settings,
    accessPolicy: AppAccessPolicy,
    allLanguages: List<LanguageMeta>,
    availableLanguages: List<LanguageMeta>,
    categories: List<CategoryTab>,
    effectiveLanguage: Language,
    isUnlocked: Boolean,
    onSetMuted: (Boolean) -> Unit,
    onSetEnabledLanguages: (Set<Language>) -> Unit,
    onSetLanguage: (Language) -> Unit,
    onSetEnabledCategories: (Set<Category>) -> Unit,
    onSetCountRange: (IntRange) -> Unit,
    onOpenFullCloudmoji: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTutorial: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundPrimary, BackgroundMid, BackgroundEdge)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("settings-panel"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GrownUpsTopBar(title = "Grown-ups", onDone = onDone, doneTag = "settings-done")

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { PlanRow(isUnlocked = isUnlocked, onClick = onOpenFullCloudmoji) }
                item { Spacer(Modifier.height(14.dp)) }

                item { SectionHeader("Sound") }
                item {
                    ToggleRow(
                        icon = if (settings.muted) "🔇" else "🔊",
                        label = if (settings.muted) "Sound is off" else "Sound is on",
                        checked = !settings.muted,
                        enabled = true,
                        testTag = "settings-sound",
                        onToggle = { on -> onSetMuted(!on) },
                    )
                }
                item { Spacer(Modifier.height(14.dp)) }

                item { SectionHeader("Languages") }
                if (accessPolicy.hasFullAccess) {
                    items(allLanguages, key = { "lang-${it.id.code}" }) { meta ->
                        ToggleRow(
                            icon = meta.short,
                            label = meta.name,
                            checked = settings.enabledLanguages.contains(meta.id),
                            enabled = SettingsControls.canDisableLanguage(settings.enabledLanguages, meta.id)
                                || !settings.enabledLanguages.contains(meta.id),
                            testTag = "settings-lang-${meta.id.code}",
                            onToggle = { on ->
                                val next = settings.enabledLanguages.toMutableSet()
                                if (on) next.add(meta.id) else next.remove(meta.id)
                                onSetEnabledLanguages(next)
                            },
                        )
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { SectionHeader("Starting language") }
                    item {
                        StartingLanguageRow(
                            options = availableLanguages,
                            selected = settings.language,
                            onSelect = onSetLanguage,
                        )
                    }
                } else {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().height(RowHeight).testTag("settings-lang-en"),
                        ) {
                            Text(
                                text = "English",
                                color = TextPrimary,
                                fontFamily = CloudmojiBodyFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Included",
                                color = Teal,
                                fontFamily = CloudmojiBodyFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    item {
                        NavRow(
                            icon = "🔒",
                            label = "4 more languages with Full",
                            testTag = "settings-full-languages",
                            onClick = onOpenFullCloudmoji,
                        )
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }

                item { SectionHeader("Categories") }
                items(categories, key = { "cat-${it.id}" }) { tab ->
                    val category = tab.category
                    if (category != null) {
                        ToggleRow(
                            icon = tab.icon,
                            label = tab.label(effectiveLanguage),
                            checked = settings.enabledCategories.contains(category),
                            enabled = SettingsControls.canDisableCategory(settings.enabledCategories, category)
                                || !settings.enabledCategories.contains(category),
                            testTag = "settings-cat-${tab.id}",
                            onToggle = { on ->
                                val next = settings.enabledCategories.toMutableSet()
                                if (on) next.add(category) else next.remove(category)
                                onSetEnabledCategories(next)
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }

                item { SectionHeader("Count mode") }
                item {
                    CountStepperRow(
                        label = "Count from ${settings.countRange.first}",
                        canDecrease = settings.countRange.first > Settings.countBounds.first,
                        canIncrease = settings.countRange.first < settings.countRange.last,
                        testTag = "settings-count-lower",
                        onDecrease = { onSetCountRange((settings.countRange.first - 1)..settings.countRange.last) },
                        onIncrease = { onSetCountRange((settings.countRange.first + 1)..settings.countRange.last) },
                    )
                }
                item {
                    CountStepperRow(
                        label = "Count up to ${settings.countRange.last}",
                        canDecrease = settings.countRange.last > settings.countRange.first,
                        canIncrease = settings.countRange.last < Settings.countBounds.last,
                        testTag = "settings-count-upper",
                        onDecrease = { onSetCountRange(settings.countRange.first..(settings.countRange.last - 1)) },
                        onIncrease = { onSetCountRange(settings.countRange.first..(settings.countRange.last + 1)) },
                    )
                }
                item { Spacer(Modifier.height(14.dp)) }

                item { SectionHeader("More") }
                item {
                    NavRow(
                        icon = "❓",
                        label = "How to use Cloudmoji",
                        testTag = "settings-tutorial-row",
                        onClick = onOpenTutorial,
                    )
                }
                item {
                    NavRow(
                        icon = "ℹ️",
                        label = "About Cloudmoji",
                        testTag = "settings-about-row",
                        onClick = onOpenAbout,
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun GrownUpsTopBar(title: String, onDone: () -> Unit, doneTag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = Teal,
            fontFamily = CloudmojiDisplayFont,
            fontSize = 24.sp,
        )
        Spacer(Modifier.weight(1f))
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(RowHeight)
                .pressScale(interactionSource, 0.9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Teal.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onDone,
                )
                .semantics { role = Role.Button; contentDescription = "Done" }
                .testTag(doneTag)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Done",
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = TextTertiary,
        fontFamily = CloudmojiBodyFont,
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun PlanRow(isUnlocked: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface, RoundedCornerShape(14.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { role = Role.Button }
            .testTag("settings-plan-row")
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isUnlocked) "Full Cloudmoji" else "Cloudmoji Free",
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isUnlocked) Teal else Surface, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (isUnlocked) "UNLOCKED" else "FREE",
                    color = if (isUnlocked) BackgroundPrimary else TextSecondary,
                    fontFamily = CloudmojiBodyFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isUnlocked) {
                "Full Cloudmoji is unlocked on this device."
            } else {
                "You’re using the free version. Included: Words and Count in English."
            },
            color = TextSecondary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        if (!isUnlocked) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "See what Full Cloudmoji unlocks →",
                color = Teal,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                modifier = Modifier.testTag("settings-full-call-to-action"),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: String,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(RowHeight),
    ) {
        Text(text = icon, fontSize = 16.sp, modifier = Modifier.width(30.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Teal, checkedThumbColor = TextPrimary),
            modifier = Modifier
                .semantics { contentDescription = label }
                .testTag(testTag),
        )
    }
}

@Composable
private fun NavRow(icon: String, label: String, testTag: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { role = Role.Button; contentDescription = label }
            .testTag(testTag),
    ) {
        Text(text = icon, fontSize = 16.sp, modifier = Modifier.width(30.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(text = "›", color = TextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun StartingLanguageRow(
    options: List<LanguageMeta>,
    selected: Language,
    onSelect: (Language) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Starting language" }
            .testTag("settings-default-lang"),
    ) {
        options.forEach { meta ->
            val isSelected = meta.id == selected
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(RowHeight)
                    .pressScale(interactionSource, 0.9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Teal.copy(alpha = 0.2f) else Surface, RoundedCornerShape(12.dp))
                    .border(
                        2.dp,
                        if (isSelected) Teal.copy(alpha = 0.4f) else SurfaceBorder,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = { onSelect(meta.id) },
                    )
                    .semantics { role = Role.Button; contentDescription = meta.name }
                    .testTag("settings-default-lang-${meta.id.code}")
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = meta.short,
                    color = TextPrimary,
                    fontFamily = CloudmojiBodyFont,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun CountStepperRow(
    label: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    testTag: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(RowHeight).testTag(testTag),
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        StepperButton(symbol = "–", enabled = canDecrease, testTag = "$testTag-dec", onClick = onDecrease)
        Spacer(Modifier.width(8.dp))
        StepperButton(symbol = "+", enabled = canIncrease, testTag = "$testTag-inc", onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, testTag: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint = if (enabled) Teal else TextTertiary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .pressScale(interactionSource, 0.88f)
            .clip(CircleShape)
            .background(Surface, CircleShape)
            .border(1.5.dp, SurfaceBorder, CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { role = Role.Button; contentDescription = if (symbol == "+") "Increase" else "Decrease" }
            .testTag(testTag),
    ) {
        Text(text = symbol, color = tint, fontFamily = CloudmojiBodyFont, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}
