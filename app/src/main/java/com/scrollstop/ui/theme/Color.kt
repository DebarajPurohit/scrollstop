package com.scrollstop.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// Approved Slate/Emerald Palette Tokens (UI_SPEC.md Section 4)
// =========================================================================

// Background & Surface Tokens
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF111827)
val Slate950 = Color(0xFF0B0F19)

// Emerald (Brand / Primary Protection) Tokens
val Emerald500 = Color(0xFF10B981) // Emerald 500 (Dark theme primary)
val Emerald600 = Color(0xFF059669) // Emerald 600 (Light theme primary)
val Emerald950 = Color(0xFF022C22) // Deep Emerald (OnPrimary dark)

// Commitment Lock Tokens
val Blue500 = Color(0xFF3B82F6) // Blue 500 (Dark theme commitment)
val Blue600 = Color(0xFF2563EB) // Blue 600 (Light theme commitment)
val Blue100 = Color(0xFFDBEAFE) // Blue 100 (Commitment container light)
val Blue950 = Color(0xFF172554) // Blue 950 (Commitment container dark)

// Warning Tokens
val Amber500 = Color(0xFFF59E0B) // Amber 500 (Dark theme warning)
val Amber600 = Color(0xFFD97706) // Amber 600 (Light theme warning)
val Amber100 = Color(0xFFFEF3C7) // Amber 100 (Warning container light)
val Amber950 = Color(0xFF451A03) // Amber 950 (Warning container dark)

// Error & Alert Tokens
val Red100 = Color(0xFFFEE2E2) // Red 100 (Error container light)
val Red300 = Color(0xFFFCA5A5) // Red 300 (OnErrorContainer dark)
val Red500 = Color(0xFFEF4444) // Red 500 (Dark theme error)
val Red600 = Color(0xFFDC2626) // Red 600 (Light theme error)
val Red800 = Color(0xFF991B1B) // Red 800 (OnErrorContainer light)
val Red950 = Color(0xFF450A0A) // Red 950 (Error container dark)

// =========================================================================
// Semantic Light Palette Tokens
// =========================================================================
val LightBackground = Slate50
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Slate100
val LightOutline = Slate200
val LightPrimary = Emerald600
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSecondary = Slate600
val LightTextPrimary = Color(0xFF0F172A)
val LightCommitment = Blue600
val LightWarning = Amber600
val LightError = Red600
val LightErrorContainer = Red100
val LightOnErrorContainer = Red800

// =========================================================================
// Semantic Dark Palette Tokens
// =========================================================================
val DarkBackground = Slate950
val DarkSurface = Slate900
val DarkSurfaceVariant = Slate800
val DarkOutline = Slate700
val DarkPrimary = Emerald500
val DarkOnPrimary = Emerald950
val DarkSecondary = Slate400
val DarkTextPrimary = Slate50
val DarkCommitment = Blue500
val DarkWarning = Amber500
val DarkError = Red500
val DarkErrorContainer = Red950
val DarkOnErrorContainer = Red300

// =========================================================================
// Backward-Compatible Aliases for existing UI code references
// =========================================================================
val PrimaryEmerald = Emerald500
val PrimaryDarkEmerald = Emerald600
val TextPrimaryDark = DarkTextPrimary
val TextSecondaryDark = DarkSecondary
val TextPrimaryLight = LightTextPrimary
val TextSecondaryLight = LightSecondary
val AccentCardBorderDark = DarkOutline
val AccentCardBorderLight = LightOutline
