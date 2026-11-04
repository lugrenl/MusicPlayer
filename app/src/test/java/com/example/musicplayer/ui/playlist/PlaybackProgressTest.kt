package com.example.musicplayer.ui.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackProgressTest {

    @Test
    fun formatsElapsedAndTotalTime() {
        val progress = PlaybackProgress(
            itemIndex = 0,
            positionMs = 12_000L,
            durationMs = 30_000L,
            isPlaying = true
        )

        assertEquals("00:12 / 00:30", progress.formattedTime())
    }

    @Test
    fun formatsZeroAndUnknownDurationWithoutFabricatingTotal() {
        val zero = PlaybackProgress(0, 0L, 0L, false)
        val unknown = PlaybackProgress(0, 5_000L, null, false)

        assertEquals("00:00 / --:--", zero.formattedTime())
        assertEquals("00:05 / --:--", unknown.formattedTime())
        assertEquals(0, zero.progress)
        assertEquals(0, unknown.progress)
    }

    @Test
    fun progressIsSafeAndClamped() {
        val beforeStart = PlaybackProgress(0, -500L, 30_000L, false)
        val halfway = PlaybackProgress(0, 15_000L, 30_000L, true)
        val pastEnd = PlaybackProgress(0, 45_000L, 30_000L, true)

        assertEquals(0, beforeStart.progress)
        assertEquals(0, beforeStart.safePositionMs)
        assertEquals(500, halfway.progress)
        assertEquals(PlaybackProgress.MAX_PROGRESS, pastEnd.progress)
        assertEquals(30_000L, pastEnd.safePositionMs)
        assertEquals("00:30 / 00:30", pastEnd.formattedTime())
    }

    @Test
    fun formatsLongDurations() {
        assertEquals("125:07", formatPlaybackTime(7_507_000L))
    }

    @Test
    fun seekTargetIsBoundedAndRejectsUnknownDuration() {
        assertEquals(0L, seekTargetMs(0, 30_000L))
        assertEquals(15_000L, seekTargetMs(500, 30_000L))
        assertEquals(30_000L, seekTargetMs(1_000, 30_000L))
        assertEquals(0L, seekTargetMs(-100, 30_000L))
        assertEquals(30_000L, seekTargetMs(1_500, 30_000L))
        assertNull(seekTargetMs(500, null))
        assertNull(seekTargetMs(500, 0L))
        assertNull(seekTargetMs(500, -1L))
    }
}
