package app.pzip.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pzip.core.Files
import app.pzip.core.archive.ArchiveEngine
import app.pzip.core.archive.ArchiveEntry
import app.pzip.core.archive.ArchiveException
import app.pzip.core.archive.ArchiveInfo
import app.pzip.core.archive.CreateFormat
import app.pzip.core.archive.CreateOptions
import app.pzip.core.archive.PasswordRequiredException
import app.pzip.core.archive.Selection
import app.pzip.core.archive.WrongPasswordException
import app.pzip.core.tasks.TaskKind
import app.pzip.core.tasks.TaskManager
import app.pzip.ui.ExtractRequest
import app.pzip.ui.Nav
import app.pzip.ui.PickRequest
import app.pzip.ui.components.Chip
import app.pzip.ui.components.ConfirmDialog
import app.pzip.ui.components.EmptyState
import app.pzip.ui.components.Gap
import app.pzip.ui.components.IconBtn
import app.pzip.ui.components.InkButton
import app.pzip.ui.components.InputDialog
import app.pzip.ui.components.KindTile
import app.pzip.ui.components.LineButton
import app.pzip.ui.components.PasswordDialog
import app.pzip.ui.components.Pill
import app.pzip.ui.components.SettingRow
import app.pzip.ui.components.Sheet
import app.pzip.ui.components.Toaster
import app.pzip.ui.components.TopBar
import app.pzip.ui.theme.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(file: File, nav: app.pzip.ui.ArchiveNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<ArchiveInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var password by nav::password
    var askPassword by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var path by nav::path
    val selected = remember { mutableStateListOf<String>() }
    var showInfo by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showConvert by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<String?>(null) }
    var deleteEntries by remember { mutableStateOf<List<String>?>(null) }
    var renameEntry by remember { mutableStateOf<ArchiveEntry?>(null) }
    var reload by remember { mutableStateOf(0) }
    val h = app.pzip.ui.rememberHaptics()

    LaunchedEffect(file, password, reload, TaskManager.version) {
        error = null
        try {
            info = withContext(Dispatchers.IO) { ArchiveEngine.open(file, password) }
            askPassword = false
        } catch (e: PasswordRequiredException) {
            askPassword = true
        } catch (e: WrongPasswordException) {
            wrong = true
            askPassword = true
        } catch (e: Exception) {
            error = e.message ?: "Can't open this archive"
        } catch (e: UnsatisfiedLinkError) {
            error = "RAR engine is not available on this device"
        }
    }

    BackHandler(enabled = selected.isNotEmpty() || path.isNotEmpty()) {
        if (selected.isNotEmpty()) selected.clear() else path = path.substringBeforeLast('/', "")
    }

    val inf = info
    val children = remember(inf, path) { inf?.children(path) ?: emptyList() }

    fun needPw(entry: ArchiveEntry?): Boolean = inf != null && password == null && (entry?.encrypted == true || inf.encryptedHeaders)

    fun preview(e: ArchiveEntry) {
        if (needPw(e)) {
            askPassword = true
            return
        }
        opening = e.name
        scope.launch {
            try {
                val out = withContext(Dispatchers.IO) { ArchiveEngine.extractOne(file, password, e.path, context.cacheDir) }
                Nav.open(context, out)
            } catch (ex: WrongPasswordException) {
                wrong = true
                askPassword = true
            } catch (ex: PasswordRequiredException) {
                askPassword = true
            } catch (ex: Exception) {
                Toaster.show(ex.message ?: "Couldn't open")
            }
            opening = null
        }
    }

    Box(Modifier.fillMaxSize().background(P.c.bg)) {
        Column(Modifier.fillMaxSize()) {
            if (selected.isNotEmpty()) {
                TopBar("${selected.size} selected", onBack = { selected.clear() }) {
                    IconBtn(Icons.Rounded.SelectAll, "Select all", {
                        if (selected.size == children.size) selected.clear() else {
                            selected.clear()
                            selected.addAll(children.map { it.path })
                        }
                    })
                }
            } else {
                TopBar(
                    file.name,
                    subtitle = inf?.let { "${it.type.label} · ${it.files} files · ${Files.formatSize(it.totalSize)}" } ?: "Opening…",
                    onBack = { if (path.isNotEmpty()) path = path.substringBeforeLast('/', "") else Nav.back() },
                ) {
                    IconBtn(Icons.Rounded.Info, "Archive info", { showInfo = true }, enabled = inf != null)
                    IconBtn(Icons.Rounded.MoreVert, "More", { showMore = true })
                }
            }

            if (inf != null) {
                app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Pill(inf.type.label)
                    if (inf.anyEncrypted) Pill(if (inf.encryptedHeaders) "Names encrypted" else "Encrypted", icon = Icons.Rounded.Lock)
                    if (inf.solid) Pill("Solid", bg = P.c.card, fg = P.c.textMuted)
                    if (inf.multiVolume) Pill("Multi-volume", bg = P.c.card, fg = P.c.textMuted, icon = Icons.Rounded.Layers)
                    if (inf.packedSize > 0 && inf.totalSize > 0) Pill("Saved ${(100 - inf.packedSize * 100 / inf.totalSize).coerceAtLeast(0)}%", bg = P.c.card, fg = P.c.textMuted)
                }
                // Path inside the archive
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val parts = if (path.isEmpty()) emptyList() else path.split('/')
                    Crumb(archiveBaseName(file), parts.isEmpty()) { path = "" }
                    parts.forEachIndexed { i, p ->
                        Icon(Icons.Rounded.ChevronRight, null, tint = P.c.textFaint, modifier = Modifier.size(16.dp))
                        Crumb(p, i == parts.lastIndex) { path = parts.take(i + 1).joinToString("/") }
                    }
                }
            }

            when {
                error != null -> EmptyState(Icons.Rounded.ErrorOutline, "Can't open archive", error!!) {
                    LineButton("Try again", { reload++ })
                }
                inf == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    if (!askPassword) CircularProgressIndicator(color = P.c.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    else EmptyState(Icons.Rounded.Lock, "Password protected", "Even file names in this archive are encrypted.") {
                        InkButton("Enter password", { askPassword = true })
                    }
                }
                children.isEmpty() -> EmptyState(Icons.Rounded.Unarchive, "Empty folder", "Nothing inside this folder.")
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 130.dp)) {
                    items(children, key = { it.path }) { e ->
                        val sel = e.path in selected
                        Row(
                            Modifier.fillMaxWidth().background(if (sel) P.c.accentSoft else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            selected.isNotEmpty() -> if (sel) selected.remove(e.path) else selected.add(e.path)
                                            e.isDir -> path = e.path
                                            else -> preview(e)
                                        }
                                    },
                                    onLongClick = { h.longPress(); if (sel) selected.remove(e.path) else selected.add(e.path) },
                                ).padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                KindTile(if (e.isDir) app.pzip.core.FileKind.FOLDER else Files.kindOf(e.name))
                                if (selected.isNotEmpty()) Icon(
                                    if (sel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                                    tint = if (sel) P.c.accent else P.c.textFaint, modifier = Modifier.align(Alignment.BottomEnd).size(17.dp).clip(CircleShape).background(P.c.bg),
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(e.name, color = P.c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = if (e.isDir) {
                                    "${inf.children(e.path).size} items"
                                } else buildString {
                                    append(Files.formatSize(e.size))
                                    if (e.packed > 0 && e.size > 0) append(" → ${Files.formatSize(e.packed)}")
                                    if (e.time > 0) append(" · ${Files.formatDate(e.time)}")
                                }
                                Text(sub, color = P.c.textMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            if (e.encrypted) Icon(Icons.Rounded.Lock, "Encrypted", tint = P.c.textFaint, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        if (inf != null) {
            Row(
                Modifier.align(Alignment.BottomEnd).widthIn(max = 560.dp).fillMaxWidth().background(P.c.bg).navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selected.isNotEmpty()) {
                    if (inf.type.canEdit) LineButton("Delete", { deleteEntries = selected.toList() }, Modifier.weight(1f), Icons.Rounded.Delete)
                    if (inf.type.canEdit && selected.size == 1) LineButton("Rename", { renameEntry = children.firstOrNull { it.path == selected[0] } }, Modifier.weight(1f), Icons.Rounded.Edit)
                    InkButton("Extract ${selected.size}", {
                        Nav.extract = ExtractRequest(file, Selection(selected.toSet(), path), password)
                        selected.clear()
                    }, Modifier.weight(1.4f), Icons.Rounded.Unarchive)
                } else {
                    LineButton("Test", { testArchive(file, password) }, Modifier.weight(1f), Icons.Rounded.VerifiedUser)
                    InkButton("Extract all", { Nav.extract = ExtractRequest(file, null, password) }, Modifier.weight(1.6f), Icons.Rounded.Unarchive)
                }
            }
        }
    }

    if (askPassword) PasswordDialog(
        message = "“${file.name}” is protected. Enter its password to continue.", wrong = wrong,
        onDismiss = { askPassword = false; if (info == null) Nav.back() },
    ) {
        wrong = false
        askPassword = false
        password = it
    }

    opening?.let {
        app.pzip.ui.components.PDialog("Opening", { }, "Hide", { opening = null }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = P.c.accent, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(it, color = P.c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (showInfo && inf != null) Sheet({ showInfo = false }, "Archive info") {
        val rows = listOf(
            "Format" to inf.type.label,
            "Files" to inf.files.toString(),
            "Folders" to inf.folders.toString(),
            "Original size" to Files.formatSize(inf.totalSize),
            "Archive size" to Files.formatSize(file.length()),
            "Compression" to if (inf.totalSize > 0) "${(file.length() * 100 / inf.totalSize).coerceAtMost(999)}% of original" else "—",
            "Encryption" to when {
                inf.encryptedHeaders -> "File data and names"
                inf.anyEncrypted -> "File data (${inf.entries.firstOrNull { it.encrypted }?.method ?: "yes"})"
                else -> "None"
            },
            "Solid" to if (inf.solid) "Yes" else "No",
            "Multi-volume" to if (inf.multiVolume) "Yes" else "No",
            "Modified" to Files.formatFullDate(file.lastModified()),
            "Location" to Files.displayPath(file.parentFile ?: file),
        )
        rows.forEach { (k, v) -> InfoRow(k, v) }
        if (inf.comment.isNotBlank()) {
            Text("Comment", color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 22.dp, top = 12.dp))
            Text(inf.comment, color = P.c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
        }
    }

    if (showMore) Sheet({ showMore = false }) {
        SettingRow("Test archive", "Check every file for errors", Icons.Rounded.VerifiedUser, onClick = { showMore = false; testArchive(file, password) })
        SettingRow("Convert format", "Repack as ZIP, 7Z or TAR", Icons.Rounded.SwapHoriz, onClick = { showMore = false; showConvert = true })
        if (inf?.type?.canEdit == true) SettingRow("Add files", "Add files into this folder of the ZIP", Icons.Rounded.Add, onClick = {
            showMore = false
            Nav.pick(PickRequest(false, "Choose a file to add", Files.primaryRoot) { picked ->
                TaskManager.start(TaskKind.EDIT, file.name) { t ->
                    ArchiveEngine.add(file, listOf(picked), path, password, t)
                    "Added ${picked.name}"
                }
            })
        })
        SettingRow("Share archive", icon = Icons.Rounded.Share, onClick = { showMore = false; Files.share(context, listOf(file)) })
        SettingRow("Delete archive", icon = Icons.Rounded.Delete, onClick = {
            showMore = false
            app.pzip.core.FileOps.delete(listOf(file)) { Nav.back() }
        })
    }

    if (showConvert) Sheet({ showConvert = false }, "Convert to") {
        Text("A new archive is created next to the original.", color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 22.dp))
        Gap(10)
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
            CreateFormat.entries.forEach { fmt ->
                Chip(fmt.label, false, {
                    showConvert = false
                    convert(context.cacheDir, file, password, fmt)
                })
            }
        }
        Gap(12)
    }

    deleteEntries?.let { list ->
        ConfirmDialog("Delete from archive?", "${list.size} item(s) will be removed from ${file.name}.", "Delete", danger = true, onDismiss = { deleteEntries = null }) {
            selected.clear()
            TaskManager.start(TaskKind.EDIT, file.name) {
                ArchiveEngine.delete(file, list)
                "Removed ${list.size} item(s)"
            }
        }
    }
    renameEntry?.let { e ->
        InputDialog("Rename in archive", e.name, confirm = "Rename", onDismiss = { renameEntry = null }) { newName ->
            renameEntry = null
            selected.clear()
            val to = if (e.parent.isEmpty()) newName else "${e.parent}/$newName"
            TaskManager.start(TaskKind.EDIT, file.name) {
                ArchiveEngine.rename(file, e.path, to)
                "Renamed to $newName"
            }
        }
    }
}

@Composable
private fun Crumb(text: String, last: Boolean, onClick: () -> Unit) {
    Text(
        text, color = if (last) P.c.text else P.c.textMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (last) P.c.card else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Text(k, color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(130.dp))
        Text(v, color = P.c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

fun testArchive(file: File, password: CharArray?) {
    val task = TaskManager.start(TaskKind.TEST, file.name) { t ->
        val r = ArchiveEngine.test(file, password, t)
        if (r.errors.isNotEmpty()) throw ArchiveException("${r.errors.size} problem(s): ${r.errors.first()}")
        "No errors · ${r.files} file(s) OK"
    }
    task.retry = { testArchive(file, password) }
    Toaster.show("Testing ${file.name}…")
}

/** Repacks an archive in another format: extract to a temporary folder, then compress. */
fun convert(cache: File, file: File, password: CharArray?, fmt: CreateFormat) {
    val base = file.name.substringBefore('.')
    val out = Files.uniqueFile(file.parentFile!!, "$base.${fmt.ext}")
    TaskManager.start(TaskKind.CONVERT, "${file.name} → ${fmt.label}") { t ->
        val tmp = File(cache, "convert/${System.nanoTime()}")
        try {
            ArchiveEngine.extract(file, password, tmp, null, app.pzip.core.OverwriteMode.OVERWRITE, t)
            val sources = tmp.listFiles()?.toList().orEmpty()
            ArchiveEngine.create(sources, out, CreateOptions(format = fmt), object : app.pzip.core.archive.Progress {
                override val cancelled get() = t.cancelled
                override fun file(name: String) = t.file(name)
            })
            app.pzip.core.FileOps.scan(out)
            "Saved ${out.name}"
        } finally {
            tmp.deleteRecursively()
        }
    }.output = out
    Toaster.show("Converting to ${fmt.label}…")
}
