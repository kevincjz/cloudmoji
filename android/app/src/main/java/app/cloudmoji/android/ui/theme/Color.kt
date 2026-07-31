package app.cloudmoji.android.ui.theme

import androidx.compose.ui.graphics.Color

val BackgroundPrimary = Color(0xFF0F0E2A)
val BackgroundMid = Color(0xFF1A1145)
val BackgroundEdge = Color(0xFF0D2137)

val Coral = Color(0xFFFF6B6B)
val Teal = Color(0xFF4ECDC4)
val Gold = Color(0xFFFFE66D)
val Amber = Color(0xFFFFB347)

/** The far stop of the count badge's `linear-gradient(135deg, #4ECDC4,
 * #44B8AC)`. Mirrors iOS `Theme.tealDeep`. A flat teal badge reads as a
 * sticker; the gradient is what makes it look like it landed on the tile. */
val TealDeep = Color(0xFF44B8AC)
val Moonlight = Color(0xFFA8D6FF)
val Lavender = Color(0xFFC4B5FD)

val HeaderPlate = Color(0xD10C0B21)
val TextPrimary = Color.White
val TextSecondary = Color.White.copy(alpha = 0.60f)
val Surface = Color.White.copy(alpha = 0.04f)
val SurfaceBorder = Color.White.copy(alpha = 0.12f)

// Mascot — mirrors iOS Theme.swift's "MARK: - Mascot" section and
// src/components/CloudMascot.tsx's inline fills exactly, so a change on
// either side is a readable diff against this file.
val CloudWhite = Color.White
val CloudHighlight = Color(0xFFF8FCFF)
val CloudShadow = Color(0xFFE8EEF4)
val MascotEyes = Color(0xFF2D3436)
val MouthStroke = Color(0xFFE55555)
val Blush = Color(0xFFFFB5B5)
val BlushBeaming = Color(0xFFFF9E9E)

