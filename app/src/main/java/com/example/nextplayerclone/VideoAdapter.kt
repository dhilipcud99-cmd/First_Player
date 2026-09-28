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
import kotlin.math.roundToInt

class VideoAdapter(
    private val items: MutableList<VideoItem>,
    private val onClick: (VideoItem) -> Unit,
    private val onMoreClick: ((VideoItem, View) -> Unit)? = null
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgThumb: ImageView = view.findViewById(R.id.imgThumb)
        val txtDuration: TextView = view.findViewById(R.id.txtDuration)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtSubtitle: TextView = view.findViewById(R.id.txtSubtitle)
        val btnMore: ImageButton? = view.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = items[position]
        holder.txtTitle.text = item.title
        holder.txtDuration.text = formatDuration(item.durationMs)
        val sizeMb = (item.sizeBytes / (1024.0 * 1024.0)).roundToInt()
        holder.txtSubtitle.text = if (item.width > 0) {
            "$sizeMb MB · ${item.width}x${item.height}"
        } else {
            "$sizeMb MB"
        }

        if (item.lastPositionMs > 0 && item.durationMs > 0) {
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = item.progressPercent
        } else {
            holder.progressBar.visibility = View.GONE
        }

        holder.imgThumb.setImageDrawable(null)
        ThumbnailLoader.load(holder.imgThumb, item.uri)

        holder.itemView.setOnClickListener { onClick(item) }
        holder.btnMore?.setOnClickListener { view ->
            onMoreClick?.invoke(item, view)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<VideoItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }
}
