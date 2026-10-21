package app.pzip.core.archive

import app.pzip.core.OverwriteMode
import java.io.File

/** Bridge to the bundled native UnRAR library (RAR 1.5 through RAR 7, including RAR5 and volumes). */
object RarNative {
    init {
        System.loadLibrary("pziprar")
    }

    const val MODE_LIST = 0
    const val MODE_EXTRACT = 1
    const val MODE_TEST = 2

    @JvmStatic
    external fun run(path: String, mode: Int, cb: Callback): Int

    interface Callback {
        fun onArchive(flags: Int)
        fun onHeader(name: String, size: Long, packed: Long, mtime: Long, flags: Int, crc: Int, method: Int): String?
        fun onData(bytes: Int): Boolean
        fun needPassword(): String?
    }
}

internal object RarHandler {
    private const val RHDF_SPLITBEFORE = 0x01
    private const val RHDF_ENCRYPTED = 0x04
    private const val RHDF_DIRECTORY = 0x20
    private const val ROADF_VOLUME = 0x01
    private const val ROADF_SOLID = 0x08
    private const val ROADF_ENCHEADERS = 0x80

    private open class Base(private val password: CharArray?, private val progress: Progress) : RarNative.Callback {
        var archiveFlags = 0
        private var asked = 0
        override fun onArchive(flags: Int) {
            archiveFlags = flags
        }
        override fun onHeader(name: String, size: Long, packed: Long, mtime: Long, flags: Int, crc: Int, method: Int): String? = null
        override fun onData(bytes: Int): Boolean {
            progress.add(bytes.toLong())
            return !progress.cancelled
        }
        override fun needPassword(): String? {
            asked++
            // Asked again means the first password was rejected.
            return if (password == null || asked > 1) null else String(password)
        }
    }

    private fun check(code: Int, password: CharArray?) {
        when (code) {
            0 -> return
            1000 -> throw CancelledException()
            22 -> throw if (password == null) PasswordRequiredException() else WrongPasswordException()
            24 -> throw WrongPasswordException()
            11 -> throw ArchiveException("Not enough memory")
            12 -> throw if (password != null) WrongPasswordException() else ArchiveException("Data is damaged (CRC error)")
            13 -> throw ArchiveException("The archive is damaged")
            14 -> throw ArchiveException("Unknown archive format")
            15 -> throw ArchiveException("Can't open the archive or a volume is missing")
            16 -> throw ArchiveException("Can't create the output file")
            18 -> throw ArchiveException("Read error")
            19 -> throw ArchiveException("Write error — is the storage full?")
            25 -> throw ArchiveException("This archive needs a dictionary too large for this device")
            else -> throw ArchiveException("RAR error $code")
        }
    }

    private fun methodLabel(m: Int) = when (m) {
        0x30 -> "Store"
        0x31 -> "Fastest"
        0x32 -> "Fast"
        0x33 -> "Normal"
        0x34 -> "Good"
        0x35 -> "Best"
        else -> ""
    }

    fun list(file: File, password: CharArray?): ArchiveInfo {
        val entries = mutableListOf<ArchiveEntry>()
        val cb = object : Base(password, Progress.NONE) {
            override fun onHeader(name: String, size: Long, packed: Long, mtime: Long, flags: Int, crc: Int, method: Int): String? {
                if (flags and RHDF_SPLITBEFORE != 0) return null
                val dir = flags and RHDF_DIRECTORY != 0
                entries += ArchiveEntry(
                    path = Output.normalize(name),
                    isDir = dir,
                    size = if (dir) -1 else size,
                    packed = if (dir) -1 else packed,
                    time = mtime,
                    encrypted = flags and RHDF_ENCRYPTED != 0,
                    crc = crc.toLong() and 0xFFFFFFFFL,
                    method = (if (flags and RHDF_ENCRYPTED != 0) "AES · " else "") + methodLabel(method),
                )
                return null
            }
        }
        check(RarNative.run(file.absolutePath, RarNative.MODE_LIST, cb), password)
        return ArchiveInfo(
            file = file,
            type = ArchiveType.RAR,
            entries = entries,
            encryptedHeaders = cb.archiveFlags and ROADF_ENCHEADERS != 0,
            solid = cb.archiveFlags and ROADF_SOLID != 0,
            multiVolume = cb.archiveFlags and ROADF_VOLUME != 0,
        )
    }

    fun extract(file: File, password: CharArray?, dest: File, selection: Selection?, mode: OverwriteMode, progress: Progress): ExtractResult {
        val info = list(file, password)
        val out = Output(dest, selection, mode, progress)
        if (password == null && info.entries.any { it.encrypted && out.wanted(it.path) }) throw PasswordRequiredException()
        progress.total(info.entries.filter { !it.isDir && out.wanted(it.path) }.sumOf { maxOf(it.size, 0L) })
        var current: File? = null
        val cb = object : Base(password, progress) {
            override fun onHeader(name: String, size: Long, packed: Long, mtime: Long, flags: Int, crc: Int, method: Int): String? {
                if (progress.cancelled) return null
                if (flags and RHDF_DIRECTORY != 0) {
                    out.dir(name)
                    return null
                }
                if (!out.wanted(name)) return null
                val target = out.resolve(name)?.let { out.prepare(it, mtime) } ?: return null
                progress.file(name.substringAfterLast('/').substringAfterLast('\\'))
                out.files++
                out.bytes += size
                current = target
                return target.absolutePath
            }
        }
        val code = RarNative.run(file.absolutePath, RarNative.MODE_EXTRACT, cb)
        if (code != 0) current?.delete()
        check(code, password)
        return out.result()
    }

    fun test(file: File, password: CharArray?, progress: Progress): TestResult {
        val info = list(file, password)
        if (password == null && info.entries.any { it.encrypted }) throw PasswordRequiredException()
        progress.total(info.totalSize)
        var count = 0
        val cb = object : Base(password, progress) {
            override fun onHeader(name: String, size: Long, packed: Long, mtime: Long, flags: Int, crc: Int, method: Int): String? {
                if (flags and RHDF_DIRECTORY != 0) return null
                count++
                progress.file(name.substringAfterLast('/'))
                return ""
            }
        }
        val code = RarNative.run(file.absolutePath, RarNative.MODE_TEST, cb)
        return when (code) {
            0 -> TestResult(count, emptyList())
            1000, 22, 24 -> {
                check(code, password)
                TestResult(count, emptyList())
            }
            else -> TestResult(count, listOf(runCatching { check(code, password); "" }.exceptionOrNull()?.message ?: "Error $code"))
        }
    }
}
