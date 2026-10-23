package app.pzip.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pzip.core.FileKind
import app.pzip.core.Files
import app.pzip.core.Prefs
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

enum class LoopMode { OFF, ALL, ONE }
enum class Decoder(val label: String) { AUTO("Auto (HW+)"), HW("Hardware"), SW("Software") }

data class PlayItem(val uri: Uri, val title: String, val isAudio: Boolean, val file: File? = null) {
    val key: String get() = file?.absolutePath ?: uri.toString()

    companion object {
        fun of(file: File) = PlayItem(Uri.fromFile(file), file.nameWithoutExtension, Files.kindOf(file.name) == FileKind.AUDIO, file)
    }
}

/**
 * One shared VLC player for the whole app, so playback can continue in the background
 * and the video screen can be closed and reopened without restarting.
 */
object Engine {
    private lateinit var app: Context
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private val main = Handler(Looper.getMainLooper())

    var playlist by mutableStateOf<List<PlayItem>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    val current: PlayItem? get() = playlist.getOrNull(index)

    var playing by mutableStateOf(false)
        private set
    var time by mutableLongStateOf(0L)
        private set
    var length by mutableLongStateOf(0L)
        private set
    var buffering by mutableFloatStateOf(100f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var ended by mutableStateOf(false)
        private set
    var videoSize by mutableStateOf(0 to 0)
        private set
    var hasVideo by mutableStateOf(false)
        private set
    var artist by mutableStateOf("")
        private set
    var album by mutableStateOf("")
        private set
    var artwork by mutableStateOf<String?>(null)
        private set
    var tracksVersion by mutableIntStateOf(0)
        private set

    var rate by mutableFloatStateOf(1f)
        private set
    var loop by mutableStateOf(LoopMode.OFF)
    var shuffle by mutableStateOf(false)
    var scale by mutableStateOf(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
        private set
    var decoder by mutableStateOf(Decoder.AUTO)
    var volumeBoost by mutableIntStateOf(100)
        private set
    var abStart by mutableLongStateOf(-1L)
    var abEnd by mutableLongStateOf(-1L)
    var sleepAt by mutableLongStateOf(0L)
    var sleepAtEnd by mutableStateOf(false)
    var equalizerPreset by mutableIntStateOf(-1)
        private set
    var recording by mutableStateOf(false)
        private set
    /** When on, videos keep playing as audio after leaving the player (VLC "play as audio"). */
    var backgroundAllowedForVideo by mutableStateOf(false)

    private var shuffleOrder: List<Int> = emptyList()
    private var softwareRetry = false

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun ensure(): MediaPlayer {
        player?.let { return it }
        val options = arrayListOf(
            "--audio-time-stretch",
            "--avcodec-skiploopfilter=1",
            "--avcodec-skip-frame=0",
            "--avcodec-skip-idct=0",
            "--subsdec-encoding=UTF-8",
            "--freetype-rel-fontsize=16",
            "--freetype-color=16777215",
            "--freetype-background-opacity=0",
            "--freetype-outline-thickness=4",
            // Leave room for the seek bar and bottom actions when controls are visible.
            "--sub-margin=200",
            "--network-caching=1500",
            "--file-caching=150",
            "--stats",
            "-v",
        )
        val vlc = LibVLC(app, options)
        val mp = MediaPlayer(vlc)
        mp.setEventListener { onEvent(it) }
        libVlc = vlc
        player = mp
        rate = Prefs.playerSpeed
        return mp
    }

    private fun onEvent(e: MediaPlayer.Event) {
        val mp = player ?: return
        when (e.type) {
            MediaPlayer.Event.Playing -> {
                playing = true
                ended = false
                error = null
                AudioService.update(app)
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                playing = false
                AudioService.update(app)
            }
            MediaPlayer.Event.TimeChanged -> {
                time = e.timeChanged
                if (abStart >= 0 && abEnd > abStart && time >= abEnd) mp.setTime(abStart)
                if (sleepAt in 1..System.currentTimeMillis()) {
                    sleepAt = 0
                    pause()
                }
                if (time % 5000 < 300) savePosition()
            }
            MediaPlayer.Event.LengthChanged -> length = e.lengthChanged
            MediaPlayer.Event.Buffering -> buffering = e.buffering
            MediaPlayer.Event.Vout -> {
                hasVideo = e.voutCount > 0
                readVideoSize()
            }
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> {
                tracksVersion++
                readVideoSize()
            }
            MediaPlayer.Event.EndReached -> {
                playing = false
                clearPosition()
                main.post { onEnded() }
            }
            MediaPlayer.Event.EncounteredError -> {
                playing = false
                if (!softwareRetry && decoder == Decoder.AUTO && current?.isAudio == false) {
                    softwareRetry = true
                    val failedIndex = index
                    val resumeAt = time
                    main.post {
                        if (index == failedIndex && playlist.isNotEmpty()) {
                            playAt(failedIndex, retry = true)
                            if (resumeAt > 0) main.postDelayed({ if (index == failedIndex) seekTo(resumeAt) }, 500)
                        }
                    }
                } else error = "Can't play this file with hardware or software decoding"
            }
        }
    }

    private fun readVideoSize() {
        val t = player?.currentVideoTrack ?: return
        videoSize = if (t.orientation == 5 || t.orientation == 6) t.height to t.width else t.width to t.height
    }

    private fun onEnded() {
        if (sleepAtEnd) {
            sleepAtEnd = false
            ended = true
            AudioService.update(app)
            return
        }
        when {
            loop == LoopMode.ONE -> playAt(index)
            hasNext() -> next()
            loop == LoopMode.ALL && playlist.isNotEmpty() -> playAt(if (shuffle) shuffleOrder.firstOrNull() ?: 0 else 0)
            else -> {
                ended = true
                AudioService.update(app)
            }
        }
    }

    // ---------- Playlist ----------

    /** Plays [file] with every other playable file of the same type in its folder as the playlist. */
    fun playFolder(file: File) {
        val kind = Files.kindOf(file.name)
        val siblings = file.parentFile?.listFiles()
            ?.filter { it.isFile && Files.kindOf(it.name) == kind && (Prefs.showHidden || !it.name.startsWith(".")) }
            ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            ?: listOf(file)
        val list = siblings.map { PlayItem.of(it) }
        play(list, siblings.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0))
    }

    fun play(list: List<PlayItem>, start: Int = 0) {
        playlist = list
        shuffleOrder = list.indices.shuffled()
        playAt(start)
    }

    fun playAt(i: Int, retry: Boolean = false) {
        val item = playlist.getOrNull(i) ?: return
        if (!retry) softwareRetry = false
        savePosition()
        val mp = ensure()
        index = i
        time = 0
        length = 0
        ended = false
        error = null
        abStart = -1
        abEnd = -1
        hasVideo = !item.isAudio
        videoSize = 0 to 0
        val media = Media(libVlc!!, item.uri)
        when (if (softwareRetry) Decoder.SW else decoder) {
            Decoder.AUTO -> media.setHWDecoderEnabled(true, false)
            Decoder.HW -> media.setHWDecoderEnabled(true, true)
            Decoder.SW -> media.setHWDecoderEnabled(false, false)
        }
        if (item.isAudio) media.addOption(":no-video")
        // Pick up subtitles that sit next to the video with the same name.
        item.file?.let { f ->
            f.parentFile?.listFiles()?.filter {
                it.nameWithoutExtension.startsWith(f.nameWithoutExtension) && Files.ext(it.name) in setOf("srt", "ass", "ssa", "vtt", "sub", "idx", "smi")
            }?.forEach { sub -> media.addSlave(IMedia.Slave(IMedia.Slave.Type.Subtitle, 4, Uri.fromFile(sub).toString())) }
        }
        mp.media = media
        media.release()
        mp.rate = rate
        // VLC needs the video surface before it starts, or the picture never appears.
        pendingStart = !item.isAudio && !surfaceReady && !AudioService.inBackground
        if (!pendingStart) mp.play()
        if (equalizerPreset >= 0) mp.setEqualizer(MediaPlayer.Equalizer.createFromPreset(equalizerPreset))
        if (volumeBoost != 100) mp.volume = volumeBoost
        val resume = if (Prefs.resumePlayback) Prefs.positionOf(item.key) else 0L
        if (resume > 5000) main.postDelayed({ if (index == i) player?.setTime(resume) }, 300)
        main.postDelayed({ readMeta(i) }, 800)
        AudioService.update(app)
    }

    private fun readMeta(i: Int) {
        if (i != index) return
        val m = player?.media ?: return
        runCatching {
            artist = m.getMeta(IMedia.Meta.Artist) ?: ""
            album = m.getMeta(IMedia.Meta.Album) ?: ""
            artwork = m.getMeta(IMedia.Meta.ArtworkURL)
            val title = m.getMeta(IMedia.Meta.Title)
            val item = playlist.getOrNull(i)
            if (item != null && item.file == null && !title.isNullOrBlank()) {
                playlist = playlist.toMutableList().also { it[i] = item.copy(title = title) }
            }
            m.release()
        }
        AudioService.update(app)
    }

    fun hasNext(): Boolean = if (shuffle) shuffleOrder.indexOf(index) < shuffleOrder.size - 1 else index < playlist.size - 1
    fun hasPrev(): Boolean = if (shuffle) shuffleOrder.indexOf(index) > 0 else index > 0

    fun next() {
        if (shuffle) shuffleOrder.getOrNull(shuffleOrder.indexOf(index) + 1)?.let { playAt(it) }
        else if (index < playlist.size - 1) playAt(index + 1)
    }

    fun prev() {
        if (time > 3000) {
            seekTo(0)
            return
        }
        if (shuffle) shuffleOrder.getOrNull(shuffleOrder.indexOf(index) - 1)?.let { playAt(it) }
        else if (index > 0) playAt(index - 1)
    }

    // ---------- Transport ----------

    fun toggle() {
        val mp = player ?: return
        if (ended) {
            playAt(index)
            return
        }
        if (mp.isPlaying) mp.pause() else mp.play()
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        player?.takeIf { !it.isPlaying }?.play()
    }

    fun seekTo(ms: Long) {
        val mp = player ?: return
        val target = ms.coerceIn(0, if (length > 0) length else Long.MAX_VALUE)
        mp.setTime(target, false)
        time = target
    }

    fun seekBy(delta: Long) = seekTo(time + delta)

    fun setSpeed(r: Float) {
        rate = r
        Prefs.playerSpeed = r
        player?.rate = r
    }

    fun setScaleType(s: MediaPlayer.ScaleType) {
        scale = s
        player?.videoScale = s
    }

    fun cycleScale(): String {
        val order = listOf(
            MediaPlayer.ScaleType.SURFACE_BEST_FIT to "Fit",
            MediaPlayer.ScaleType.SURFACE_FILL to "Stretch",
            MediaPlayer.ScaleType.SURFACE_FIT_SCREEN to "Crop",
            MediaPlayer.ScaleType.SURFACE_16_9 to "16:9",
            MediaPlayer.ScaleType.SURFACE_4_3 to "4:3",
            MediaPlayer.ScaleType.SURFACE_ORIGINAL to "Original",
        )
        val i = order.indexOfFirst { it.first == scale }
        val next = order[(i + 1) % order.size]
        setScaleType(next.first)
        return next.second
    }

    /** VLC volume goes up to 200% for quiet videos. */
    fun setBoost(v: Int) {
        volumeBoost = v.coerceIn(0, 200)
        player?.volume = volumeBoost
    }

    fun audioTracks(): List<Pair<Int, String>> = player?.audioTracks?.map { it.id to it.name }.orEmpty()
    fun subtitleTracks(): List<Pair<Int, String>> = player?.spuTracks?.map { it.id to it.name }.orEmpty()
    fun videoTracks(): List<Pair<Int, String>> = player?.videoTracks?.map { it.id to it.name }.orEmpty()
    val audioTrack: Int get() = player?.audioTrack ?: -1
    val subtitleTrack: Int get() = player?.spuTrack ?: -1
    fun setAudioTrack(id: Int) {
        player?.audioTrack = id
        tracksVersion++
    }
    fun setSubtitleTrack(id: Int) {
        player?.spuTrack = id
        tracksVersion++
    }
    fun addSubtitle(file: File) {
        player?.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(file), true)
        tracksVersion++
    }

