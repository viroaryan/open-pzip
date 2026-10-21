package app.pzip.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pzip.core.Accent
import app.pzip.core.Prefs
import app.pzip.core.Security
import app.pzip.core.ThemeMode
import app.pzip.ui.components.Gap
import app.pzip.ui.components.InkButton
import app.pzip.ui.components.PzipSpark
import app.pzip.ui.components.TextLink
import app.pzip.ui.screens.PinPad
import app.pzip.ui.theme.P
import app.pzip.ui.theme.accentPreview
import kotlinx.coroutines.launch
import kotlin.math.sin

// Soft Pinterest-style pastels used by the illustrations.
private val Sky = Color(0xFFDDEFFB)
private val Mint = Color(0xFFDDF3E6)
private val Lilac = Color(0xFFEAE4FB)
private val Peach = Color(0xFFFCE7DA)
private val Butter = Color(0xFFFBF1D2)
private val Rose = Color(0xFFFBE3E8)
private val Ink = Color(0xFF141413)

object Perms {
    fun files(c: Context) = hasStorageAccess(c)
    fun notifications(c: Context) =
        if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(c).areNotificationsEnabled()
    fun installs(c: Context) = c.packageManager.canRequestPackageInstalls()

    fun openFiles(c: Context) {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${c.packageName}"))) }
                .onFailure { c.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    fun openInstalls(c: Context) {
        runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))) }
    }

    fun openNotificationSettings(c: Context) {
        runCatching { c.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)) }
    }
}

/** First-run flow: welcome → tour → permissions → personalize → security → done. */
@Composable
fun Onboarding(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(step > 0) { step-- }
    Box(Modifier.fillMaxSize().background(P.c.bg)) {
        AnimatedContent(
            targetState = step, label = "onboarding",
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320)) { it * dir / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it * dir / 3 } + fadeOut(tween(200)))
            },
        ) { s ->
            when (s) {
                0 -> Tour { step = 1 }
                1 -> PermissionsStep(required = false) { step = 2 }
                2 -> PersonalizeStep { step = 3 }
                3 -> SecurityStep { step = 4 }
                else -> DoneStep(onFinished)
            }
        }
    }
}

// ---------- Tour ----------

private data class TourPage(val title: String, val text: String, val art: @Composable BoxScope.() -> Unit)

@Composable
private fun Tour(onDone: () -> Unit) {
    val pages = listOf(
        TourPage("Everything,\nbeautifully in one place", "Files, gallery, video player and archiver — one light, private app.") { WelcomeArt() },
        TourPage("Plays every video\nyou throw at it", "Powered by the VLC engine. MKV, AVI, HEVC, subtitles, 4K, streams.") { PlayerArt() },
        TourPage("Opens every archive.\nLocks what matters.", "RAR, RAR5, 7Z, ZIP, TAR. Create ZIPs with AES-256 encryption.") { ArchiveArt() },
        TourPage("A cleaner phone,\na safer you", "Free up space in a tap. Hide private files in an encrypted Safe folder.") { CheerArt() },
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PzipSpark(26.dp)
            Spacer(Modifier.width(8.dp))
            Text("pzip", style = MaterialTheme.typography.titleLarge, color = P.c.text, modifier = Modifier.weight(1f))
            if (pager.currentPage < pages.lastIndex) TextLink("Skip", onDone, P.c.textMuted)
        }
        val wide = isWide()
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = pages[i]
            if (wide) {
                // Landscape: illustration on the left, words on the right.
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FitArt(Modifier.weight(1f).fillMaxHeight(), p.art)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                        Text(p.title, style = MaterialTheme.typography.headlineMedium, color = P.c.text)
                        Text(p.text, style = MaterialTheme.typography.bodyLarge, color = P.c.textMuted, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    FitArt(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp), p.art)
                    Text(p.title, style = MaterialTheme.typography.headlineMedium, color = P.c.text, modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 18.dp))
                    Text(p.text, style = MaterialTheme.typography.bodyLarge, color = P.c.textMuted, modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = if (wide) 6.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size) { i ->
                    val w by animateFloatAsState(if (i == pager.currentPage) 26f else 8f, spring(), label = "dot")
                    Box(Modifier.height(8.dp).width(w.dp).clip(RoundedCornerShape(50)).background(if (i == pager.currentPage) P.c.ink else P.c.cardStrong))
                }
            }
            InkButton(if (pager.currentPage == pages.lastIndex) "Get started" else "Next", {
                if (pager.currentPage == pages.lastIndex) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            })
        }
    }
}

