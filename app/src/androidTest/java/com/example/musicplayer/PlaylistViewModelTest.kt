package com.example.musicplayer

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.data.PlaylistStorage
import com.example.musicplayer.domain.PlaylistManager
import com.example.musicplayer.ui.main.MainActivity
import com.example.musicplayer.ui.playlist.PlaybackProgress
import com.example.musicplayer.ui.playlist.PlaylistViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

@RunWith(AndroidJUnit4::class)
class PlaylistViewModelTest {

    private lateinit var application: Application
    private lateinit var storage: SharedPreferences
    private lateinit var tempDir: File
    private var viewModelStore: ViewModelStore? = null

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        storage = PreferenceManager.getDefaultSharedPreferences(application)
        storage.edit().clear().commit()
        tempDir = File(application.cacheDir, "playlist_view_model_test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        viewModelStore?.let { store ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                store.clear()
            }
        }
        viewModelStore = null
        storage.edit().clear().commit()
        tempDir.deleteRecursively()
    }

    @Test
    fun restoreOnInitLoadsSavedPlaylist() {
        val first = Uri.fromFile(createWavFile("first.wav"))
        val second = Uri.fromFile(createWavFile("second.wav"))
        storage.edit()
            .putString("KEY_PLAYLIST", PlaylistStorage.encode(listOf(first, second)))
            .commit()

        val restored = CountDownLatch(2)
        val store = ViewModelStore()
        viewModelStore = store
        lateinit var viewModel: PlaylistViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[PlaylistViewModel::class.java]
            viewModel.model.addListener(object : PlaylistManager.Listener {
                override fun onItemAdded(index: Int) {
                    restored.countDown()
                }
            })
        }

        assertTrue("Playlist was not restored in time", restored.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }

        assertEquals(2, viewModel.model.size)
        assertEquals(first, viewModel.model.itemAt(0).uri)
        assertEquals(second, viewModel.model.itemAt(1).uri)
    }

    @Test
    fun savePersistsRestoredPlaylist() {
        val uri = Uri.fromFile(createWavFile("saved.wav"))
        storage.edit()
            .putString("KEY_PLAYLIST", PlaylistStorage.encode(listOf(uri)))
            .commit()

        val restored = CountDownLatch(1)
        val store = ViewModelStore()
        viewModelStore = store
        lateinit var viewModel: PlaylistViewModel

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[PlaylistViewModel::class.java]
            viewModel.model.addListener(object : PlaylistManager.Listener {
                override fun onItemAdded(index: Int) {
                    restored.countDown()
                }
            })
        }

        assertTrue("Playlist was not restored in time", restored.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
        assertEquals(1, viewModel.model.size)

        storage.edit().clear().commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.save()
        }

        val decoded = PlaylistStorage.decode(storage.getString("KEY_PLAYLIST", null))
        assertTrue(decoded is PlaylistStorage.Decoded.Parsed)
        assertEquals(listOf(uri), (decoded as PlaylistStorage.Decoded.Parsed).uris)
    }

    @Test
    fun viewModelSurvivesActivityRecreation() {
        val uri = Uri.fromFile(createWavFile("recreated.wav"))
        storage.edit()
            .putString("KEY_PLAYLIST", PlaylistStorage.encode(listOf(uri)))
            .commit()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val restored = CountDownLatch(1)
            lateinit var original: PlaylistViewModel

            scenario.onActivity { activity ->
                original = ViewModelProvider(activity)[PlaylistViewModel::class.java]
                if (original.model.size == 0) {
                    original.model.addListener(object : PlaylistManager.Listener {
                        override fun onItemAdded(index: Int) {
                            restored.countDown()
                        }
                    })
                } else {
                    restored.countDown()
                }
            }

            assertTrue("Playlist was not restored in time", restored.await(10, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { }
            assertEquals(1, original.model.size)

            scenario.recreate()

            lateinit var recreated: PlaylistViewModel
            scenario.onActivity { activity ->
                recreated = ViewModelProvider(activity)[PlaylistViewModel::class.java]
            }

            assertSame(original, recreated)
            assertEquals(1, recreated.model.size)
            assertEquals(uri, recreated.model.itemAt(0).uri)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun playbackProgressIsHiddenUntilFirstPlayAndSurvivesRecreation() {
        val uri = Uri.fromFile(createWavFile("progress.wav"))
        storage.edit()
            .putString("KEY_PLAYLIST", PlaylistStorage.encode(listOf(uri)))
            .commit()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val restored = CountDownLatch(1)
            lateinit var original: PlaylistViewModel

            scenario.onActivity { activity ->
                original = ViewModelProvider(activity)[PlaylistViewModel::class.java]
                if (original.model.size == 0) {
                    original.model.addListener(object : PlaylistManager.Listener {
                        override fun onItemAdded(index: Int) {
                            restored.countDown()
                        }
                    })
                } else {
                    restored.countDown()
                }
            }

            assertTrue("Playlist was not restored in time", restored.await(10, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { }
            assertEquals(1, original.model.size)

            scenario.onActivity {
                assertNull(original.playbackProgress())
                original.onPreviewClicked(0)
                val progress = original.playbackProgress()
                assertNotNull(progress)
                assertEquals(0, progress!!.itemIndex)
            }

            scenario.recreate()

            lateinit var recreated: PlaylistViewModel
            scenario.onActivity { activity ->
                recreated = ViewModelProvider(activity)[PlaylistViewModel::class.java]
            }

            assertSame(original, recreated)
            var progressAfterRecreation: PlaybackProgress? = null
            scenario.onActivity {
                progressAfterRecreation = recreated.playbackProgress()
            }
            assertNotNull(progressAfterRecreation)
            assertEquals(0, progressAfterRecreation!!.itemIndex)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun removeTrackBeforeCurrentKeepsPlaybackAndPosition() {
        val (scenario, viewModel) = launchWithTracks(10_000, 10_000, 10_000)
        try {
            scenario.onActivity { viewModel.onPreviewClicked(1) }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            awaitCondition { (viewModel.playbackProgress()?.positionMs ?: 0L) >= 1_000L }

            lateinit var currentUri: Uri
            var positionBefore = 0L
            scenario.onActivity {
                currentUri = viewModel.model.itemAt(1).uri
                positionBefore = viewModel.playbackProgress()!!.positionMs
                viewModel.removeAt(0)
            }

            awaitCondition {
                viewModel.model.size == 2 &&
                    viewModel.playbackProgressForScreen()?.itemIndex == 0
            }
            scenario.onActivity {
                assertEquals(currentUri, viewModel.model.itemAt(0).uri)
                assertEquals(true, viewModel.playbackProgress()?.isPlaying)
                assertTrue(viewModel.playbackProgress()!!.positionMs >= positionBefore)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun removeTrackAfterCurrentDoesNotAffectPlayback() {
        val (scenario, viewModel) = launchWithTracks(10_000, 10_000, 10_000)
        try {
            scenario.onActivity { viewModel.onPreviewClicked(0) }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            awaitCondition { (viewModel.playbackProgress()?.positionMs ?: 0L) >= 1_000L }

            lateinit var currentUri: Uri
            var positionBefore = 0L
            scenario.onActivity {
                currentUri = viewModel.model.itemAt(0).uri
                positionBefore = viewModel.playbackProgress()!!.positionMs
                viewModel.removeAt(2)
            }

            awaitCondition { viewModel.model.size == 2 }
            scenario.onActivity {
                assertEquals(currentUri, viewModel.model.itemAt(0).uri)
                assertEquals(0, viewModel.playbackProgressForScreen()?.itemIndex)
                assertEquals(true, viewModel.playbackProgress()?.isPlaying)
                assertTrue(viewModel.playbackProgress()!!.positionMs >= positionBefore)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun removeCurrentWhilePlayingStopsAndDoesNotStartNeighbor() {
        val (scenario, viewModel) = launchWithTracks(10_000, 10_000, 10_000)
        try {
            scenario.onActivity { viewModel.onPreviewClicked(1) }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            awaitCondition { (viewModel.playbackProgress()?.positionMs ?: 0L) >= 500L }

            scenario.onActivity { viewModel.removeAt(1) }
            awaitCondition { viewModel.playbackProgressForScreen()?.isPlaying == false }
            waitForMainThread(700)

            scenario.onActivity {
                assertEquals(2, viewModel.model.size)
                assertEquals(false, viewModel.playbackProgressForScreen()?.isPlaying)
                assertEquals(false, viewModel.model.itemAt(0).state == PlaylistManager.Item.State.PLAYING)
                assertEquals(false, viewModel.model.itemAt(1).state == PlaylistManager.Item.State.PLAYING)
                assertNull(viewModel.playbackProgress())
                val currentIndex = requireNotNull(viewModel.playbackProgressForScreen()?.itemIndex)
                assertEquals(
                    PlaylistManager.Item.State.ACTIVE,
                    viewModel.model.itemAt(currentIndex).state
                )
            }

            scenario.onActivity { viewModel.onPreviewClicked(0) }
            awaitCondition {
                val progress = viewModel.playbackProgress() ?: return@awaitCondition false
                progress.itemIndex == 0 && progress.isPlaying
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun removeCurrentWhilePausedDoesNotStartNeighbor() {
        val (scenario, viewModel) = launchWithTracks(10_000, 10_000)
        try {
            scenario.onActivity { viewModel.onPreviewClicked(0) }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
            awaitCondition { (viewModel.playbackProgress()?.positionMs ?: 0L) >= 500L }

            scenario.onActivity { viewModel.onPreviewClicked(0) }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == false }

            scenario.onActivity { viewModel.removeAt(0) }
            waitForMainThread(700)

            scenario.onActivity {
                assertEquals(1, viewModel.model.size)
                assertEquals(false, viewModel.playbackProgressForScreen()?.isPlaying)
                assertEquals(false, viewModel.model.itemAt(0).state == PlaylistManager.Item.State.PLAYING)
            }

            scenario.onActivity { viewModel.onPreviewClicked(0) }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun removeCurrentAfterEndDoesNotStartNeighbor() {
        val (scenario, viewModel) = launchWithTracks(1_200, 2_500)
        try {
            scenario.onActivity { viewModel.onPreviewClicked(1) }
            awaitEnded(viewModel, index = 1)

            scenario.onActivity { viewModel.removeAt(1) }
            waitForMainThread(700)

            scenario.onActivity {
                assertEquals(1, viewModel.model.size)
                assertEquals(false, viewModel.playbackProgressForScreen()?.isPlaying)
                assertEquals(false, viewModel.model.itemAt(0).state == PlaylistManager.Item.State.PLAYING)
            }

            scenario.onActivity { viewModel.onPreviewClicked(0) }
            awaitCondition { viewModel.playbackProgress()?.isPlaying == true }
        } finally {
            scenario.close()
        }
    }

    private fun launchWithTracks(
        vararg durationsMs: Int
    ): Pair<ActivityScenario<MainActivity>, PlaylistViewModel> {
        val uris = durationsMs.mapIndexed { index, durationMs ->
            Uri.fromFile(createWavFile("view_model_track_$index.wav", durationMs))
        }
        storage.edit()
            .putString("KEY_PLAYLIST", PlaylistStorage.encode(uris))
            .commit()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var viewModel: PlaylistViewModel
        val restored = CountDownLatch(uris.size)
        scenario.onActivity { activity ->
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
        return scenario to viewModel
    }

    private fun awaitEnded(viewModel: PlaylistViewModel, index: Int) {
        awaitCondition(timeoutMs = 6_000) {
            val progress = viewModel.playbackProgressForScreen()
                ?: return@awaitCondition false
            progress.itemIndex == index &&
                !progress.isPlaying &&
                viewModel.isPlaybackEnded()
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

    private fun createWavFile(name: String, durationMs: Int = 1000): File {
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
