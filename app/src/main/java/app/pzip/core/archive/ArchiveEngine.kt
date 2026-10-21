package app.pzip.core.archive

import app.pzip.core.OverwriteMode
import java.io.File

/** Collects files and folders into archive entries with relative names. */
object Packer {
    data class Item(val file: File, val name: String, val isDir: Boolean)

    fun collect(sources: List<File>, exclude: File? = null): List<Item> {
        val out = mutableListOf<Item>()
        val skip = exclude?.absolutePath
        for (src in sources) {
            val base = src.parentFile
            src.walkTopDown().forEach { f ->
                if (f.absolutePath == skip) return@forEach
                if (exclude != null && f.name.startsWith(exclude.name + ".") && f.parentFile == exclude.parentFile) return@forEach
                val rel = if (base == null) f.name else f.absolutePath.removePrefix(base.absolutePath).trimStart('/')
                out += Item(f, rel, f.isDirectory)
            }
        }
        return out
    }
}

/** Single entry point for every archive format. */
object ArchiveEngine {

    /** For split ZIPs (.z01 ...) and RAR volumes (.r00, .part2.rar) open the first part. */
    fun primary(file: File): File {
        val n = file.name
        Regex("(.*)\\.z\\d\\d$", RegexOption.IGNORE_CASE).matchEntire(n)?.let {
            val zip = File(file.parentFile, it.groupValues[1] + ".zip")
            if (zip.exists()) return zip
        }
        Regex("(.*)\\.r\\d\\d$", RegexOption.IGNORE_CASE).matchEntire(n)?.let {
            val rar = File(file.parentFile, it.groupValues[1] + ".rar")
            if (rar.exists()) return rar
        }
        Regex("(.*)\\.part(\\d+)\\.rar$", RegexOption.IGNORE_CASE).matchEntire(n)?.let {
            val digits = it.groupValues[2].length
            val first = File(file.parentFile, it.groupValues[1] + ".part" + "1".padStart(digits, '0') + ".rar")
            if (first.exists()) return first
        }
        Regex("(.*\\.7z)\\.\\d{3}$", RegexOption.IGNORE_CASE).matchEntire(n)?.let {
            val first = File(file.parentFile, it.groupValues[1] + ".001")
            if (first.exists()) return first
        }
        return file
    }

    fun typeOf(file: File): ArchiveType = ArchiveType.detect(file) ?: throw ArchiveException("This file is not a supported archive")

    fun open(file: File, password: CharArray? = null): ArchiveInfo {
        val f = primary(file)
        return when (val type = typeOf(f)) {
            ArchiveType.ZIP -> ZipHandler.list(f, password)
            ArchiveType.SEVEN_Z -> SevenZipHandler.list(f, password)
            ArchiveType.RAR -> RarHandler.list(f, password)
            else -> StreamHandler.list(f, type)
        }
    }

    fun extract(
        file: File,
        password: CharArray?,
        dest: File,
        selection: Selection? = null,
        mode: OverwriteMode = OverwriteMode.RENAME,
        progress: Progress = Progress.NONE,
    ): ExtractResult {
        val f = primary(file)
        return when (val type = typeOf(f)) {
            ArchiveType.ZIP -> ZipHandler.extract(f, password, dest, selection, mode, progress)
            ArchiveType.SEVEN_Z -> SevenZipHandler.extract(f, password, dest, selection, mode, progress)
            ArchiveType.RAR -> RarHandler.extract(f, password, dest, selection, mode, progress)
            else -> StreamHandler.extract(f, type, dest, selection, mode, progress)
        }
    }

    fun test(file: File, password: CharArray?, progress: Progress = Progress.NONE): TestResult {
        val f = primary(file)
        return when (val type = typeOf(f)) {
            ArchiveType.ZIP -> ZipHandler.test(f, password, progress)
            ArchiveType.SEVEN_Z -> SevenZipHandler.test(f, password, progress)
            ArchiveType.RAR -> RarHandler.test(f, password, progress)
            else -> StreamHandler.test(f, type, progress)
        }
    }

    fun create(sources: List<File>, out: File, opts: CreateOptions, progress: Progress = Progress.NONE) {
        val items = Packer.collect(sources, out)
        progress.total(items.sumOf { if (it.isDir) 0L else it.file.length() })
        out.parentFile?.mkdirs()
        try {
            when (opts.format) {
                CreateFormat.ZIP -> ZipHandler.create(items, out, opts, progress)
                CreateFormat.SEVEN_Z -> SevenZipHandler.create(items, out, opts, progress)
                else -> StreamHandler.createTar(items, out, opts.format, opts.level, progress)
            }
        } catch (e: Exception) {
            out.delete()
            // Remove partial split volumes too.
            out.parentFile?.listFiles()?.filter { it.name.matches(Regex(Regex.escape(out.nameWithoutExtension) + "\\.z\\d\\d")) }?.forEach { it.delete() }
            throw e
        }
    }

    /** Extracts one entry to a private cache folder, e.g. to preview it. */
    fun extractOne(file: File, password: CharArray?, entry: String, cacheRoot: File, progress: Progress = Progress.NONE): File {
        val dir = File(cacheRoot, "preview/" + Integer.toHexString((file.absolutePath + entry).hashCode()))
        dir.deleteRecursively()
        extract(file, password, dir, Selection(setOf(entry), entry.substringBeforeLast('/', "")), OverwriteMode.OVERWRITE, progress)
        return File(dir, entry.substringAfterLast('/'))
    }

    fun delete(file: File, paths: Collection<String>) {
        if (typeOf(file) != ArchiveType.ZIP) throw ArchiveException("Only ZIP archives can be edited")
        ZipHandler.delete(file, paths)
    }

    fun rename(file: File, from: String, to: String) {
        if (typeOf(file) != ArchiveType.ZIP) throw ArchiveException("Only ZIP archives can be edited")
        ZipHandler.rename(file, from, to)
    }

    fun add(file: File, sources: List<File>, intoDir: String, password: CharArray?, progress: Progress = Progress.NONE) {
        if (typeOf(file) != ArchiveType.ZIP) throw ArchiveException("Only ZIP archives can be edited")
        ZipHandler.add(file, sources, intoDir, password, Level.NORMAL, progress)
    }
}
