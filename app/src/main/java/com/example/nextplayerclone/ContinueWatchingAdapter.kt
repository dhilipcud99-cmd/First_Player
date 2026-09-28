package com.example.nextplayerclone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.concurrent.TimeUnit

class ContinueWatchingAdapter(
    private var videos: MutableList<VideoItem>,
    private val onClick: (VideoItem) -> Unit,
    private val onMoreClick: (VideoItem, View) -> Unit
) : RecyclerView.Adapter<ContinueWatchingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
        val btnMore: ImageButton = view.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_watching, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        holder.tvTitle.text = video.title

        val durStr = formatDuration(video.durationMs)
        val posStr = formatDuration(video.lastPositionMs)
        val percent = video.progressPercent

        holder.tvDuration.text = durStr
        holder.progressBar.progress = percent
        holder.tvSubtitle.text = "$posStr / $durStr • $percent%"

        holder.ivThumbnail.setImageDrawable(null)
        ThumbnailLoader.load(holder.ivThumbnail, video.uri)

        holder.itemView.setOnClickListener { onClick(video) }
        holder.btnMore.setOnClickListener { onMoreClick(video, it) }
    }

    override fun getItemCount(): Int = videos.size

    fun updateData(newVideos: List<VideoItem>) {
        videos.clear()
        videos.addAll(newVideos)
        notifyDataSetChanged()
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
}
