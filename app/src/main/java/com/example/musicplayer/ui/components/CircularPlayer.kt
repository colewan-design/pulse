package com.example.musicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.musicplayer.ui.theme.GlassFill
import com.example.musicplayer.ui.theme.GlassStroke
import com.example.musicplayer.ui.theme.GlassStrokeBright
import com.example.musicplayer.ui.theme.PulseMagenta
import com.example.musicplayer.ui.theme.PulseOrchid
import com.example.musicplayer.ui.theme.PulseViolet
import kotlin.math.atan2

/**
 * The now-playing centrepiece: circular artwork seated inside a glass collar,
 * with elapsed progress drawn around it as a sweep arc.
 *
 * The collar doubles as the seek control — dragging around it scrubs, which is
 * why the arc is drawn on the collar rather than tight against the artwork.
 */
@Composable
fun CircularPlayer(
    artworkUri: String?,
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 264.dp,
    collar: Dp = 34.dp,
) {
    val density = LocalDensity.current
    val collarPx = with(density) { collar.toPx() }
    val strokePx = with(density) { 4.dp.toPx() }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val clamped = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(GlassFill)
                .border(1.dp, GlassStroke, CircleShape)
                .onSizeChanged { boxSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeekFraction(fractionFor(offset, boxSize))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onSeekFraction(fractionFor(change.position, boxSize))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = collarPx / 2f
                val arcSize = Size(size.width - collarPx, size.height - collarPx)
                val topLeft = Offset(inset, inset)

                drawArc(
                    color = GlassStroke,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )

                if (clamped > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(PulseViolet, PulseOrchid, PulseMagenta, PulseViolet),
                        ),
                        // -90 puts zero at twelve o'clock, matching the reference.
                        startAngle = -90f,
                        sweepAngle = 360f * clamped,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
            }

            ArtworkImage(
                artworkUri = artworkUri,
                shape = CircleShape,
                modifier = Modifier
                    .size(diameter - collar * 2)
                    .border(1.dp, GlassStrokeBright, CircleShape),
            )
        }
    }
}

/**
 * Neighbouring track artwork, peeking out from behind the main dial. Purely a
 * depth cue for what is on either side in the queue — not interactive.
 */
@Composable
fun PeekArtwork(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
) {
    ArtworkImage(
        artworkUri = artworkUri,
        shape = CircleShape,
        modifier = modifier
            .size(size)
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
    )
}

private fun fractionFor(position: Offset, size: IntSize): Float {
    if (size.width <= 0 || size.height <= 0) return 0f
    val cx = size.width / 2f
    val cy = size.height / 2f
    // atan2 measures from three o'clock; +90 rotates zero up to twelve.
    var degrees = Math.toDegrees(
        atan2((position.y - cy).toDouble(), (position.x - cx).toDouble()),
    ).toFloat() + 90f
    if (degrees < 0f) degrees += 360f
    return (degrees / 360f).coerceIn(0f, 1f)
}
