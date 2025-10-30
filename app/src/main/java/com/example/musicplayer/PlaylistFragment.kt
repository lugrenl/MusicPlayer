package com.example.musicplayer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

class PlaylistFragment : Fragment(R.layout.fragment_playlist) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val playlistView = view.findViewById<RecyclerView>(R.id.playlist_view)
        playlistView.adapter = RVAdapter()
    }


}
