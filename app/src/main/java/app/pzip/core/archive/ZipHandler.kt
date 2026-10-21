package app.pzip.core.archive

import app.pzip.core.OverwriteMode
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.outputstream.SplitOutputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

internal object ZipHandler {

    private fun open(file: File, password: CharArray?): ZipFile {
        val zip = ZipFile(file, password)
        if (!zip.isValidZipFile) throw ArchiveException("Not a valid ZIP file or it is damaged")
        return zip
    }

    fun list(file: File, password: CharArray?): ArchiveInfo {
        val zip = open(file, password)
        zip.use {
            val entries = zip.fileHeaders.map { h -> h.toEntry() }
            return ArchiveInfo(
                file = file,
                type = ArchiveType.ZIP,
                entries = entries,
                multiVolume = zip.isSplitArchive,
                comment = runCatching { zip.comment ?: "" }.getOrDefault(""),
            )
        }
    }

    private fun FileHeader.toEntry() = ArchiveEntry(
        path = Output.normalize(fileName),
        isDir = isDirectory,
        size = uncompressedSize,
        packed = compressedSize,
        time = lastModifiedTimeEpoch,
        encrypted = isEncrypted,
        crc = crc,
        method = when {
            encryptionMethod == EncryptionMethod.AES -> "AES · " + compressionMethodLabel(this)
            encryptionMethod == EncryptionMethod.ZIP_STANDARD -> "ZipCrypto · " + compressionMethodLabel(this)
            else -> compressionMethodLabel(this)
        },
    )

    private fun compressionMethodLabel(h: FileHeader) = when (h.compressionMethod) {
        CompressionMethod.STORE -> "Store"
        CompressionMethod.DEFLATE -> "Deflate"
        else -> h.compressionMethod?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
    }

    fun extract(file: File, password: CharArray?, dest: File, selection: Selection?, mode: OverwriteMode, progress: Progress): ExtractResult {
        val zip = open(file, password)
        zip.use {
            val headers = zip.fileHeaders
            val out = Output(dest, selection, mode, progress)
            if (password == null && headers.any { it.isEncrypted && out.wanted(it.fileName) }) throw PasswordRequiredException()
            progress.total(headers.filter { !it.isDirectory && out.wanted(it.fileName) }.sumOf { it.uncompressedSize })
            for (h in headers) {
                progress.check()
                if (h.isDirectory) {
                    out.dir(h.fileName)
                    continue
                }
                if (!out.wanted(h.fileName)) continue
                mapErrors { zip.getInputStream(h).use { input -> out.write(h.fileName, h.lastModifiedTimeEpoch, input) } }
            }
            return out.result()
        }
    }

