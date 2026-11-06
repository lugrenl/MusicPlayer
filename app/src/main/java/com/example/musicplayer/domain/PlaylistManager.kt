package com.example.musicplayer.domain

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri

interface PlaylistManager {

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
        fun onItemRemoved(index: Int) = Unit
    }

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

    val size: Int

    suspend fun addFiles(files: Array<Uri>)

    fun itemAt(index: Int): Item

    fun removeAt(index: Int)

    fun setActive(uri: Uri?)

    fun setPlayingState(isPlaying: Boolean)

    fun save(storage: SharedPreferences)

    suspend fun restore(storage: SharedPreferences)

    fun release()
}