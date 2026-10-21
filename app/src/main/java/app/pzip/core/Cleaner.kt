package app.pzip.core

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Finds space that can be freed: junk, large files, duplicates, old downloads and more. */
object Cleaner {
    enum class Kind(val title: String, val hint: String) {
        JUNK("Junk files", "Temporary files, thumbnails caches and logs"),
        EMPTY("Empty folders", "Folders with nothing inside"),
        DUPLICATES("Duplicate files", "Identical copies of the same file"),
        LARGE("Large files", "Files bigger than 100 MB"),
        OLD_DOWNLOADS("Old downloads", "Downloads older than 90 days"),
        SCREENSHOTS("Old screenshots", "Screenshots older than 30 days"),
        APKS("Install files", "APK files you may no longer need"),
    }

    data class Group(val kind: Kind, val files: List<File>, val duplicateSets: List<List<File>> = emptyList()) {
        val size get() = files.sumOf { if (it.isFile) it.length() else 0L }
    }

    data class Report(val groups: List<Group>) {
        val total get() = groups.flatMap { it.files }.distinctBy { it.absolutePath }
            .sumOf { if (it.isFile) it.length() else 0L }
    }

    private val junkExt = setOf("tmp", "temp", "log", "bak", "old", "dmp", "chk", "thumbdata", "partial", "crdownload")

    fun scan(root: File = Files.primaryRoot, onProgress: (String) -> Unit = {}): Report {
        val now = System.currentTimeMillis()
        val all = ArrayList<File>(20000)
        val empty = ArrayList<File>()
        val junk = ArrayList<File>()
        var seen = 0

        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles() ?: return
            if (children.isEmpty() && depth > 1) {
                empty += dir
                return
            }
            for (c in children) {
                if (c.isDirectory) {
                    if (depth == 0 && c.name == "Android") continue
                    if (c.name == ".thumbnails" || c.name == ".Thumbnails") {
                        junk += c.walkTopDown().filter { it.isFile }
                        continue
                    }
                    walk(c, depth + 1)
                } else {
                    all += c
                    if (Files.ext(c.name) in junkExt || c.name.startsWith("~$") || c.length() == 0L) junk += c
                    if (++seen % 500 == 0) onProgress(c.parentFile?.name ?: "")
                }
            }
        }
        walk(root, 0)

        val large = all.filter { it.length() > 100L * 1024 * 1024 }.sortedByDescending { it.length() }
        val downloads = Files.downloads().absolutePath
        val oldDownloads = all.filter { it.absolutePath.startsWith(downloads) && now - it.lastModified() > 90L * 86_400_000 }
        val screenshots = all.filter {
            it.absolutePath.contains("/Screenshots/", true) && Files.kindOf(it.name) == FileKind.IMAGE && now - it.lastModified() > 30L * 86_400_000
        }
        val apks = all.filter { Files.ext(it.name) in setOf("apk", "apks", "xapk", "apkm") }
        onProgress("Looking for duplicates")
        val dupSets = duplicates(all.filter { it.length() > 4096 })

        return Report(
            listOf(
                Group(Kind.JUNK, junk),
                Group(Kind.EMPTY, empty),
                // Keep the first file of each set, offer the rest.
                Group(Kind.DUPLICATES, dupSets.flatMap { it.drop(1) }, dupSets),
                Group(Kind.LARGE, large),
                Group(Kind.OLD_DOWNLOADS, oldDownloads),
                Group(Kind.SCREENSHOTS, screenshots),
                Group(Kind.APKS, apks),
            ).filter { it.files.isNotEmpty() },
        )
    }

    /** Groups by size, then by a quick sample hash, then confirms with a full SHA-256. */
    fun duplicates(files: List<File>): List<List<File>> {
        val bySize = files.groupBy { it.length() }.values.filter { it.size > 1 }
        val result = mutableListOf<List<File>>()
        for (group in bySize) {
            val bySample = group.groupBy { sampleHash(it) }.values.filter { it.size > 1 }
            for (g in bySample) {
                g.groupBy { fullHash(it) }.values.filter { it.size > 1 }.forEach { set ->
                    result += set.sortedBy { it.lastModified() }
                }
            }
        }
        return result.sortedByDescending { it.first().length() * (it.size - 1) }
    }

    private fun sampleHash(f: File): String = runCatching {
        val md = MessageDigest.getInstance("SHA-1")
        java.io.RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(16 * 1024)
            val n1 = raf.read(buf)
            if (n1 > 0) md.update(buf, 0, n1)
            if (raf.length() > buf.size * 2) {
                raf.seek(raf.length() / 2)
                val n2 = raf.read(buf)
                if (n2 > 0) md.update(buf, 0, n2)
                raf.seek(raf.length() - buf.size)
                val n3 = raf.read(buf)
                if (n3 > 0) md.update(buf, 0, n3)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault(f.absolutePath)

    private fun fullHash(f: File): String = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault(f.absolutePath)
}
