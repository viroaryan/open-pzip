package app.pzip.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import app.pzip.R

/** Keeps audio (or a video's sound) playing with lock-screen and notification controls. */
class AudioService : Service() {
    private var session: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "pzip").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = Engine.resume()
                override fun onPause() = Engine.pause()
                override fun onSkipToNext() = Engine.next()
                override fun onSkipToPrevious() = Engine.prev()
                override fun onSeekTo(pos: Long) = Engine.seekTo(pos)
                override fun onStop() = Engine.stop()
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> Engine.toggle()
            ACTION_NEXT -> Engine.next()
            ACTION_PREV -> Engine.prev()
            ACTION_CLOSE -> {
                Engine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val n = build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun action(icon: Int, title: String, action: String): Notification.Action {
        val pi = PendingIntent.getService(this, action.hashCode(), Intent(this, AudioService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun build(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW))
        val item = Engine.current
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, item?.title ?: "pzip")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, Engine.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Engine.length)
                .build(),
        )
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP,
                )
                .setState(if (Engine.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, Engine.time, if (Engine.playing) Engine.rate else 0f)
                .build(),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, PlayerActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item?.title ?: "pzip")
            .setContentText(Engine.artist.ifEmpty { if (item?.isAudio == true) "Audio" else "Playing in background" })
            .setContentIntent(open)
            .setOngoing(Engine.playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(R.drawable.ic_prev, "Previous", ACTION_PREV))
            .addAction(action(if (Engine.playing) R.drawable.ic_pause else R.drawable.ic_play, if (Engine.playing) "Pause" else "Play", ACTION_TOGGLE))
            .addAction(action(R.drawable.ic_next, "Next", ACTION_NEXT))
            .addAction(action(R.drawable.ic_close, "Close", ACTION_CLOSE))
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    companion object {
        private const val CHANNEL = "playback"
        private const val ID = 42
        private const val ACTION_TOGGLE = "app.pzip.TOGGLE"
        private const val ACTION_NEXT = "app.pzip.NEXT"
        private const val ACTION_PREV = "app.pzip.PREV"
        private const val ACTION_CLOSE = "app.pzip.CLOSE"

        /** True while the player screen is hidden and audio should keep going. */
        @Volatile var inBackground = false

        fun update(context: Context) {
            if (!inBackground || !Engine.active) return
            val intent = Intent(context, AudioService::class.java)
            runCatching { if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AudioService::class.java)) }
        }
    }
}
