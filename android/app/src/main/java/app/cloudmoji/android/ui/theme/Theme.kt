package app.cloudmoji.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CloudmojiDarkColors = darkColorScheme(
    primary = Teal,
    secondary = Coral,
    tertiary = Gold,
    background = BackgroundPrimary,
    surface = BackgroundMid,
    onPrimary = BackgroundPrimary,
    onSecondary = BackgroundPrimary,
    onTertiary = BackgroundPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun CloudmojiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CloudmojiDarkColors,
        typography = CloudmojiTypography,
        content = content,
    )
}

