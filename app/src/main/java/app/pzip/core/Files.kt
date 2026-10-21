package app.pzip.core

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class FileKind { FOLDER, ARCHIVE, IMAGE, VIDEO, AUDIO, DOCUMENT, PDF, SHEET, SLIDES, TEXT, CODE, APK, FONT, OTHER }

object Files {
    private val archiveExt = setOf(
        "zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz", "txz", "lzma", "tlz", "z",
        "cpio", "ar", "deb", "arj", "jar", "cbz", "cbr", "cb7", "xapk", "apks", "001", "lz4", "r00",
    )
    private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "ico", "tif", "tiff", "dng", "raw")
    private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "3g2", "flv", "wmv", "m4v", "ts", "m2ts", "mts", "mpg", "mpeg", "ogv", "vob", "rmvb")
    private val audioExt = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr", "mid", "midi", "aiff", "ape", "mka")
    private val docExt = setOf("doc", "docx", "odt", "rtf", "epub", "mobi", "pages")
    private val sheetExt = setOf("xls", "xlsx", "ods", "csv", "tsv", "numbers")
    private val slideExt = setOf("ppt", "pptx", "odp", "key")
    private val textExt = setOf("txt", "md", "log", "ini", "cfg", "conf", "srt", "vtt", "ass", "nfo")
    private val codeExt = setOf("json", "xml", "html", "htm", "css", "js", "ts", "kt", "java", "py", "c", "cpp", "h", "sh", "yml", "yaml", "toml", "gradle", "sql", "php", "go", "rs", "swift", "dart")
    private val fontExt = setOf("ttf", "otf", "woff", "woff2")

    val documentExtensions = docExt + sheetExt + slideExt + textExt + setOf("pdf")
    val archiveExtensions = archiveExt

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun kindOf(file: File): FileKind = if (file.isDirectory) FileKind.FOLDER else kindOf(file.name)

    fun kindOf(name: String): FileKind {
        val lower = name.lowercase(Locale.ROOT)
        if (Regex(".*\\.(part\\d+\\.rar|r\\d\\d|z\\d\\d|7z\\.\\d{3}|zip\\.\\d{3})$").matches(lower)) return FileKind.ARCHIVE
        return when (val e = ext(lower)) {
            in archiveExt -> FileKind.ARCHIVE
            "apk", "apkm" -> FileKind.APK
            in imageExt -> FileKind.IMAGE
            in videoExt -> FileKind.VIDEO
            in audioExt -> FileKind.AUDIO
            "pdf" -> FileKind.PDF
            in docExt -> FileKind.DOCUMENT
            in sheetExt -> FileKind.SHEET
            in slideExt -> FileKind.SLIDES
            in textExt -> FileKind.TEXT
            in codeExt -> FileKind.CODE
            in fontExt -> FileKind.FONT
            else -> if (e.isEmpty()) FileKind.OTHER else FileKind.OTHER
        }
    }

    /** APKs are zip files, so they can be browsed like archives too. */
    fun isArchive(file: File): Boolean = file.isFile && kindOf(file.name) == FileKind.ARCHIVE

    fun mimeOf(name: String): String {
        val e = ext(name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(e) ?: when (kindOf(name)) {
            FileKind.VIDEO -> "video/*"
            FileKind.AUDIO -> "audio/*"
            FileKind.IMAGE -> "image/*"
            FileKind.TEXT, FileKind.CODE -> "text/plain"
            FileKind.APK -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes / 1024.0
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[i])
        else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun formatDate(ms: Long): String {
        if (ms <= 0) return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ms }
        val pattern = when {
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> "h:mm a"
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> "d MMM"
            else -> "d MMM yyyy"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    }

    fun formatFullDate(ms: Long): String =
        if (ms <= 0) "—" else SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(ms))

    fun formatDuration(ms: Long): String {
        if (ms < 0) return "--:--"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** Lists a folder honoring the hidden-file and sort settings. */
    fun list(dir: File, showHidden: Boolean = Prefs.showHidden): List<File> {
        val items = dir.listFiles()?.filter { showHidden || !it.name.startsWith(".") } ?: emptyList()
        return sort(items)
    }

    fun sort(items: List<File>, by: SortBy = Prefs.sortBy, desc: Boolean = Prefs.sortDesc, foldersFirst: Boolean = Prefs.foldersFirst): List<File> {
        val cmp: Comparator<File> = when (by) {
            SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortBy.DATE -> compareBy { it.lastModified() }
            SortBy.SIZE -> compareBy { if (it.isDirectory) 0L else it.length() }
            SortBy.TYPE -> compareBy<File> { ext(it.name) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val ordered = if (desc) cmp.reversed() else cmp
        return if (foldersFirst) items.sortedWith(compareBy<File> { !it.isDirectory }.then(ordered)) else items.sortedWith(ordered)
    }

    fun childCount(dir: File): Int = dir.list()?.count { Prefs.showHidden || !it.startsWith(".") } ?: 0

    fun folderSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Returns "name (1).ext" style names until a free one is found. */
    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.').takeIf { it > 0 && !File(dir, name).isDirectory } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    fun isValidName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\u0000' } && name.length <= 255

    // ---------- Storage ----------

    val primaryRoot: File get() = Environment.getExternalStorageDirectory()

    data class Volume(val root: File, val label: String, val removable: Boolean) {
        val total: Long get() = runCatching { StatFs(root.path).totalBytes }.getOrDefault(0L)
        val free: Long get() = runCatching { StatFs(root.path).availableBytes }.getOrDefault(0L)
        val used: Long get() = total - free
    }

    fun volumes(context: Context): List<Volume> {
        val result = mutableListOf(Volume(primaryRoot, "Internal storage", false))
        context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
            val path = dir.absolutePath
            val idx = path.indexOf("/Android/data/")
            if (idx > 0) {
                val root = File(path.substring(0, idx))
                if (root.absolutePath != primaryRoot.absolutePath && root.canRead()) {
                    result += Volume(root, "SD card", true)
                }
            }
        }
        return result
    }

    fun displayPath(file: File): String {
        val p = file.absolutePath.let { if (it == "/sdcard" || it.startsWith("/sdcard/")) primaryRoot.absolutePath + it.removePrefix("/sdcard") else it }
        val root = primaryRoot.absolutePath
        return if (p.startsWith(root)) "Internal storage" + p.removePrefix(root) else p
    }

    fun downloads(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    // ---------- Intents ----------

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun openExternal(context: Context, file: File, mime: String = mimeOf(file.name)) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, file), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, files: List<File>) {
        val list = files.filter { it.isFile }
        if (list.isEmpty()) {
            Toast.makeText(context, "Folders can't be shared directly. Compress them first.", Toast.LENGTH_LONG).show()
            return
        }
        val uris = ArrayList(list.map { uriFor(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(mimeOf(list[0].name))
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
        }
        intent.clipData = ClipData.newRawUri("", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