    /** Delays are in milliseconds here; VLC uses microseconds. */
    val subtitleDelay: Long get() = (player?.spuDelay ?: 0L) / 1000
    val audioDelay: Long get() = (player?.audioDelay ?: 0L) / 1000
    fun setSubtitleDelay(ms: Long) {
        player?.spuDelay = ms * 1000
        tracksVersion++
    }
    fun setAudioDelay(ms: Long) {
        player?.audioDelay = ms * 1000
        tracksVersion++
    }

    fun equalizerPresets(): List<String> = (0 until MediaPlayer.Equalizer.getPresetCount()).map { MediaPlayer.Equalizer.getPresetName(it) }
    fun setEqualizer(preset: Int) {
        equalizerPreset = preset
        val mp = player ?: return
        if (preset < 0) mp.setEqualizer(null) else mp.setEqualizer(MediaPlayer.Equalizer.createFromPreset(preset))
    }

    fun chapters(): List<Pair<Long, String>> = runCatching {
        player?.getChapters(-1)?.mapIndexed { i, c -> c.timeOffset to (c.name ?: "Chapter ${i + 1}") }.orEmpty()
    }.getOrDefault(emptyList())

    fun info(): List<Pair<String, String>> {
        val mp = player ?: return emptyList()
        val list = mutableListOf<Pair<String, String>>()
        current?.let { list += "File" to (it.file?.name ?: it.uri.toString()) }
        run {
            val (w, h) = videoSize
            if (w > 0) list += "Resolution" to "${w}×$h"
        }
        mp.currentVideoTrack?.let { t ->
            if (t.frameRateDen > 0) list += "Frame rate" to "%.2f fps".format(t.frameRateNum.toFloat() / t.frameRateDen)
            t.codec?.let { list += "Video codec" to it }
        }
        if (length > 0) list += "Duration" to Files.formatDuration(length)
        current?.file?.let { list += "Size" to Files.formatSize(it.length()) }
        list += "Audio tracks" to audioTracks().count { it.first >= 0 }.toString()
        list += "Subtitle tracks" to subtitleTracks().count { it.first >= 0 }.toString()
        list += "Decoder" to decoder.label
        return list
    }

