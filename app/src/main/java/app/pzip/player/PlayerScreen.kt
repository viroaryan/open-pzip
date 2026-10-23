package app.pzip.player

import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.pzip.core.Files
import app.pzip.core.Prefs
import app.pzip.ui.components.Chip
import app.pzip.ui.components.Gap
import app.pzip.ui.components.IconBtn
import app.pzip.ui.components.PzipSpark
import app.pzip.ui.components.SettingRow
import app.pzip.ui.components.Sheet
import app.pzip.ui.components.TextLink
import app.pzip.ui.theme.P
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import kotlin.math.abs

private enum class Panel { NONE, AUDIO, SUBS, SPEED, MORE, PLAYLIST, INFO, SUBFILE }

@Composable
fun PlayerScreen(activity: PlayerActivity) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var feedback by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var showRemaining by remember { mutableStateOf(false) }
    var interaction by remember { mutableStateOf(0) }
    val h = app.pzip.ui.rememberHaptics()
    val item = Engine.current
    val isAudio = item?.isAudio == true || (!Engine.hasVideo && Engine.length > 0 && Engine.videoSize.first == 0)

    // Auto-hide controls while playing.
    LaunchedEffect(controls, Engine.playing, interaction, panel) {
        if (controls && Engine.playing && panel == Panel.NONE && !isAudio) {
            delay(3500)
            controls = false
        }
    }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(900)
            feedback = null
        }
    }
    LaunchedEffect(Engine.videoSize, activity.orientationLock) { activity.applyOrientation() }
    LaunchedEffect(controls) { if (!controls) activity.hideSystemUi() }

    BackHandler {
        when {
            panel != Panel.NONE -> panel = Panel.NONE
            locked -> feedback = Icons.Rounded.Lock to "Tap the lock to unlock"
            else -> {
                if (!isAudio && !Engine.backgroundAllowedForVideo) Engine.pause()
                activity.finish()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> VLCVideoLayout(ctx).also { Engine.attach(it) } },
            onRelease = { Engine.detach() },
            modifier = Modifier.fillMaxSize(),
        )

        if (isAudio) AudioArt()

        // Gesture layer
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            var mode by remember { mutableStateOf(0) } // 0 none, 1 seek, 2 brightness, 3 volume
            var startTime by remember { mutableStateOf(0L) }
            var acc by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            var startLevel by remember { mutableFloatStateOf(0f) }
            var lastStep by remember { mutableStateOf(Int.MIN_VALUE) }
            fun step(s: Int) {
                if (s != lastStep) {
                    if (lastStep != Int.MIN_VALUE) h.tick()
                    lastStep = s
                }
            }
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = {
                                interaction++
                                controls = !controls
                            },
                            onDoubleTap = { o ->
                                if (locked) return@detectTapGestures
                                h.tap()
                                when {
                                    o.x < widthPx / 3 -> {
                                        Engine.seekBy(-10_000)
                                        feedback = Icons.Rounded.FastRewind to "−10s"
                                    }
                                    o.x > widthPx * 2 / 3 -> {
                                        Engine.seekBy(10_000)
                                        feedback = Icons.Rounded.FastForward to "+10s"
                                    }
                                    else -> Engine.toggle()
                                }
                            },
                        )
                    }
                    .pointerInput(locked, Prefs.playerGestures) {
                        if (locked || !Prefs.playerGestures) return@pointerInput
                        detectDragGestures(
                            onDragStart = { o ->
                                mode = 0
                                acc = androidx.compose.ui.geometry.Offset.Zero
                                lastStep = Int.MIN_VALUE
                                startTime = Engine.time
                                startLevel = if (o.x < widthPx / 2) {
                                    mode = -2
                                    activity.brightness()
                                } else {
                                    mode = -3
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    if (cur >= max && Engine.volumeBoost > 100) 1f + (Engine.volumeBoost - 100) / 100f else cur.toFloat() / max
                                }
                            },
                            onDragEnd = {
                                if (mode == 1) seekPreview?.let { Engine.seekTo(it) }
                                seekPreview = null
                                mode = 0
                            },
                            onDragCancel = {
                                seekPreview = null
                                mode = 0
                            },
                        ) { change, drag ->
                            change.consume()
                            acc += drag
                            if (mode < 0 && (abs(acc.x) > 24 || abs(acc.y) > 24)) {
                                mode = if (abs(acc.x) > abs(acc.y)) 1 else -mode
                            }
                            when (mode) {
                                1 -> {
                                    val len = Engine.length.coerceAtLeast(1)
                                    val delta = (acc.x / widthPx * minOf(len, 300_000L) * 1.2f).toLong()
                                    val target = (startTime + delta).coerceIn(0, len)
                                    seekPreview = target
                                    step((target / 5000).toInt())
                                    val sign = if (delta >= 0) "+" else "−"
                                    feedback = (if (delta >= 0) Icons.Rounded.FastForward else Icons.Rounded.FastRewind) to
                                        "${Files.formatDuration(target)}  ($sign${Files.formatDuration(abs(delta))})"
                                }
                                2 -> {
                                    val v = (startLevel - acc.y / heightPx * 1.4f).coerceIn(0.01f, 1f)
                                    activity.setBrightness(v)
                                    step((v * 20).toInt())
                                    feedback = Icons.Rounded.BrightnessMedium to "Brightness ${(v * 100).toInt()}%"
                                }
                                3 -> {
                                    // Up to 200%: the system volume first, then VLC's boost.
                                    val v = (startLevel - acc.y / heightPx * 1.6f).coerceIn(0f, 2f)
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (minOf(v, 1f) * max).toInt(), 0)
                                    Engine.setBoost(if (v > 1f) (100 + (v - 1f) * 100).toInt() else 100)
                                    step((v * 20).toInt())
                                    feedback = Icons.AutoMirrored.Rounded.VolumeUp to "Volume ${(v * 100).toInt()}%"
                                }
                            }
                        }
                    },
            )
        }

        // Gesture feedback
        feedback?.let { (icon, text) ->
            Row(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(18.dp)).background(Color(0xB0000000)).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        if (Engine.playing && Engine.buffering < 100f && Engine.time == 0L) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(40.dp))
        }

        Engine.error?.let { err ->
            Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xCC111111)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(err, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Gap(6)
                Text("The file may be damaged or use DRM protection.", color = Color(0xAAFFFFFF), style = MaterialTheme.typography.bodySmall)
                Gap(10)
                TextLink("Try software decoding", { Engine.decoder = Decoder.SW; Engine.playAt(Engine.index) }, Color.White)
            }
        }

        if (!activity.inPip) {
            if (locked) {
                AnimatedVisibility(controls, Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(20.dp), enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0x99000000)).clickable { locked = false; controls = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.LockOpen, "Unlock", tint = Color.White)
                    }
                }
            } else {
                AnimatedVisibility(controls, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                    Controls(
                        activity, isAudio, showRemaining, { showRemaining = !showRemaining },
                        onPanel = { panel = it; interaction++ },
                        onLock = { h.longPress(); locked = true; feedback = Icons.Rounded.Lock to "Screen locked" },
                        onInteract = { interaction++ },
                        onFeedback = { feedback = it },
                    )
                }
            }
        }
    }

    when (panel) {
        Panel.NONE -> {}
        Panel.AUDIO -> AudioPanel { panel = Panel.NONE }
        Panel.SUBS -> SubtitlePanel({ panel = Panel.SUBFILE }) { panel = Panel.NONE }
        Panel.SUBFILE -> SubtitleFilePanel { panel = Panel.NONE }
        Panel.SPEED -> SpeedPanel { panel = Panel.NONE }
        Panel.MORE -> MorePanel(activity, { panel = it }) { panel = Panel.NONE }
        Panel.PLAYLIST -> PlaylistPanel { panel = Panel.NONE }
        Panel.INFO -> Sheet({ panel = Panel.NONE }, "Media info") {
            Engine.info().forEach { (k, v) -> app.pzip.ui.screens.InfoRow(k, v) }
        }
    }
}

