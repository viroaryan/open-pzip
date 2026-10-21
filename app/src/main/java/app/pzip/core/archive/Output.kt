package app.pzip.core.archive

import app.pzip.core.Files
import app.pzip.core.OverwriteMode
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files as NioFiles
import java.nio.file.StandardCopyOption

/** Shared extraction plumbing: safe paths, overwrite rules and progress-aware copying. */
internal class Output(
    private val dest: File,
    private val selection: Selection?,
    private val mode: OverwriteMode,
    private val progress: Progress,
    private val countBytes: Boolean = true,
) {
    private val root = dest.canonicalFile
    var files = 0
    var bytes = 0L
    var skipped = 0
    private val buffer = ByteArray(256 * 1024)

    init {
        if (!dest.exists() && !dest.mkdirs()) throw ArchiveException("Can't create ${dest.path}")
    }

    fun wanted(path: String): Boolean = selection == null || selection.includes(normalize(path))

    /** Maps an entry path to a file inside [dest], or null when it must be skipped. Blocks path traversal ("zip slip"). */
    fun resolve(entryPath: String): File? {
        var rel = normalize(entryPath)
        if (selection != null) {
            if (!selection.includes(rel)) return null
            if (selection.stripPrefix.isNotEmpty()) rel = rel.removePrefix(selection.stripPrefix).trimStart('/')
        }
        if (rel.isEmpty()) return null
        val target = File(root, rel).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) {
            skipped++
            return null
        }
        return target
    }

    fun dir(entryPath: String) {
        resolve(entryPath)?.mkdirs()
    }

    /** Applies the overwrite rule; returns the final file to write or null to skip. */
    fun prepare(target: File, entryTime: Long): File? {
        target.parentFile?.mkdirs()
        if (!target.exists()) return target
        if (target.isDirectory) return Files.uniqueFile(target.parentFile!!, target.name)
        return when (mode) {
            OverwriteMode.OVERWRITE -> target
            OverwriteMode.SKIP -> null
            OverwriteMode.RENAME -> Files.uniqueFile(target.parentFile!!, target.name)
            OverwriteMode.UPDATE -> if (entryTime > target.lastModified()) target else null
        }.also { if (it == null) skipped++ }
    }

    fun write(entryPath: String, entryTime: Long, input: InputStream) {
        val target = resolve(entryPath)?.let { prepare(it, entryTime) } ?: return
        progress.file(entryPath.substringAfterLast('/'))
        // Keep an existing file intact until the archive entry has been read and verified.
        val pending = File.createTempFile(".pzip-", ".tmp", target.parentFile)
        try {
            FileOutputStream(pending).use { out -> copy(input, out) }
            NioFiles.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            pending.delete()
            throw e
        }
        if (entryTime > 0) target.setLastModified(entryTime)
        files++
    }

    fun copy(input: InputStream, out: OutputStream) {
        while (true) {
            progress.check()
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            bytes += n
            if (countBytes) progress.add(n.toLong())
        }
    }

    fun result() = ExtractResult(files, bytes, skipped, dest)

    companion object {
        fun normalize(path: String): String =
            path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")

        /** Reads a stream to the end (used for testing integrity). */
        fun drain(input: InputStream, progress: Progress, buffer: ByteArray = ByteArray(256 * 1024)): Long {
            var total = 0L
            while (true) {
                progress.check()
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                progress.add(n.toLong())
            }
            return total
        }
    }
}

/** Counts bytes pulled from the underlying file so stream formats can report progress. */
internal class CountingInput(private val input: InputStream, private val progress: Progress) : InputStream() {
    override fun read(): Int = input.read().also { if (it >= 0) progress.add(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        progress.check()
        return input.read(b, off, len).also { if (it > 0) progress.add(it.toLong()) }
    }
    override fun skip(n: Long): Long = input.skip(n).also { if (it > 0) progress.add(it) }
    override fun available(): Int = input.available()
    override fun close() = input.close()
}
