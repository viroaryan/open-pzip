package app.pzip.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pzip.core.Category
import app.pzip.core.FileOps
import app.pzip.core.Files
import app.pzip.core.Prefs
import app.pzip.core.SafeVault
import app.pzip.core.Security
import app.pzip.core.Tools
import app.pzip.core.Trash
import app.pzip.core.tasks.PzipTask
import app.pzip.core.tasks.TaskManager
import app.pzip.core.tasks.TaskState
import app.pzip.player.Engine
import app.pzip.ui.Nav
import app.pzip.ui.PickRequest
import app.pzip.ui.Screen
import app.pzip.ui.components.ConfirmDialog
import app.pzip.ui.components.EmptyState
import app.pzip.ui.components.FileThumb
import app.pzip.ui.components.Gap
import app.pzip.ui.components.IconBtn
import app.pzip.ui.components.InkButton
import app.pzip.ui.components.KindTile
import app.pzip.ui.components.LineButton
import app.pzip.ui.components.PCard
import app.pzip.ui.components.PDialog
import app.pzip.ui.components.ProgressLine
import app.pzip.ui.components.SectionHeader
import app.pzip.ui.components.SettingRow
import app.pzip.ui.components.Sheet
import app.pzip.ui.components.SoftButton
import app.pzip.ui.components.TextLink
import app.pzip.ui.components.Toaster
import app.pzip.ui.components.TopBar
import app.pzip.ui.components.pzipFieldColors
import app.pzip.ui.theme.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("pzip", text))
    Toaster.show("Copied")
}

// ---------- Tools tab ----------

