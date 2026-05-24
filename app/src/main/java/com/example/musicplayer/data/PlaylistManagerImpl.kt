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
        private const val SEPARATOR = ", "
    }

    private val retriever = MediaMetadataRetriever()
    private val items = LinkedList<ItemImpl>()

    private var activeItem: ItemImpl? = null
    private var isPlaying = false

    private var listeners = mutableSetOf<PlaylistManager.Listener>()

    override fun addListener(listener: PlaylistManager.Listener) {
        if (listeners.contains(listener)) return
        listeners.add(listener)
    }

    override fun removeListener(listener: PlaylistManager.Listener) {
        listeners.remove(listener)
    }

    override val size: Int get() = items.size

    override fun addFiles(files: Array<Uri>) {
        files.forEach { uri ->
            try {
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
            } catch (_: RuntimeException) {
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
        storage.edit {
            putString(KEY_PLAYLIST, items.map { it.uri }.joinToString(SEPARATOR))
        }
    }

    override fun restore(storage: SharedPreferences) {
        val items = storage.getString(KEY_PLAYLIST, null)?.split(SEPARATOR)
        if (items.isNullOrEmpty()) return
        addFiles(items.map { Uri.parse(it) }.toTypedArray())
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