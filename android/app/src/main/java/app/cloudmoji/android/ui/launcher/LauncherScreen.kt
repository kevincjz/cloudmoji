package app.cloudmoji.android.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.ui.theme.Amber
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.CloudmojiDisplayFont
import app.cloudmoji.android.ui.theme.Coral
import app.cloudmoji.android.ui.theme.Gold
import app.cloudmoji.android.ui.theme.HeaderPlate
import app.cloudmoji.android.ui.theme.Lavender
import app.cloudmoji.android.ui.theme.Moonlight
import app.cloudmoji.android.ui.theme.SurfaceBorder
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary

@Composable
fun LauncherScreen(
    apps: List<MiniApp>,
    language: Language,
    onOpen: (MiniApp) -> Unit,
    onParent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BackgroundPrimary, BackgroundMid, BackgroundEdge),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("launcher"),
    ) {
        val expanded = maxWidth >= 600.dp
        val horizontalPadding = if (expanded) 32.dp else 14.dp
        val iconSide = if (expanded) 118.dp else 76.dp
        val cellHeight = if (expanded) 164.dp else 112.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
        ) {
            LauncherHeader(onParent = onParent, expanded = expanded)
            Spacer(Modifier.height(if (expanded) 34.dp else 18.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(if (expanded) 20.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (expanded) 20.dp else 10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(apps, key = { it.route }) { app ->
                    LauncherTile(
                        app = app,
                        label = app.label(language),
                        iconSide = iconSide,
                        cellHeight = cellHeight,
                        onClick = { onOpen(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherHeader(
    onParent: () -> Unit,
    expanded: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(if (expanded) 32.dp else 26.dp))
            .background(HeaderPlate, RoundedCornerShape(if (expanded) 32.dp else 26.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(if (expanded) 32.dp else 26.dp))
            .padding(
                horizontal = if (expanded) 20.dp else 12.dp,
                vertical = if (expanded) 13.dp else 9.dp,
            ),
    ) {
        CloudMascot(size = if (expanded) 66.dp else 50.dp)
        Spacer(Modifier.width(if (expanded) 13.dp else 9.dp))

        Column {
            Text(
                text = "Cloudmoji",
                color = Teal,
                fontFamily = CloudmojiDisplayFont,
                fontSize = if (expanded) 27.sp else 20.sp,
            )
            Text(
                text = "Tap. Listen. Learn!",
                color = TextSecondary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (expanded) 12.sp else 9.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(if (expanded) 54.dp else 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Teal.copy(alpha = 0.16f))
                .clickable(role = Role.Button, onClick = onParent)
                .semantics {
                    role = Role.Button
                    contentDescription = "For grown-ups"
                }
                .testTag("launcher-parent")
                .padding(horizontal = if (expanded) 18.dp else 12.dp),
        ) {
            Text(
                text = "🔒  Grown-ups",
                color = TextPrimary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Black,
                fontSize = if (expanded) 15.sp else 12.sp,
            )
        }
    }
}

@Composable
private fun CloudMascot(size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size),
    ) {
        Box(
            modifier = Modifier
                .size(width = size, height = size * 0.68f)
                .background(Color.White, RoundedCornerShape(50)),
        )
        Text(
            text = "•ᴗ•",
            color = BackgroundPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.28f).sp,
        )
    }
}

@Composable
private fun LauncherTile(
    app: MiniApp,
    label: String,
    iconSide: Dp,
    cellHeight: Dp,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(cellHeight)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .testTag("launcher-tile-${app.route}")
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSide)
                .shadow(12.dp, RoundedCornerShape(iconSide * 0.25f))
                .background(app.iconBrush(), RoundedCornerShape(iconSide * 0.25f))
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(iconSide * 0.25f),
                ),
        ) {
            Text(
                text = app.icon,
                fontSize = (iconSide.value * 0.46f).sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = CloudmojiBodyFont,
            fontWeight = FontWeight.Black,
            fontSize = if (iconSide >= 100.dp) 17.sp else 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private fun MiniApp.iconBrush(): Brush {
    val colors = when (this) {
        MiniApp.Words -> listOf(Teal, Coral)
        MiniApp.Count -> listOf(Gold, Amber)
        MiniApp.FlashCards -> listOf(Gold, Lavender)
        MiniApp.Music -> listOf(Coral, Moonlight)
        MiniApp.Animals -> listOf(Teal, Gold)
        MiniApp.Photos -> listOf(Coral, Amber)
        MiniApp.Sleepy -> listOf(Moonlight, Lavender)
    }
    return Brush.linearGradient(colors)
}