@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    var shredPick by remember { mutableStateOf<File?>(null) }
    var generated by remember { mutableStateOf<String?>(null) }
    var stream by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(P.c.bg), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { TopBar("Tools", big = true) { IconBtn(Icons.Rounded.SwapVert, "Operations", { Nav.go(Screen.Tasks) }) } }
        item { SectionHeader("Archiver") }
        item {
            SettingRow("Compress files", "ZIP (AES-256), 7Z, TAR.GZ and more", Icons.Rounded.Archive, onClick = {
                Nav.switchTab(app.pzip.ui.Tab.FILES)
                Toaster.show("Long-press files to select them, then tap Compress")
            })
            SettingRow("Extract an archive", "RAR, RAR5, 7Z, ZIP, TAR, GZ, XZ and more", Icons.Rounded.Unarchive, onClick = { Nav.go(Screen.CategoryView(Category.ARCHIVES)) })
            SettingRow("Test integrity", "Check an archive for damaged files", Icons.Rounded.VerifiedUser, onClick = {
                Nav.pick(PickRequest(false, "Choose an archive to test", Files.primaryRoot) { f ->
                    if (Files.isArchive(f)) testArchive(f, null) else Toaster.show("Not an archive")
                })
            })
            SettingRow("Convert format", "Repack RAR or 7Z as ZIP and more", Icons.Rounded.SwapHoriz, onClick = {
                Nav.pick(PickRequest(false, "Choose an archive to convert", Files.primaryRoot) { f ->
                    if (Files.isArchive(f)) Nav.go(Screen.Archive(f)) else Toaster.show("Not an archive")
                })
            })
            SettingRow("Benchmark", "Measure compression speed on this phone", Icons.Rounded.Speed, onClick = { Nav.go(Screen.Bench) })
        }
        item { SectionHeader("Privacy & security") }
        item {
            SettingRow("Safe folder", "${SafeVault.items.size} files · AES-256, hardware key", Icons.Rounded.Shield, onClick = { Nav.go(Screen.Safe) })
            SettingRow("Checksums", "MD5, SHA-1, SHA-256, SHA-512, CRC32", Icons.Rounded.Fingerprint, onClick = { Nav.go(Screen.Hash(null)) })
            SettingRow("Secure shred", "Overwrite files so they can't be recovered", Icons.Rounded.LocalFireDepartment, onClick = {
                Nav.pick(PickRequest(false, "Choose a file to shred", Files.primaryRoot) { shredPick = it })
            })
            SettingRow("Password generator", "Create a strong random password", Icons.Rounded.Key, onClick = { generated = Security.generatePassword(20) })
        }
        item { SectionHeader("Media") }
        item {
            if (Engine.active) SettingRow("Now playing", Engine.current?.title, Icons.Rounded.PlayCircle, onClick = { Engine.openPlayer(context) })
            SettingRow("Open network stream", "HTTP, HLS (m3u8), RTSP, RTMP", Icons.Rounded.Link, onClick = { stream = true })
            SettingRow("Music", "All audio on your phone", Icons.Rounded.LibraryMusic, onClick = { Nav.go(Screen.CategoryView(Category.AUDIO)) })
        }
        item { SectionHeader("Storage") }
        item {
            SettingRow("Storage analyzer", "Biggest folders and files", Icons.Rounded.Straighten, onClick = { Nav.go(Screen.Analyzer) })
            SettingRow("Trash", "${Trash.items.size} items", Icons.Rounded.Delete, onClick = { Nav.go(Screen.Trash) })
            SettingRow("Operations", "${TaskManager.running.size} running", Icons.Rounded.SwapVert, onClick = { Nav.go(Screen.Tasks) })
        }
        item {
            PCard(Modifier.padding(20.dp).fillMaxWidth()) {
                Column {
                    Text("Supported formats", color = P.c.text, style = MaterialTheme.typography.titleMedium)
                    Gap(6)
                    Text("Open: ZIP (incl. ZIP64 and split .z01), RAR (v1.5–v7, RAR5, multi-part), 7Z (AES, multi-volume), TAR, GZ, TGZ, BZ2, XZ, LZMA, Z, LZ4, CPIO, AR/DEB, ARJ, JAR, APK, XAPK, CBZ, CBR, CB7", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                    Gap(6)
                    Text("Create: ZIP (AES-256/128, split parts), 7Z (LZMA2), TAR, TAR.GZ, TAR.XZ, TAR.BZ2", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                    Gap(6)
                    Text("Play: nearly every video and audio format via the VLC engine — MKV, MP4, AVI, WMV, FLV, MOV, TS, HEVC, AV1, FLAC, DTS, AC3, Opus, with SRT/ASS/VTT subtitles", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    shredPick?.let { f ->
        ConfirmDialog("Shred ${f.name}?", "It will be overwritten 3 times and deleted. This can't be undone.", "Shred", danger = true, onDismiss = { shredPick = null }) {
            FileOps.shred(listOf(f), 3)
        }
    }
    generated?.let { pw ->
        PDialog("Strong password", { generated = null }, "Copy", { copyText(context, pw); generated = null }) {
            Column {
                Text(pw, color = P.c.text, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                TextLink("Generate another", { generated = Security.generatePassword(20) })
            }
        }
    }
    if (stream) app.pzip.ui.components.InputDialog(
        "Open network stream", "", label = "URL", confirm = "Play", selectBase = false,
        validate = { if (it.isNotBlank() && !it.contains("://")) "Enter a full URL" else null },
        onDismiss = { stream = false },
    ) { url ->
        stream = false
        Engine.play(listOf(app.pzip.player.PlayItem(Uri.parse(url), url.substringAfterLast('/').ifEmpty { url }, false)))
        Engine.openPlayer(context)
    }
}

// ---------- Hash ----------

@Composable
fun HashScreen(initial: File?) {
    val context = LocalContext.current
    var file by remember { mutableStateOf(initial) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<Tools.Hashes?>(null) }
    var compare by remember { mutableStateOf("") }
    LaunchedEffect(file) {
        val f = file ?: return@LaunchedEffect
        result = null
        progress = 0f
        result = withContext(Dispatchers.IO) { runCatching { Tools.hashes(f, { progress = it }) }.getOrNull() }
    }
    LazyColumn(Modifier.fillMaxSize().background(P.c.bg), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { TopBar("Checksums", onBack = { Nav.back() }) }
        item {
            PCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), onClick = { Nav.pick(PickRequest(false, "Choose a file", file?.parentFile ?: Files.primaryRoot) { file = it }) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (file != null) FileThumb(file!!) else KindTile(app.pzip.core.FileKind.OTHER)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(file?.name ?: "Choose a file", color = P.c.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(file?.let { Files.formatSize(it.length()) } ?: "Tap to pick", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.FolderOpen, null, tint = P.c.textMuted)
                }
            }
        }
        if (file != null && result == null) item {
            Column(Modifier.padding(20.dp)) {
                Text("Calculating… ${(progress * 100).toInt()}%", color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium)
                Gap(8)
                ProgressLine(progress)
            }
        }
        result?.let { r ->
            item {
                OutlinedTextField(
                    compare, { compare = it.trim() }, label = { Text("Paste a checksum to compare") }, singleLine = true,
                    colors = pzipFieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
                if (compare.isNotEmpty()) {
                    val match = listOf(r.crc32, r.md5, r.sha1, r.sha256, r.sha512).any { it.equals(compare, true) }
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (match) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, tint = if (match) P.c.success else P.c.danger)
                        Spacer(Modifier.width(8.dp))
                        Text(if (match) "Match — the file is intact" else "No match", color = if (match) P.c.success else P.c.danger, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            items(listOf("CRC32" to r.crc32, "MD5" to r.md5, "SHA-1" to r.sha1, "SHA-256" to r.sha256, "SHA-512" to r.sha512)) { (k, v) ->
                Column(Modifier.fillMaxWidth().clickable { copyText(context, v) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k, color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ContentCopy, "Copy", tint = P.c.textFaint, modifier = Modifier.size(16.dp))
                    }
                    Text(v, color = P.c.text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

// ---------- Benchmark ----------

@Composable
fun BenchScreen() {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Tools.BenchResult>>(emptyList()) }
    Column(Modifier.fillMaxSize().background(P.c.bg)) {
        TopBar("Benchmark", "16 MB of mixed data, in memory", onBack = { Nav.back() })
        Text("Shows how fast each format compresses and extracts on this phone, and how small it makes files.", color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Gap(16)
        InkButton(if (running) "Running $step…" else "Run benchmark", {
            if (running) return@InkButton
            running = true
            scope.launch {
                results = withContext(Dispatchers.Default) { Tools.benchmark(16) { step = it } }
                running = false
            }
        }, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), Icons.Rounded.Speed, enabled = !running)
        Gap(12)
        val maxSpeed = results.maxOfOrNull { maxOf(it.compressMBs, it.decompressMBs) }?.coerceAtLeast(1.0) ?: 1.0
        results.filter { it.name != "Store" }.forEach { r ->
            PCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth()) {
                Column {
                    Row {
                        Text(r.name, color = P.c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${"%.0f".format(r.ratio * 100)}% size", color = P.c.accent, style = MaterialTheme.typography.labelLarge)
                    }
                    Gap(8)
                    Text("Compress ${"%.1f".format(r.compressMBs)} MB/s", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                    ProgressLine((r.compressMBs / maxSpeed).toFloat())
                    Gap(6)
                    Text("Extract ${"%.1f".format(r.decompressMBs)} MB/s", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                    ProgressLine((r.decompressMBs / maxSpeed).toFloat(), color = P.c.ink)
                }
            }
        }
    }
}

// ---------- Storage analyzer ----------

@Composable
fun AnalyzerScreen() {
    var dir by remember { mutableStateOf(Files.primaryRoot) }
    val sizes by produceState<List<Pair<File, Long>>?>(null, dir) {
        value = null
        value = withContext(Dispatchers.IO) {
            (dir.listFiles() ?: emptyArray()).map { it to FileOps.sizeOf(it) }.sortedByDescending { it.second }
        }
    }
    androidx.activity.compose.BackHandler(dir.absolutePath != Files.primaryRoot.absolutePath) { dir = dir.parentFile ?: dir }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(P.c.bg)) {
        TopBar("Storage analyzer", Files.displayPath(dir), onBack = { if (dir.absolutePath != Files.primaryRoot.absolutePath) dir = dir.parentFile ?: dir else Nav.back() })
        val list = sizes
        if (list == null) {
            Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = P.c.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                Gap(10)
                Text("Measuring folders…", color = P.c.textMuted)
            }
        } else {
            val total = list.sumOf { it.second }.coerceAtLeast(1)
            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                items(list, key = { it.first.absolutePath }) { (f, size) ->
                    Column(Modifier.fillMaxWidth().clickable { if (f.isDirectory) dir = f else Nav.open(context, f) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FileThumb(f, 36.dp, 11.dp)
                            Text(f.name, color = P.c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            Text(Files.formatSize(size), color = P.c.text, style = MaterialTheme.typography.labelLarge)
                        }
                        Gap(6)
                        ProgressLine(size.toFloat() / total, height = 5.dp)
                    }
                }
            }
        }
    }
}

// ---------- Trash ----------

@Composable
fun TrashScreen() {
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Trash.load() } }
    var emptyAsk by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Trash.Item?>(null) }
    Column(Modifier.fillMaxSize().background(P.c.bg)) {
        TopBar("Trash", "${Trash.items.size} items · ${Files.formatSize(Trash.totalSize())}", onBack = { Nav.back() }) {
            if (Trash.items.isNotEmpty()) TextLink("Empty", { emptyAsk = true }, P.c.danger)
        }
        Text("Items are deleted forever after ${Trash.KEEP_DAYS} days.", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
        if (Trash.items.isEmpty()) EmptyState(Icons.Rounded.Delete, "Trash is empty", "Deleted files stay here for ${Trash.KEEP_DAYS} days so you can restore them.")
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            items(Trash.items.toList(), key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().clickable { menuFor = item }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindTile(if (item.isDir) app.pzip.core.FileKind.FOLDER else Files.kindOf(item.name))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(item.name, color = P.c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${Files.formatSize(item.size)} · ${Trash.daysLeft(item)} days left", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconBtn(Icons.Rounded.Restore, "Restore", {
                        val f = Trash.restore(item)
                        Toaster.show("Restored to ${f.parentFile?.name}")
                        TaskManager.refresh()
                    }, tint = P.c.accent)
                }
            }
        }
    }
    menuFor?.let { item ->
        Sheet({ menuFor = null }, item.name) {
            Text(Files.displayPath(File(item.original).parentFile ?: File("/")), color = P.c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 22.dp))
            Gap(8)
            SettingRow("Restore", icon = Icons.Rounded.Restore, onClick = { Trash.restore(item); menuFor = null; TaskManager.refresh() })
            SettingRow("Delete forever", icon = Icons.Rounded.DeleteForever, onClick = { Trash.deleteForever(item); menuFor = null })
        }
    }
    if (emptyAsk) ConfirmDialog("Empty trash?", "${Trash.items.size} items will be deleted forever.", "Empty trash", danger = true, onDismiss = { emptyAsk = false }) { Trash.empty() }
}

// ---------- Safe folder ----------

private object SafeSession {
    var unlocked by mutableStateOf(false)
}

@Composable
fun SafeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { SafeVault.load() } }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<SafeVault.Item?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (!SafeSession.unlocked) {
        Column(Modifier.fillMaxSize().background(P.c.bg).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            TopBar("Safe folder", onBack = { Nav.back() })
            if (!SafeVault.hasPin) {
                PinPad(
                    title = if (firstPin == null) "Create a PIN" else "Confirm your PIN",
                    subtitle = "Files in the Safe folder are encrypted with AES-256 and only open with this PIN.",
                    error = error,
                ) { pin ->
                    if (firstPin == null) {
                        firstPin = pin
                        error = null
                    } else if (firstPin == pin) {
                        Prefs.safePinHash = Security.hashPin(pin)
                        SafeSession.unlocked = true
                    } else {
                        firstPin = null
                        error = "PINs didn't match. Try again."
                    }
                }
            } else {
                PinPad("Enter PIN", "Unlock your Safe folder", error) { pin ->
                    if (Security.verifyPin(pin, Prefs.safePinHash)) SafeSession.unlocked = true else error = "Wrong PIN"
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(P.c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Safe folder", "${SafeVault.items.size} files · encrypted", onBack = { Nav.back() }) {
                IconBtn(Icons.Rounded.Lock, "Lock", { SafeSession.unlocked = false; SafeVault.clearViewCache() })
            }
            if (SafeVault.items.isEmpty()) EmptyState(Icons.Rounded.Shield, "Nothing here yet", "Move private photos, videos and documents here. They're encrypted and hidden from other apps.")
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 120.dp)) {
                items(SafeVault.items.toList(), key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            busy = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { SafeVault.open(item) } }
                                    .onSuccess { Nav.open(context, it) }
                                    .onFailure { Toaster.show("Couldn't open: ${it.message}") }
                                busy = false
                            }
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KindTile(Files.kindOf(item.name))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(item.name, color = P.c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${Files.formatSize(item.size)} · added ${Files.formatDate(item.added)}", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconBtn(Icons.Rounded.MoreVert, "Options", { menuFor = item }, tint = P.c.textMuted)
                    }
                }
            }
        }
        InkButton(
            "Add files", { Nav.pick(PickRequest(false, "Choose a file to protect", Files.primaryRoot) { SafeVault.add(listOf(it)) }) },
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp).fillMaxWidth(), Icons.Rounded.Shield,
        )
        if (busy) Box(Modifier.align(Alignment.Center).size(64.dp).clip(RoundedCornerShape(18.dp)).background(P.c.card), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = P.c.accent, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
        }
    }
    menuFor?.let { item ->
        Sheet({ menuFor = null }, item.name) {
            SettingRow("Move out of Safe folder", "Decrypt to ${Files.displayPath(File(item.original).parentFile ?: Files.primaryRoot)}", Icons.Rounded.Restore, onClick = { menuFor = null; SafeVault.restore(listOf(item)) })
            SettingRow("Delete forever", icon = Icons.Rounded.DeleteForever, onClick = { menuFor = null; SafeVault.delete(item) })
        }
    }
}

/** Number pad for PINs (4 to 8 digits). */
@Composable
fun PinPad(title: String, subtitle: String, error: String?, onBiometric: (() -> Unit)? = null, onDone: (String) -> Unit) {
    var pin by remember(title) { mutableStateOf("") }
    val h = app.pzip.ui.rememberHaptics()
    LaunchedEffect(error) { if (error != null) h.reject() }
    // Keys shrink on short screens so the whole pad and the button always fit.
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val key = when {
        screenH < 600 -> 54.dp
        screenH < 720 -> 62.dp
        else -> 74.dp
    }
    val rowGap = if (screenH < 720) 4.dp else 7.dp
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Gap(if (screenH < 720) 4 else 10)
        app.pzip.ui.components.PzipSpark(if (screenH < 720) 30.dp else 40.dp)
        Gap(if (screenH < 720) 10 else 18)
        Text(title, color = P.c.text, style = MaterialTheme.typography.headlineSmall)
        Gap(6)
        Text(subtitle, color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Gap(if (screenH < 720) 12 else 22)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(if (i < pin.length) P.c.ink else P.c.cardStrong))
            }
        }
        Gap(if (screenH < 720) 4 else 10)
        Text(error ?: " ", color = P.c.danger, style = MaterialTheme.typography.bodySmall)
        Gap(if (screenH < 720) 6 else 14)
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (onBiometric != null) "bio" else "", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = rowGap)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(key).clip(RoundedCornerShape(50)).background(if (k.length == 1) P.c.card else P.c.bg)
                            .clickable(enabled = k.isNotEmpty()) {
                                h.tick()
                                when (k) {
                                    "del" -> pin = pin.dropLast(1)
                                    "bio" -> onBiometric?.invoke()
                                    else -> if (pin.length < 8) pin += k
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "del" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = P.c.text)
                            "bio" -> Icon(Icons.Rounded.Fingerprint, "Fingerprint", tint = P.c.accent)
                            else -> Text(k, color = P.c.text, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
        Gap(if (screenH < 720) 6 else 14)
        InkButton("Continue", { if (pin.length >= 4) { onDone(pin); pin = "" } }, Modifier.fillMaxWidth(), enabled = pin.length >= 4)
    }
}

