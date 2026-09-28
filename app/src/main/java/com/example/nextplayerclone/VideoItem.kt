package com.example.nextplayerclone

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    var lastPositionMs: Long = 0L,
    var isFavorite: Boolean = false
) {
    val progressPercent: Int
        get() = if (durationMs > 0) ((lastPositionMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0
}
