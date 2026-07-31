package app.cloudmoji.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.model.Language
import app.cloudmoji.android.model.MiniApp
import app.cloudmoji.android.ui.theme.BackgroundEdge
import app.cloudmoji.android.ui.theme.BackgroundMid
import app.cloudmoji.android.ui.theme.BackgroundPrimary
import app.cloudmoji.android.ui.theme.CloudmojiBodyFont
import app.cloudmoji.android.ui.theme.CloudmojiDisplayFont
import app.cloudmoji.android.ui.theme.Teal
import app.cloudmoji.android.ui.theme.TextPrimary
import app.cloudmoji.android.ui.theme.TextSecondary
import androidx.compose.ui.platform.testTag

@Composable
fun MiniAppPlaceholder(
    app: MiniApp,
    language: Language,
    onHome: () -> Unit,
) {
    PlaceholderShell(
        icon = app.icon,
        title = app.label(language),
        message = "The Android foundation is ready. This mini-app lands in its implementation phase.",
        onHome = onHome,
        screenTag = "mini-app-${app.route}",
    )
}

@Composable
fun ParentPlaceholder(onHome: () -> Unit) {
    PlaceholderShell(
        icon = "🔒",
        title = "For Grown-ups",
        message = "The arithmetic parental gate and settings arrive before any parent controls or purchase UI.",
        onHome = onHome,
        screenTag = "parent-placeholder",
    )
}

@Composable
private fun PlaceholderShell(
    icon: String,
    title: String,
    message: String,
    onHome: () -> Unit,
    screenTag: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BackgroundPrimary, BackgroundMid, BackgroundEdge),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(screenTag),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 120.dp),
        ) {
            Text(text = icon, fontSize = 72.sp)
            Text(
                text = title,
                color = Teal,
                fontFamily = CloudmojiDisplayFont,
                fontSize = 34.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(14.dp))
            Text(
                text = message,
                color = TextSecondary,
                fontFamily = CloudmojiBodyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .size(84.dp)
                .clip(CircleShape)
                .background(Teal)
                .clickable(role = Role.Button, onClick = onHome)
                .semantics {
                    role = Role.Button
                    contentDescription = "Cloud home"
                }
                .testTag("cloud-home"),
        ) {
            Text(text = "☁️", fontSize = 42.sp)
        }
    }
}