@Composable
private fun AudioArt() {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(P.c.accentSoft, Color(0xFF0E0E0E), Color.Black))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 140.dp)) {
            val art = Engine.artwork?.takeIf { it.startsWith("file://") }?.let { File(android.net.Uri.parse(it).path ?: "") }?.takeIf { it.exists() }
            val bmp = remember(art) { art?.let { app.pzip.ui.components.Thumbs.decodeSampled(it, 600) } }
            Box(Modifier.size(240.dp).clip(RoundedCornerShape(36.dp)).background(Color(0xFF1C1C1C)), contentAlignment = Alignment.Center) {
                if (bmp != null) androidx.compose.foundation.Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else PzipSpark(96.dp, P.c.accent)
            }
        }
    }
}

@Composable
private fun Controls(
    activity: PlayerActivity,
    isAudio: Boolean,
    showRemaining: Boolean,
    toggleRemaining: () -> Unit,
    onPanel: (Panel) -> Unit,
    onLock: () -> Unit,
    onInteract: () -> Unit,
    onFeedback: (Pair<ImageVector, String>) -> Unit,
) {
    val item = Engine.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    Box(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .safeDrawingPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { activity.onBackPressedDispatcher.onBackPressed() }, tint = Color.White)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(item?.title ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    Engine.artist.takeIf { it.isNotEmpty() },
                    if (Engine.playlist.size > 1) "${Engine.index + 1} of ${Engine.playlist.size}" else null,
                    Engine.videoSize.takeIf { it.first > 0 }?.let { "${it.first}×${it.second}" },
                    if (Engine.recording) "● REC" else null,
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = Color(0xB3FFFFFF), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            IconBtn(Icons.Rounded.Audiotrack, "Audio", { onPanel(Panel.AUDIO) }, tint = Color.White)
            if (!isAudio) IconBtn(Icons.Rounded.ClosedCaption, "Subtitles", { onPanel(Panel.SUBS) }, tint = Color.White)
            IconBtn(Icons.Rounded.MoreVert, "More", { onPanel(Panel.MORE) }, tint = Color.White)
        }

        // Center transport
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RoundBtn(Icons.Rounded.SkipPrevious, "Previous", 54.dp, Engine.hasPrev() || Engine.time > 3000) { Engine.prev(); onInteract() }
            RoundBtn(
                when {
                    Engine.ended -> Icons.Rounded.Replay
                    Engine.playing -> Icons.Rounded.Pause
                    else -> Icons.Rounded.PlayArrow
                },
                "Play/Pause", 76.dp, true, strong = true,
            ) { Engine.toggle(); onInteract() }
            RoundBtn(Icons.Rounded.SkipNext, "Next", 54.dp, Engine.hasNext()) { Engine.next(); onInteract() }
        }

        // Bottom: seek bar and tools
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .safeDrawingPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            val len = Engine.length.coerceAtLeast(0)
            val pos = dragging ?: if (len > 0) Engine.time.toFloat() / len else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Files.formatDuration((pos * len).toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(62.dp))
                Box(Modifier.weight(1f)) {
                    if (Engine.abStart >= 0 && len > 0) {
                        Text("A", color = P.c.accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth((Engine.abStart.toFloat() / len).coerceIn(0.02f, 1f)))
                    }
                    Slider(
                        value = pos.coerceIn(0f, 1f),
                        onValueChange = { dragging = it; onInteract() },
                        onValueChangeFinished = {
                            dragging?.let { Engine.seekTo((it * len).toLong()) }
                            dragging = null
                        },
                        enabled = len > 0,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = P.c.accent, inactiveTrackColor = Color(0x55FFFFFF)),
                    )
                }
                Text(
                    if (showRemaining && len > 0) "−" + Files.formatDuration(len - (pos * len).toLong()) else Files.formatDuration(len),
                    color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(66.dp).clickable(onClick = toggleRemaining).padding(start = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.Lock, "Lock screen", onLock, tint = Color.White)
                if (!isAudio) IconBtn(Icons.Rounded.AspectRatio, "Aspect ratio", { onFeedback(Icons.Rounded.AspectRatio to Engine.cycleScale()); onInteract() }, tint = Color.White)
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable { onPanel(Panel.SPEED) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Speed, "Speed", tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${trim(Engine.rate)}×", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                if (!isAudio) IconBtn(Icons.Rounded.PictureInPictureAlt, "Picture in picture", { activity.enterPip() }, tint = Color.White)
                IconBtn(Icons.AutoMirrored.Rounded.PlaylistPlay, "Playlist", { onPanel(Panel.PLAYLIST) }, tint = Color.White)
            }
        }
    }
}