/** Draws an illustration at its natural size and scales it down to fit short screens (landscape, small phones). */
@Composable
private fun FitArt(modifier: Modifier, art: @Composable BoxScope.() -> Unit, natural: Dp = 480.dp) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val s = minOf(maxHeight / natural, maxWidth / 360.dp).coerceIn(0.2f, 1f)
        Box(
            Modifier.requiredHeight(natural).requiredWidth(maxWidth / s).scale(s),
            contentAlignment = Alignment.Center,
            content = art,
        )
    }
}


/** Gentle floating motion so the illustrations feel alive. */
@Composable
private fun floatOffset(phase: Float, amp: Float = 6f): Float {
    val t = rememberInfiniteTransition(label = "float")
    val v by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "f")
    return sin(v + phase) * amp
}

@Composable
private fun Tile(color: Color, height: Dp, phase: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val y = floatOffset(phase)
    Box(
        modifier.fillMaxWidth().height(height).offset(y = y.dp).clip(RoundedCornerShape(26.dp)).background(color),
        content = content,
    )
}

@Composable
private fun TileLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Ink, style = MaterialTheme.typography.labelMedium)
    }
}

/** Masonry collage, like a Pinterest board of your phone's content. */
@Composable
private fun BoxScope.CollageArt() {
    Row(Modifier.fillMaxSize().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Peach, 190.dp, 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFFF6B48F), radius = size.width * 0.18f, center = Offset(size.width * 0.68f, size.height * 0.32f))
                    val hills = Path().apply {
                        moveTo(0f, size.height * 0.78f)
                        quadraticBezierTo(size.width * 0.3f, size.height * 0.52f, size.width * 0.6f, size.height * 0.74f)
                        quadraticBezierTo(size.width * 0.82f, size.height * 0.62f, size.width, size.height * 0.7f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(hills, Color(0xFFE99A74))
                }
                TileLabel(Icons.Rounded.Image, "Photos", Modifier.align(Alignment.TopStart).padding(12.dp))
            }
            Tile(Lilac, 120.dp, 1.4f) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FolderZip, null, tint = Color(0xFF7B68D9), modifier = Modifier.size(38.dp))
                    Text("RAR · 7Z · ZIP", color = Ink, style = MaterialTheme.typography.labelMedium)
                }
            }
            Tile(Butter, 110.dp, 2.6f) {
                Canvas(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 46.dp, bottom = 12.dp)) {
                    val bars = floatArrayOf(0.4f, 0.8f, 0.55f, 1f, 0.65f, 0.3f, 0.75f, 0.5f, 0.9f)
                    val w = size.width / (bars.size * 2f)
                    bars.forEachIndexed { i, h ->
                        val x = i * w * 2 + w / 2
                        drawLine(Color(0xFFD9A834), Offset(x, size.height * (1 - h) / 2 + size.height * 0.1f), Offset(x, size.height * (1 + h) / 2), strokeWidth = w, cap = StrokeCap.Round)
                    }
                }
                TileLabel(Icons.Rounded.MusicNote, "Music", Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }
        Column(Modifier.weight(1f).padding(top = 34.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Ink, 150.dp, 0.8f) {
                Box(Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Box(Modifier.align(Alignment.BottomStart).padding(14.dp).fillMaxWidth(0.62f).height(4.dp).clip(RoundedCornerShape(50)).background(P.c.accent))
            }
            Tile(Mint, 170.dp, 2f) {
                Column(Modifier.padding(16.dp)) {
                    repeat(3) { i ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Folder, null, tint = Color(0xFF3DAA77), modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.height(7.dp).fillMaxWidth(listOf(0.8f, 0.6f, 0.7f)[i]).clip(RoundedCornerShape(50)).background(Color.White))
                        }
                    }
                }
            }
            Tile(Rose, 100.dp, 3.3f) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = Color(0xFFD5607A), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AES-256", color = Ink, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.WelcomeArt() {
    PersonArt(
        Pose.WAVE, Modifier.align(Alignment.Center).fillMaxWidth().height(420.dp),
        shirt = P.c.accent, accent = P.c.accent, backdrop = P.c.accentSoft,
    )
    androidx.compose.foundation.layout.FlowRow(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("Files" to Sky, "Gallery" to Peach, "Player" to Lilac, "Archives" to Mint).forEachIndexed { i, (t, c) ->
            val y = floatOffset(i * 1.3f, 3f)
            Text(t, color = Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                modifier = Modifier.offset(y = y.dp).clip(RoundedCornerShape(50)).background(c).padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun BoxScope.CheerArt() {
    val vol = remember { app.pzip.core.Files.Volume(app.pzip.core.Files.primaryRoot, "", false) }
    PersonArt(
        Pose.CHEER, Modifier.align(Alignment.Center).fillMaxWidth().height(400.dp),
        skin = Color(0xFF8D5B3E), hair = Color(0xFF1B1411), shirt = Color(0xFF34A574), accent = Color(0xFF34A574), backdrop = Mint,
    )
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ArtChip(Icons.Rounded.CheckCircle, "${app.pzip.core.Files.formatSize(vol.free)} free", Butter, Modifier, 0.4f)
        ArtChip(Icons.Rounded.Shield, "Safe folder", Rose, Modifier, 2f)
    }
}

@Composable
private fun BoxScope.PlayerArt() {
    Column(Modifier.align(Alignment.Center).fillMaxWidth()) {
        PersonArt(
            Pose.WATCH, Modifier.fillMaxWidth().height(270.dp),
            skin = Color(0xFFE0A47E), hair = Color(0xFF5A3A28), shirt = Color(0xFFF2B595), accent = P.c.accent, backdrop = Peach,
        )
        Gap(12)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtChip(Icons.Rounded.Swipe, "Swipe gestures", Sky, Modifier.weight(1f), 0.5f)
            ArtChip(Icons.Rounded.Speed, "0.25× – 4×", Butter, Modifier.weight(1f), 1.8f)
        }
        Gap(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtChip(Icons.Rounded.ClosedCaption, "Subtitles", Lilac, Modifier.weight(1f), 2.7f)
            ArtChip(Icons.Rounded.MusicNote, "200% boost", Peach, Modifier.weight(1f), 3.6f)
        }
    }
}

@Composable
private fun ArtChip(icon: ImageVector, text: String, color: Color, modifier: Modifier, phase: Float) {
    val y = floatOffset(phase, 3f)
    Row(modifier.offset(y = y.dp).clip(RoundedCornerShape(18.dp)).background(color).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun BoxScope.ArchiveArt() {
    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(320.dp)) {
        listOf(
            Triple("RAR", Lilac, -8f), Triple("7Z", Mint, 5f), Triple("ZIP", Sky, -2f),
        ).forEachIndexed { i, (label, color, angle) ->
            val y = floatOffset(i * 1.3f, 5f)
            Box(
                Modifier.align(Alignment.Center).offset(x = ((i - 1) * 62).dp, y = (y + (i - 1) * 18).dp).rotate(angle)
                    .size(width = 150.dp, height = 190.dp).clip(RoundedCornerShape(28.dp)).background(color).border(1.dp, Color.White, RoundedCornerShape(28.dp)),
            ) {
                Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(width = 14.dp, height = 90.dp)) {
                    // Zipper teeth
                    var yy = 0f
                    var left = true
                    while (yy < size.height) {
                        drawRoundRect(Ink.copy(alpha = 0.55f), topLeft = Offset(if (left) 0f else size.width / 2, yy), size = Size(size.width / 2, 6f))
                        yy += 11f
                        left = !left
                    }
                }
                Text(label, color = Ink, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
            }
        }
        val y = floatOffset(2.2f, 4f)
        Row(
            Modifier.align(Alignment.BottomEnd).offset(y = y.dp).padding(end = 10.dp, bottom = 6.dp).clip(RoundedCornerShape(50)).background(Ink).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("AES-256 protected", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BoxScope.CleanArt() {
    Row(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Real numbers from this phone's storage.
            val vol = remember { app.pzip.core.Files.Volume(app.pzip.core.Files.primaryRoot, "", false) }
            val usedFraction = remember { if (vol.total > 0) vol.used.toFloat() / vol.total else 0f }
            Tile(Mint, 210.dp, 0.4f) {
                val sweep = 360f * usedFraction
                Canvas(Modifier.align(Alignment.Center).size(110.dp)) {
                    drawArc(Color.White, 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(18f))
                    drawArc(Color(0xFF34A574), -90f, sweep, false, style = androidx.compose.ui.graphics.drawscope.Stroke(18f, cap = StrokeCap.Round))
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(app.pzip.core.Files.formatSize(vol.free), color = Ink, style = MaterialTheme.typography.titleMedium)
                    Text("free now", color = Ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Tile(Butter, 100.dp, 2.4f) { TileLabel(Icons.Rounded.CheckCircle, "Smart clean", Modifier.align(Alignment.Center)) }
        }
        Column(Modifier.weight(1f).padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Ink, 160.dp, 1.2f) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Shield, null, tint = P.c.accent, modifier = Modifier.size(44.dp))
                    Gap(6)
                    Text("Safe folder", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
            Tile(Rose, 120.dp, 3f) {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { Box(Modifier.size(12.dp).clip(CircleShape).background(Ink)) }
                }
            }
        }
    }
}

// ---------- Permissions ----------

@Composable
fun PermissionsStep(required: Boolean, onContinue: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(Perms.files(context)) }
    var notif by remember { mutableStateOf(Perms.notifications(context)) }
    var installs by remember { mutableStateOf(Perms.installs(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        files = Perms.files(context)
        notif = Perms.notifications(context)
        installs = Perms.installs(context)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notif = granted
        if (!granted) Perms.openNotificationSettings(context)
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { files = Perms.files(context) }

    StepScaffold(
        eyebrow = if (required) "Access needed" else "Step 1 of 3",
        title = "A few permissions",
        text = "pzip only uses these to work with files on your phone. Nothing is uploaded, ever.",
        button = "Continue", buttonEnabled = files, onButton = onContinue,
        footer = if (!files) "File access is required to continue." else null,
    ) {
        PermissionCard(
            Icons.Rounded.Folder, Sky, "All files access", "Browse, compress, extract and clean files anywhere on your phone.",
            granted = files, required = true,
        ) {
            if (Build.VERSION.SDK_INT >= 30) Perms.openFiles(context)
            else legacyLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
        PermissionCard(
            Icons.Rounded.Notifications, Butter, "Notifications", "See progress of long tasks and control music from the lock screen.",
            granted = notif, required = false,
        ) { if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else Perms.openNotificationSettings(context) }
        PermissionCard(
            Icons.Rounded.Android, Mint, "Install apps", "Install APK files you open from pzip. Optional.",
            granted = installs, required = false,
        ) { Perms.openInstalls(context) }
        Gap(4)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.c.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = P.c.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("No ads, no tracking, no account. You can change these anytime in Android Settings.", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, tint: Color, title: String, text: String, granted: Boolean, required: Boolean, onAllow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, if (granted) P.c.accent.copy(alpha = 0.5f) else P.c.border, RoundedCornerShape(24.dp))
            .background(P.c.surface).clickable(enabled = !granted, onClick = onAllow).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size( 46.dp).clip(RoundedCornerShape(17.dp)).background(if (P.c.dark) tint.copy(alpha = 0.18f) else tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (P.c.dark) Color.White else Ink, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (required) Text("Required", color = P.c.onAccentSoft, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(P.c.accentSoft).padding(horizontal = 7.dp, vertical = 2.dp), maxLines = 1)
                    Text(title, color = P.c.text, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(8.dp))
                if (granted) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(P.c.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Allowed", tint = Color.White, modifier = Modifier.size(18.dp))
            }
                } else {
            Text("Allow", color = P.c.onInk, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(P.c.ink).clickable(onClick = onAllow).padding(horizontal = 16.dp, vertical = 9.dp))
                }
            }
            Text(text, color = P.c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---------- Personalize ----------

@Composable
private fun PersonalizeStep(onContinue: () -> Unit) {
    StepScaffold("Step 2 of 3", "Make it yours", "Pick a look. You can change it later in Settings.", "Continue", true, onContinue) {
        // Live preview card
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.c.card).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PzipSpark(22.dp)
                Spacer(Modifier.width(8.dp))
                Text("Your phone", color = P.c.text, style = MaterialTheme.typography.titleMedium)
            }
            Gap(14)
            // Real folders and files from this phone, so the preview shows what you will actually see.
            val context = LocalContext.current
            val items by androidx.compose.runtime.produceState<List<java.io.File>>(emptyList()) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val root = app.pzip.core.Files.primaryRoot
                    val folders = listOf("DCIM", "Download", "Pictures", "Movies", "Music", "Documents").map { java.io.File(root, it) }.filter { it.exists() }
                    (folders.take(1) + app.pzip.core.MediaIndex.recent(context, 6).take(2) + folders.drop(1)).distinct().take(3)
                }
            }
            items.forEachIndexed { i, f ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    app.pzip.ui.components.FileThumb(f, 38.dp, 12.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(f.name, color = P.c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(if (f.isDirectory) "${app.pzip.core.Files.childCount(f)} items" else app.pzip.core.Files.formatSize(f.length()), color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Gap(10)
            val vol = remember { app.pzip.core.Files.Volume(app.pzip.core.Files.primaryRoot, "", false) }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(P.c.cardStrong)) {
                Box(Modifier.fillMaxWidth(if (vol.total > 0) vol.used.toFloat() / vol.total else 0f).fillMaxHeight().clip(RoundedCornerShape(50)).background(P.c.accent))
            }
        }
        Gap(6)
        Text("Theme", color = P.c.textMuted, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeChoice(Icons.Rounded.LightMode, "Light", Prefs.themeMode == ThemeMode.LIGHT, Modifier.weight(1f)) { Prefs.themeMode = ThemeMode.LIGHT }
            ThemeChoice(Icons.Rounded.DarkMode, "Dark", Prefs.themeMode == ThemeMode.DARK, Modifier.weight(1f)) { Prefs.themeMode = ThemeMode.DARK }
            ThemeChoice(Icons.Rounded.PhoneAndroid, "Auto", Prefs.themeMode == ThemeMode.SYSTEM, Modifier.weight(1f)) { Prefs.themeMode = ThemeMode.SYSTEM }
        }
        Gap(6)
        Text("Accent", color = P.c.textMuted, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Accent.entries.forEach { a ->
                val (strong, soft) = accentPreview(a)
                val sel = Prefs.accent == a
                val s by animateFloatAsState(if (sel) 1f else 0.92f, spring(), label = "s")
                Column(
                    Modifier.weight(1f).scale(s).clip(RoundedCornerShape(20.dp)).background(soft)
                        .border(2.dp, if (sel) P.c.ink else Color.Transparent, RoundedCornerShape(20.dp)).clickable { Prefs.accent = a }.padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(strong), contentAlignment = Alignment.Center) {
                        if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Gap(6)
                    Text(a.label, color = Ink, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ThemeChoice(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) P.c.ink else P.c.card).clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) P.c.onInk else P.c.text)
        Gap(6)
        Text(label, color = if (selected) P.c.onInk else P.c.text, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Security ----------

@Composable
private fun SecurityStep(onContinue: () -> Unit) {
    var settingPin by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    if (settingPin) {
        BackHandler { settingPin = false; first = null }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            PinPad(if (first == null) "Choose a PIN" else "Confirm your PIN", "4 to 8 digits. You'll use it to open pzip.", error) { pin ->
                if (first == null) {
                    first = pin
                    error = null
                } else if (first == pin) {
                    Prefs.pinHash = Security.hashPin(pin)
                    Prefs.appLock = true
                    onContinue()
                } else {
                    first = null
                    error = "PINs didn't match. Try again."
                }
            }
        }
        return
    }
    StepScaffold("Step 3 of 3", "Keep it private", "Lock pzip with a PIN or fingerprint so only you can open it.", "Set up app lock", true, { settingPin = true },
        secondary = "Not now", onSecondary = onContinue) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val y = floatOffset(0f, 5f)
            Box(Modifier.size(180.dp).offset(y = y.dp).clip(RoundedCornerShape(60.dp)).background(Brush.linearGradient(listOf(P.c.accentSoft, Lilac))), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(Ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PIN", "Fingerprint", "Safe folder").forEachIndexed { i, t ->
                    val yy = floatOffset(i + 1f, 3f)
                    Text(t, color = Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        modifier = Modifier.offset(y = yy.dp).clip(RoundedCornerShape(50)).background(listOf(Sky, Mint, Peach)[i]).padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
    }
}

// ---------- Done ----------

@Composable
private fun DoneStep(onFinished: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else 0.4f, spring(dampingRatio = 0.45f, stiffness = 120f), label = "pop")
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "a")
    @Composable
    fun Badge(size: Dp) = Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.9f).scale(s).clip(CircleShape).background(P.c.accentSoft))
        PzipSpark(size * 0.5f, P.c.accent, Modifier.scale(s).rotate(angle))
    }

    @Composable
    fun Words(align: TextAlign) {
        Text("You're all set", style = MaterialTheme.typography.displaySmall, color = P.c.text, textAlign = align, modifier = Modifier.fillMaxWidth())
        Gap(10)
        Text("Your files, gallery, player and archives are ready.", style = MaterialTheme.typography.bodyLarge, color = P.c.textMuted, textAlign = align, modifier = Modifier.fillMaxWidth())
    }

    if (isWide()) {
        Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Badge(150.dp) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Words(TextAlign.Start)
                Gap(20)
                InkButton("Start using pzip", onFinished, Modifier.fillMaxWidth())
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Badge(220.dp)
        Gap(30)
        Words(TextAlign.Center)
        Spacer(Modifier.weight(1f))
        InkButton("Start using pzip", onFinished, Modifier.fillMaxWidth())
    }
}

// ---------- Shared layout ----------

@Composable
private fun StepScaffold(
    eyebrow: String,
    title: String,
    text: String,
    button: String,
    buttonEnabled: Boolean,
    onButton: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Gap(18)
            Text(eyebrow.uppercase(), color = P.c.accent, style = MaterialTheme.typography.labelLarge)
            Text(title, color = P.c.text, style = MaterialTheme.typography.headlineMedium)
            Text(text, color = P.c.textMuted, style = MaterialTheme.typography.bodyLarge)
            Gap(4)
            content()
            Gap(8)
        }
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (footer != null) Text(footer, color = P.c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            InkButton(button, onButton, Modifier.fillMaxWidth(), enabled = buttonEnabled)
            if (secondary != null) TextLink(secondary, onSecondary, P.c.textMuted)
        }
    }
}
