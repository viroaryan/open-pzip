package app.pzip.core.archive

import app.pzip.core.OverwriteMode
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.z.ZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/** TAR family, single-file compressors (gz, bz2, xz ...) and cpio / ar / arj. */
internal object StreamHandler {

    private fun decompress(type: ArchiveType, input: InputStream): InputStream = when (type) {
        ArchiveType.TAR_GZ, ArchiveType.GZ -> GzipCompressorInputStream(input, true)
        ArchiveType.TAR_BZ2, ArchiveType.BZ2 -> BZip2CompressorInputStream(input, true)
        ArchiveType.TAR_XZ, ArchiveType.XZ -> XZCompressorInputStream(input, true)
        ArchiveType.TAR_LZMA, ArchiveType.LZMA -> LZMACompressorInputStream(input)
        ArchiveType.TAR_Z, ArchiveType.Z -> ZCompressorInputStream(input)
        ArchiveType.TAR_LZ4, ArchiveType.LZ4 -> FramedLZ4CompressorInputStream(input, true)
        else -> input
    }

    private fun archive(type: ArchiveType, input: InputStream): ArchiveInputStream<*> = when {
        type.isTar -> TarArchiveInputStream(decompress(type, input))
        type == ArchiveType.CPIO -> CpioArchiveInputStream(input)
        type == ArchiveType.AR -> ArArchiveInputStream(input)
        type == ArchiveType.ARJ -> ArjArchiveInputStream(input)
        else -> throw ArchiveException("Unsupported format")
    }

    private fun raw(file: File, progress: Progress?): InputStream {
        val base = BufferedInputStream(FileInputStream(file), 256 * 1024)
        return if (progress != null) BufferedInputStream(CountingInput(base, progress), 64 * 1024) else base
    }

    private fun singleName(file: File, type: ArchiveType, input: InputStream?): String {
        if (input is GzipCompressorInputStream) input.metaData?.fileName?.takeIf { it.isNotBlank() }?.let { return it.substringAfterLast('/') }
        val n = file.name
        val dot = n.lastIndexOf('.')
        val base = if (dot > 0) n.substring(0, dot) else "$n.out"
        return if (type == ArchiveType.GZ && n.endsWith(".svgz", true)) "$base.svg" else base
    }

    private fun org.apache.commons.compress.archivers.ArchiveEntry.isLink() =
        this is TarArchiveEntry && (isSymbolicLink || isLink)

    fun list(file: File, type: ArchiveType): ArchiveInfo {
        if (type.isSingleStream) {
            raw(file, null).use { r ->
                val d = decompress(type, r)
                return ArchiveInfo(file, type, listOf(ArchiveEntry(singleName(file, type, d), false, -1, file.length(), file.lastModified())))
            }
        }
        val entries = mutableListOf<ArchiveEntry>()
        raw(file, null).use { r ->
            val ais = archive(type, r)
            var e = ais.nextEntry
            while (e != null) {
                entries += ArchiveEntry(
                    path = Output.normalize(e.name),
                    isDir = e.isDirectory,
                    size = if (e.isDirectory) -1 else e.size,
                    packed = -1,
                    time = runCatching { e!!.lastModifiedDate?.time ?: 0L }.getOrDefault(0L),
                    method = if (e.isLink()) "Link" else "",
                )
                e = ais.nextEntry
            }
        }
        return ArchiveInfo(file, type, entries.filter { it.path.isNotEmpty() })
    }

    fun extract(file: File, type: ArchiveType, dest: File, selection: Selection?, mode: OverwriteMode, progress: Progress): ExtractResult {
        progress.total(file.length())
        val out = Output(dest, selection, mode, progress, countBytes = false)
        raw(file, progress).use { r ->
            if (type.isSingleStream) {
                val d = decompress(type, r)
                out.write(singleName(file, type, d), file.lastModified(), d)
                return out.result()
            }
            val ais = archive(type, r)
            var e = ais.nextEntry
            while (e != null) {
                progress.check()
                when {
                    e.isDirectory -> out.dir(e.name)
                    e.isLink() -> {} // Links are skipped for safety.
                    out.wanted(e.name) && ais.canReadEntryData(e) ->
                        out.write(e.name, runCatching { e!!.lastModifiedDate?.time ?: 0L }.getOrDefault(0L), ais)
                }
                e = ais.nextEntry
            }
        }
        return out.result()
    }

    fun test(file: File, type: ArchiveType, progress: Progress): TestResult {
        progress.total(file.length())
        val silent = object : Progress {
            override val cancelled get() = progress.cancelled
        }
        var count = 0
        val errors = mutableListOf<String>()
        try {
            raw(file, progress).use { r ->
                if (type.isSingleStream) {
                    Output.drain(decompress(type, r), silent)
                    count = 1
                } else {
                    val ais = archive(type, r)
                    var e = ais.nextEntry
                    while (e != null) {
                        progress.file(e.name.substringAfterLast('/'))
                        if (!e.isDirectory) {
                            Output.drain(ais, silent)
                            count++
                        }
                        e = ais.nextEntry
                    }
                }
            }
        } catch (e: CancelledException) {
            throw e
        } catch (e: Exception) {
            errors += e.message ?: "Damaged data"
        }
        return TestResult(count, errors)
    }

    fun createTar(items: List<Packer.Item>, out: File, format: CreateFormat, level: Level, progress: Progress) {
        val fileOut = BufferedOutputStream(FileOutputStream(out), 256 * 1024)
        val compressed: OutputStream = when (format) {
            CreateFormat.TAR_GZ -> GzipCompressorOutputStream(fileOut, GzipParameters().apply {
                compressionLevel = when (level) {
                    Level.STORE -> 0
                    Level.FASTEST -> 1
                    Level.FAST -> 3
                    Level.NORMAL -> 6
                    Level.GOOD -> 8
                    Level.BEST -> 9
                }
            })
            CreateFormat.TAR_XZ -> XZCompressorOutputStream(fileOut, when (level) {
                Level.STORE, Level.FASTEST -> 0
                Level.FAST -> 2
                Level.NORMAL -> 5
                Level.GOOD -> 6
                Level.BEST -> 7
            })
            CreateFormat.TAR_BZ2 -> BZip2CompressorOutputStream(fileOut, when (level) {
                Level.STORE, Level.FASTEST -> 1
                Level.FAST -> 3
                Level.NORMAL -> 6
                else -> 9
            })
            else -> fileOut
        }
        TarArchiveOutputStream(compressed).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            val buf = ByteArray(256 * 1024)
            for (item in items) {
                progress.check()
                val entry = TarArchiveEntry(item.file, item.name)
                tar.putArchiveEntry(entry)
                if (!item.isDir) {
                    progress.file(item.file.name)
                    FileInputStream(item.file).use { input ->
                        while (true) {
                            progress.check()
                            val n = input.read(buf)
                            if (n < 0) break
                            tar.write(buf, 0, n)
                            progress.add(n.toLong())
                        }
                    }
                }
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
    }
}
