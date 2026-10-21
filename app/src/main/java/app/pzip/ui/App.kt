package app.pzip.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pzip.core.Prefs
import app.pzip.core.Security
import app.pzip.player.Engine
import app.pzip.ui.components.Gap
import app.pzip.ui.components.IconBtn
import app.pzip.ui.components.InkButton
import app.pzip.ui.components.PzipSpark
import app.pzip.ui.components.ToastHost
import app.pzip.ui.screens.AboutScreen
import app.pzip.ui.screens.WhatsNewScreen
import app.pzip.ui.screens.AlbumScreen
import app.pzip.ui.screens.AnalyzerScreen
import app.pzip.ui.screens.ArchiveScreen
import app.pzip.ui.screens.BenchScreen
import app.pzip.ui.screens.BrowseScreen
import app.pzip.ui.screens.CategoryScreen
import app.pzip.ui.screens.CleanScreen
import app.pzip.ui.screens.CompressSheet
import app.pzip.ui.screens.DetailsSheet
import app.pzip.ui.screens.ExtractSheet
import app.pzip.ui.screens.FilesRootScreen
import app.pzip.ui.screens.GalleryScreen
import app.pzip.ui.screens.HashScreen
import app.pzip.ui.screens.HomeScreen
import app.pzip.ui.screens.ImageViewer
import app.pzip.ui.screens.PdfViewer
import app.pzip.ui.screens.PinPad
import app.pzip.ui.screens.SafeScreen
import app.pzip.ui.screens.SearchScreen
import app.pzip.ui.screens.SettingsScreen
import app.pzip.ui.screens.TasksScreen
import app.pzip.ui.screens.TextViewer
import app.pzip.ui.screens.ToolsScreen
import app.pzip.ui.screens.TrashScreen
import app.pzip.ui.theme.P

object AppLock {
    var locked by mutableStateOf(false)
    var backgroundAt = 0L
    var biometric: (() -> Unit)? = null
}

fun hasStorageAccess(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

@Composable
fun App() {
    var access by androidx.compose.runtime.remember { mutableStateOf(true) }
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = hasStorageAccess(context) }

    when {
        !Prefs.onboarded -> {
            Onboarding { Prefs.onboarded = true }
            return
        }
        !access -> {
            Box(Modifier.fillMaxSize().background(P.c.bg)) { PermissionsStep(required = true) { access = hasStorageAccess(context) } }
            return
        }
        AppLock.locked -> {
            LockScreen()
            return
        }
    }

    val wide = isWide()
    Box(Modifier.fillMaxSize().background(P.c.bg)) {
        // Portrait (9:16): tabs at the bottom, in thumb reach. Landscape (16:9): a rail on the left.
        Row(Modifier.fillMaxSize()) {
            if (wide && Nav.top is Screen.Root) NavRail()
            val sideInsets = if (!wide) Modifier else Modifier
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars.only(androidx.compose.foundation.layout.WindowInsetsSides.End))
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout.only(if (Nav.top is Screen.Root) androidx.compose.foundation.layout.WindowInsetsSides.End else androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
            Column(Modifier.weight(1f).fillMaxSize().then(sideInsets)) {
                Box(Modifier.weight(1f)) {
                    val top = Nav.top
                    AnimatedContent(
                        targetState = top, label = "screen",
                        transitionSpec = { fadeIn(androidx.compose.animation.core.tween(160)) togetherWith fadeOut(androidx.compose.animation.core.tween(120)) },
                        contentKey = { it.hashCode() },
                    ) { s -> ScreenHost(s) }
                }
                if (Engine.active && !Engine.ended) MiniPlayer()
                if (!wide && Nav.top is Screen.Root) BottomBar()
            }
        }
        // Keeps scrolled content from showing through the status bar.
        if (Nav.top !is Screen.Images) Box(
            Modifier.fillMaxWidth().windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars).background(P.c.bg),
        )
        ToastHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (wide) 24.dp else 96.dp))
    }

    if (Nav.top !is Screen.Pick) {
        Nav.compress?.let { CompressSheet(it) }
        Nav.extract?.let { ExtractSheet(it) }
    }
    Nav.details?.let { DetailsSheet(it) }
    BackHandler(enabled = Nav.stack.size > 1 || Nav.tab != Tab.HOME) { Nav.back() }
}

