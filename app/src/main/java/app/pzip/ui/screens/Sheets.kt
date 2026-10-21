package app.pzip.ui.screens

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pzip.core.FileKind
import app.pzip.core.FileOps
import app.pzip.core.Files
import app.pzip.core.OverwriteMode
import app.pzip.core.Prefs
import app.pzip.core.Security
import app.pzip.core.archive.ArchiveEngine
import app.pzip.core.archive.ArchiveInfo
import app.pzip.core.archive.CreateFormat
import app.pzip.core.archive.CreateOptions
import app.pzip.core.archive.Level
import app.pzip.core.archive.PasswordRequiredException
import app.pzip.core.archive.WrongPasswordException
import app.pzip.core.archive.ZipEncryption
import app.pzip.core.tasks.TaskKind
import app.pzip.core.tasks.TaskManager
import app.pzip.ui.ExtractRequest
import app.pzip.ui.Nav
import app.pzip.ui.PickRequest
import app.pzip.ui.Screen
import app.pzip.ui.components.Chip
import app.pzip.ui.components.FileThumb
import app.pzip.ui.components.Gap
import app.pzip.ui.components.InkButton
import app.pzip.ui.components.LineButton
import app.pzip.ui.components.PSwitch
import app.pzip.ui.components.PasswordField
import app.pzip.ui.components.ProgressLine
import app.pzip.ui.components.Sheet
import app.pzip.ui.components.TextLink
import app.pzip.ui.components.Toaster
import app.pzip.ui.components.pzipFieldColors
import app.pzip.ui.theme.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------- Compress ----------

class CompressForm(val files: List<File>) {
    var name by mutableStateOf(defaultName(files))
    var dir by mutableStateOf(files.first().parentFile ?: Files.primaryRoot)
    var format by mutableStateOf(runCatching { CreateFormat.valueOf(Prefs.defaultFormat) }.getOrDefault(CreateFormat.ZIP))
    var level by mutableStateOf(Level.entries.getOrElse(Prefs.defaultLevel) { Level.NORMAL })
    var protect by mutableStateOf(false)
    var password by mutableStateOf("")
    var confirm by mutableStateOf("")
    var encryption by mutableStateOf(runCatching { ZipEncryption.valueOf(Prefs.defaultEncryption) }.getOrDefault(ZipEncryption.AES256))
    var split by mutableStateOf(0L)
    var deleteSource by mutableStateOf(Prefs.deleteAfterCompress)

    companion object {
        fun defaultName(files: List<File>): String =
            if (files.size == 1) files[0].let { if (it.isDirectory) it.name else it.nameWithoutExtension }
            else files.first().parentFile?.name?.takeIf { it.isNotBlank() && it != "0" } ?: "Archive"
    }
}

private var compressForm: CompressForm? = null

private val splitOptions = listOf(0L to "Off", 10L shl 20 to "10 MB", 25L shl 20 to "25 MB", 100L shl 20 to "100 MB", 700L shl 20 to "700 MB", 1L shl 30 to "1 GB", 4000L shl 20 to "4 GB")

