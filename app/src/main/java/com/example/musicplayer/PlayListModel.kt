package com.example.musicplayer

import android.graphics.Bitmap
import android.net.Uri

interface PlayListModel {

    interface Item {

        enum class State {
            NONE,     // не выбран
            ACTIVE,   // выбран, не воспроизводится (приостановлен)
            PLAYING   // выбран, воспроизводится (играет)
        }

        val artist: String?
        val track: String?
        val duration: Long
        val preview: Bitmap?
        val uri: Uri
        val state: State
    }

    interface Listener {
        fun onItemAdded(index: Int) = Unit
        fun onItemChanged(index: Int) = Unit
    }

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

    val size: Int

    fun addFiles(files: Array<Uri>)

    fun itemAt(index: Int): Item

    fun setActive(uri: Uri?)

    fun setPlayingState(isPlaying: Boolean)
}