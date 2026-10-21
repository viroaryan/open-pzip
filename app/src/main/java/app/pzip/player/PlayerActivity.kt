package app.pzip.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.pzip.core.FileKind
import app.pzip.core.Files
import app.pzip.ui.theme.PzipTheme
import java.io.File

/** Full-screen VLC player for video and audio. */
class PlayerActivity : ComponentActivity() {
    var inPip by mutableStateOf(false)
        private set
    var orientationLock by mutableStateOf(0) // 0 auto, 1 landscape, 2 portrait

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        if (savedInstanceState == null) handle(intent)
        setContent { PzipTheme(forceDark = true) { PlayerScreen(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        val file = when (uri.scheme) {
            "file" -> uri.path?.let { File(it).canonicalFile }
            "content" -> runCatching {
                contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { File(it) } else null }
            }.getOrNull()?.takeIf { it.canRead() }
            else -> null
        }
        if (file != null && file.exists()) {
            Engine.playFolder(file)
        } else {
            val title = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment ?: "Stream"
            val isAudio = intent.type?.startsWith("audio") == true || Files.kindOf(title) == FileKind.AUDIO
            Engine.play(listOf(PlayItem(uri, title.substringBeforeLast('.'), isAudio)))
        }
    }

    fun hideSystemUi() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Rotates to match the video, like VLC's automatic orientation. */
    fun applyOrientation() {
        requestedOrientation = when (orientationLock) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> {
                val (w, h) = Engine.videoSize
                when {
                    !Engine.hasVideo || w == 0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    w > h -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
    }

    fun setBrightness(v: Float) {
        window.attributes = window.attributes.apply { screenBrightness = v.coerceIn(0.01f, 1f) }
    }

    fun brightness(): Float = window.attributes.screenBrightness.takeIf { it >= 0 } ?: runCatching {
        android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)

    fun enterPip(): Boolean {
        if (Build.VERSION.SDK_INT < 26 || !Engine.hasVideo) return false
        val (w, h) = Engine.videoSize
        val ratio = if (w > 0 && h > 0) Rational(w, h).let { r ->
            // Android only allows ratios between 1:2.39 and 2.39:1.
            val f = w.toFloat() / h
            if (f > 2.39f) Rational(239, 100) else if (f < 0.42f) Rational(100, 239) else r
        } else Rational(16, 9)
        return runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build()) }.getOrDefault(false)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Engine.playing && Engine.hasVideo && !Engine.backgroundAllowedForVideo) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Engine.updateSurfaces()
    }

    override fun onStart() {
        super.onStart()
        AudioService.inBackground = false
        AudioService.stop(this)
    }

    override fun onStop() {
        super.onStop()
        val audioOnly = Engine.current?.isAudio == true || !Engine.hasVideo
        if (Engine.playing && (audioOnly || Engine.backgroundAllowedForVideo)) {
            AudioService.inBackground = true
            AudioService.update(this)
        } else if (!isChangingConfigurations) {
            Engine.pause()
        }
        if (inPip && isFinishing) Engine.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations && !AudioService.inBackground) Engine.detach()
    }

    companion object {
        fun open(activity: ComponentActivity, uri: Uri) {
            activity.startActivity(Intent(activity, PlayerActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri))
        }
    }
}