private fun trim(f: Float) = if (f % 1f == 0f) f.toInt().toString() else "%.2f".format(f).trimEnd('0').trimEnd('.')

@Composable
private fun RoundBtn(icon: ImageVector, desc: String, size: androidx.compose.ui.unit.Dp, enabled: Boolean, strong: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (strong) Color(0x33FFFFFF) else Color(0x22000000)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(size * 0.5f))
    }
}

// ---------- Panels ----------

@Composable
private fun TrackList(tracks: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit) {
    tracks.forEach { (id, name) ->
        Row(
            Modifier.fillMaxWidth().clickable { onPick(id) }.padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (id == selected) P.c.accent else Color.Transparent))
            Spacer(Modifier.width(14.dp))
            Text(name, color = if (id == selected) P.c.text else P.c.textMuted, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DelayRow(label: String, value: Long, onChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Chip("−", false, { onChange(value - 100) })
        Text("${value} ms", color = P.c.text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp))
        Chip("+", false, { onChange(value + 100) })
        TextLink("Reset", { onChange(0) })
    }
}

@Composable
private fun AudioPanel(onDismiss: () -> Unit) {
    val v = Engine.tracksVersion
    Sheet(onDismiss, "Audio") {
        key(v) { TrackList(Engine.audioTracks(), Engine.audioTrack) { Engine.setAudioTrack(it) } }
        DelayRow("Audio delay", Engine.audioDelay) { Engine.setAudioDelay(it) }
        Text("Volume boost · ${Engine.volumeBoost}%", color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 22.dp, top = 10.dp))
        Slider(
            value = Engine.volumeBoost.toFloat(), onValueChange = { Engine.setBoost(it.toInt()) }, valueRange = 0f..200f,
            colors = SliderDefaults.colors(thumbColor = P.c.ink, activeTrackColor = P.c.accent, inactiveTrackColor = P.c.cardStrong),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text("Equalizer", color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 6.dp))
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
            Chip("Off", Engine.equalizerPreset < 0, { Engine.setEqualizer(-1) })
            Engine.equalizerPresets().forEachIndexed { i, name -> Chip(name, Engine.equalizerPreset == i, { Engine.setEqualizer(i) }) }
        }
        Gap(10)
    }
}

