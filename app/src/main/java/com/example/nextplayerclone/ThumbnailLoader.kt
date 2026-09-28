package com.example.nextplayerclone

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Very small thumbnail loader: extracts a frame from each video URI on a background
 * thread pool, caches the resulting bitmap in memory, and sets it on the ImageView
 * if that view is still bound to the same URI when the load completes.
 */
object ThumbnailLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(imageView: ImageView, uri: Uri) {
        val key = uri.toString()
        imageView.tag = key

        cache[key]?.let {
            imageView.setImageBitmap(it)
            return
        }

        val weakView = WeakReference(imageView)
        val appContext = imageView.context.applicationContext
        executor.execute {
            val bitmap = try {
                val retriever = MediaMetadataRetriever()
                val frame = try {
                    retriever.setDataSource(appContext, uri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        val videoWidth = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                        )?.toIntOrNull() ?: 480
                        val videoHeight = retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                        )?.toIntOrNull() ?: 270
                        val scale = minOf(1f, 480f / videoWidth, 270f / videoHeight)
                        retriever.getScaledFrameAtTime(
                            0,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            (videoWidth * scale).toInt().coerceAtLeast(1),
                            (videoHeight * scale).toInt().coerceAtLeast(1)
                        )
                    } else {
                        retriever.getFrameAtTime(0)
                    }
                } finally {
                    retriever.release()
                }
                frame?.let { source ->
                    val scale = minOf(1f, 480f / source.width, 270f / source.height)
                    if (scale < 1f) {
                        val scaled = Bitmap.createScaledBitmap(
                            source,
                            (source.width * scale).toInt().coerceAtLeast(1),
                            (source.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                        if (scaled !== source) source.recycle()
                        scaled
                    } else {
                        source
                    }
                }
            } catch (e: Exception) {
                null
            }

            if (bitmap != null) {
                cache.put(key, bitmap)
                mainHandler.post {
                    val view = weakView.get()
                    if (view != null && view.tag == key) {
                        view.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }
}
