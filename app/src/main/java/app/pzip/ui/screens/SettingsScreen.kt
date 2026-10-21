package app.pzip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pzip.BuildConfig
import app.pzip.core.Accent
import app.pzip.core.Files
import app.pzip.core.OverwriteMode
import app.pzip.core.Prefs
import app.pzip.core.Security
import app.pzip.core.ThemeMode
import app.pzip.core.archive.CreateFormat
import app.pzip.core.archive.Level
import app.pzip.ui.Nav
import app.pzip.ui.Screen
import app.pzip.ui.components.Chip
import app.pzip.ui.components.Gap
import app.pzip.ui.components.PCard
import app.pzip.ui.components.SectionHeader
import app.pzip.ui.components.SettingRow
import app.pzip.ui.components.SettingSwitch
import app.pzip.ui.components.Toaster
import app.pzip.ui.components.TopBar
import app.pzip.ui.components.Wordmark
import app.pzip.ui.components.Thumbs
import app.pzip.ui.theme.P
import app.pzip.ui.theme.accentPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { content() }
}

@Composable
private fun Caption(text: String) {
    Text(text, color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 10.dp))
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var settingPin by remember { mutableStateOf(false) }
    var cacheVersion by remember { mutableStateOf(0) }
    val cacheSize by produceState(0L, cacheVersion) { value = withContext(Dispatchers.IO) { Files.folderSize(context.cacheDir) } }

    if (settingPin) {
        var first by remember { mutableStateOf<String?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        androidx.activity.compose.BackHandler { settingPin = false }
        Column(Modifier.fillMaxSize().background(P.c.bg).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            TopBar("App lock", onBack = { settingPin = false })
            PinPad(if (first == null) "Choose a PIN" else "Confirm PIN", "You'll enter it every time you open pzip.", err) { pin ->
                if (first == null) first = pin
                else if (first == pin) {
                    Prefs.pinHash = Security.hashPin(pin)
                    Prefs.appLock = true
                    settingPin = false
                    Toaster.show("App lock is on")
                } else {
                    first = null
                    err = "PINs didn't match"
                }
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(P.c.bg), contentPadding = PaddingValues(bottom = 60.dp)) {
        item { TopBar("Settings", onBack = { Nav.back() }) }

        item { SectionHeader("Appearance") }
        item {
            Caption("Theme")
            ChipRow {
                Chip("Light", Prefs.themeMode == ThemeMode.LIGHT, { Prefs.themeMode = ThemeMode.LIGHT })
                Chip("Dark", Prefs.themeMode == ThemeMode.DARK, { Prefs.themeMode = ThemeMode.DARK })
                Chip("System", Prefs.themeMode == ThemeMode.SYSTEM, { Prefs.themeMode = ThemeMode.SYSTEM })
            }
            Caption("Accent color")
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Accent.entries.forEach { a ->
                    val (strong, soft) = accentPreview(a)
                    val sel = Prefs.accent == a
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(soft).border(if (sel) 2.dp else 1.dp, if (sel) P.c.ink else P.c.border, CircleShape).clickable { Prefs.accent = a },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(strong), contentAlignment = Alignment.Center) {
                                if (sel) Icon(Icons.Rounded.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(a.label, color = P.c.textMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        item { SectionHeader("Compression") }
        item {
            Caption("Default format")
            ChipRow { CreateFormat.entries.forEach { f -> Chip(f.label, Prefs.defaultFormat == f.name, { Prefs.defaultFormat = f.name }) } }
            Caption("Default level")
            ChipRow { Level.entries.forEach { l -> Chip(l.label, Prefs.defaultLevel == l.ordinal, { Prefs.defaultLevel = l.ordinal }) } }
            SettingSwitch("Delete files after compressing", icon = Icons.Rounded.Archive, checked = Prefs.deleteAfterCompress, onChange = { Prefs.deleteAfterCompress = it })
        }

        item { SectionHeader("Extraction") }
        item {
            SettingSwitch("Extract into a new folder", "Named after the archive", Icons.Rounded.Unarchive, Prefs.extractToSubfolder) { Prefs.extractToSubfolder = it }
            SettingSwitch("Open folder when done", icon = Icons.Rounded.FolderOpen, checked = Prefs.openAfterExtract, onChange = { Prefs.openAfterExtract = it })
            Caption("When a file already exists")
            ChipRow { OverwriteMode.entries.forEach { m -> Chip(m.label, Prefs.overwriteMode == m, { Prefs.overwriteMode = m }) } }
        }

        item { SectionHeader("Files") }
        item {
            SettingSwitch("Show hidden files", icon = Icons.Rounded.Visibility, checked = Prefs.showHidden, onChange = { Prefs.showHidden = it })
            SettingSwitch("Use Trash", "Deleted items can be restored for 30 days", Icons.Rounded.Restore, Prefs.useTrash) { Prefs.useTrash = it }
            SettingSwitch("Remember recent archives", icon = Icons.Rounded.History, checked = Prefs.keepHistory, onChange = {
                Prefs.keepHistory = it
                if (!it) Prefs.recents = ""
            })
        }

        item { SectionHeader("Player") }
        item {
            SettingSwitch("Resume where you left off", icon = Icons.Rounded.PlayCircle, checked = Prefs.resumePlayback, onChange = { Prefs.resumePlayback = it })
            SettingSwitch("Swipe gestures", "Brightness, volume and seeking", Icons.Rounded.Swipe, Prefs.playerGestures) { Prefs.playerGestures = it }
            SettingSwitch("Haptic feedback", "Gentle vibration on taps, gestures and PIN keys", Icons.Rounded.Vibration, Prefs.haptics) { Prefs.haptics = it }
        }

        item { SectionHeader("Privacy & security") }
        item {
            SettingSwitch("App lock", "Ask for a PIN when opening pzip", Icons.Rounded.Lock, Prefs.appLock) { on ->
                if (on) settingPin = true else {
                    Prefs.appLock = false
                    Prefs.pinHash = ""
                }
            }
            if (Prefs.appLock) SettingSwitch("Unlock with fingerprint", icon = Icons.Rounded.Fingerprint, checked = Prefs.useBiometric, onChange = { Prefs.useBiometric = it })
            SettingSwitch("Block screenshots", "Hide pzip in recent apps and screen recordings", Icons.Rounded.ScreenLockPortrait, Prefs.blockScreenshots) { Prefs.blockScreenshots = it }
            SettingRow("Clear temporary files", "${Files.formatSize(cacheSize)} of previews and caches", Icons.Rounded.CleaningServices, onClick = {
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                Thumbs.clear()
                cacheVersion++
                Toaster.show("Temporary files cleared")
            })
            SettingRow("Clear history", "Recent archives list", Icons.Rounded.DeleteSweep, onClick = {
                Prefs.recents = ""
                Toaster.show("History cleared")
            })
        }

        item { SectionHeader("About") }
        item { SettingRow("What's new", "Version ${BuildConfig.VERSION_NAME} release notes", Icons.Rounded.NewReleases, onClick = { Nav.go(Screen.WhatsNew) }) }
        item { SettingRow("About pzip", "Version ${BuildConfig.VERSION_NAME}", Icons.Rounded.Info, onClick = { Nav.go(Screen.About) }) }
    }
}

@Composable
fun AboutScreen() {
    LazyColumn(Modifier.fillMaxSize().background(P.c.bg), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { TopBar("About", onBack = { Nav.back() }) }
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Wordmark(size = 40)
                Gap(10)
                Text("Version ${BuildConfig.VERSION_NAME}", color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium)
                Gap(8)
                Text(
                    "One light app for your files, gallery, videos, music and archives.",
                    color = P.c.text, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        item {
            PCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Column {
                    Text("Privacy", color = P.c.text, style = MaterialTheme.typography.titleMedium)
                    Gap(6)
                    Text(
                        "pzip has no ads, analytics or accounts. Files are processed on your phone only. Internet is used only when you open a network stream in the player.",
                        color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item { SectionHeader("Open-source software") }
        item {
            listOf(
                "libVLC (VideoLAN)" to "LGPL 2.1 — video and audio playback engine",
                "UnRAR (Alexander Roshal)" to "UnRAR license — RAR extraction. UnRAR source code may be used in any software to handle RAR archives without limitations free of charge, but cannot be used to develop RAR (WinRAR) compatible archiver and to re-create RAR compression algorithm, which is proprietary.",
                "Apache Commons Compress" to "Apache 2.0 — 7z, tar, gzip, bzip2 and more",
                "XZ for Java (Tukaani)" to "Public domain — LZMA / LZMA2 / XZ",
                "Zip4j" to "Apache 2.0 — ZIP with AES encryption and split archives",
                "Jetpack Compose, AndroidX" to "Apache 2.0",
            ).forEach { (name, lic) -> SettingRow(name, lic) }
        }
    }
}
