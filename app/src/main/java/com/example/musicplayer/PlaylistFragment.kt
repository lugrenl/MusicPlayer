package com.example.musicplayer

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PlaylistFragment : Fragment(R.layout.fragment_playlist) {

    companion object {
        private const val REQUEST_AUDIO = 12345
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val playlistView = view.findViewById<RecyclerView>(R.id.playlist_view)
        playlistView.adapter = RVAdapter()

        view.findViewById<FloatingActionButton>(R.id.add_tracks_button).setOnClickListener {
            onAddTracksButtonClicked()
        }
    }

    private fun onAddTracksButtonClicked() {
        startActivityForResult(
            Intent.createChooser(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                },
                getString(R.string.title_audio_file_picker)
            ),
            REQUEST_AUDIO
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_AUDIO) {
            if (resultCode == RESULT_OK) {
                val items = Array(checkNotNull(data).clipData?.itemCount ?: 1) { index -> checkNotNull(
                    if (index == 0 && data.data != null) {
                        data.data  // один элемент
                    } else {
                        data.clipData?.getItemAt(index)?.uri  // множество элементов
                    }
                )}
                Toast.makeText(requireContext(), "SELECT FILE(S): ${items.size}", Toast.LENGTH_SHORT).show()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
