package com.example.musicplayer.data

import com.example.musicplayer.domain.PlaylistManager

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.*
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.LinkedList

class PlaylistManagerImpl(private val context: Context): PlaylistManager {

    private data class ItemImpl(
        override val artist: String?,
        override val track: String?,
        override val duration: Long,
        override val preview: Bitmap?,
        override val uri: Uri,
        override var state: PlaylistManager.Item.State = PlaylistManager.Item.State.NONE
    ) : PlaylistManager.Item

    companion object {
        private const val KEY_PLAYLIST = "KEY_PLAYLIST"
    }

    @Volatile
    private var released = false
    private val items = LinkedList<ItemImpl>()

    private var activeItem: ItemImpl? = null
    private var isPlaying = false

    private var listeners = mutableSetOf<PlaylistManager.Listener>()
    private var storage: SharedPreferences? = null
    private var keepStoredData = false

    override fun addListener(listener: PlaylistManager.Listener) {
        if (listeners.contains(listener)) return
        listeners.add(listener)
    }

    override fun removeListener(listener: PlaylistManager.Listener) {
        listeners.remove(listener)
    }

    override val size: Int get() = items.size

    override suspend fun addFiles(files: Array<Uri>) {
        if (released) return
        val existing = items.mapTo(HashSet()) { it.uri }

        val parsed = withContext(Dispatchers.IO) {
            val result = mutableListOf<ItemImpl>()
            val retriever = MediaMetadataRetriever()
            try {
                files.forEach { uri ->
                    ensureActive()
                    if (uri in existing || result.any { it.uri == uri }) return@forEach

                    val item = try {
                        retriever.setDataSource(context, uri)
                        ItemImpl(
                            artist = retriever.extractMetadata(METADATA_KEY_ARTIST),
                            track = retriever.extractMetadata(METADATA_KEY_TITLE),
                            duration = checkNotNull(retriever.extractMetadata(METADATA_KEY_DURATION)).toLong(),
                            preview = retriever.embeddedPicture?.let { rawPreview ->
                                BitmapFactory.decodeByteArray(rawPreview, 0, rawPreview.size, BitmapFactory.Options())
                            },
                            uri = uri
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: RuntimeException) {
                        null
                    }

                    if (item != null) result.add(item)
                }
            } finally {
                retriever.release()
            }
            result
        }

        withContext(Dispatchers.Main.immediate) {
            if (released) return@withContext
            var added = false
            parsed.forEach { item ->
                if (items.any { it.uri == item.uri }) return@forEach

                items.add(item)
                notify { onItemAdded(items.lastIndex) }
                added = true
            }

            if (added) {
                save(storage ?: PreferenceManager.getDefaultSharedPreferences(context))
            }
        }
    }

    override fun itemAt(index: Int): PlaylistManager.Item = items[index]

    override fun setActive(uri: Uri?) {
        val item = items.find { it.uri == uri }
        if (item == activeItem) return
        activeItem?.let {
            it.state = PlaylistManager.Item.State.NONE
            notifyItemChanged(it)
        }
        activeItem = item
        applyPlayingState()
    }

    override fun setPlayingState(isPlaying: Boolean) {
        if (this.isPlaying == isPlaying) return
        this.isPlaying = isPlaying
        applyPlayingState()
    }

    override fun save(storage: SharedPreferences) {
        this.storage = storage
        if (keepStoredData && items.isEmpty()) return

        storage.edit {
            putString(KEY_PLAYLIST, PlaylistStorage.encode(items.map { it.uri }))
        }
        if (items.isNotEmpty()) {
            keepStoredData = false
        }
    }

    override suspend fun restore(storage: SharedPreferences) {
        this.storage = storage
        if (released) return

        val decoded = withContext(Dispatchers.IO) {
            PlaylistStorage.decode(storage.getString(KEY_PLAYLIST, null))
        }

        if (decoded is PlaylistStorage.Decoded.Invalid) {
            keepStoredData = true
            return
        }
        if (decoded !is PlaylistStorage.Decoded.Parsed || decoded.uris.isEmpty()) return

        val sizeBefore = items.size
        addFiles(decoded.uris.toTypedArray())
        if (items.size == sizeBefore) {
            keepStoredData = true
        }
    }

    override fun release() {
        released = true
    }

    private fun applyPlayingState() {
        with(activeItem ?: return) {
            state = if (isPlaying) PlaylistManager.Item.State.PLAYING else PlaylistManager.Item.State.ACTIVE
            notifyItemChanged(this)
        }
    }

    private fun notifyItemChanged(item: ItemImpl) {
        val index = items.indexOf(item)
        if (index in 0 until size) {
            notify { onItemChanged(index) }
        }
    }

    private fun notify(event: PlaylistManager.Listener.() -> Unit) {
        listeners.forEach(event)
    }
}