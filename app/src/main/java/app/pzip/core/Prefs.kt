package app.pzip.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class Accent(val label: String) { SKY("Sky"), MINT("Mint"), LAVENDER("Lavender"), PEACH("Peach") }
enum class SortBy(val label: String) { NAME("Name"), DATE("Date"), SIZE("Size"), TYPE("Type") }
enum class OverwriteMode(val label: String) { RENAME("Keep both"), OVERWRITE("Replace"), SKIP("Skip"), UPDATE("If newer") }
enum class ViewMode { LIST, GRID }

/** App settings backed by SharedPreferences and observable from Compose. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("pzip", Context.MODE_PRIVATE)
    }

    var onboarded by BoolPref("onboarded", false)
    var themeMode by EnumPref("theme", ThemeMode.LIGHT)
    var accent by EnumPref("accent", Accent.SKY)

    var defaultFormat by StringPref("def_format", "ZIP")
    var defaultLevel by IntPref("def_level", 3)
    var defaultEncryption by StringPref("def_enc", "AES256")
    var deleteAfterCompress by BoolPref("del_after", false)

    var extractToSubfolder by BoolPref("extract_sub", true)
    var overwriteMode by EnumPref("overwrite", OverwriteMode.RENAME)
    var openAfterExtract by BoolPref("open_after", true)

    var showHidden by BoolPref("hidden", false)
    var foldersFirst by BoolPref("folders_first", true)
    var sortBy by EnumPref("sort", SortBy.NAME)
    var sortDesc by BoolPref("sort_desc", false)
    var viewMode by EnumPref("view", ViewMode.LIST)
    var useTrash by BoolPref("use_trash", true)

    var appLock by BoolPref("lock", false)
    var pinHash by StringPref("pin_hash", "")
    var useBiometric by BoolPref("biometric", true)
    var blockScreenshots by BoolPref("secure", false)
    var keepHistory by BoolPref("history", true)

    var playerSpeed by FloatPref("speed", 1f)
    var resumePlayback by BoolPref("resume", true)
    var playerGestures by BoolPref("gestures", true)
    var haptics by BoolPref("haptics", true)

    var recents by StringPref("recents", "")
    var favorites by StringPref("favorites", "")
    var safePinHash by StringPref("safe_pin", "")

    fun recentList(): List<String> = recents.split('\n').filter { it.isNotBlank() }
    fun favoriteList(): List<String> = favorites.split('\n').filter { it.isNotBlank() }

    fun addRecent(path: String) {
        if (!keepHistory) return
        recents = (listOf(path) + recentList().filter { it != path }).take(30).joinToString("\n")
    }

    fun removeRecent(path: String) {
        recents = recentList().filter { it != path }.joinToString("\n")
    }

    fun toggleFavorite(path: String) {
        val list = favoriteList()
        favorites = (if (path in list) list - path else list + path).joinToString("\n")
    }

    fun positionOf(key: String): Long = sp.getLong("pos:$key", 0L)
    fun savePosition(key: String, pos: Long) {
        sp.edit().putLong("pos:$key", pos).apply()
    }

    private abstract class Pref<T>(val key: String, val def: T) : ReadWriteProperty<Any?, T> {
        private var state: MutableState<T>? = null
        abstract fun read(): T
        abstract fun write(e: SharedPreferences.Editor, v: T)
        private fun st(): MutableState<T> = state ?: mutableStateOf(read()).also { state = it }
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = st().value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            st().value = value
            sp.edit().also { write(it, value) }.apply()
        }
    }

    private class BoolPref(key: String, def: Boolean) : Pref<Boolean>(key, def) {
        override fun read() = sp.getBoolean(key, def)
        override fun write(e: SharedPreferences.Editor, v: Boolean) { e.putBoolean(key, v) }
    }

    private class IntPref(key: String, def: Int) : Pref<Int>(key, def) {
        override fun read() = sp.getInt(key, def)
        override fun write(e: SharedPreferences.Editor, v: Int) { e.putInt(key, v) }
    }

    private class FloatPref(key: String, def: Float) : Pref<Float>(key, def) {
        override fun read() = sp.getFloat(key, def)
        override fun write(e: SharedPreferences.Editor, v: Float) { e.putFloat(key, v) }
    }

    private class StringPref(key: String, def: String) : Pref<String>(key, def) {
        override fun read() = sp.getString(key, def) ?: def
        override fun write(e: SharedPreferences.Editor, v: String) { e.putString(key, v) }
    }

    private class EnumPref<E : Enum<E>>(key: String, def: E) : Pref<E>(key, def) {
        override fun read(): E {
            val name = sp.getString(key, null) ?: return def
            return def.declaringJavaClass.enumConstants?.firstOrNull { it.name == name } ?: def
        }
        override fun write(e: SharedPreferences.Editor, v: E) { e.putString(key, v.name) }
    }
}
