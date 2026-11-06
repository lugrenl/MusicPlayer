package com.example.musicplayer.ui.playlist

import java.util.Locale

data class PlaybackProgress(
    val itemIndex: Int,
    val positionMs: Long,
    val durationMs: Long?,
    val isPlaying: Boolean
) {
    val safeDurationMs: Long?
        get() = durationMs?.takeIf { it > 0L }

    val safePositionMs: Long
        get() = safeDurationMs?.let { positionMs.coerceIn(0L, it) }
            ?: positionMs.coerceAtLeast(0L)

    val progress: Int
        get() = safeDurationMs?.let { duration ->
            (safePositionMs.toDouble() * MAX_PROGRESS / duration)
                .toInt()
                .coerceIn(0, MAX_PROGRESS)
        } ?: 0

    fun formattedTime(): String {
        val duration = safeDurationMs?.let(::formatPlaybackTime) ?: UNKNOWN_TIME
        return "${formatPlaybackTime(safePositionMs)} / $duration"
    }

    companion object {
        const val MAX_PROGRESS = 1000
        private const val UNKNOWN_TIME = "--:--"
    }
}

internal fun PlaybackProgress?.remapAfterItemRemoved(index: Int): PlaybackProgress? {
    val progress = this ?: return null
    return when {
        progress.itemIndex == index -> null
        progress.itemIndex > index -> progress.copy(itemIndex = progress.itemIndex - 1)
        else -> progress
    }
}

internal fun seekTargetMs(progress: Int, durationMs: Long?): Long? {
    val duration = durationMs?.takeIf { it > 0L } ?: return null
    val clampedProgress = progress.coerceIn(0, PlaybackProgress.MAX_PROGRESS)
    return (duration.toDouble() * clampedProgress / PlaybackProgress.MAX_PROGRESS)
        .toLong()
        .coerceIn(0L, duration)
}

internal fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
