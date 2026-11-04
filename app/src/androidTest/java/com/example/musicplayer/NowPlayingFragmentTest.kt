package com.example.musicplayer

import android.app.Application
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.data.PlaylistStorage
import com.example.musicplayer.domain.PlaylistManager
import com.example.musicplayer.ui.main.MainActivity
import com.example.musicplayer.ui.nowplaying.NowPlayingFragment
import com.example.musicplayer.ui.playlist.PlaylistFragment
import com.example.musicplayer.ui.playlist.PlaylistViewModel
import com.example.musicplayer.ui.playlist.formatPlaybackTime
import com.example.musicplayer.ui.playlist.seekTargetMs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class NowPlayingFragmentTest {

    private lateinit var application: Application
    private lateinit var storage: SharedPreferences
    private lateinit var tempDir: File
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        storage = PreferenceManager.getDefaultSharedPreferences(application)
        storage.edit().clear().commit()
        tempDir = File(application.cacheDir, "now_playing_fragment_test").apply {
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
    fun opensFromPlaylistControlsPlaybackAndReturnsToList() {
        val uri = Uri.fromFile(createWavFile("now_playing.wav", durationMs = 5000))
        storage.edit()
            .putString("KEY_PLAYLIST", PlaylistStorage.encode(listOf(uri)))
            .commit()

        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched

        lateinit var viewModel: PlaylistViewModel
        val restored = CountDownLatch(1)
        launched.onActivity { activity ->
            viewModel = ViewModelProvider(activity)[PlaylistViewModel::class.java]
            if (viewModel.model.size == 0) {
                viewModel.model.addListener(object : PlaylistManager.Listener {
                    override fun onItemAdded(index: Int) {
                        restored.countDown()
                    }
                })
            } else {
                restored.countDown()
            }
        }

        assertTrue("Playlist was not restored in time", restored.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(1, viewModel.model.size)

        onView(withId(R.id.state)).perform(click())
        awaitCondition { viewModel.playbackProgress()?.isPlaying == true }

        onView(withId(R.id.track)).perform(click())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        lateinit var nowPlaying: NowPlayingFragment
        launched.onActivity { activity ->
            assertTrue(
                "PlaylistFragment should be hidden",
                activity.supportFragmentManager.fragments
                    .filterIsInstance<PlaylistFragment>()
                    .single()
                    .isHidden
            )
            nowPlaying = requireNotNull(
                activity.supportFragmentManager.fragments
                    .filterIsInstance<NowPlayingFragment>()
                    .singleOrNull()
            )
        }

        onView(withId(R.id.now_playing_track)).check(matches(withText(R.string.unknown_track)))
        onView(withId(R.id.now_playing_artist)).check(matches(withText(R.string.unknown_artist)))
        onView(withId(R.id.now_playing_play_pause)).check(matches(isDisplayed()))

        launched.onActivity { activity ->
            val cover = nowPlaying.requireView()
                .findViewById<ImageView>(R.id.now_playing_cover)
            val background = cover.background
            assertTrue(background is ColorDrawable)
            assertEquals(
                ContextCompat.getColor(activity, R.color.preview_placeholder),
                (background as ColorDrawable).color
            )
        }

        awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
        awaitCondition {
            nowPlaying.view
                ?.findViewById<TextView>(R.id.now_playing_elapsed)
                ?.text
                ?.toString() != "00:00"
        }

        onView(withId(R.id.now_playing_play_pause)).perform(click())
        awaitCondition { viewModel.playbackProgress()?.isPlaying == false }

        var pausedTime = ""
        launched.onActivity {
            pausedTime = nowPlaying.requireView()
                .findViewById<TextView>(R.id.now_playing_elapsed)
                .text
                .toString()
        }
        waitForMainThread(1200)
        launched.onActivity {
            assertEquals(
                pausedTime,
                nowPlaying.requireView()
                    .findViewById<TextView>(R.id.now_playing_elapsed)
                    .text
                    .toString()
            )
        }

        onView(withId(R.id.now_playing_play_pause)).perform(click())
        awaitCondition { viewModel.playbackProgress()?.isPlaying == true }

        launched.recreate()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        lateinit var recreatedViewModel: PlaylistViewModel
        launched.onActivity { activity ->
            recreatedViewModel = ViewModelProvider(activity)[PlaylistViewModel::class.java]
            assertTrue(
                activity.supportFragmentManager.fragments
                    .any { it is NowPlayingFragment }
            )
            assertTrue(
                activity.supportFragmentManager.fragments
                    .filterIsInstance<PlaylistFragment>()
                    .single()
                    .isHidden
            )
        }
        assertSame(viewModel, recreatedViewModel)
        launched.onActivity {
            assertNotNull(recreatedViewModel.playbackProgress())
        }
        onView(withId(R.id.now_playing_track)).check(matches(withText(R.string.unknown_track)))

        launched.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        launched.onActivity { activity ->
            assertTrue(
                activity.supportFragmentManager.fragments.none { it is NowPlayingFragment }
            )
            assertFalse(
                activity.supportFragmentManager.fragments
                    .filterIsInstance<PlaylistFragment>()
                    .single()
                    .isHidden
            )
        }
        onView(withId(R.id.playlist_view)).check(matches(isDisplayed()))
        assertEquals(1, viewModel.model.size)
    }

    @Test
    fun seekBarKeepsDraggedPositionAndSeeksOnRelease() {
        val uri = Uri.fromFile(createWavFile("seek.wav", durationMs = 10_000))
        storage.edit()
            .putString("KEY_PLAYLIST", PlaylistStorage.encode(listOf(uri)))
            .commit()

        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched

        lateinit var viewModel: PlaylistViewModel
        val restored = CountDownLatch(1)
        launched.onActivity { activity ->
            viewModel = ViewModelProvider(activity)[PlaylistViewModel::class.java]
            if (viewModel.model.size == 0) {
                viewModel.model.addListener(object : PlaylistManager.Listener {
                    override fun onItemAdded(index: Int) {
                        restored.countDown()
                    }
                })
            } else {
                restored.countDown()
            }
        }
        assertTrue("Playlist was not restored in time", restored.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        onView(withId(R.id.state)).perform(click())
        awaitCondition { viewModel.playbackProgress()?.isPlaying == true }

        onView(withId(R.id.track)).perform(click())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        lateinit var nowPlaying: NowPlayingFragment
        launched.onActivity { activity ->
            nowPlaying = requireNotNull(
                activity.supportFragmentManager.fragments
                    .filterIsInstance<NowPlayingFragment>()
                    .singleOrNull()
            )
        }

        awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
        awaitCondition { (viewModel.playbackProgress()?.positionMs ?: 0L) >= 1_000L }
        onView(withId(R.id.now_playing_play_pause)).perform(click())
        awaitCondition { viewModel.playbackProgress()?.isPlaying == false }

        awaitCondition {
            val progress = viewModel.playbackProgress() ?: return@awaitCondition false
            val bar = nowPlaying.view?.findViewById<SeekBar>(R.id.now_playing_seek)
                ?: return@awaitCondition false
            bar.isEnabled && bar.progress == progress.progress
        }

        var startX = 0f
        var endX = 0f
        var centerY = 0f
        launched.onActivity {
            val bar = nowPlaying.requireView().findViewById<SeekBar>(R.id.now_playing_seek)
            val location = IntArray(2)
            bar.getLocationOnScreen(location)
            centerY = location[1] + bar.height / 2f
            startX = location[0] + bar.width * 0.1f
            endX = location[0] + bar.width * 0.8f
        }

        val downTime = SystemClock.uptimeMillis()
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, startX, centerY)
        sendPointer(downTime, downTime + 50, MotionEvent.ACTION_MOVE, endX, centerY)

        var draggedProgress = 0
        launched.onActivity {
            draggedProgress = nowPlaying.requireView()
                .findViewById<SeekBar>(R.id.now_playing_seek)
                .progress
        }
        assertTrue("SeekBar progress should change during drag", draggedProgress > 0)

        waitForMainThread(1200)
        launched.onActivity {
            assertEquals(
                "Handler must not reset the SeekBar while dragging",
                draggedProgress,
                nowPlaying.requireView()
                    .findViewById<SeekBar>(R.id.now_playing_seek)
                    .progress
            )
        }

        sendPointer(
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            endX,
            centerY
        )

        var expectedTarget = 0L
        launched.onActivity {
            expectedTarget = requireNotNull(
                seekTargetMs(
                    draggedProgress,
                    viewModel.playbackProgress()?.safeDurationMs
                )
            )
        }
        awaitCondition {
            val progress = viewModel.playbackProgress() ?: return@awaitCondition false
            abs(progress.safePositionMs - expectedTarget) <= 500L
        }

        awaitCondition {
            val progress = viewModel.playbackProgress() ?: return@awaitCondition false
            val bar = nowPlaying.view?.findViewById<SeekBar>(R.id.now_playing_seek)
                ?: return@awaitCondition false
            val elapsed = nowPlaying.view
                ?.findViewById<TextView>(R.id.now_playing_elapsed)
                ?.text
                ?.toString()
            bar.progress == progress.progress &&
                elapsed == formatPlaybackTime(progress.safePositionMs)
        }
    }

    private fun awaitCondition(timeoutMs: Long = 5000, condition: () -> Boolean) {
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
                latch.await(timeoutMs + 1000, TimeUnit.MILLISECONDS)
            )
        } finally {
            handler.removeCallbacks(runnable)
        }
    }

    private fun waitForMainThread(delayMs: Long) {
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).postDelayed({ latch.countDown() }, delayMs)
        assertTrue(latch.await(delayMs + 1000, TimeUnit.MILLISECONDS))
    }

    private fun sendPointer(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event)
        } finally {
            event.recycle()
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
