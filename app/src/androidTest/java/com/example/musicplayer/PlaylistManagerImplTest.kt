package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.data.PlaylistManagerImpl
import com.example.musicplayer.data.PlaylistStorage
import com.example.musicplayer.domain.PlaylistManager
import kotlinx.coroutines.runBlocking
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

@RunWith(AndroidJUnit4::class)
class PlaylistManagerImplTest {

    private lateinit var context: Context
    private lateinit var storage: SharedPreferences
    private lateinit var tempDir: File
    private lateinit var manager: PlaylistManagerImpl

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = context.getSharedPreferences("playlist_manager_test", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        tempDir = File(context.cacheDir, "playlist_manager_test").apply {
            deleteRecursively()
            mkdirs()
        }
        manager = PlaylistManagerImpl(context)
    }

    @After
    fun tearDown() {
        manager.release()
        storage.edit().clear().commit()
        tempDir.deleteRecursively()
    }

    @Test
    fun addFilesAddsValidTrackAndPersistsUri() {
        runBlocking {
            val uri = Uri.fromFile(createWavFile("valid.wav"))
            val added = mutableListOf<Int>()
            manager.addListener(object : PlaylistManager.Listener {
                override fun onItemAdded(index: Int) {
                    added.add(index)
                }
            })
            manager.save(storage)

            manager.addFiles(arrayOf(uri))

            assertEquals(1, manager.size)
            assertEquals(uri, manager.itemAt(0).uri)
            assertTrue(manager.itemAt(0).duration > 0)
            assertEquals(listOf(0), added)
            assertSavedUris(uri)
        }
    }

    @Test
    fun addFilesIgnoresDuplicateUri() {
        runBlocking {
            val uri = Uri.fromFile(createWavFile("duplicate.wav"))
            val added = mutableListOf<Int>()
            manager.addListener(object : PlaylistManager.Listener {
                override fun onItemAdded(index: Int) {
                    added.add(index)
                }
            })
            manager.save(storage)

            manager.addFiles(arrayOf(uri))
            manager.addFiles(arrayOf(uri))

            assertEquals(1, manager.size)
            assertEquals(listOf(0), added)
            assertSavedUris(uri)
        }
    }

    @Test
    fun addFilesSkipsUnreadableFileAndKeepsValidOne() {
        runBlocking {
            val valid = Uri.fromFile(createWavFile("valid.wav"))
            val broken = Uri.fromFile(File(tempDir, "broken.wav").apply {
                writeText("not an audio file")
            })
            val missing = Uri.fromFile(File(tempDir, "missing.wav"))
            val added = mutableListOf<Int>()
            manager.addListener(object : PlaylistManager.Listener {
                override fun onItemAdded(index: Int) {
                    added.add(index)
                }
            })
            manager.save(storage)

            manager.addFiles(arrayOf(broken, valid, missing))

            assertEquals(1, manager.size)
            assertEquals(valid, manager.itemAt(0).uri)
            assertEquals(listOf(0), added)
            assertSavedUris(valid)
        }
    }

    @Test
    fun restoreLoadsSavedPlaylistInOrder() {
        runBlocking {
            val first = Uri.fromFile(createWavFile("first.wav"))
            val second = Uri.fromFile(createWavFile("second.wav"))
            storage.edit()
                .putString("KEY_PLAYLIST", PlaylistStorage.encode(listOf(first, second)))
                .commit()
            val added = mutableListOf<Int>()
            manager.addListener(object : PlaylistManager.Listener {
                override fun onItemAdded(index: Int) {
                    added.add(index)
                }
            })

            manager.restore(storage)

            assertEquals(2, manager.size)
            assertEquals(first, manager.itemAt(0).uri)
            assertEquals(second, manager.itemAt(1).uri)
            assertEquals(listOf(0, 1), added)
        }
    }

    @Test
    fun restoreSkipsUnavailableUriAndKeepsStoredDataConsistent() {
        runBlocking {
            val valid = Uri.fromFile(createWavFile("valid.wav"))
            val missing = Uri.fromFile(File(tempDir, "missing.wav"))
            storage.edit()
                .putString("KEY_PLAYLIST", PlaylistStorage.encode(listOf(missing, valid)))
                .commit()

            manager.restore(storage)

            assertEquals(1, manager.size)
            assertEquals(valid, manager.itemAt(0).uri)
            assertSavedUris(valid)
        }
    }

    @Test
    fun restoreKeepsStoredDataWhenNothingCanBeRestored() {
        runBlocking {
            val missing = Uri.fromFile(File(tempDir, "missing.wav"))
            val stored = PlaylistStorage.encode(listOf(missing))
            storage.edit().putString("KEY_PLAYLIST", stored).commit()

            manager.restore(storage)

            assertEquals(0, manager.size)
            assertEquals(stored, storage.getString("KEY_PLAYLIST", null))
        }
    }

