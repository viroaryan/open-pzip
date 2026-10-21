package app.pzip.core.tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import app.pzip.MainActivity
import app.pzip.R
import app.pzip.core.Files

/** Keeps long operations alive in the background and shows their progress. */
class OperationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val running = TaskManager.running
            if (running.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            nm().notify(ONGOING_ID, build(running))
            handler.postDelayed(this, 700)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            TaskManager.cancelAll()
            return START_NOT_STICKY
        }
        ensureChannels(this)
        val n = build(TaskManager.running)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ONGOING_ID, n)
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 300)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun nm() = getSystemService(NotificationManager::class.java)

    private fun build(running: List<PzipTask>): Notification {
        val first = running.firstOrNull()
        val title = when {
            first == null -> "pzip"
            running.size > 1 -> "${running.size} operations running"
            else -> "${first.kind.verb} ${first.title}"
        }
        val text = first?.let {
            buildString {
                append("${(it.fraction * 100).toInt()}%")
                if (it.speed > 0) append(" · ${Files.formatSize(it.speed)}/s")
                if (it.currentFile.isNotEmpty()) append(" · ${it.currentFile}")
            }
        } ?: "Working…"
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, OperationService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(1000, ((first?.fraction ?: 0f) * 1000).toInt(), first == null || first.totalBytes == 0L)
            .setContentIntent(openApp(this))
            .addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            .build()
    }

    companion object {
        private const val CHANNEL_PROGRESS = "progress"
        private const val CHANNEL_DONE = "done"
        private const val ONGOING_ID = 7
        private const val ACTION_CANCEL = "app.pzip.CANCEL"

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Operations in progress", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Finished operations", NotificationManager.IMPORTANCE_DEFAULT))
        }

        private fun openApp(context: Context) = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        fun notifyFinished(context: Context, task: PzipTask) {
            if (task.state == TaskState.CANCELLED) return
            ensureChannels(context)
            val ok = task.state == TaskState.DONE
            val n = Notification.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (ok) "${task.kind.done} ${task.title}" else "Couldn't finish: ${task.title}")
                .setContentText(task.message)
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(1000 + task.id.toInt(), n) }
        }
    }
}