    /** Records the stream to the Movies/pzip folder (VLC's record feature). */
    fun toggleRecord(): String? {
        val mp = player ?: return null
        return if (!recording) {
            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "pzip")
            dir.mkdirs()
            if (mp.record(dir.absolutePath)) {
                recording = true
                dir.absolutePath
            } else null
        } else {
            mp.record(null)
            recording = false
            null
        }
    }

    fun attach(layout: VLCVideoLayout) {
        val mp = ensure()
        runCatching { mp.detachViews() }
        mp.attachViews(layout, null, true, false)
        mp.videoScale = scale
        surfaceReady = true
        if (pendingStart) {
            pendingStart = false
            mp.play()
        }
    }

    fun detach() {
        surfaceReady = false
        runCatching { player?.detachViews() }
    }

    private var surfaceReady = false
    private var pendingStart = false

    fun updateSurfaces() {
        player?.updateVideoSurfaces()
    }

    private fun savePosition() {
        val item = current ?: return
        if (length > 60_000 && time > 5000 && time < length - 5000) Prefs.savePosition(item.key, time)
    }

    private fun clearPosition() {
        current?.let { Prefs.savePosition(it.key, 0) }
    }

    fun stop() {
        pendingStart = false
        savePosition()
        player?.stop()
        playing = false
        AudioService.stop(app)
    }

    /** Fully releases VLC (when the user closes playback). */
    fun release() {
        pendingStart = false
        savePosition()
        runCatching {
            player?.detachViews()
            player?.release()
            libVlc?.release()
        }
        player = null
        libVlc = null
        playing = false
        playlist = emptyList()
        AudioService.stop(app)
    }

    val active: Boolean get() = player != null && playlist.isNotEmpty()

    fun openPlayer(context: Context) {
        context.startActivity(Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}
