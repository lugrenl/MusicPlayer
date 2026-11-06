package com.example.musicplayer

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.domain.PlaylistManager
import com.example.musicplayer.ui.main.MainActivity
import com.example.musicplayer.ui.playlist.PlaybackProgress
import com.example.musicplayer.ui.playlist.PlaylistAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistProgressUiTest {

    @Test
    fun progressAppearsOnlyOnCurrentTrackAndMovesBetweenRows() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            lateinit var recyclerView: RecyclerView
            lateinit var adapter: PlaylistAdapter
            lateinit var model: FakePlaylistManager

            scenario.onActivity { activity ->
                model = FakePlaylistManager(
                    listOf(FakeItem(0), FakeItem(1), FakeItem(2))
                )
                adapter = PlaylistAdapter(
                    model,
                    object : PlaylistAdapter.Actions {
                        override fun onPreviewClicked(index: Int) = Unit
                        override fun onTrackClicked(index: Int) = Unit
                        override fun onRemoveClicked(index: Int) = Unit
                    }
                )
                recyclerView = RecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity)
                    itemAnimator = null
                    this.adapter = adapter
                }
                activity.findViewById<ViewGroup>(android.R.id.content).addView(
                    recyclerView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1200
                    )
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertProgressVisibility(recyclerView, visiblePosition = null)
            }

            scenario.onActivity {
                adapter.updateProgress(PlaybackProgress(1, 6_000L, 30_000L, true))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertProgressVisibility(recyclerView, visiblePosition = 1)
                val row = row(recyclerView, 1)
                assertEquals(
                    "00:06 / 00:30",
                    row.findViewById<TextView>(R.id.progress_time).text.toString()
                )
                assertEquals(
                    200,
                    row.findViewById<ProgressBar>(R.id.progress).progress
                )
            }

            scenario.onActivity {
                adapter.updateProgress(PlaybackProgress(2, 15_000L, 30_000L, true))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertProgressVisibility(recyclerView, visiblePosition = 2)
            }

            scenario.onActivity {
                model.removeAt(0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertProgressVisibility(recyclerView, visiblePosition = 1)
                assertEquals(
                    "00:15 / 00:30",
                    row(recyclerView, 1).findViewById<TextView>(R.id.progress_time).text.toString()
                )
            }

            scenario.onActivity {
                adapter.updateProgress(null)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertProgressVisibility(recyclerView, visiblePosition = null)
            }
        } finally {
            scenario.close()
        }
    }

    private fun assertProgressVisibility(
        recyclerView: RecyclerView,
        visiblePosition: Int?
    ) {
        for (position in 0 until recyclerView.adapter!!.itemCount) {
            val row = row(recyclerView, position)
            val expected = if (position == visiblePosition) View.VISIBLE else View.GONE
            assertEquals(expected, row.findViewById<View>(R.id.progress).visibility)
            assertEquals(expected, row.findViewById<View>(R.id.progress_time).visibility)
        }
    }

    private fun row(recyclerView: RecyclerView, position: Int): View {
        val holder = requireNotNull(recyclerView.findViewHolderForAdapterPosition(position)) {
            "No ViewHolder for position $position"
        }
        return holder.itemView
    }

    private class FakePlaylistManager(
        items: List<PlaylistManager.Item>
    ) : PlaylistManager {

        private val items = items.toMutableList()
        private val listeners = mutableSetOf<PlaylistManager.Listener>()

        override val size: Int get() = items.size

        override fun addListener(listener: PlaylistManager.Listener) {
            listeners.add(listener)
        }

        override fun removeListener(listener: PlaylistManager.Listener) {
            listeners.remove(listener)
        }

        override suspend fun addFiles(files: Array<Uri>) = Unit

        override fun itemAt(index: Int): PlaylistManager.Item = items[index]

        override fun removeAt(index: Int) {
            if (index !in items.indices) return
            items.removeAt(index)
            listeners.forEach { it.onItemRemoved(index) }
        }

        override fun setActive(uri: Uri?) = Unit

        override fun setPlayingState(isPlaying: Boolean) = Unit

        override fun save(storage: SharedPreferences) = Unit

        override suspend fun restore(storage: SharedPreferences) = Unit

        override fun release() = Unit
    }

    private class FakeItem(index: Int) : PlaylistManager.Item {
        override val artist: String = "Artist $index"
        override val track: String = "Track $index"
        override val duration: Long = 30_000L
        override val preview: Bitmap? = null
        override val uri: Uri = Uri.EMPTY
        override val state: PlaylistManager.Item.State = PlaylistManager.Item.State.NONE
    }
}
