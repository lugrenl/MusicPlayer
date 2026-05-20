package com.example.musicplayer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.*
import android.net.Uri
import java.util.LinkedList

class PlaylistModelImpl(private val context: Context): PlayListModel {

    private val retriever = MediaMetadataRetriever()
    private val items = LinkedList<PlayListModel.Item>()

    override var listener: PlayListModel.Listener? = null

    override val size: Int get() = items.size

    override fun addFiles(files: Array<Uri>) {
        files.forEach { uri ->
            try {
                retriever.setDataSource(context, uri)
                val item = PlayListModel.Item(
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

    override fun itemAt(index: Int) = items[index]

    private fun addItem(item: PlayListModel.Item) {
        items.add(item)
        listener?.onItemAdded(size - 1)
    }
}