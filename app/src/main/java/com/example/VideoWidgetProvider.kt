package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import com.example.data.WidgetAnimationManager
import com.example.data.WidgetDatabase
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("VideoWidgetProvider", "onUpdate dipanggil untuk widget: ${appWidgetIds.joinToString()}")
        
        // Ensure layouts are rendered initially with some placeholder or active configs
        val dao = WidgetDatabase.getDatabase(context).videoWidgetDao()
        CoroutineScope(Dispatchers.IO).launch {
            for (widgetId in appWidgetIds) {
                val binding = dao.getBindingForWidget(widgetId)
                val config = binding?.let { dao.getConfigById(it.configId) }

                val views = RemoteViews(context.packageName, R.layout.video_widget_layout)
                
                // Clicking the widget launches the MainActivity to play or manage the video
                val clickIntent = Intent(context, MainActivity::class.java).apply {
                    action = "SHOW_VIDEO"
                    putExtra("config_id", config?.id ?: -1)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    widgetId,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                if (config != null) {
                    views.setTextViewText(R.id.widget_title, config.title)
                    views.setTextViewText(R.id.widget_status, "Hore! Video aktif")
                    
                    // Render first frame as initial snapshot
                    val folder = File(context.filesDir, config.localFolderName)
                    val frameFile = File(folder, "frame_0.jpg")
                    if (frameFile.exists()) {
                        val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath)
                        if (bitmap != null) {
                            views.setImageViewBitmap(R.id.widget_image_view, bitmap)
                        }
                    }
                } else {
                    views.setTextViewText(R.id.widget_title, "Belum diatur")
                    views.setTextViewText(R.id.widget_status, "Ketuk untuk memilih video")
                    views.setImageViewResource(R.id.widget_image_view, android.R.color.darker_gray)
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }

            // Immediately boot/resume the animation runner
            WidgetAnimationManager.startAnimationLoop(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.d("VideoWidgetProvider", "Widget dihapus: ${appWidgetIds.joinToString()}")
        val dao = WidgetDatabase.getDatabase(context).videoWidgetDao()
        CoroutineScope(Dispatchers.IO).launch {
            for (id in appWidgetIds) {
                dao.deleteBinding(id)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("VideoWidgetProvider", "Widget pertama ditambahkan. Mendaftarkan pendengar layar.")
        registerScreenReceiver(context)
        WidgetAnimationManager.startAnimationLoop(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("VideoWidgetProvider", "Semua widget dihapus. Berhenti animasi.")
        WidgetAnimationManager.stopAnimationLoop()
    }

    companion object {
        private var screenReceiver: ScreenOnOffReceiver? = null

        fun registerScreenReceiver(context: Context) {
            if (screenReceiver == null) {
                screenReceiver = ScreenOnOffReceiver()
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                context.applicationContext.registerReceiver(screenReceiver, filter)
            }
        }
    }
}

class ScreenOnOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d("ScreenOnOffReceiver", "Screen ON - Melanjutkan render widget")
                WidgetAnimationManager.setScreenState(true, context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("ScreenOnOffReceiver", "Screen OFF - Jeda render widget untuk hemat baterai")
                WidgetAnimationManager.setScreenState(false, context)
            }
        }
    }
}
