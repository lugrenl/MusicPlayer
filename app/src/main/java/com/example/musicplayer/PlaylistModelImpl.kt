package com.example.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.*
import android.net.Uri
import java.util.LinkedList

class PlaylistModelImpl(private val context: Context): PlayListModel {

    private data class ItemImpl(
        override val artist: String?,
        override val track: String?,
        override val duration: Long,
        override val preview: Bitmap?,
        override val uri: Uri,
        override var state: PlayListModel.Item.State = PlayListModel.Item.State.NONE
    ) : PlayListModel.Item

    private val retriever = MediaMetadataRetriever()
    private val items = LinkedList<ItemImpl>()

    private var activeItem: ItemImpl? = null
    private var isPlaying = false

    private var listeners = mutableSetOf<PlayListModel.Listener>()

    override fun addListener(listener: PlayListModel.Listener) {
        if (listeners.contains(listener)) return
        listeners.add(listener)
    }

    override fun removeListener(listener: PlayListModel.Listener) {
        listeners.remove(listener)
    }

    override val size: Int get() = items.size

    override fun addFiles(files: Array<Uri>) {
        files.forEach { uri ->
            try {
                retriever.setDataSource(context, uri)
                val item = ItemImpl(
                    artist = retriever.extractMetadata(METADATA_KEY_ARTIST),
                    track = retriever.extractMetadata(METADATA_KEY_TITLE),
                    duration = checkNotNull(retriever.extractMetadata(METADATA_KEY_DURATION)).toLong(),
                    preview = retriever.embeddedPicture?.let { rawPreview ->
                        BitmapFactory.decodeByteArray(rawPreview, 0, rawPreview.size, BitmapFactory.Options())
                    },
                    uri = uri
                )
            } catch (ignore: RuntimeException) {
            }
        }
    }

    override fun itemAt(index: Int): PlayListModel.Item = items[index]

    override fun setActive(uri: Uri?) {
        val item = items.find { it.uri == uri }
        if (item == activeItem) return
        activeItem?.let {
            it.state = PlayListModel.Item.State.NONE
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

    private fun addItem(item: ItemImpl) {
        items.add(item)
        notify { onItemAdded(size - 1) }
    }

    private fun applyPlayingState() {
        with(activeItem ?: return) {
            state = if (isPlaying) PlayListModel.Item.State.PLAYING else PlayListModel.Item.State.ACTIVE
            notifyItemChanged(this)
        }
    }

    private fun notifyItemChanged(item: ItemImpl) {
        val index = items.indexOf(item)
        if (index in 0 until size) {
            notify { onItemChanged(index) }
        }
    }

    private fun notify(event: PlayListModel.Listener.() -> Unit) {
        listeners.forEach(event)
    }
}