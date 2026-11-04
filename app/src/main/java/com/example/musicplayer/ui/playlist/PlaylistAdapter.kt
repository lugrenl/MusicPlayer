package com.example.musicplayer.ui.playlist

import com.example.musicplayer.R
import com.example.musicplayer.domain.PlaylistManager

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

class PlaylistAdapter(
    private val model: PlaylistManager,
    private val actions: Actions) : RecyclerView.Adapter<TrackViewHolder>() {

    companion object {
        private const val PAYLOAD_PROGRESS = "progress"
    }

    private var playbackProgress: PlaybackProgress? = null

    interface Actions {
        fun onPreviewClicked(index: Int)
        fun onTrackClicked(index: Int)
    }

    private val listener = object : PlaylistManager.Listener {
        override fun onItemAdded(index: Int) = notifyItemInserted(index)
        override fun onItemChanged(index: Int) = notifyItemChanged(index, Unit)
    }

    fun updateProgress(progress: PlaybackProgress?) {
        if (playbackProgress == progress) return

        val previousIndex = playbackProgress?.itemIndex
        playbackProgress = progress
        val currentIndex = progress?.itemIndex

        if (previousIndex != null && previousIndex != currentIndex) {
            notifyItemChanged(previousIndex, PAYLOAD_PROGRESS)
        }
        if (currentIndex != null) {
            notifyItemChanged(currentIndex, PAYLOAD_PROGRESS)
        }
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
        holder.bind(model.itemAt(position), progressFor(position))
    }

    override fun onBindViewHolder(
        holder: TrackViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_PROGRESS }) {
            holder.bindProgress(progressFor(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int {
        return model.size
    }

    private fun progressFor(position: Int): PlaybackProgress? {
        return playbackProgress?.takeIf { it.itemIndex == position }
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
    private val state = view.findViewById<ImageView>(R.id.state).apply {
        setOnClickListener {
            val index = adapterPosition
            if (index != RecyclerView.NO_POSITION) {
                actions.onPreviewClicked(index)
            }
        }
    }
    private val track = view.findViewById<TextView>(R.id.track)
    private val artist = view.findViewById<TextView>(R.id.artist)
    private val duration = view.findViewById<TextView>(R.id.duration)
    private val progressBar = view.findViewById<ProgressBar>(R.id.progress)
    private val progressTime = view.findViewById<TextView>(R.id.progress_time)

    init {
        itemView.setOnClickListener {
            val index = adapterPosition
            if (index != RecyclerView.NO_POSITION) {
                actions.onTrackClicked(index)
            }
        }
    }


    fun bind(value: PlaylistManager.Item, playbackProgress: PlaybackProgress?) {
        artist.text = value.artist ?: getString(R.string.unknown_artist)
        track.text = value.track ?: getString(R.string.unknown_track)
        duration.text = value.duration.msBeautify()
        preview.setImageBitmap(value.preview)
        state.setImageResource(
            when (value.state) {
                PlaylistManager.Item.State.NONE -> R.drawable.play
                PlaylistManager.Item.State.ACTIVE -> R.drawable.play
                PlaylistManager.Item.State.PLAYING -> R.drawable.pause
            }
        )
        bindProgress(playbackProgress)
    }

    fun bindProgress(playbackProgress: PlaybackProgress?) {
        val visible = playbackProgress != null
        progressBar.visibility = if (visible) View.VISIBLE else View.GONE
        progressTime.visibility = if (visible) View.VISIBLE else View.GONE

        if (playbackProgress == null) {
            progressBar.progress = 0
            progressTime.text = null
            return
        }

        progressBar.progress = playbackProgress.progress
        progressTime.text = playbackProgress.formattedTime()
    }

    private fun getString(@StringRes resId: Int) = itemView.context.getString(resId)

    @SuppressLint("DefaultLocale")
    private fun Long.msBeautify(): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
        val seconds = (this - TimeUnit.MINUTES.toMillis(minutes)) / 1000
        return String.format("%02d:%02d", minutes, seconds)
    }
}

