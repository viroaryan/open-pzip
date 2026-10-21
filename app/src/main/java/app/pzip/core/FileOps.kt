package app.pzip.core

import android.content.Context
import android.media.MediaScannerConnection
import app.pzip.core.archive.Progress
import app.pzip.core.tasks.PzipTask
import app.pzip.core.tasks.TaskKind
import app.pzip.core.tasks.TaskManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.SecureRandom

/** Copy, move, delete and other file operations, run as background tasks. */
object FileOps {
    lateinit var app: Context
        private set

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun label(files: List<File>) = if (files.size == 1) files[0].name else "${files.size} items"

    fun scan(vararg files: File) {
        runCatching { MediaScannerConnection.scanFile(app, files.map { it.absolutePath }.toTypedArray(), null, null) }
    }

    fun copy(files: List<File>, dest: File, onDone: () -> Unit = {}): PzipTask =
        TaskManager.start(TaskKind.COPY, label(files), { onDone() }) { task ->
            task.total(files.sumOf { sizeOf(it) })
            files.forEach { src ->
                if (dest.absolutePath.startsWith(src.absolutePath + "/") || dest == src) throw IOException("Can't copy a folder into itself")
                copyRecursive(src, Files.uniqueFile(dest, src.name), task)
            }
            scan(dest)
            "Copied to ${dest.name}"
        }

    fun move(files: List<File>, dest: File, onDone: () -> Unit = {}): PzipTask =
        TaskManager.start(TaskKind.MOVE, label(files), { onDone() }) { task ->
            task.total(files.sumOf { sizeOf(it) })
            files.forEach { src ->
                if (dest.absolutePath.startsWith(src.absolutePath + "/")) throw IOException("Can't move a folder into itself")
                if (src.parentFile == dest) return@forEach
                val target = Files.uniqueFile(dest, src.name)
                if (!src.renameTo(target)) {
                    copyRecursive(src, target, task)
                    src.deleteRecursively()
                } else {
                    task.add(sizeOf(target))
                }
            }
            scan(dest)
            "Moved to ${dest.name}"
        }

    fun delete(files: List<File>, permanent: Boolean = !Prefs.useTrash, onDone: () -> Unit = {}): PzipTask =
        TaskManager.start(TaskKind.DELETE, label(files), { onDone() }) { task ->
            task.total(files.size.toLong())
            files.forEach { f ->
                task.check()
                task.file(f.name)
                if (permanent) {
                    if (!f.deleteRecursively()) throw IOException("Couldn't delete ${f.name}")
                } else {
                    Trash.put(f)
                }
                task.add(1)
            }
            scan(*files.toTypedArray())
            if (permanent) "Deleted permanently" else "Moved to Trash"
        }

    fun shred(files: List<File>, passes: Int, onDone: () -> Unit = {}): PzipTask =
        TaskManager.start(TaskKind.SHRED, label(files), { onDone() }) { task ->
            val all = files.flatMap { f -> f.walkTopDown().filter { it.isFile }.toList() }
            task.total(all.sumOf { it.length() } * passes)
            all.forEach { shredFile(it, passes, task) }
            files.forEach { it.deleteRecursively() }
            "Shredded ${all.size} file(s) with $passes pass(es)"
        }

    fun sizeOf(f: File): Long = if (f.isDirectory) Files.folderSize(f) else f.length()

    fun copyRecursive(src: File, dest: File, progress: Progress) {
        progress.check()
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { copyRecursive(it, File(dest, it.name), progress) }
            return
        }
        progress.file(src.name)
        dest.parentFile?.mkdirs()
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(512 * 1024)
                    while (true) {
                        progress.check()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        progress.add(n.toLong())
                    }
                }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
        dest.setLastModified(src.lastModified())
    }

    private fun shredFile(f: File, passes: Int, progress: Progress) {
        progress.file(f.name)
        val rnd = SecureRandom()
        val len = f.length()
        val buf = ByteArray(256 * 1024)
        RandomAccessFile(f, "rws").use { raf ->
            repeat(passes) { pass ->
                raf.seek(0)
                var left = len
                while (left > 0) {
                    progress.check()
                    if (pass == passes - 1 && passes > 1) buf.fill(0) else rnd.nextBytes(buf)
                    val n = minOf(left, buf.size.toLong()).toInt()
                    raf.write(buf, 0, n)
                    left -= n
                    progress.add(n.toLong())
                }
                raf.fd.sync()
            }
        }
        val renamed = File(f.parentFile, java.util.UUID.randomUUID().toString().replace("-", ""))
        if (f.renameTo(renamed)) renamed.delete() else f.delete()
    }

    fun rename(file: File, newName: String): File? {
        if (!Files.isValidName(newName)) return null
        val target = File(file.parentFile, newName)
        if (target.exists()) return null
        return if (file.renameTo(target)) target.also { scan(file, it) } else null
    }

    fun newFolder(parent: File, name: String): File? {
        if (!Files.isValidName(name)) return null
        val f = File(parent, name)
        return if (!f.exists() && f.mkdirs()) f else null
    }

    fun newFile(parent: File, name: String): File? {
        if (!Files.isValidName(name)) return null
        val f = File(parent, name)
        return if (!f.exists() && runCatching { f.createNewFile() }.getOrDefault(false)) f else null
    }
}
