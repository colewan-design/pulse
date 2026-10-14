package com.example.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicplayer.ui.theme.GlassFill
import com.example.musicplayer.ui.theme.GlassStroke
import com.example.musicplayer.ui.theme.PulseBlack
import com.example.musicplayer.ui.theme.PulseOrchid

/**
 * The canvas every screen sits on: near-black, with a single violet bloom
 * bleeding down from the top. One bloom per screen — stacking them muddies
 * the gradient and washes out the glass surfaces layered above it.
 */
@Composable
fun PulseBackground(
    modifier: Modifier = Modifier,
    bloom: Color = PulseOrchid,
    bloomAlpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PulseBlack)
            .drawWithCache {
                // Anchored slightly above the top edge so the brightest part of
                // the bloom is cropped — you see the falloff, not the hotspot.
                val brush = Brush.radialGradient(
                    colors = listOf(
                        bloom.copy(alpha = bloomAlpha),
                        bloom.copy(alpha = bloomAlpha * 0.35f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.5f, -size.height * 0.05f),
                    radius = size.height * 0.55f,
                )
                onDrawBehind { drawRect(brush) }
            },
        content = content,
    )
}

/**
 * A translucent panel with a hairline highlight. The stroke is doing most of
 * the work — without it the fill just reads as a lighter rectangle.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    fill: Color = GlassFill,
    stroke: Color = GlassStroke,
    strokeWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(strokeWidth, stroke, shape),
        content = content,
    )
}
