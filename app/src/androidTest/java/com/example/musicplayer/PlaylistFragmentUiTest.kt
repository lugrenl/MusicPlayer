package com.example.musicplayer

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.domain.PlaylistManager
import com.example.musicplayer.ui.main.MainActivity
import com.example.musicplayer.ui.playlist.PlaylistViewModel
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
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
class PlaylistFragmentUiTest {

    private lateinit var application: Application
    private lateinit var storage: SharedPreferences
    private lateinit var tempDir: File
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        storage = PreferenceManager.getDefaultSharedPreferences(application)
        storage.edit().clear().commit()
        tempDir = File(application.cacheDir, "playlist_fragment_ui_test").apply {
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
    fun emptyStateTogglesAfterAddingTrack() {
        val uri = Uri.fromFile(createWavFile("ui_track.wav"))
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        scenario = activityScenario

        try {
            lateinit var viewModel: PlaylistViewModel
            activityScenario.onActivity { activity ->
                viewModel = ViewModelProvider(activity)[PlaylistViewModel::class.java]
            }

            onView(withId(R.id.image_empty_playlist)).check(matches(isDisplayed()))
            onView(withId(R.id.text_empty_playlist)).check(matches(isDisplayed()))
            onView(withId(R.id.playlist_view)).check(matches(not(isDisplayed())))

            val added = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.model.addListener(object : PlaylistManager.Listener {
                    override fun onItemAdded(index: Int) {
                        added.countDown()
                    }
                })
                viewModel.addFiles(arrayOf(uri))
            }

            assertTrue("Track was not added in time", added.await(10, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { }

            onView(withId(R.id.image_empty_playlist)).check(matches(not(isDisplayed())))
            onView(withId(R.id.text_empty_playlist)).check(matches(not(isDisplayed())))
            onView(withId(R.id.playlist_view)).check(matches(isDisplayed()))
            onView(withId(R.id.playlist_view)).check { view, noViewFoundException ->
                if (noViewFoundException != null) throw noViewFoundException
                val recyclerView = requireNotNull(view) as RecyclerView
                assertEquals(1, recyclerView.adapter?.itemCount ?: -1)
            }
            onView(allOf(withId(R.id.track), withText(R.string.unknown_track)))
                .check(matches(isDisplayed()))
            onView(allOf(withId(R.id.artist), withText(R.string.unknown_artist)))
                .check(matches(isDisplayed()))
        } finally {
            activityScenario.close()
            scenario = null
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
