package com.vrla.forest

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class VideoItem(
    val uri: Uri,
    val fileName: String,
    val hasParameters: Boolean
)

class VideoListAdapter(
    private val videos: List<VideoItem>,
    private val onVideoSelected: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoListAdapter.VideoViewHolder>() {

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.videoFileName)
        val parameterStatus: TextView = view.findViewById(R.id.videoParameterStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.video_list_item, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.fileName.text = video.fileName

        if (video.hasParameters) {
            holder.parameterStatus.text = "✓ Parameter-Datei gefunden"
            holder.parameterStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            holder.parameterStatus.text = "○ Keine Parameter-Datei"
            holder.parameterStatus.setTextColor(0xFFAAAAAA.toInt())
        }

        holder.itemView.setOnClickListener {
            onVideoSelected(video)
        }
    }

    override fun getItemCount() = videos.size
}
