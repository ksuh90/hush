package app.hush

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/** Microphone foreground service. It runs only while Hush records. */
class MicService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = intent?.getIntExtra(EXTRA_SESSION, 0) ?: 0
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            Log.w("Hush", "startForeground failed", e)
            Diagnostics.mic(this, "startForeground failed: ${e.javaClass.simpleName}")
            Recorder.fail(session, Notice.MIC_FAILED)
            stopSelf()
            return START_NOT_STICKY
        }
        Diagnostics.mic(this, "foreground service started")
        Recorder.start(session)
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notification_listening))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_SESSION = "session"

        fun start(context: Context, session: Int) {
            context.startForegroundService(Intent(context, MicService::class.java).putExtra(EXTRA_SESSION, session))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MicService::class.java))
        }
    }
}
