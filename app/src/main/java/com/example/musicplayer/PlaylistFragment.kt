package com.example.musicplayer

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PlaylistFragment : Fragment(R.layout.fragment_playlist) {

    companion object {
        private const val REQUEST_AUDIO = 12345
    }

    private val model by lazy { PlaylistModelImpl(requireContext()) }

    private val playlistView by lazy { requireView().findViewById<RecyclerView>(R.id.playlist_view) }

    private val player by lazy { SimpleExoPlayer.Builder(requireContext()).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
        addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uri = mediaItem?.playbackProperties?.uri
                model.setActive(uri)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                model.setPlayingState(isPlaying)
            }
        })
    } }

    private val modelListener = object : PlayListModel.Listener {
        override fun onItemAdded(index: Int) {
            player.addMediaItem(model.itemAt(index).toMediaItem())
            player.prepare()
        }

        private fun PlayListModel.Item.toMediaItem(): MediaItem = MediaItem.fromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.addListener(modelListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistView.adapter = RVAdapter(
            model = model,
            actions = object : RVAdapter.Actions {
                override fun onPreviewClicked(index: Int) {
                    val item = model.itemAt(index)
                    when (item.state) {
                        PlayListModel.Item.State.NONE -> {
                            player.seekToDefaultPosition(index)
                            player.play()
                        }
                        PlayListModel.Item.State.ACTIVE -> player.play()
                        PlayListModel.Item.State.PLAYING -> {
                            player.pause()
                        }
                    }
                }
            }
        )

        view.findViewById<FloatingActionButton>(R.id.add_tracks_button).setOnClickListener {
            onAddTracksButtonClicked()
        }

        if (savedInstanceState == null) {
            model.restore(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_AUDIO) {
            if (resultCode == RESULT_OK) {
                val items = Array(checkNotNull(data).clipData?.itemCount ?: 1) { index -> processUri(checkNotNull(
                    if (index == 0 && data.data != null) {
                        data.data  // один элемент
                    } else {
                        data.clipData?.getItemAt(index)?.uri // множество элементов
                    }
                ))}
                playlistView.visibility = View.VISIBLE
                model.addFiles(items)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStop() {
        super.onStop()
        model.save(PreferenceManager.getDefaultSharedPreferences(requireContext()))
    }

    override fun onDestroy() {
        super.onDestroy()
        model.removeListener(modelListener)
    }

    private fun processUri(uri: Uri): Uri {
        with(requireContext()) {
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return uri
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