@Composable
private fun ScreenHost(s: Screen) {
    Box(Modifier.fillMaxSize().imePadding()) {
        when (s) {
            is Screen.Root -> when (s.tab) {
                Tab.HOME -> HomeScreen()
                Tab.FILES -> FilesRootScreen()
                Tab.GALLERY -> GalleryScreen()
                Tab.CLEAN -> CleanScreen()
                Tab.TOOLS -> ToolsScreen()
            }
            is Screen.Browse -> BrowseScreen(s.dir, s.nav)
            is Screen.Pick -> BrowseScreen(s.req.start, s.nav, s.req)
            is Screen.Archive -> ArchiveScreen(s.file, s.nav)
            is Screen.CategoryView -> CategoryScreen(s.cat)
            Screen.Search -> SearchScreen()
            Screen.Trash -> TrashScreen()
            Screen.Safe -> SafeScreen()
            Screen.Tasks -> TasksScreen()
            is Screen.Hash -> HashScreen(s.file)
            Screen.Bench -> BenchScreen()
            Screen.Settings -> SettingsScreen()
            Screen.About -> AboutScreen()
            Screen.WhatsNew -> WhatsNewScreen()
            Screen.Analyzer -> AnalyzerScreen()
            is Screen.Album -> AlbumScreen(s.name, s.files)
            is Screen.Images -> ImageViewer(s.files, s.index)
            is Screen.Pdf -> PdfViewer(s.file)
            is Screen.Text -> TextViewer(s.file)
        }
    }
}

private val tabs = listOf(
    Tab.HOME to Icons.Rounded.Home,
    Tab.FILES to Icons.Rounded.Folder,
    Tab.GALLERY to Icons.Rounded.PhotoLibrary,
    Tab.CLEAN to Icons.Rounded.CleaningServices,
    Tab.TOOLS to Icons.Rounded.Handyman,
)

@Composable
private fun BottomBar() {
    Row(
        Modifier.fillMaxWidth().background(P.c.bg).navigationBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        tabs.forEach { (tab, icon) -> NavItem(tab, icon, Modifier.weight(1f)) }
    }
}

@Composable
private fun NavRail() {
    Column(
        Modifier.fillMaxHeight().background(P.c.bg).statusBarsPadding().navigationBarsPadding()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout.only(androidx.compose.foundation.layout.WindowInsetsSides.Start))
            .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        tabs.forEach { (tab, icon) -> NavItem(tab, icon, Modifier) }
    }
}

@Composable
private fun NavItem(tab: Tab, icon: ImageVector, modifier: Modifier) {
    val h = rememberHaptics()
    val sel = Nav.tab == tab
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable { if (Nav.tab != tab) h.tap(); Nav.switchTab(tab) }.padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (sel) P.c.accentSoft else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 16.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, tab.label, tint = if (sel) P.c.onAccentSoft else P.c.textMuted, modifier = Modifier.size(22.dp))
        }
        Text(tab.label, color = if (sel) P.c.text else P.c.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

/** VLC-style mini player while something plays in the background. */
@Composable
private fun MiniPlayer() {
    val context = LocalContext.current
    val item = Engine.current ?: return
    Column(Modifier.fillMaxWidth().background(P.c.bg)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.c.card)
                .clickable { Engine.openPlayer(context) }.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (item.isAudio) Icons.Rounded.AutoAwesome else Icons.Rounded.Movie, null, tint = P.c.accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, color = P.c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Engine.artist.ifEmpty { app.pzip.core.Files.formatDuration(Engine.time) + " / " + app.pzip.core.Files.formatDuration(Engine.length) }, color = P.c.textMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            IconBtn(if (Engine.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", { Engine.toggle() })
            IconBtn(Icons.Rounded.Close, "Stop", { Engine.release() }, tint = P.c.textMuted)
        }
    }
}

@Composable
private fun LockScreen() {
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var attempts by androidx.compose.runtime.remember { mutableStateOf(0) }
    var blockedUntil by androidx.compose.runtime.remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (Prefs.useBiometric) AppLock.biometric?.invoke() }
    Column(Modifier.fillMaxSize().background(P.c.bg).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Gap(30)
        Icon(Icons.Rounded.Lock, null, tint = P.c.textMuted)
        PinPad("pzip is locked", "Enter your PIN", error, onBiometric = if (Prefs.useBiometric && AppLock.biometric != null) ({ AppLock.biometric?.invoke() }) else null) { pin ->
            val now = System.currentTimeMillis()
            if (now < blockedUntil) {
                error = "Too many tries. Wait ${(blockedUntil - now) / 1000 + 1}s"
                return@PinPad
            }
            if (Security.verifyPin(pin, Prefs.pinHash)) {
                AppLock.locked = false
                attempts = 0
            } else {
                attempts++
                if (attempts >= 5) {
                    blockedUntil = now + 30_000
                    attempts = 0
                    error = "Too many tries. Wait 30s"
                } else error = "Wrong PIN"
            }
        }
    }
}
