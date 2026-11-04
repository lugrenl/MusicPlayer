package com.example.musicplayer

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
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
