package com.example.musicplayer

import android.graphics.Bitmap
import android.net.Uri

interface PlayListModel {

    data class Item(
        val artist: String?,
        val track: String?,
        val duration: Long,
        val preview: Bitmap?,
        private val uri: Uri
    )

    interface Listener {
        fun onItemAdded(index: Int)
    }

    var listener: Listener?

    val size: Int

    fun addFiles(files: Array<Uri>)

    fun itemAt(index: Int): Item
}