@Composable
private fun key(v: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(v) { content() }

@Composable
private fun SubtitlePanel(onLoadFile: () -> Unit, onDismiss: () -> Unit) {
    val v = Engine.tracksVersion
    Sheet(onDismiss, "Subtitles") {
        key(v) { TrackList(Engine.subtitleTracks().ifEmpty { listOf(-1 to "None") }, Engine.subtitleTrack) { Engine.setSubtitleTrack(it) } }
        SettingRow("Load subtitle file…", "SRT, ASS, SSA, VTT, SUB", Icons.Rounded.ClosedCaption, onClick = onLoadFile)
        DelayRow("Subtitle delay", Engine.subtitleDelay) { Engine.setSubtitleDelay(it) }
        Gap(8)
    }
}

@Composable
private fun SubtitleFilePanel(onDismiss: () -> Unit) {
    val subs = remember {
        val dirs = listOfNotNull(Engine.current?.file?.parentFile, Files.downloads())
        dirs.flatMap { d -> d.listFiles()?.filter { Files.ext(it.name) in setOf("srt", "ass", "ssa", "vtt", "sub", "smi", "txt") }.orEmpty() }.distinct()
    }
    Sheet(onDismiss, "Choose subtitles") {
        if (subs.isEmpty()) Text("No subtitle files in this folder or Downloads.", color = P.c.textMuted, modifier = Modifier.padding(22.dp))
        subs.forEach { f -> SettingRow(f.name, f.parentFile?.name, Icons.Rounded.ClosedCaption, onClick = { Engine.addSubtitle(f); onDismiss() }) }
    }
}

@Composable
private fun SpeedPanel(onDismiss: () -> Unit) {
    Sheet(onDismiss, "Playback speed · ${trim(Engine.rate)}×") {
        Slider(
            value = Engine.rate, onValueChange = { Engine.setSpeed((it * 20).toInt() / 20f) }, valueRange = 0.25f..4f,
            colors = SliderDefaults.colors(thumbColor = P.c.ink, activeTrackColor = P.c.accent, inactiveTrackColor = P.c.cardStrong),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
            listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f).forEach { s -> Chip("${trim(s)}×", Engine.rate == s, { Engine.setSpeed(s) }) }
        }
        Gap(12)
    }
}

