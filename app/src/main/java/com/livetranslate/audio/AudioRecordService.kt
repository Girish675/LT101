package com.livetranslate.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground Service for continuous audio recording in Live Duplex Mode.
 * Design §6.1: "User enables Live Mode. AudioRecordService starts as a Foreground Service."
 *
 * This service keeps the microphone alive even when the app is backgrounded,
 * and displays a persistent notification as required by Android for foreground services.
 */
class AudioRecordService : Service() {

    companion object {
        const val CHANNEL_ID = "live_translate_audio_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.livetranslate.action.START_RECORDING"
        const val ACTION_STOP = "com.livetranslate.action.STOP_RECORDING"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                // The actual AudioRecord is managed by AudioPipelineManager,
                // coordinated by the ViewModel. This service only keeps the process alive.
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live Translation",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active while live translation is running"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AudioRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveTranslate")
            .setContentText("Listening and translating…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
