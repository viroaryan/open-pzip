package app.pzip.core.archive

import app.pzip.core.OverwriteMode
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.utils.MultiReadOnlySeekableByteChannel
import org.tukaani.xz.LZMA2Options
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import org.apache.commons.compress.PasswordRequiredException as CcPasswordRequired

internal object SevenZipHandler {

    /** Opens plain or multi-volume (.7z.001, .7z.002 ...) archives. */
    private fun open(file: File, password: CharArray?): SevenZFile {
        val builder = SevenZFile.builder()
        val volume = Regex("(.*\\.7z)\\.(\\d{3})$", RegexOption.IGNORE_CASE).matchEntire(file.name)
        if (volume != null) {
            val base = volume.groupValues[1]
            val parts = generateSequence(1) { it + 1 }
                .map { File(file.parentFile, "%s.%03d".format(base, it)) }
                .takeWhile { it.exists() }
                .toList()
            builder.setSeekableByteChannel(MultiReadOnlySeekableByteChannel.forFiles(*parts.toTypedArray()))
        } else {
            builder.setFile(file)
        }
        if (password != null) builder.setPassword(password)
        return try {
            builder.get()
        } catch (e: CcPasswordRequired) {
            throw PasswordRequiredException()
        } catch (e: IOException) {
            throw if (password != null) WrongPasswordException() else ArchiveException(e.message ?: "Damaged 7z archive")
        }
    }

    private fun SevenZArchiveEntry.encrypted() = runCatching {
        contentMethods?.any { it.method == SevenZMethod.AES256SHA256 } == true
    }.getOrDefault(false)

    private fun SevenZArchiveEntry.time() = if (hasLastModifiedDate) lastModifiedDate.time else 0L

    private fun SevenZArchiveEntry.methodLabel() = runCatching {
        contentMethods?.joinToString(" · ") { c ->
            when (c.method) {
                SevenZMethod.LZMA2 -> "LZMA2"
                SevenZMethod.LZMA -> "LZMA"
                SevenZMethod.AES256SHA256 -> "AES-256"
                SevenZMethod.COPY -> "Store"
                SevenZMethod.BZIP2 -> "BZip2"
                SevenZMethod.DEFLATE -> "Deflate"
                else -> c.method.name
            }
        } ?: ""
    }.getOrDefault("")

    fun list(file: File, password: CharArray?): ArchiveInfo {
        open(file, password).use { sz ->
            val entries = sz.entries.filter { !it.isAntiItem }.map { e ->
                ArchiveEntry(
                    path = Output.normalize(e.name ?: ""),
                    isDir = e.isDirectory,
                    size = if (e.isDirectory) -1 else e.size,
                    packed = -1,
                    time = e.time(),
                    encrypted = e.encrypted(),
                    crc = if (e.hasCrc) e.crcValue else -1,
                    method = e.methodLabel(),
                )
            }
            return ArchiveInfo(
                file = file,
                type = ArchiveType.SEVEN_Z,
                entries = entries,
                encryptedHeaders = password != null && entries.isNotEmpty() && entries.none { it.encrypted },
                solid = true,
                multiVolume = file.name.matches(Regex(".*\\.\\d{3}$")),
            )
        }
    }

    private class EntryStream(private val sz: SevenZFile) : InputStream() {
        override fun read(): Int = sz.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = sz.read(b, off, len)
        override fun close() {}
    }

    fun extract(file: File, password: CharArray?, dest: File, selection: Selection?, mode: OverwriteMode, progress: Progress): ExtractResult {
        open(file, password).use { sz ->
            val out = Output(dest, selection, mode, progress)
            val all = sz.entries.toList()
            if (password == null && all.any { it.encrypted() && out.wanted(it.name ?: "") }) throw PasswordRequiredException()
            progress.total(all.filter { !it.isDirectory && out.wanted(it.name ?: "") }.sumOf { it.size })
            var entry = sz.nextEntry
            while (entry != null) {
                progress.check()
                val name = entry.name ?: ""
                when {
                    entry.isAntiItem -> {}
                    entry.isDirectory -> out.dir(name)
                    out.wanted(name) -> wrap(password) { out.write(name, entry!!.time(), EntryStream(sz)) }
                }
                entry = sz.nextEntry
            }
            return out.result()
        }
    }

    fun test(file: File, password: CharArray?, progress: Progress): TestResult {
        open(file, password).use { sz ->
            val all = sz.entries.filter { !it.isDirectory }
            if (password == null && all.any { it.encrypted() }) throw PasswordRequiredException()
            progress.total(all.sumOf { it.size })
            val errors = mutableListOf<String>()
            val buf = ByteArray(256 * 1024)
            var entry = sz.nextEntry
            while (entry != null) {
                progress.check()
                if (!entry.isDirectory && !entry.isAntiItem) {
                    progress.file(entry.name.substringAfterLast('/'))
                    try {
                        Output.drain(EntryStream(sz), progress, buf)
                    } catch (e: CancelledException) {
                        throw e
                    } catch (e: IOException) {
                        errors += "${entry.name}: ${e.message ?: "damaged"}"
                    }
                }
                entry = sz.nextEntry
            }
            return TestResult(all.size, errors)
        }
    }

    private inline fun wrap(password: CharArray?, block: () -> Unit) {
        try {
            block()
        } catch (e: CancelledException) {
            throw e
        } catch (e: CcPasswordRequired) {
            throw PasswordRequiredException()
        } catch (e: IOException) {
            if (password != null && (e.message?.contains("checksum", true) == true || e.message?.contains("corrupt", true) == true)) {
                throw WrongPasswordException()
            }
            throw e
        }
    }

    fun create(items: List<Packer.Item>, out: File, opts: CreateOptions, progress: Progress) {
        SevenZOutputFile(out).use { sz ->
            val method = if (opts.level == Level.STORE) {
                SevenZMethodConfiguration(SevenZMethod.COPY)
            } else {
                // Presets above 7 need hundreds of MB of RAM, too much for phones.
                val preset = when (opts.level) {
                    Level.FASTEST -> 1
                    Level.FAST -> 3
                    Level.NORMAL -> 5
                    Level.GOOD -> 6
                    else -> 7
                }
                SevenZMethodConfiguration(SevenZMethod.LZMA2, LZMA2Options(preset))
            }
            sz.setContentMethods(listOf(method))
            val buf = ByteArray(256 * 1024)
            for (item in items) {
                progress.check()
                val entry = sz.createArchiveEntry(item.file, item.name)
                sz.putArchiveEntry(entry)
                if (!item.isDir) {
                    progress.file(item.file.name)
                    FileInputStream(item.file).use { input ->
                        while (true) {
                            progress.check()
                            val n = input.read(buf)
                            if (n < 0) break
                            sz.write(buf, 0, n)
                            progress.add(n.toLong())
                        }
                    }
                }
                sz.closeArchiveEntry()
            }
            sz.finish()
        }
    }
}