@Composable
private fun MorePanel(activity: PlayerActivity, open: (Panel) -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    Sheet(onDismiss, "More") {
        SettingRow("Repeat", listOf("Off", "All", "One")[Engine.loop.ordinal],
            if (Engine.loop == LoopMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            onClick = { Engine.loop = LoopMode.entries[(Engine.loop.ordinal + 1) % LoopMode.entries.size] })
        SettingRow("Screen rotation", listOf("Auto rotate", "Landscape", "Portrait")[activity.orientationLock],
            Icons.Rounded.ScreenRotation, onClick = { activity.orientationLock = (activity.orientationLock + 1) % 3 })
        Text("Sleep timer" + if (Engine.sleepAt > now) " · ${Files.formatDuration(Engine.sleepAt - now)} left" else if (Engine.sleepAtEnd) " · end of track" else "",
            color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 22.dp, bottom = 6.dp))
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
            Chip("Off", Engine.sleepAt == 0L && !Engine.sleepAtEnd, { Engine.sleepAt = 0; Engine.sleepAtEnd = false })
            listOf(15, 30, 45, 60, 90).forEach { m -> Chip("$m min", false, { Engine.sleepAt = System.currentTimeMillis() + m * 60_000L; Engine.sleepAtEnd = false }) }
            Chip("End of track", Engine.sleepAtEnd, { Engine.sleepAtEnd = true; Engine.sleepAt = 0 })
        }
        Text("A-B repeat", color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 6.dp))
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
            Chip(if (Engine.abStart >= 0) "A ${Files.formatDuration(Engine.abStart)}" else "Set A", Engine.abStart >= 0, { Engine.abStart = Engine.time; Engine.abEnd = -1 })
            Chip(if (Engine.abEnd >= 0) "B ${Files.formatDuration(Engine.abEnd)}" else "Set B", Engine.abEnd >= 0, { if (Engine.abStart >= 0 && Engine.time > Engine.abStart) Engine.abEnd = Engine.time })
            Chip("Clear", false, { Engine.abStart = -1; Engine.abEnd = -1 })
        }
        Text("Decoder", color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 6.dp))
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
            Decoder.entries.forEach { d ->
                Chip(d.label, Engine.decoder == d, {
                    if (Engine.decoder != d) {
                        Engine.decoder = d
                        val t = Engine.time
                        Engine.playAt(Engine.index)
                        Engine.seekTo(t)
                    }
                })
            }
        }
        Gap(8)
        val chapters = remember { Engine.chapters() }
        if (chapters.size > 1) {
            Text("Chapters", color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 6.dp))
            app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
                chapters.forEach { (t, name) -> Chip(name, false, { Engine.seekTo(t) }) }
            }
        }
        app.pzip.ui.components.SettingSwitch("Shuffle", icon = Icons.Rounded.Shuffle, checked = Engine.shuffle, onChange = { Engine.shuffle = it })
        app.pzip.ui.components.SettingSwitch("Keep playing in background", "Play video as audio when you leave", Icons.Rounded.Headphones, Engine.backgroundAllowedForVideo) { Engine.backgroundAllowedForVideo = it }
        SettingRow(if (Engine.recording) "Stop recording" else "Record", "Saves to Movies/pzip", Icons.Rounded.FiberManualRecord, onClick = {
            val path = Engine.toggleRecord()
            app.pzip.ui.components.Toaster.show(if (path != null) "Recording…" else if (!Engine.recording) "Recording saved" else "Can't record this")
            onDismiss()
        })
        SettingRow("Media info", icon = Icons.Rounded.Info, onClick = { open(Panel.INFO) })
        Gap(6)
    }
}

@Composable
private fun PlaylistPanel(onDismiss: () -> Unit) {
    Sheet(onDismiss, "Playlist · ${Engine.playlist.size}") {
        Engine.playlist.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().background(if (i == Engine.index) P.c.accentSoft else Color.Transparent).clickable { Engine.playAt(i); onDismiss() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (p.file != null) app.pzip.ui.components.FileThumb(p.file, 48.dp, 10.dp) else app.pzip.ui.components.KindTile(app.pzip.core.FileKind.VIDEO, 48.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(p.title, color = P.c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    p.file?.let { Text(Files.formatSize(it.length()), color = P.c.textMuted, style = MaterialTheme.typography.bodySmall) }
                }
                if (i == Engine.index) Icon(Icons.Rounded.PlayArrow, null, tint = P.c.accent)
            }
        }
    }
}
