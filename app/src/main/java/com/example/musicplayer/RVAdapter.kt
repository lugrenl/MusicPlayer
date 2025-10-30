package com.example.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RVAdapter : RecyclerView.Adapter<TrackViewHolder>() {

    private val items = IntArray(30) { it }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        return TrackViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.track_item_view, parent, false)

        )
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

}

class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    private val track = view.findViewById<TextView>(R.id.track)
    private val artist = view.findViewById<TextView>(R.id.artist)
    private val duration = view.findViewById<TextView>(R.id.duration)
    private val preview = view.findViewById<ImageView>(R.id.preview)
    private val state = view.findViewById<ImageView>(R.id.state).apply { visibility = View.VISIBLE }

    fun bind(value: Int) {
        if (value % 2 == 0) {
            state.setImageResource(R.drawable.play)
        } else {
            state.setImageResource(R.drawable.pause)
        }
        val text = value.toString()
        track.text = text
        artist.text = text
        duration.text = text

    }



}

