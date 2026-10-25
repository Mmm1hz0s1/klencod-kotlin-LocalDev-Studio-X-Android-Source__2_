package com.localdev.studiox.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.localdev.studiox.LocalDevApplication
import com.localdev.studiox.MainActivity
import com.localdev.studiox.R

/**
 * Android Foreground Service keeping the LocalDev Studio X HTTP/PHP/SQLite stack alive
 * when the developer switches to a mobile browser for testing.
 */
class LocalDevForegroundService : Service() {

    companion object {
        const val ACTION_START_FOREGROUND = "com.localdev.studiox.action.START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "com.localdev.studiox.action.STOP_FOREGROUND"
        const val EXTRA_PORT = "extra_port"
        const val NOTIFICATION_ID = 10042
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_FOREGROUND -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
                val notification = buildNotification(port)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(port: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocalDevForegroundService::class.java).apply {
            action = ACTION_STOP_FOREGROUND
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, LocalDevApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("LocalDev Studio X — Active Stack")
            .setContentText("HTTP listening on http://127.0.0.1:$port/")
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(0, "Stop Foreground", pendingStop)
            .build()
    }
}
