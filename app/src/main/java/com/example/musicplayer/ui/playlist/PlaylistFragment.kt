package com.example.musicplayer.ui.playlist

import com.example.musicplayer.R
import com.example.musicplayer.domain.PlaylistManager
import com.example.musicplayer.ui.nowplaying.NowPlayingFragment

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PlaylistFragment : Fragment(R.layout.fragment_playlist) {

    companion object {
        private const val REQUEST_AUDIO = 12345
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        private const val NOW_PLAYING_TAG = "now_playing"
    }

    private val viewModel: PlaylistViewModel by lazy {
        ViewModelProvider(requireActivity())[PlaylistViewModel::class.java]
    }

    private val model get() = viewModel.model

    private val playlistView by lazy { requireView().findViewById<RecyclerView>(R.id.playlist_view) }

    private var playlistAdapter: PlaylistAdapter? = null
    private var progressUpdatesStarted = false
    private var navigatingToNowPlaying = false
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            if (!progressUpdatesStarted) return
            updateProgress()
            progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    private val playlistListener = object : PlaylistManager.Listener {
        override fun onItemAdded(index: Int) {
            updateEmptyState()
        }

        override fun onItemRemoved(index: Int) {
            updateEmptyState()
            updateProgress()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistAdapter = PlaylistAdapter(
            model = model,
            actions = object : PlaylistAdapter.Actions {
                override fun onPreviewClicked(index: Int) {
                    viewModel.onPreviewClicked(index)
                    updateProgress()
                }

                override fun onTrackClicked(index: Int) {
                    viewModel.selectTrack(index)
                    updateProgress()
                    openNowPlaying()
                }

                override fun onRemoveClicked(index: Int) {
                    viewModel.removeAt(index)
                }
            }
        )
        playlistView.adapter = playlistAdapter
        view.findViewById<FloatingActionButton>(R.id.add_tracks_button).setOnClickListener {
            onAddTracksButtonClicked()
        }

        model.addListener(playlistListener)
        updateEmptyState()
    }

    override fun onStart() {
        super.onStart()
        startProgressUpdates()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            navigatingToNowPlaying = false
            stopProgressUpdates()
        } else {
            startProgressUpdates()
        }
    }

    override fun onDestroyView() {
        stopProgressUpdates()
        progressHandler.removeCallbacks(progressTick)
        playlistAdapter = null
        model.removeListener(playlistListener)
        super.onDestroyView()
    }

    private fun updateEmptyState() {
        val hasItems = model.size > 0
        playlistView.visibility = if (hasItems) View.VISIBLE else View.GONE
        requireView().findViewById<View>(R.id.image_empty_playlist).visibility =
            if (hasItems) View.GONE else View.VISIBLE
        requireView().findViewById<View>(R.id.text_empty_playlist).visibility =
            if (hasItems) View.GONE else View.VISIBLE
    }

    private fun openNowPlaying() {
        if (navigatingToNowPlaying || isHidden) return
        if (parentFragmentManager.findFragmentByTag(NOW_PLAYING_TAG) != null) return

        navigatingToNowPlaying = true
        parentFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hide(this)
            .add(R.id.fragment_playlist, NowPlayingFragment(), NOW_PLAYING_TAG)
            .addToBackStack(null)
            .commit()
    }

    private fun startProgressUpdates() {
        if (isHidden || progressUpdatesStarted) return
        progressUpdatesStarted = true
        progressHandler.removeCallbacks(progressTick)
        updateProgress()
        progressHandler.postDelayed(progressTick, PROGRESS_UPDATE_INTERVAL_MS)
    }

    private fun stopProgressUpdates() {
        progressUpdatesStarted = false
        progressHandler.removeCallbacks(progressTick)
    }

    private fun updateProgress() {
        playlistAdapter?.updateProgress(viewModel.playbackProgress())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_AUDIO && resultCode == RESULT_OK) {
            data?.let { result ->
                val uris = linkedSetOf<Uri>()
                result.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) {
                        val uri = clip.getItemAt(index).uri ?: continue
                        uris.add(uri)
                    }
                }
                result.data?.let { uris.add(it) }

                val grantedUris = uris.mapNotNull { uri -> processUri(uri, result.flags) }
                if (grantedUris.isNotEmpty()) {
                    viewModel.addFiles(grantedUris.toTypedArray())
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStop() {
        stopProgressUpdates()
        super.onStop()
        viewModel.save()
    }

    private fun processUri(uri: Uri, resultFlags: Int): Uri? {
        val hasReadPermission = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        val canPersistPermission = resultFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        if (!hasReadPermission || !canPersistPermission) return null

        return try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            uri
        } catch (_: SecurityException) {
            null
        }
    }

    private fun onAddTracksButtonClicked() {
        startActivityForResult(
            Intent.createChooser(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "audio/*"
                    flags = flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                },
                getString(R.string.title_audio_file_picker)
            ),
            REQUEST_AUDIO
        )
    }
}
