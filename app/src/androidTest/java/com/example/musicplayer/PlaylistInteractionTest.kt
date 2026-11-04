package com.example.musicplayer

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.data.PlaylistStorage
import com.example.musicplayer.domain.PlaylistManager
import com.example.musicplayer.ui.main.MainActivity
import com.example.musicplayer.ui.nowplaying.NowPlayingFragment
import com.example.musicplayer.ui.playlist.PlaylistViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PlaylistInteractionTest {

    private lateinit var application: Application
    private lateinit var storage: SharedPreferences
    private lateinit var tempDir: File
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        storage = PreferenceManager.getDefaultSharedPreferences(application)
        storage.edit().clear().commit()
        tempDir = File(application.cacheDir, "playlist_interaction_test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        storage.edit().clear().commit()
        tempDir.deleteRecursively()
    }

    @Test
    fun playPauseButtonDoesNotOpenNowPlaying() {
        val (launched, viewModel) = launchWithTracks(5_000)
        try {
            launched.onActivity { activity ->
                playPause(activity, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            launched.onActivity { activity ->
                assertFalse(hasNowPlaying(activity))
            }

            launched.onActivity { activity ->
                playPause(activity, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitCondition { viewModel.playbackProgress()?.isPlaying == false }
            launched.onActivity { activity ->
                assertFalse(hasNowPlaying(activity))
            }
        } finally {
            launched.close()
        }
    }

    @Test
    fun rowClickPreservesPlayingAndPausedState() {
        val (launched, viewModel) = launchWithTracks(10_000)
        try {
            launched.onActivity { activity ->
                playPause(activity, 0)
            }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            awaitCondition { (viewModel.playbackProgress()?.positionMs ?: 0L) >= 1_000L }

            var positionBefore = 0L
            launched.onActivity { activity ->
                positionBefore = viewModel.playbackProgress()?.positionMs ?: 0L
                clickRow(activity, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            launched.onActivity { activity ->
                assertTrue(hasNowPlaying(activity))
            }

            var positionAfterPlaying = 0L
            launched.onActivity {
                assertEquals(true, viewModel.playbackProgress()?.isPlaying)
                positionAfterPlaying = viewModel.playbackProgress()?.positionMs ?: 0L
            }
            assertTrue(positionAfterPlaying >= positionBefore)

            launched.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            launched.onActivity { activity ->
                playPause(activity, 0)
            }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == false }

            var pausedPosition = 0L
            launched.onActivity { activity ->
                pausedPosition = viewModel.playbackProgress()?.positionMs ?: 0L
                clickRow(activity, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            launched.onActivity { activity ->
                assertTrue(hasNowPlaying(activity))
                assertEquals(false, viewModel.playbackProgress()?.isPlaying)
                val pausedPositionAfter = viewModel.playbackProgress()?.positionMs ?: -1L
                assertTrue(abs(pausedPositionAfter - pausedPosition) <= 500L)
            }
        } finally {
            launched.close()
        }
    }

    @Test
    fun titleArtistAndFreeAreaOpenNowPlaying() {
        val (launched, viewModel) = launchWithTracks(5_000)
        try {
            launched.onActivity { activity ->
                playPause(activity, 0)
            }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            launched.onActivity { activity ->
                playPause(activity, 0)
            }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == false }

            onView(withId(R.id.track)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            launched.onActivity { activity ->
                assertTrue(hasNowPlaying(activity))
                assertEquals(false, viewModel.playbackProgress()?.isPlaying)
            }
            launched.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(R.id.artist)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            launched.onActivity { activity ->
                assertTrue(hasNowPlaying(activity))
                assertEquals(false, viewModel.playbackProgress()?.isPlaying)
            }
            launched.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            launched.onActivity { activity ->
                clickRow(activity, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            launched.onActivity { activity ->
                assertTrue(hasNowPlaying(activity))
                assertEquals(false, viewModel.playbackProgress()?.isPlaying)
            }
        } finally {
            launched.close()
        }
    }

    @Test
    fun rowClickOnDifferentTrackStartsIt() {
        val (launched, viewModel) = launchWithTracks(5_000, 5_000)
        try {
            launched.onActivity { activity ->
                playPause(activity, 0)
            }
            awaitCondition { viewModel.playbackProgress()?.itemIndex == 0 }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }

            launched.onActivity { activity ->
                clickRow(activity, 1)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            launched.onActivity { activity ->
                assertTrue(hasNowPlaying(activity))
            }
            awaitCondition { viewModel.playbackProgress()?.itemIndex == 1 }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            launched.onActivity {
                assertEquals(1, viewModel.playbackProgress()?.itemIndex)
            }
        } finally {
            launched.close()
        }
    }

    @Test
    fun rowClickOnAutoSelectedPausedTrackOpensWithoutStarting() {
        val (launched, viewModel) = launchWithTracks(5_000)
        try {
            launched.onActivity {
                assertNull(viewModel.playbackProgress())
            }

            launched.onActivity { activity ->
                clickRow(activity, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            launched.onActivity { activity ->
                assertTrue(hasNowPlaying(activity))
                assertNull(viewModel.playbackProgress())
                assertEquals(false, viewModel.playbackProgressForScreen()?.isPlaying)
            }

            onView(withId(R.id.now_playing_play_pause)).perform(click())
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
        } finally {
            launched.close()
        }
    }

    @Test
    fun endedLastTrackRestartsFromBeginningInList() {
        val (launched, viewModel) = launchWithTracks(1_200, 2_500)
        try {
            launched.onActivity { activity ->
                playPause(activity, 1)
            }
            awaitEnded(viewModel, index = 1)

            launched.onActivity { activity ->
                playPause(activity, 1)
            }
            awaitCondition {
                val progress = viewModel.playbackProgress() ?: return@awaitCondition false
                progress.itemIndex == 1 &&
                    progress.isPlaying &&
                    progress.safePositionMs < 1_000L
            }
            awaitCondition {
                (viewModel.playbackProgressForScreen()?.safePositionMs ?: 0L) >= 1_000L
            }

            awaitEnded(viewModel, index = 1)
            waitForMainThread(700)
            launched.onActivity {
                val progress = viewModel.playbackProgressForScreen()
                assertEquals(1, progress?.itemIndex)
                assertEquals(false, progress?.isPlaying)
            }

            launched.onActivity { activity ->
                playPause(activity, 0)
            }
            awaitCondition {
                val progress = viewModel.playbackProgress() ?: return@awaitCondition false
                progress.itemIndex == 0 && progress.isPlaying
            }
        } finally {
            launched.close()
        }
    }

    @Test
    fun endedLastTrackRestartsFromNowPlayingAndPauseResumeKeepsPosition() {
        val (launched, viewModel) = launchWithTracks(1_200, 2_500)
        try {
            launched.onActivity { activity ->
                playPause(activity, 1)
            }
            awaitEnded(viewModel, index = 1)

            launched.onActivity { activity ->
                clickRow(activity, 1)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            launched.onActivity { activity ->
                assertTrue(hasNowPlaying(activity))
            }

            onView(withId(R.id.now_playing_play_pause)).perform(click())
            awaitCondition {
                val progress = viewModel.playbackProgress() ?: return@awaitCondition false
                progress.itemIndex == 1 &&
                    progress.isPlaying &&
                    progress.safePositionMs < 1_000L
            }
            awaitCondition {
                (viewModel.playbackProgressForScreen()?.safePositionMs ?: 0L) >= 600L
            }

            onView(withId(R.id.now_playing_play_pause)).perform(click())
            awaitCondition { viewModel.playbackProgress()?.isPlaying == false }

            var pausedPosition = 0L
            launched.onActivity {
                pausedPosition = viewModel.playbackProgressForScreen()?.safePositionMs ?: 0L
            }

            onView(withId(R.id.now_playing_play_pause)).perform(click())
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            awaitCondition {
                val progress = viewModel.playbackProgressForScreen()
                    ?: return@awaitCondition false
                progress.safePositionMs >= pausedPosition - 300L
            }

            awaitEnded(viewModel, index = 1)
            waitForMainThread(700)
            launched.onActivity {
                val progress = viewModel.playbackProgressForScreen()
                assertEquals(1, progress?.itemIndex)
                assertEquals(false, progress?.isPlaying)
            }
        } finally {
            launched.close()
        }
    }

    @Test
    fun autoTransitionUpdatesNowPlayingAndStopsAfterLastTrack() {
        val (launched, viewModel) = launchWithTracks(1_500, 3_000)
        try {
            launched.onActivity { activity ->
                playPause(activity, 0)
            }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            launched.onActivity { activity ->
                clickRow(activity, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            lateinit var nowPlaying: NowPlayingFragment
            launched.onActivity { activity ->
                nowPlaying = requireNotNull(nowPlayingFragment(activity))
            }
            awaitCondition { viewModel.playbackProgress()?.itemIndex == 0 }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            awaitCondition { viewModel.playbackProgress()?.itemIndex == 1 }

            launched.onActivity { activity ->
                assertSame(nowPlaying, nowPlayingFragment(activity))
            }
            awaitCondition {
                val progress = viewModel.playbackProgress() ?: return@awaitCondition false
                val total = nowPlaying.view
                    ?.findViewById<TextView>(R.id.now_playing_total)
                    ?.text
                    ?.toString()
                total == "00:03" && progress.itemIndex == 1
            }
            awaitCondition {
                val progress = viewModel.playbackProgress() ?: return@awaitCondition false
                val bar = nowPlaying.view?.findViewById<SeekBar>(R.id.now_playing_seek)
                    ?: return@awaitCondition false
                bar.progress == progress.progress
            }

            awaitCondition(timeoutMs = 6_000) {
                viewModel.playbackProgress()?.isPlaying == false
            }
            launched.onActivity {
                assertEquals(1, viewModel.playbackProgress()?.itemIndex)
            }
            launched.onActivity { activity ->
                assertSame(nowPlaying, nowPlayingFragment(activity))
            }
        } finally {
            launched.close()
        }
    }

    private fun launchWithTracks(
        vararg durationsMs: Int
    ): Pair<ActivityScenario<MainActivity>, PlaylistViewModel> {
        val uris = durationsMs.mapIndexed { index, durationMs ->
            Uri.fromFile(createWavFile("track_$index.wav", durationMs))
        }
        storage.edit()
            .putString("KEY_PLAYLIST", PlaylistStorage.encode(uris))
            .commit()

        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched
        lateinit var viewModel: PlaylistViewModel
        val restored = CountDownLatch(uris.size)
        launched.onActivity { activity ->
            viewModel = ViewModelProvider(activity)[PlaylistViewModel::class.java]
            if (viewModel.model.size == 0) {
                viewModel.model.addListener(object : PlaylistManager.Listener {
                    override fun onItemAdded(index: Int) {
                        restored.countDown()
                    }
                })
            } else {
                repeat(uris.size) { restored.countDown() }
            }
        }
        assertTrue("Playlist was not restored in time", restored.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(uris.size, viewModel.model.size)
        return launched to viewModel
    }

    private fun playPause(activity: MainActivity, position: Int) {
        row(activity, position)
            .findViewById<ImageView>(R.id.state)
            .performClick()
    }

    private fun clickRow(activity: MainActivity, position: Int) {
        row(activity, position).performClick()
    }

    private fun row(activity: MainActivity, position: Int): View {
        val recyclerView = activity.findViewById<RecyclerView>(R.id.playlist_view)
        return requireNotNull(recyclerView.findViewHolderForAdapterPosition(position)) {
            "No ViewHolder for position $position"
        }.itemView
    }

    private fun nowPlayingFragment(activity: MainActivity): NowPlayingFragment? {
        return activity.supportFragmentManager.fragments
            .filterIsInstance<NowPlayingFragment>()
            .singleOrNull()
    }

    private fun hasNowPlaying(activity: MainActivity): Boolean {
        return nowPlayingFragment(activity) != null
    }

    private fun awaitEnded(viewModel: PlaylistViewModel, index: Int) {
        awaitCondition(timeoutMs = 6_000) {
            val progress = viewModel.playbackProgressForScreen()
                ?: return@awaitCondition false
            val duration = progress.safeDurationMs ?: return@awaitCondition false
            progress.itemIndex == index &&
                !progress.isPlaying &&
                progress.safePositionMs >= duration - 300L
        }
    }

    private fun waitForMainThread(delayMs: Long) {
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).postDelayed({ latch.countDown() }, delayMs)
        assertTrue(latch.await(delayMs + 1_000, TimeUnit.MILLISECONDS))
    }

    private fun awaitCondition(
        timeoutMs: Long = 5_000,
        condition: () -> Boolean
    ) {
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        val runnable = object : Runnable {
            override fun run() {
                if (condition()) {
                    latch.countDown()
                    return
                }
                if (SystemClock.uptimeMillis() < deadline) {
                    handler.postDelayed(this, 50)
                }
            }
        }

        handler.post(runnable)
        try {
            assertTrue(
                "Condition was not met within $timeoutMs ms",
                latch.await(timeoutMs + 1_000, TimeUnit.MILLISECONDS)
            )
        } finally {
            handler.removeCallbacks(runnable)
        }
    }

    private fun createWavFile(name: String, durationMs: Int): File {
        val sampleRate = 8000
        val channels = 1
        val bitsPerSample = 16
        val sampleCount = sampleRate * durationMs / 1000
        val dataSize = sampleCount * channels * bitsPerSample / 8
        val file = File(tempDir, name)

        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { output ->
            output.writeBytes("RIFF")
            output.writeIntLe(36 + dataSize)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeIntLe(16)
            output.writeShortLe(1)
            output.writeShortLe(channels)
            output.writeIntLe(sampleRate)
            output.writeIntLe(sampleRate * channels * bitsPerSample / 8)
            output.writeShortLe(channels * bitsPerSample / 8)
            output.writeShortLe(bitsPerSample)
            output.writeBytes("data")
            output.writeIntLe(dataSize)
            output.write(ByteArray(dataSize))
        }

        return file
    }

    private fun DataOutputStream.writeIntLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }
}