@Composable
fun CompressSheet(files: List<File>) {
    val form = compressForm?.takeIf { it.files === files } ?: CompressForm(files).also { compressForm = it }
    val total by produceState(-1L, files) { value = withContext(Dispatchers.IO) { files.sumOf { FileOps.sizeOf(it) } } }
    val pwOk = !form.protect || (form.password.isNotEmpty() && form.password == form.confirm)
    val canPassword = form.format.supportsPassword

    Sheet({ Nav.compress = null; compressForm = null }, "Create archive") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumb(files.first(), 40.dp)
            Text(
                (if (files.size == 1) files[0].name else "${files.size} items") + if (total >= 0) " · ${Files.formatSize(total)}" else "",
                color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Gap(12)
        OutlinedTextField(
            form.name, { form.name = it }, label = { Text("Archive name") }, singleLine = true,
            suffix = { Text(".${form.format.ext}", color = P.c.textMuted) },
            colors = pzipFieldColors(), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Gap(10)
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.c.card)
                .clickable { Nav.pick(PickRequest(true, "Save archive in", form.dir) { form.dir = it }) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Folder, null, tint = P.c.accent)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Save in", color = P.c.textMuted, style = MaterialTheme.typography.labelMedium)
                Text(Files.displayPath(form.dir), color = P.c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text("Change", color = P.c.accent, style = MaterialTheme.typography.labelLarge)
        }

        Label("Format")
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
            CreateFormat.entries.forEach { f -> Chip(f.label, form.format == f, { form.format = f }) }
        }

        Label("Compression · ${form.level.label}")
        Slider(
            value = form.level.ordinal.toFloat(), onValueChange = { form.level = Level.entries[it.toInt()] },
            valueRange = 0f..5f, steps = 4,
            colors = SliderDefaults.colors(thumbColor = P.c.ink, activeTrackColor = P.c.ink, inactiveTrackColor = P.c.cardStrong, activeTickColor = P.c.onInk, inactiveTickColor = P.c.textFaint),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text("Faster, larger", color = P.c.textFaint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Slower, smaller", color = P.c.textFaint, style = MaterialTheme.typography.labelSmall)
        }

        Gap(8)
        app.pzip.ui.components.SettingRow(
            "Protect with password",
            if (canPassword) "Encrypt files with ${form.encryption.label}" else "Passwords need ZIP format",
            Icons.Rounded.Fingerprint,
            onClick = { if (canPassword) form.protect = !form.protect else { form.format = CreateFormat.ZIP; form.protect = true } },
        ) { PSwitch(form.protect && canPassword) { if (canPassword) form.protect = it else { form.format = CreateFormat.ZIP; form.protect = it } } }
        if (form.protect && canPassword) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                PasswordField(form.password, { form.password = it })
                Gap(8)
                PasswordField(form.confirm, { form.confirm = it }, "Confirm password", error = form.confirm.isNotEmpty() && form.confirm != form.password)
                Gap(8)
                val strength = Security.passwordStrength(form.password.toCharArray())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(strength / 4f, Modifier.weight(1f), color = when (strength) { 0, 1 -> P.c.danger; 2 -> P.c.accent; else -> P.c.success })
                    Spacer(Modifier.width(10.dp))
                    Text(listOf("Too short", "Weak", "Fair", "Good", "Strong")[strength], color = P.c.textMuted, style = MaterialTheme.typography.labelMedium)
                    TextLink("Generate", {
                        val g = Security.generatePassword()
                        form.password = g
                        form.confirm = g
                        Toaster.show("Strong password generated — save it somewhere safe")
                    })
                }
                Gap(8)
                app.pzip.ui.components.ChipFlow {
                    ZipEncryption.entries.forEach { e -> Chip(e.label, form.encryption == e, { form.encryption = e }) }
                }
                if (form.encryption == ZipEncryption.ZIPCRYPTO) Text("ZipCrypto is weak. Use it only for very old apps.", color = P.c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }

        if (form.format.supportsSplit) {
            Label("Split into parts")
            app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
                splitOptions.forEach { (size, label) -> Chip(label, form.split == size, { form.split = size }) }
            }
        }
        Gap(6)
        app.pzip.ui.components.SettingSwitch("Delete files after compressing", "Only after the archive is created successfully", checked = form.deleteSource, onChange = { form.deleteSource = it })

        Gap(10)
        InkButton(
            "Compress", {
                val name = form.name.trim().ifEmpty { "Archive" }
                if (!Files.isValidName(name)) {
                    Toaster.show("Invalid name")
                    return@InkButton
                }
                val out = Files.uniqueFile(form.dir, "$name.${form.format.ext}")
                val opts = CreateOptions(
                    format = form.format, level = form.level,
                    password = if (form.protect && canPassword) form.password.toCharArray() else null,
                    encryption = form.encryption, splitSize = if (form.format.supportsSplit) form.split else 0,
                    deleteSource = form.deleteSource,
                )
                startCompress(files, out, opts)
                Nav.compress = null
                compressForm = null
            },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp), Icons.Rounded.Archive, enabled = pwOk && files.isNotEmpty(),
        )
    }
}

