package app.pzip.core

import android.content.Context
import android.provider.MediaStore
import java.io.File

enum class Category(val label: String) {
    IMAGES("Images"), VIDEOS("Videos"), AUDIO("Audio"), DOCUMENTS("Documents"), ARCHIVES("Archives"), APKS("APKs"), DOWNLOADS("Downloads"), RECENT("Recent"),
}

/** Fast lookups through Android's media index instead of walking the whole disk. */
object MediaIndex {
    private val uri = MediaStore.Files.getContentUri("external")

    private fun query(context: Context, selection: String?, args: Array<String>?, limit: Int = 5000): List<File> {
        val result = ArrayList<File>()
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        runCatching {
            context.contentResolver.query(uri, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { c ->
                val col = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                while (c.moveToNext() && result.size < limit) {
                    val path = c.getString(col) ?: continue
                    val f = File(path)
                    if (!Prefs.showHidden && path.contains("/.")) continue
                    if (f.isFile) result += f
                }
            }
        }
        return result
    }

    private fun byExtensions(context: Context, exts: Collection<String>): List<File> {
        val sel = exts.joinToString(" OR ") { "${MediaStore.Files.FileColumns.DATA} LIKE ?" }
        val indexed = query(context, sel, exts.map { "%.$it" }.toTypedArray())
        // Files copied by USB/adb may not be indexed yet; walk storage so nothing is missed.
        val walked = walk(exts.toSet())
        return (indexed + walked).distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
    }

    private val walkCache = HashMap<Set<String>, Pair<Long, List<File>>>()

    /** Cached for a short time so the home screen doesn't rescan for every tile. */
    private fun walk(exts: Set<String>, limit: Int = 5000): List<File> {
        synchronized(walkCache) {
            walkCache[exts]?.let { (at, list) -> if (System.currentTimeMillis() - at < 20_000) return list.filter { it.exists() } }
        }
        val list = Files.primaryRoot.walkTopDown()
            .onEnter { it.name != "Android" && (Prefs.showHidden || !it.name.startsWith(".")) }
            .filter { it.isFile && Files.ext(it.name) in exts }
            .take(limit)
            .toList()
        synchronized(walkCache) { walkCache[exts] = System.currentTimeMillis() to list }
        return list
    }

    fun category(context: Context, cat: Category): List<File> = when (cat) {
        Category.IMAGES -> query(context, "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?", arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()))
        Category.VIDEOS -> query(context, "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?", arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()))
        Category.AUDIO -> query(context, "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?", arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString()))
        Category.DOCUMENTS -> byExtensions(context, Files.documentExtensions)
        Category.ARCHIVES -> byExtensions(context, Files.archiveExtensions - setOf("001", "r00", "z"))
        Category.APKS -> byExtensions(context, listOf("apk", "apks", "xapk", "apkm"))
        Category.DOWNLOADS -> Files.sort(Files.downloads().listFiles()?.filter { Prefs.showHidden || !it.name.startsWith(".") } ?: emptyList(), SortBy.DATE, true, true)
        Category.RECENT -> recent(context, 200)
    }

    fun recent(context: Context, limit: Int = 30): List<File> {
        val since = System.currentTimeMillis() / 1000 - 30L * 86400
        return query(
            context,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED}>? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE}!=?",
            arrayOf(since.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_NONE.toString()),
            limit,
        ).filter { !it.path.contains("/Android/") }
    }

    fun search(context: Context, text: String, limit: Int = 500): List<File> {
        if (text.isBlank()) return emptyList()
        val q = text.trim().replace("%", "\\%").replace("_", "\\_")
        val found = query(context, "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'", arrayOf("%$q%"), limit)
        if (found.isNotEmpty()) return found
        // Fall back to walking storage for files the media index doesn't know about.
        val lower = text.lowercase()
        return Files.primaryRoot.walkTopDown()
            .onEnter { it.name != "Android" && (Prefs.showHidden || !it.name.startsWith(".")) }
            .filter { it.name.lowercase().contains(lower) }
            .take(limit)
            .toList()
    }

    /** Count and total size for each category, used on the home screen. */
    fun stats(context: Context, cat: Category): Pair<Int, Long> {
        val files = category(context, cat)
        return files.size to files.sumOf { it.length() }
    }
}
