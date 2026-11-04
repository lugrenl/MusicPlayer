package com.example.musicplayer.ui.nowplaying

import com.example.musicplayer.R
import com.example.musicplayer.ui.playlist.PlaybackProgress
import com.example.musicplayer.ui.playlist.PlaylistViewModel
import com.example.musicplayer.ui.playlist.formatPlaybackTime
import com.example.musicplayer.ui.playlist.seekTargetMs

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton

class NowPlayingFragment : Fragment(R.layout.fragment_now_playing) {

    companion object {
        private const val UPDATE_INTERVAL_MS = 500L
    }

    private val viewModel: PlaylistViewModel by lazy {
        ViewModelProvider(requireActivity())[PlaylistViewModel::class.java]
    }

    private var progressUpdatesStarted = false
    private var lastItemIndex = -1
    private var currentProgress: PlaybackProgress? = null
    private var isUserSeeking = false

    private var cover: ImageView? = null
    private var track: TextView? = null
    private var artist: TextView? = null
    private var seekBar: SeekBar? = null
    private var elapsed: TextView? = null
    private var total: TextView? = null
    private var playPause: FloatingActionButton? = null

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            if (!progressUpdatesStarted) return
            update()
            progressHandler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cover = view.findViewById(R.id.now_playing_cover)
        track = view.findViewById(R.id.now_playing_track)
        artist = view.findViewById(R.id.now_playing_artist)
        seekBar = view.findViewById(R.id.now_playing_seek)
        elapsed = view.findViewById(R.id.now_playing_elapsed)
        total = view.findViewById(R.id.now_playing_total)
        playPause = view.findViewById(R.id.now_playing_play_pause)

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val target = seekTargetMs(progress, currentProgress?.safeDurationMs) ?: return
                elapsed?.text = formatPlaybackTime(target)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val bar = seekBar ?: return
                val target = seekTargetMs(bar.progress, currentProgress?.safeDurationMs) ?: return
                viewModel.seekTo(target)
                update()
            }
        })

        view.findViewById<ImageButton>(R.id.now_playing_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        playPause?.setOnClickListener {
            viewModel.togglePlayPause()
            update()
        }

        update()
    }

    override fun onStart() {
        super.onStart()
        startProgressUpdates()
    }

    override fun onStop() {
        stopProgressUpdates()
        super.onStop()
    }

    override fun onDestroyView() {
        stopProgressUpdates()
        progressHandler.removeCallbacks(progressTick)
        cover = null
        track = null
        artist = null
        seekBar = null
        elapsed = null
        total = null
        playPause = null
        currentProgress = null
        isUserSeeking = false
        super.onDestroyView()
    }

    private fun startProgressUpdates() {
        if (progressUpdatesStarted) return
        progressUpdatesStarted = true
        progressHandler.removeCallbacks(progressTick)
        update()
        progressHandler.postDelayed(progressTick, UPDATE_INTERVAL_MS)
    }

    private fun stopProgressUpdates() {
        progressUpdatesStarted = false
        progressHandler.removeCallbacks(progressTick)
    }

    private fun update() {
        val progress = viewModel.playbackProgressForScreen()
        if (progress == null || progress.itemIndex !in 0 until viewModel.model.size) {
            returnToPlaylist()
            return
        }

        if (progress.itemIndex != lastItemIndex) {
            lastItemIndex = progress.itemIndex
            val item = viewModel.model.itemAt(progress.itemIndex)
            track?.text = item.track ?: getString(R.string.unknown_track)
            artist?.text = item.artist ?: getString(R.string.unknown_artist)
            cover?.setImageBitmap(item.preview)
        }

        currentProgress = progress
        seekBar?.isEnabled = progress.safeDurationMs != null

        if (!isUserSeeking) {
            seekBar?.progress = progress.progress
            elapsed?.text = formatPlaybackTime(progress.safePositionMs)
        }
        total?.text = progress.safeDurationMs?.let(::formatPlaybackTime) ?: "--:--"

        playPause?.setImageResource(
            if (progress.isPlaying) R.drawable.pause else R.drawable.play
        )
        playPause?.contentDescription = getString(
            if (progress.isPlaying) R.string.pause else R.string.play
        )
    }

    private fun returnToPlaylist() {
        stopProgressUpdates()
        if (!isAdded || parentFragmentManager.isStateSaved) return
        parentFragmentManager.popBackStack()
    }
}