fun startCompress(files: List<File>, out: File, opts: CreateOptions) {
    val task = TaskManager.start(TaskKind.COMPRESS, out.name) { t ->
        ArchiveEngine.create(files, out, opts, t)
        if (opts.deleteSource) files.forEach { it.deleteRecursively() }
        FileOps.scan(out)
        val ratio = if (t.totalBytes > 0) " · ${(out.length() * 100 / t.totalBytes).coerceAtMost(100)}% of original" else ""
        "Saved ${out.name} (${Files.formatSize(out.length())}$ratio)"
    }
    task.output = out
    Prefs.addRecent(out.absolutePath)
    Toaster.show("Compressing in the background…")
}

// ---------- Extract ----------

class ExtractForm(val req: ExtractRequest) {
    var mode by mutableStateOf(if (Prefs.extractToSubfolder) 0 else 1)
    var custom by mutableStateOf<File?>(null)
    var overwrite by mutableStateOf(Prefs.overwriteMode)
    var password by mutableStateOf(req.password?.let { String(it) } ?: "")
    var openAfter by mutableStateOf(Prefs.openAfterExtract)
}

private var extractForm: ExtractForm? = null

fun archiveBaseName(f: File): String {
    var n = f.name
    n = n.replace(Regex("\\.part\\d+\\.rar$", RegexOption.IGNORE_CASE), "")
    n = n.replace(Regex("\\.(tar\\.(gz|bz2|xz|lzma|z|lz4)|7z\\.\\d{3}|zip\\.\\d{3})$", RegexOption.IGNORE_CASE), "")
    return n.substringBeforeLast('.').ifEmpty { n }
}

@Composable
fun ExtractSheet(req: ExtractRequest) {
    val form = extractForm?.takeIf { it.req === req } ?: ExtractForm(req).also { extractForm = it }
    val file = req.file
    val parent = file.parentFile ?: Files.primaryRoot
    var info by androidx.compose.runtime.remember(req) { mutableStateOf<ArchiveInfo?>(null) }
    var headersLocked by androidx.compose.runtime.remember(req) { mutableStateOf(false) }
    LaunchedEffect(req) {
        try {
            info = withContext(Dispatchers.IO) { ArchiveEngine.open(file, req.password) }
        } catch (e: PasswordRequiredException) {
            headersLocked = true
        } catch (e: WrongPasswordException) {
            headersLocked = true
        } catch (_: Exception) {
        } catch (_: UnsatisfiedLinkError) {
        }
    }
    val needsPassword = headersLocked || info?.let { inf ->
        val sel = req.selection
        inf.entries.any { it.encrypted && (sel == null || sel.includes(it.path)) }
    } == true
    val dest = when (form.mode) {
        0 -> File(parent, archiveBaseName(file))
        1 -> parent
        else -> form.custom ?: parent
    }

    Sheet({ Nav.extract = null; extractForm = null }, if (req.selection != null) "Extract ${req.selection.paths.size} item(s)" else "Extract") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumb(file, 40.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(file.name, color = P.c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(info?.let { "${it.type.label} · ${it.files} files · ${Files.formatSize(it.totalSize)}" } ?: Files.formatSize(file.length()), color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Label("Extract to")
        DestOption(0, form, "New folder “${archiveBaseName(file)}”", Files.displayPath(File(parent, archiveBaseName(file))))
        DestOption(1, form, "This folder", Files.displayPath(parent))
        DestOption(2, form, "Choose a folder…", form.custom?.let { Files.displayPath(it) } ?: "Pick any location") {
            Nav.pick(PickRequest(true, "Extract to", form.custom ?: parent) { form.custom = it; form.mode = 2 })
        }

        if (needsPassword) {
            Label(if (req.wrongPassword) "Wrong password — try again" else "Password")
            Box(Modifier.padding(horizontal = 20.dp)) { PasswordField(form.password, { form.password = it }, error = req.wrongPassword) }
        }

        Label("If a file already exists")
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp)) {
            OverwriteMode.entries.forEach { m -> Chip(m.label, form.overwrite == m, { form.overwrite = m }) }
        }
        Gap(6)
        app.pzip.ui.components.SettingSwitch("Open folder when done", checked = form.openAfter, onChange = { form.openAfter = it })
        Gap(10)
        InkButton(
            "Extract", {
                val pw = form.password.takeIf { it.isNotEmpty() }?.toCharArray()
                startExtract(req, dest, pw, form.overwrite, form.openAfter)
                Nav.extract = null
                extractForm = null
            },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp), Icons.Rounded.Unarchive,
            enabled = !needsPassword || form.password.isNotEmpty(),
        )
    }
}

