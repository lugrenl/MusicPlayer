package com.example.musicplayer.ui.playlist

import com.example.musicplayer.R
import com.example.musicplayer.domain.PlaylistManager

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

class PlaylistAdapter(
    private val model: PlaylistManager,
    private val actions: Actions) : RecyclerView.Adapter<TrackViewHolder>() {

    interface Actions {
        fun onPreviewClicked(index: Int)
    }

    private val listener = object : PlaylistManager.Listener {
        override fun onItemAdded(index: Int) = notifyItemInserted(index)
        override fun onItemChanged(index: Int) = notifyItemChanged(index, Unit)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        model.addListener(listener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        model.removeListener(listener)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        return TrackViewHolder(
            view = LayoutInflater.from(parent.context)
                .inflate(R.layout.track_item_view, parent, false),
            actions = actions
        )
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(model.itemAt(position))
    }

    override fun getItemCount(): Int {
        return model.size
    }

}

class TrackViewHolder(
    view: View,
    private val actions: PlaylistAdapter.Actions
) : RecyclerView.ViewHolder(view) {

    private val preview = view.findViewById<ImageView>(R.id.preview).apply {
        clipToOutline = true
        setOnClickListener {
            val index = adapterPosition
            if (index != RecyclerView.NO_POSITION) {
                actions.onPreviewClicked(index)
            }
        }
    }
    private val state = view.findViewById<ImageView>(R.id.state)
    private val track = view.findViewById<TextView>(R.id.track)
    private val artist = view.findViewById<TextView>(R.id.artist)
    private val duration = view.findViewById<TextView>(R.id.duration)


    fun bind(value: PlaylistManager.Item) {
        artist.text = value.artist ?: getString(R.string.unknown_artist)
        track.text = value.track ?: getString(R.string.unknown_track)
        duration.text = value.duration.msBeautify()
        preview.setImageBitmap(value.preview)
        state.setImageResource(
            when (value.state) {
                PlaylistManager.Item.State.NONE -> android.R.color.transparent
                PlaylistManager.Item.State.ACTIVE -> R.drawable.play
                PlaylistManager.Item.State.PLAYING -> R.drawable.pause
            }
        )
    }

    private fun getString(@StringRes resId: Int) = itemView.context.getString(resId)

    @SuppressLint("DefaultLocale")
    private fun Long.msBeautify(): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
        val seconds = (this - TimeUnit.MINUTES.toMillis(minutes)) / 1000
        return String.format("%02d:%02d", minutes, seconds)
    }
}

