package com.example.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.VideoWidgetProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object WidgetAnimationManager {
    private const val TAG = "WidgetAnimation"
    private var animationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Maps to track states: appWidgetId -> current displayed frame index
    private val frameStates = mutableMapOf<Int, Int>()
    
    // Maps to track dynamic timestamps: appWidgetId -> last frame render epoch timestamp
    private val lastRenderTimes = mutableMapOf<Int, Long>()

    @Volatile
    private var isScreenOn = true

    fun setScreenState(on: Boolean, context: Context) {
        isScreenOn = on
        if (on) {
            startAnimationLoop(context.applicationContext)
        } else {
            stopAnimationLoop()
        }
    }

    @Synchronized
    fun startAnimationLoop(context: Context) {
        if (animationJob?.isActive == true) return
        Log.d(TAG, "Memulai loop animasi widget...")

        val appContext = context.applicationContext
        isScreenOn = true

        animationJob = scope.launch {
            val db = WidgetDatabase.getDatabase(appContext)
            val dao = db.videoWidgetDao()

            // The loop ticks very rapidly (50ms interval) to allow various configs (e.g., 5fps, 10fps, 1fps)
            // without any stuttering.
            while (isScreenOn) {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(appContext)
                    val thisWidgetName = ComponentName(appContext, VideoWidgetProvider::class.java)
                    val activeWidgetIds = appWidgetManager.getAppWidgetIds(thisWidgetName)

                    if (activeWidgetIds.isEmpty()) {
                        // Sleep for 2 seconds when no widgets exist to preserve resources
                        delay(2000)
                        continue
                    }

                    val now = System.currentTimeMillis()

                    for (widgetId in activeWidgetIds) {
                        val binding = dao.getBindingForWidget(widgetId)
                        if (binding != null) {
                            val config = dao.getConfigById(binding.configId)
                            if (config != null && config.frameCount > 0) {
                                val fps = config.fps.coerceAtLeast(1)
                                val delayNeededMs = 1000L / fps
                                val lastRender = lastRenderTimes[widgetId] ?: 0L

                                if (now - lastRender >= delayNeededMs) {
                                    lastRenderTimes[widgetId] = now

                                    val curFrame = frameStates[widgetId] ?: 0
                                    val nextFrame = (curFrame + 1) % config.frameCount
                                    frameStates[widgetId] = nextFrame

                                    // Decode and load the local frame as bitmapped RemoteViews
                                    val folder = File(appContext.filesDir, config.localFolderName)
                                    val frameFile = File(folder, "frame_$curFrame.jpg")

                                    if (frameFile.exists()) {
                                        val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath)
                                        if (bitmap != null) {
                                            val views = RemoteViews(appContext.packageName, R.layout.video_widget_layout)
                                            views.setImageViewBitmap(R.id.widget_image_view, bitmap)
                                            views.setTextViewText(R.id.widget_title, config.title)
                                            views.setTextViewText(R.id.widget_status, "Memutar • ${config.fps} FPS")
                                            
                                            // Play/Pause badge indicator state
                                            views.setImageViewResource(
                                                R.id.widget_play_badge,
                                                if (config.isLooping) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                                            )

                                            // Setup pending intent inside loop just in case bindings altered
                                            val clickIntent = Intent(appContext, MainActivity::class.java).apply {
                                                action = "SHOW_VIDEO"
                                                putExtra("config_id", config.id)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            }
                                            
                                            val pendingIntent = PendingIntent.getActivity(
                                                appContext,
                                                widgetId,
                                                clickIntent,
                                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                                            )
                                            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                                            appWidgetManager.updateAppWidget(widgetId, views)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Kesalahan dalam loop render widget: ${e.message}")
                }

                // Smooth clock tick interval
                delay(50)
            }
        }
    }

    @Synchronized
    fun stopAnimationLoop() {
        Log.d(TAG, "Menghentikan loop animasi widget.")
        animationJob?.cancel()
        animationJob = null
    }
}