    fun test(file: File, password: CharArray?, progress: Progress): TestResult {
        val zip = open(file, password)
        zip.use {
            val headers = zip.fileHeaders.filter { !it.isDirectory }
            if (password == null && headers.any { it.isEncrypted }) throw PasswordRequiredException()
            progress.total(headers.sumOf { it.uncompressedSize })
            val errors = mutableListOf<String>()
            val buf = ByteArray(256 * 1024)
            for (h in headers) {
                progress.check()
                progress.file(h.fileName.substringAfterLast('/'))
                try {
                    mapErrors { zip.getInputStream(h).use { Output.drain(it, progress, buf) } }
                } catch (e: CancelledException) {
                    throw e
                } catch (e: WrongPasswordException) {
                    throw e
                } catch (e: Exception) {
                    errors += "${h.fileName}: ${e.message ?: "damaged"}"
                }
            }
            return TestResult(headers.size, errors)
        }
    }

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: ZipException) {
        throw when (e.type) {
            ZipException.Type.WRONG_PASSWORD -> WrongPasswordException()
            ZipException.Type.CHECKSUM_MISMATCH -> ArchiveException("Checksum failed — wrong password or damaged data")
            else -> if (e.message?.contains("password", true) == true) WrongPasswordException() else ArchiveException(e.message ?: "ZIP error")
        }
    }

    fun create(items: List<Packer.Item>, out: File, opts: CreateOptions, progress: Progress) {
        val password = opts.password?.takeIf { it.isNotEmpty() }
        val raw: OutputStream = if (opts.splitSize > 0) SplitOutputStream(out, opts.splitSize) else BufferedOutputStream(FileOutputStream(out), 256 * 1024)
        ZipOutputStream(raw, password).use { zos ->
            val buf = ByteArray(256 * 1024)
            for (item in items) {
                progress.check()
                val p = ZipParameters().apply {
                    fileNameInZip = if (item.isDir) item.name + "/" else item.name
                    lastModifiedFileTime = item.file.lastModified()
                    if (opts.level == Level.STORE || item.isDir) {
                        compressionMethod = CompressionMethod.STORE
                    } else {
                        compressionMethod = CompressionMethod.DEFLATE
                        compressionLevel = when (opts.level) {
                            Level.FASTEST -> CompressionLevel.FASTEST
                            Level.FAST -> CompressionLevel.FAST
                            Level.NORMAL -> CompressionLevel.NORMAL
                            Level.GOOD -> CompressionLevel.MAXIMUM
                            else -> CompressionLevel.ULTRA
                        }
                    }
                    if (!item.isDir) entrySize = item.file.length()
                    if (password != null && !item.isDir) {
                        isEncryptFiles = true
                        when (opts.encryption) {
                            ZipEncryption.AES256 -> {
                                encryptionMethod = EncryptionMethod.AES
                                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                            }
                            ZipEncryption.AES128 -> {
                                encryptionMethod = EncryptionMethod.AES
                                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_128
                            }
                            ZipEncryption.ZIPCRYPTO -> encryptionMethod = EncryptionMethod.ZIP_STANDARD
                        }
                    }
                }
                zos.putNextEntry(p)
                if (!item.isDir) {
                    progress.file(item.file.name)
                    FileInputStream(item.file).use { input ->
                        while (true) {
                            progress.check()
                            val n = input.read(buf)
                            if (n < 0) break
                            zos.write(buf, 0, n)
                            progress.add(n.toLong())
                        }
                    }
                }
                zos.closeEntry()
            }
        }
    }

    // ---------- Editing (ZIP only) ----------

    fun delete(file: File, paths: Collection<String>) {
        val zip = ZipFile(file)
        zip.use {
            val names = zip.fileHeaders.map { it.fileName }.filter { n ->
                val norm = Output.normalize(n)
                paths.any { norm == it || norm.startsWith("$it/") }
            }
            if (names.isNotEmpty()) zip.removeFiles(names)
        }
    }

    fun rename(file: File, from: String, to: String) {
        val zip = ZipFile(file)
        zip.use {
            val map = LinkedHashMap<String, String>()
            zip.fileHeaders.forEach { h ->
                val norm = Output.normalize(h.fileName)
                if (norm == from || norm.startsWith("$from/")) {
                    map[h.fileName] = to + norm.removePrefix(from) + if (h.isDirectory) "/" else ""
                }
            }
            if (map.isNotEmpty()) zip.renameFiles(map)
        }
    }

    fun add(file: File, sources: List<File>, intoDir: String, password: CharArray?, level: Level, progress: Progress) {
        val zip = ZipFile(file, password)
        zip.use {
            val items = Packer.collect(sources, file)
            progress.total(items.sumOf { if (it.isDir) 0L else it.file.length() })
            for (item in items) {
                progress.check()
                progress.file(item.file.name)
                val name = (if (intoDir.isEmpty()) "" else "$intoDir/") + item.name
                val p = ZipParameters().apply {
                    fileNameInZip = if (item.isDir) "$name/" else name
                    compressionMethod = if (level == Level.STORE) CompressionMethod.STORE else CompressionMethod.DEFLATE
                    if (password != null && password.isNotEmpty() && !item.isDir) {
                        isEncryptFiles = true
                        encryptionMethod = EncryptionMethod.AES
                        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    }
                }
                if (item.isDir) {
                    zip.addStream(java.io.ByteArrayInputStream(ByteArray(0)), p)
                } else {
                    FileInputStream(item.file).use { zip.addStream(it, p) }
                    progress.add(item.file.length())
                }
            }
        }
    }
}
