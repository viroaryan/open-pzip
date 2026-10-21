package app.pzip.core

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** Recycle bin: deleted items stay here for 30 days before they are removed for good. */
object Trash {
    data class Item(val id: String, val name: String, val original: String, val deleted: Long, val size: Long, val isDir: Boolean, val stored: String)

    const val KEEP_DAYS = 30
    val items = mutableStateListOf<Item>()
    private var loaded = false

    /** Each storage volume gets its own trash so deleting stays an instant rename. */
    private fun trashDirFor(file: File): File {
        val dirs = FileOps.app.getExternalFilesDirs("trash").filterNotNull()
        val match = dirs.firstOrNull { d ->
            val root = d.absolutePath.substringBefore("/Android/data/")
            file.absolutePath.startsWith("$root/")
        }
        return (match ?: dirs.first()).also { it.mkdirs() }
    }

    private val indexFile get() = File(FileOps.app.filesDir, "trash.json")

    @Synchronized
    fun load() {
        if (loaded) return
        loaded = true
        val list = runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Item(o.getString("id"), o.getString("name"), o.getString("original"), o.getLong("deleted"), o.getLong("size"), o.getBoolean("dir"), o.getString("stored"))
            }
        }.getOrDefault(emptyList())
        items.clear()
        items.addAll(list.filter { File(it.stored).exists() })
        purgeExpired()
    }

    @Synchronized
    private fun save() {
        val arr = JSONArray()
        items.toList().forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("original", it.original).put("deleted", it.deleted)
                .put("size", it.size).put("dir", it.isDir).put("stored", it.stored))
        }
        indexFile.writeText(arr.toString())
    }

    fun put(file: File) {
        load()
        val id = UUID.randomUUID().toString()
        val stored = File(trashDirFor(file), id)
        val size = FileOps.sizeOf(file)
        val isDir = file.isDirectory
        if (!file.renameTo(stored)) {
            FileOps.copyRecursive(file, stored, app.pzip.core.archive.Progress.NONE)
            if (!file.deleteRecursively()) throw IOException("Couldn't delete ${file.name}")
        }
        val item = Item(id, file.name, file.absolutePath, System.currentTimeMillis(), size, isDir, stored.absolutePath)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            items.add(0, item)
            save()
        }
    }

    fun restore(item: Item): File {
        val original = File(item.original)
        original.parentFile?.mkdirs()
        val target = if (original.exists()) Files.uniqueFile(original.parentFile!!, original.name) else original
        val stored = File(item.stored)
        if (!stored.renameTo(target)) {
            FileOps.copyRecursive(stored, target, app.pzip.core.archive.Progress.NONE)
            stored.deleteRecursively()
        }
        items.remove(item)
        save()
        FileOps.scan(target)
        return target
    }

    fun deleteForever(item: Item) {
        File(item.stored).deleteRecursively()
        items.remove(item)
        save()
    }

    fun empty() {
        items.toList().forEach { File(it.stored).deleteRecursively() }
        items.clear()
        save()
    }

    fun totalSize(): Long = items.sumOf { it.size }

    fun daysLeft(item: Item): Int {
        val age = (System.currentTimeMillis() - item.deleted) / 86_400_000L
        return (KEEP_DAYS - age).toInt().coerceAtLeast(0)
    }

    private fun purgeExpired() {
        val expired = items.filter { daysLeft(it) <= 0 }
        if (expired.isEmpty()) return
        expired.forEach { File(it.stored).deleteRecursively() }
        items.removeAll(expired)
        save()
    }
}