    @Test
    fun restoreKeepsInvalidStoredDataUntouched() {
        runBlocking {
            storage.edit().putString("KEY_PLAYLIST", "{broken").commit()

            manager.restore(storage)

            assertEquals(0, manager.size)
            assertEquals("{broken", storage.getString("KEY_PLAYLIST", null))
        }
    }

    @Test
    fun activeAndPlayingStateChangeItemState() {
        runBlocking {
            val uri = Uri.fromFile(createWavFile("state.wav"))
            manager.save(storage)
            manager.addFiles(arrayOf(uri))

            val changed = mutableListOf<Int>()
            manager.addListener(object : PlaylistManager.Listener {
                override fun onItemChanged(index: Int) {
                    changed.add(index)
                }
            })

            manager.setActive(uri)
            assertEquals(PlaylistManager.Item.State.ACTIVE, manager.itemAt(0).state)

            manager.setPlayingState(true)
            assertEquals(PlaylistManager.Item.State.PLAYING, manager.itemAt(0).state)

            manager.setPlayingState(false)
            assertEquals(PlaylistManager.Item.State.ACTIVE, manager.itemAt(0).state)

            manager.setActive(null)
            assertEquals(PlaylistManager.Item.State.NONE, manager.itemAt(0).state)
            assertEquals(listOf(0, 0, 0, 0), changed)
        }
    }

    @Test
    fun removeAtKeepsRemainingOrderPersistsAndNotifiesRemovedIndex() {
        runBlocking {
            val firstFile = createWavFile("remove_first.wav")
            val secondFile = createWavFile("remove_second.wav")
            val thirdFile = createWavFile("remove_third.wav")
            val first = Uri.fromFile(firstFile)
            val second = Uri.fromFile(secondFile)
            val third = Uri.fromFile(thirdFile)
            manager.save(storage)
            manager.addFiles(arrayOf(first, second, third))

            val removed = mutableListOf<Int>()
            manager.addListener(object : PlaylistManager.Listener {
                override fun onItemRemoved(index: Int) {
                    removed.add(index)
                }
            })

            manager.removeAt(1)

            assertEquals(2, manager.size)
            assertEquals(first, manager.itemAt(0).uri)
            assertEquals(third, manager.itemAt(1).uri)
            assertEquals(listOf(1), removed)
            assertSavedUris(first, third)
            assertTrue("Removed source file must stay on disk", secondFile.exists())
            assertTrue("Other source files must stay on disk", firstFile.exists() && thirdFile.exists())
        }
    }

    @Test
    fun removeActiveItemClearsActiveAndPlayingState() {
        runBlocking {
            val first = Uri.fromFile(createWavFile("remove_active_first.wav"))
            val second = Uri.fromFile(createWavFile("remove_active_second.wav"))
            manager.save(storage)
            manager.addFiles(arrayOf(first, second))
            manager.setActive(first)
            manager.setPlayingState(true)

            assertEquals(PlaylistManager.Item.State.PLAYING, manager.itemAt(0).state)

            manager.removeAt(0)

            assertEquals(1, manager.size)
            assertEquals(second, manager.itemAt(0).uri)
            assertEquals(PlaylistManager.Item.State.NONE, manager.itemAt(0).state)

            manager.setPlayingState(true)
            assertEquals(PlaylistManager.Item.State.NONE, manager.itemAt(0).state)
        }
    }

    @Test
    fun removeLastItemPersistsEmptyPlaylist() {
        runBlocking {
            val uri = Uri.fromFile(createWavFile("remove_only.wav"))
            manager.save(storage)
            manager.addFiles(arrayOf(uri))

            manager.removeAt(0)

            assertEquals(0, manager.size)
            assertEquals("[]", storage.getString("KEY_PLAYLIST", null))
            assertSavedUris()
        }
    }

    @Test
    fun removeAtIgnoresInvalidIndex() {
        runBlocking {
            val uri = Uri.fromFile(createWavFile("remove_invalid.wav"))
            manager.save(storage)
            manager.addFiles(arrayOf(uri))

            val removed = mutableListOf<Int>()
            manager.addListener(object : PlaylistManager.Listener {
                override fun onItemRemoved(index: Int) {
                    removed.add(index)
                }
            })

            manager.removeAt(-1)
            manager.removeAt(1)
            manager.removeAt(99)

            assertEquals(1, manager.size)
            assertEquals(uri, manager.itemAt(0).uri)
            assertTrue(removed.isEmpty())
            assertSavedUris(uri)
        }
    }

    private fun assertSavedUris(vararg expected: Uri) {
        val decoded = PlaylistStorage.decode(storage.getString("KEY_PLAYLIST", null))
        assertTrue(decoded is PlaylistStorage.Decoded.Parsed)
        assertEquals(expected.toList(), (decoded as PlaylistStorage.Decoded.Parsed).uris)
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
