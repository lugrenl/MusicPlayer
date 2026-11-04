package com.example.musicplayer.ui.playlist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.example.musicplayer.data.PlaylistManagerImpl
import com.example.musicplayer.domain.PlaylistManager
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import kotlinx.coroutines.launch

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {

    val model: PlaylistManager = PlaylistManagerImpl(application)

    private var hasUserStartedPlayback = false

    private val player = SimpleExoPlayer.Builder(application).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uri = mediaItem?.playbackProperties?.uri
                model.setActive(uri)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                model.setPlayingState(isPlaying)
            }
        })
    }

    private val modelListener = object : PlaylistManager.Listener {
        override fun onItemAdded(index: Int) {
            player.addMediaItem(MediaItem.fromUri(model.itemAt(index).uri))
            player.prepare()
        }
    }

    init {
        model.addListener(modelListener)
        viewModelScope.launch {
            model.restore(PreferenceManager.getDefaultSharedPreferences(application))
        }
    }

    fun addFiles(files: Array<Uri>) {
        viewModelScope.launch {
            model.addFiles(files)
        }
    }

    fun onPreviewClicked(index: Int) {
        hasUserStartedPlayback = true
        when (model.itemAt(index).state) {
            PlaylistManager.Item.State.NONE -> {
                player.seekToDefaultPosition(index)
                player.play()
            }
            PlaylistManager.Item.State.ACTIVE -> {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekToDefaultPosition(index)
                }
                player.play()
            }
            PlaylistManager.Item.State.PLAYING -> player.pause()
        }
    }

    fun selectTrack(index: Int) {
        if (model.itemAt(index).state == PlaylistManager.Item.State.NONE) {
            hasUserStartedPlayback = true
            player.seekToDefaultPosition(index)
            player.play()
        }
    }

    fun togglePlayPause() {
        val index = playbackProgressForScreen()?.itemIndex ?: return
        onPreviewClicked(index)
    }

    fun seekTo(positionMs: Long) {
        val duration = player.duration.takeIf { it > 0L } ?: return
        player.seekTo(positionMs.coerceIn(0L, duration))
    }

    fun playbackProgress(): PlaybackProgress? {
        if (!hasUserStartedPlayback) return null
        return playbackProgressForScreen()
    }

    fun playbackProgressForScreen(): PlaybackProgress? {
        val index = player.currentMediaItemIndex
        if (index !in 0 until model.size) return null

        val isEnded = player.playbackState == Player.STATE_ENDED
        val duration = player.duration.takeIf { it > 0L }
        val position = if (isEnded) 0L else player.currentPosition.coerceAtLeast(0L)
        return PlaybackProgress(index, position, duration, player.isPlaying)
    }

    internal fun isPlaybackEnded(): Boolean {
        return player.playbackState == Player.STATE_ENDED
    }

    fun save() {
        model.save(PreferenceManager.getDefaultSharedPreferences(getApplication()))
    }

    override fun onCleared() {
        model.removeListener(modelListener)
        player.release()
        model.release()
    }
}
