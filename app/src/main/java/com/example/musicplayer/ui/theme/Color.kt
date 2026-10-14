package com.example.musicplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pulse palette.
 *
 * The design is a dark, glass-and-violet look: a near-black canvas with a
 * single warm violet bloom behind the content, translucent "glass" surfaces
 * stacked on top, and magenta reserved for the one thing that is playing.
 */

// Canvas — the near-black the whole app sits on.
val PulseBlack = Color(0xFF060509)
val PulseNight = Color(0xFF0C0912)
val PulseSurface = Color(0xFF15111E)
val PulseSurfaceHigh = Color(0xFF1F1A2C)

// Bloom — the violet→magenta wash that bleeds from the top of every screen.
val PulseViolet = Color(0xFF8B5CF6)
val PulseOrchid = Color(0xFFB265DC)
val PulseMagenta = Color(0xFFC65FB0)
val PulsePlum = Color(0xFF5C2F7A)

// Type.
val PulseText = Color(0xFFF5F2F9)
val PulseTextMuted = Color(0xFF9C93AD)
val PulseTextFaint = Color(0xFF6E6680)

// Glass. Compose cannot cheaply blur what is *behind* a view, so the glass
// effect is built from translucent white fills plus a hairline stroke — the
// highlight is what actually sells it, not the blur.
val GlassFill = Color(0x12FFFFFF)
val GlassFillStrong = Color(0x1FFFFFFF)
val GlassStroke = Color(0x24FFFFFF)
val GlassStrokeBright = Color(0x3DFFFFFF)
