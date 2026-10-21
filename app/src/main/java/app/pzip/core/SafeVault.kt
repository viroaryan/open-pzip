package app.pzip.core

import androidx.compose.runtime.mutableStateListOf
import app.pzip.core.tasks.TaskKind
import app.pzip.core.tasks.TaskManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Safe folder: files are encrypted with AES-256-GCM using a key that lives in the
 * phone's hardware keystore, stored inside pzip's private storage and hidden from other apps.
 */
object SafeVault {
    data class Item(val id: String, val name: String, val size: Long, val added: Long, val original: String)

    val items = mutableStateListOf<Item>()
    private var loaded = false

    private val dir get() = File(FileOps.app.filesDir, "safe").also { it.mkdirs() }
    private val index get() = File(dir, "index.bin")
    val viewCache get() = File(FileOps.app.cacheDir, "safe_view")

    val hasPin get() = Prefs.safePinHash.isNotEmpty()

    @Synchronized
    fun load() {
        if (loaded) return
        loaded = true
        val list = runCatching {
            val out = ByteArrayOutputStream()
            FileInputStream(index).use { Security.Chunked.decrypt(Security.safeKey(), it, out) }
            val arr = JSONArray(out.toString(Charsets.UTF_8.name()))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Item(o.getString("id"), o.getString("name"), o.getLong("size"), o.getLong("added"), o.optString("original"))
            }
        }.getOrDefault(emptyList())
        items.clear()
        items.addAll(list.filter { File(dir, it.id).exists() })
    }

    @Synchronized
    private fun save() {
        val arr = JSONArray()
        items.toList().forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("size", it.size).put("added", it.added).put("original", it.original))
        }
        val tmp = File(dir, "index.tmp")
        FileOutputStream(tmp).use { Security.Chunked.encrypt(Security.safeKey(), ByteArrayInputStream(arr.toString().toByteArray()), it) }
        tmp.renameTo(index)
    }

    /** Encrypts files into the safe folder and removes the originals. */
    fun add(files: List<File>, onDone: () -> Unit = {}) {
        load()
        val all = files.flatMap { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.toList() else listOf(f) }
        TaskManager.start(TaskKind.SAFE, if (all.size == 1) all[0].name else "${all.size} files", { onDone() }) { task ->
            task.total(all.sumOf { it.length() })
            val key = Security.safeKey()
            val added = mutableListOf<Item>()
            for (f in all) {
                task.check()
                task.file(f.name)
                val id = UUID.randomUUID().toString()
                val target = File(dir, id)
                try {
                    FileInputStream(f).use { input ->
                        FileOutputStream(target).use { out -> Security.Chunked.encrypt(key, input, out) { task.add(it.toLong()); task.check() } }
                    }
                } catch (e: Exception) {
                    target.delete()
                    throw e
                }
                added += Item(id, f.name, f.length(), System.currentTimeMillis(), f.absolutePath)
                f.delete()
            }
            files.filter { it.isDirectory }.forEach { it.deleteRecursively() }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                items.addAll(0, added)
                save()
            }
            FileOps.scan(*files.toTypedArray())
            "Moved ${added.size} file(s) to Safe folder"
        }
    }

    /** Decrypts a file into a private cache folder for viewing. */
    fun open(item: Item): File {
        val out = File(viewCache, item.id.take(8)).also { it.mkdirs() }
        val f = File(out, item.name)
        if (f.exists() && f.length() == item.size) return f
        FileInputStream(File(dir, item.id)).use { input ->
            FileOutputStream(f).use { Security.Chunked.decrypt(Security.safeKey(), input, it) }
        }
        return f
    }

    /** Decrypts back to normal storage and removes it from the safe. */
    fun restore(items: List<Item>, onDone: () -> Unit = {}) {
        TaskManager.start(TaskKind.SAFE, if (items.size == 1) items[0].name else "${items.size} files", { onDone() }) { task ->
            task.total(items.sumOf { it.size })
            val key = Security.safeKey()
            for (item in items) {
                task.check()
                task.file(item.name)
                val original = File(item.original)
                val parent = original.parentFile?.takeIf { it.exists() || it.mkdirs() } ?: File(Files.downloads(), "pzip")
                parent.mkdirs()
                val target = Files.uniqueFile(parent, item.name)
                FileInputStream(File(dir, item.id)).use { input ->
                    FileOutputStream(target).use { Security.Chunked.decrypt(key, input, it) { n -> task.add(n.toLong()) } }
                }
                File(dir, item.id).delete()
                FileOps.scan(target)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    this@SafeVault.items.remove(item)
                    save()
                }
            }
            "Moved out of Safe folder"
        }
    }

    fun delete(item: Item) {
        File(dir, item.id).delete()
        items.remove(item)
        save()
    }

    fun clearViewCache() {
        viewCache.deleteRecursively()
    }
}