// ---------- Operations ----------

@Composable
fun TasksScreen() {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(P.c.bg)) {
        TopBar("Operations", "${TaskManager.running.size} running", onBack = { Nav.back() }) {
            if (TaskManager.tasks.any { it.state != TaskState.RUNNING }) TextLink("Clear", { TaskManager.clearFinished() })
        }
        if (TaskManager.tasks.isEmpty()) EmptyState(Icons.Rounded.SwapVert, "No operations", "Compressing, extracting, copying and moving show up here.")
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            items(TaskManager.tasks.toList(), key = { it.id }) { t -> TaskCard(t, context) }
        }
    }
}

@Composable
fun TaskCard(t: PzipTask, context: Context) {
    PCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (t.state) {
                            TaskState.RUNNING -> t.kind.verb
                            TaskState.DONE -> t.kind.done
                            TaskState.FAILED -> "Failed"
                            TaskState.CANCELLED -> "Cancelled"
                        },
                        color = when (t.state) {
                            TaskState.FAILED -> P.c.danger
                            TaskState.DONE -> P.c.success
                            else -> P.c.textMuted
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(t.title, color = P.c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (t.state == TaskState.RUNNING) IconBtn(Icons.Rounded.Close, "Cancel", { t.cancel() }, tint = P.c.textMuted)
            }
            Gap(8)
            if (t.state == TaskState.RUNNING) {
                ProgressLine(t.fraction)
                Gap(6)
                Text(
                    buildString {
                        append("${(t.fraction * 100).toInt()}%")
                        if (t.totalBytes > 0) append(" · ${Files.formatSize(t.processed)} of ${Files.formatSize(t.totalBytes)}")
                        if (t.speed > 0) append(" · ${Files.formatSize(t.speed)}/s")
                        if (t.etaMillis > 0) append(" · ${Files.formatDuration(t.etaMillis)} left")
                    },
                    color = P.c.textMuted, style = MaterialTheme.typography.bodySmall,
                )
                if (t.currentFile.isNotEmpty()) Text(t.currentFile, color = P.c.textFaint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(t.message, color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                val out = t.output
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (t.state == TaskState.DONE && out != null && out.exists()) {
                        SoftButton("Show", { Nav.go(Screen.Browse(if (out.isDirectory) out else out.parentFile ?: out)) })
                        if (out.isFile) LineButton("Share", { Files.share(context, listOf(out)) })
                    }
                    if (t.state == TaskState.FAILED && t.retry != null) SoftButton(if (t.needsPassword) "Enter password" else "Retry", { t.retry?.invoke() })
                }
            }
        }
    }
}