fun startExtract(req: ExtractRequest, dest: File, password: CharArray?, mode: OverwriteMode, openAfter: Boolean) {
    val task = TaskManager.start(TaskKind.EXTRACT, req.file.name, onDone = { if (openAfter) Nav.go(Screen.Browse(dest)) }) { t ->
        val r = ArchiveEngine.extract(req.file, password, dest, req.selection, mode, t)
        FileOps.scan(dest)
        buildString {
            append("${r.files} file(s) · ${Files.formatSize(r.bytes)}")
            if (r.skipped > 0) append(" · ${r.skipped} skipped")
        }
    }
    task.output = dest
    task.retry = { Nav.extract = req.copy(password = null, wrongPassword = true) }
    Prefs.addRecent(req.file.absolutePath)
    Toaster.show("Extracting in the background…")
}

@Composable
private fun DestOption(index: Int, form: ExtractForm, title: String, sub: String, onClick: (() -> Unit)? = null) {
    val sel = form.mode == index
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp))
            .background(if (sel) P.c.accentSoft else P.c.card)
            .clickable { if (onClick != null) onClick() else form.mode = index }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (sel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (sel) P.c.accent else P.c.textFaint)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = P.c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, color = P.c.textMuted, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (index == 2) Icon(Icons.Rounded.FolderOpen, null, tint = P.c.textMuted)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 8.dp))
}

// ---------- Details ----------

@Composable
fun DetailsSheet(file: File) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val stats by produceState<Pair<Long, Int>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            if (file.isDirectory) {
                var size = 0L
                var count = 0
                file.walkTopDown().forEach { if (it.isFile) { size += it.length(); count++ } }
                size to count
            } else file.length() to 1
        }
    }
    val media by produceState<List<Pair<String, String>>>(emptyList(), file) {
        value = withContext(Dispatchers.IO) { mediaInfo(file) }
    }
    Sheet({ Nav.details = null }, "Details") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumb(file, 56.dp)
            Text(file.name, color = P.c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
        }
        InfoRow("Type", if (file.isDirectory) "Folder" else Files.mimeOf(file.name))
        InfoRow("Size", stats?.let { (s, c) -> Files.formatSize(s) + if (file.isDirectory) " · $c files" else " (${"%,d".format(s)} bytes)" } ?: "Calculating…")
        InfoRow("Location", Files.displayPath(file.parentFile ?: file))
        InfoRow("Modified", Files.formatFullDate(file.lastModified()))
        InfoRow("Access", buildString {
            append(if (file.canRead()) "Read" else "No read")
            append(if (file.canWrite()) " · Write" else " · Read-only")
            if (file.isHidden) append(" · Hidden")
        })
        media.forEach { (k, v) -> InfoRow(k, v) }
        Gap(8)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineButton("Open location", { Nav.details = null; Nav.go(Screen.Browse(file.parentFile ?: file)) }, Modifier.weight(1f), Icons.Rounded.FolderOpen)
            if (file.isFile) LineButton("Checksums", { Nav.details = null; Nav.go(Screen.Hash(file)) }, Modifier.weight(1f), Icons.Rounded.AutoAwesome)
        }
        Gap(4)
        if (context == null) Unit
    }
}

private fun mediaInfo(file: File): List<Pair<String, String>> = runCatching {
    when (Files.kindOf(file)) {
        FileKind.IMAGE -> {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, o)
            if (o.outWidth > 0) listOf("Dimensions" to "${o.outWidth} × ${o.outHeight} (${"%.1f".format(o.outWidth * o.outHeight / 1e6)} MP)") else emptyList()
        }
        FileKind.VIDEO, FileKind.AUDIO -> {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.absolutePath)
                val list = mutableListOf<Pair<String, String>>()
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { list += "Duration" to Files.formatDuration(it) }
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (w != null && h != null) list += "Resolution" to "$w × $h"
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { list += "Bitrate" to "${it / 1000} kbps" }
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { list += "Artist" to it }
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { list += "Album" to it }
                list
            } finally {
                r.release()
            }
        }
        else -> emptyList()
    }
}.getOrDefault(emptyList())
