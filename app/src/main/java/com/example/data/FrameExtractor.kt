package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FrameExtractor {
    private const val TAG = "FrameExtractor"

    suspend fun extractFrames(
        context: Context,
        videoUri: Uri,
        targetFolderName: String,
        frameCount: Int,
        maxSize: Int = 320 // Resized to max 320px for high density with proper 1MB RemoteViews limits
    ): Int = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var extractedCount = 0
        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 2000L

            // Create target folder
            val folder = File(context.filesDir, targetFolderName)
            if (folder.exists()) {
                folder.deleteRecursively()
            }
            folder.mkdirs()

            // Calculate sample points in microseconds (us)
            // Evenly distribute sampling over the length of the video
            val stepUs = if (frameCount > 1) {
                (durationMs * 1000) / (frameCount - 1)
            } else {
                durationMs * 1000
            }

            for (i in 0 until frameCount) {
                val timeUs = i * stepUs
                // Fetch frame at closest sync point or exact point
                val rawBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime // fallback
                
                if (rawBitmap != null) {
                    // Resize to size limit to keep file sizes very low and decoding extremely fast
                    val width = rawBitmap.width
                    val height = rawBitmap.height
                    val maxDim = maxOf(width, height)
                    
                    val targetBitmap = if (maxDim > maxSize) {
                        val scale = maxSize.toFloat() / maxDim.toFloat()
                        val w = (width * scale).toInt().coerceAtLeast(1)
                        val h = (height * scale).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(rawBitmap, w, h, true)
                    } else {
                        rawBitmap
                    }

                    // Save frame compressed as JPEG
                    val file = File(folder, "frame_$i.jpg")
                    FileOutputStream(file).use { out ->
                        targetBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out) // 70% quality is very responsive
                    }
                    extractedCount++

                    if (targetBitmap != rawBitmap) {
                        targetBitmap.recycle()
                    }
                    rawBitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengekstrak frame video: ${e.message}", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore legacy exception releases
            }
        }
        return@withContext extractedCount
    }
}
