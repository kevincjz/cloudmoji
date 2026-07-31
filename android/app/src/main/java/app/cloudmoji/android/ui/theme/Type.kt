package app.cloudmoji.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.cloudmoji.android.R

val CloudmojiBodyFont = FontFamily(
    Font(R.font.nunito_variable, weight = FontWeight.Normal),
    Font(R.font.nunito_variable, weight = FontWeight.Bold),
    Font(R.font.nunito_variable, weight = FontWeight.Black),
)

val CloudmojiDisplayFont = FontFamily(
    Font(R.font.lilita_one_regular, weight = FontWeight.Normal),
)

val CloudmojiTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = CloudmojiBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = CloudmojiBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = CloudmojiBodyFont,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = CloudmojiDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
    ),
)

