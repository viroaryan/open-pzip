package app.pzip.core.archive

import java.io.File
import java.io.IOException
import java.util.Locale

/** Formats pzip can open. */
enum class ArchiveType(val label: String, val canEdit: Boolean = false) {
    ZIP("ZIP", canEdit = true), SEVEN_Z("7Z"), RAR("RAR"),
    TAR("TAR"), TAR_GZ("TAR.GZ"), TAR_BZ2("TAR.BZ2"), TAR_XZ("TAR.XZ"), TAR_LZMA("TAR.LZMA"), TAR_Z("TAR.Z"), TAR_LZ4("TAR.LZ4"),
    GZ("GZ"), BZ2("BZ2"), XZ("XZ"), LZMA("LZMA"), Z("Z"), LZ4("LZ4"),
    CPIO("CPIO"), AR("AR"), ARJ("ARJ");

    val isTar get() = name.startsWith("TAR")
    val isSingleStream get() = this in setOf(GZ, BZ2, XZ, LZMA, Z, LZ4)

    companion object {
        fun detect(file: File): ArchiveType? = byName(file.name) ?: sniff(file)

        fun byName(name: String): ArchiveType? {
            val n = name.lowercase(Locale.ROOT)
            return when {
                n.endsWith(".tar.gz") || n.endsWith(".tgz") -> TAR_GZ
                n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> TAR_BZ2
                n.endsWith(".tar.xz") || n.endsWith(".txz") -> TAR_XZ
                n.endsWith(".tar.lzma") || n.endsWith(".tlz") -> TAR_LZMA
                n.endsWith(".tar.z") -> TAR_Z
                n.endsWith(".tar.lz4") -> TAR_LZ4
                n.endsWith(".tar") -> TAR
                n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") ||
                    n.endsWith(".aar") || n.endsWith(".cbz") || n.endsWith(".epub") || n.endsWith(".apkm") ||
                    Regex(".*\\.zip\\.\\d{3}$").matches(n) || Regex(".*\\.z\\d\\d$").matches(n) -> ZIP
                n.endsWith(".7z") || n.endsWith(".cb7") || Regex(".*\\.7z\\.\\d{3}$").matches(n) -> SEVEN_Z
                n.endsWith(".rar") || n.endsWith(".cbr") || Regex(".*\\.r\\d\\d$").matches(n) -> RAR
                n.endsWith(".gz") -> GZ
                n.endsWith(".bz2") -> BZ2
                n.endsWith(".xz") -> XZ
                n.endsWith(".lzma") -> LZMA
                n.endsWith(".z") -> Z
                n.endsWith(".lz4") -> LZ4
                n.endsWith(".cpio") -> CPIO
                n.endsWith(".ar") || n.endsWith(".deb") -> AR
                n.endsWith(".arj") -> ARJ
                else -> null
            }
        }

        /** Detects the format from the file's magic bytes. */
        fun sniff(file: File): ArchiveType? {
            val head = ByteArray(512)
            val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
            if (read < 6) return null
            fun at(off: Int, vararg b: Int) = b.indices.all { off + it < read && (head[off + it].toInt() and 0xFF) == b[it] }
            return when {
                at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) || at(0, 0x50, 0x4B, 0x07, 0x08) -> ZIP
                at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> SEVEN_Z
                at(0, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> RAR
                at(0, 0x1F, 0x8B) -> GZ
                at(0, 0x42, 0x5A, 0x68) -> BZ2
                at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> XZ
                at(0, 0x60, 0xEA) -> ARJ
                at(0, 0x21, 0x3C, 0x61, 0x72, 0x63, 0x68, 0x3E) -> AR
                read >= 262 && at(257, 0x75, 0x73, 0x74, 0x61, 0x72) -> TAR
                else -> null
            }
        }
    }
}

