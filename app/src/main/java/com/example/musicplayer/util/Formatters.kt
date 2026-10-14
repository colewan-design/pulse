package com.example.musicplayer.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

fun formatPlaybackSpeed(speed: Float): String = String.format(Locale.getDefault(), "%.1fx", speed)

fun formatLastScan(timestamp: Long?): String {
    if (timestamp == null) {
        return "Never scanned"
    }
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
}

fun formatSleepTimerRemaining(remainingMs: Long): String {
    val minutes = (remainingMs / 60_000f).roundToInt()
    return if (minutes <= 1) {
        "Ends in 1 minute"
    } else {
        "Ends in $minutes minutes"
    }
}