/** Formats pzip can create. */
enum class CreateFormat(val label: String, val ext: String, val supportsPassword: Boolean, val supportsSplit: Boolean) {
    ZIP("ZIP", "zip", true, true),
    SEVEN_Z("7Z", "7z", false, false),
    TAR("TAR", "tar", false, false),
    TAR_GZ("TAR.GZ", "tar.gz", false, false),
    TAR_XZ("TAR.XZ", "tar.xz", false, false),
    TAR_BZ2("TAR.BZ2", "tar.bz2", false, false),
}

enum class Level(val label: String) { STORE("Store"), FASTEST("Fastest"), FAST("Fast"), NORMAL("Normal"), GOOD("Good"), BEST("Best") }

enum class ZipEncryption(val label: String) { AES256("AES-256"), AES128("AES-128"), ZIPCRYPTO("ZipCrypto") }

data class CreateOptions(
    val format: CreateFormat = CreateFormat.ZIP,
    val level: Level = Level.NORMAL,
    val password: CharArray? = null,
    val encryption: ZipEncryption = ZipEncryption.AES256,
    val splitSize: Long = 0,
    val deleteSource: Boolean = false,
)

data class ArchiveEntry(
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val packed: Long,
    val time: Long,
    val encrypted: Boolean = false,
    val crc: Long = -1,
    val method: String = "",
) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = path.substringBeforeLast('/', "")
}

data class ArchiveInfo(
    val file: File,
    val type: ArchiveType,
    val entries: List<ArchiveEntry>,
    val encryptedHeaders: Boolean = false,
    val solid: Boolean = false,
    val multiVolume: Boolean = false,
    val comment: String = "",
) {
    val files get() = entries.count { !it.isDir }
    val folders get() = entries.count { it.isDir }
    val totalSize get() = entries.sumOf { if (it.size > 0) it.size else 0L }
    val packedSize get() = entries.sumOf { if (it.packed > 0) it.packed else 0L }
    val anyEncrypted get() = encryptedHeaders || entries.any { it.encrypted }

    /** Children of a virtual folder, synthesizing folders that only exist implicitly. */
    fun children(dir: String): List<ArchiveEntry> {
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val result = LinkedHashMap<String, ArchiveEntry>()
        for (e in entries) {
            if (!e.path.startsWith(prefix) || e.path == dir) continue
            val rest = e.path.removePrefix(prefix)
            val slash = rest.indexOf('/')
            if (slash < 0) {
                if (e.isDir) result.putIfAbsent(e.path, e) else result[e.path] = e
            } else {
                val folder = prefix + rest.substring(0, slash)
                if (!result.containsKey(folder)) result[folder] = ArchiveEntry(folder, true, -1, -1, 0)
            }
        }
        return result.values.sortedWith(compareBy<ArchiveEntry> { !it.isDir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Total size of everything under the given paths (files or folders). */
    fun sizeOf(paths: Collection<String>): Long =
        entries.filter { e -> !e.isDir && paths.any { p -> e.path == p || e.path.startsWith("$p/") } }.sumOf { maxOf(it.size, 0L) }
}

/** Reports progress and lets long work stop when the user cancels. */
interface Progress {
    fun total(bytes: Long) {}
    fun add(bytes: Long) {}
    fun file(name: String) {}
    val cancelled: Boolean get() = false
    fun check() {
        if (cancelled) throw CancelledException()
    }

    companion object {
        val NONE = object : Progress {}
    }
}

class CancelledException : IOException("Cancelled")
class PasswordRequiredException : IOException("This archive is password protected")
class WrongPasswordException : IOException("Wrong password")
class ArchiveException(message: String) : IOException(message)

data class ExtractResult(val files: Int, val bytes: Long, val skipped: Int, val output: File)
data class TestResult(val files: Int, val errors: List<String>)

/** What to extract: entry paths (folders include everything under them) and a prefix to strip. */
data class Selection(val paths: Set<String>, val stripPrefix: String = "") {
    fun includes(path: String): Boolean = paths.any { path == it || path.startsWith("$it/") }
}
