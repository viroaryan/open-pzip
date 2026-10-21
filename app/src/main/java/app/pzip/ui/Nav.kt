package app.pzip.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pzip.core.Category
import app.pzip.core.FileKind
import app.pzip.core.Files
import app.pzip.core.Prefs
import app.pzip.core.archive.Selection
import app.pzip.player.Engine
import app.pzip.ui.components.Toaster
import java.io.File

enum class Tab(val label: String) { HOME("Home"), FILES("Files"), GALLERY("Gallery"), CLEAN("Clean"), TOOLS("Tools") }

data class PickRequest(val folder: Boolean, val title: String, val start: File, val onPick: (File) -> Unit)

sealed class Screen {
    data class Root(val tab: Tab) : Screen()
    data class Browse(val dir: File) : Screen() {
        val nav = FolderNav(dir)
    }
    data class Pick(val req: PickRequest, val dir: File) : Screen() {
        val nav = FolderNav(dir)
    }
    data class Archive(val file: File) : Screen() {
        val nav = ArchiveNav()
    }
    data class CategoryView(val cat: Category) : Screen()
    data object Search : Screen()
    data object Trash : Screen()
    data object Safe : Screen()
    data object Tasks : Screen()
    data class Hash(val file: File?) : Screen()
    data object Bench : Screen()
    data object Settings : Screen()
    data object About : Screen()
    data object WhatsNew : Screen()
    data object Analyzer : Screen()
    data class Album(val name: String, val files: List<File>) : Screen()
    data class Images(val files: List<File>, val index: Int) : Screen()
    data class Pdf(val file: File) : Screen()
    data class Text(val file: File) : Screen()
}

/** What the compress and extract sheets are working on (shown over any screen). */
data class ExtractRequest(val file: File, val selection: Selection? = null, val password: CharArray? = null, val wrongPassword: Boolean = false)

object Nav {
    val stack = mutableStateListOf<Screen>(Screen.Root(Tab.HOME))
    val top: Screen get() = stack.last()
    val tab: Tab get() = (stack.firstOrNull() as? Screen.Root)?.tab ?: Tab.HOME

    var compress by mutableStateOf<List<File>?>(null)
    var extract by mutableStateOf<ExtractRequest?>(null)
    var details by mutableStateOf<File?>(null)

    fun go(s: Screen) {
        stack.add(s)
    }

    fun replace(s: Screen) {
        stack[stack.lastIndex] = s
    }

    fun switchTab(t: Tab) {
        stack.clear()
        stack.add(Screen.Root(t))
    }

    fun back(): Boolean {
        if (stack.size <= 1) {
            if (tab != Tab.HOME) {
                switchTab(Tab.HOME)
                return true
            }
            return false
        }
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun pick(req: PickRequest) = go(Screen.Pick(req, req.start))

    /** Opens any file with the best built-in viewer, or another app. */
    fun open(context: Context, file: File) {
        when (Files.kindOf(file)) {
            FileKind.FOLDER -> go(Screen.Browse(file))
            FileKind.ARCHIVE -> {
                Prefs.addRecent(file.absolutePath)
                go(Screen.Archive(file))
            }
            FileKind.VIDEO, FileKind.AUDIO -> {
                Engine.playFolder(file)
                Engine.openPlayer(context)
            }
            FileKind.IMAGE -> {
                val siblings = file.parentFile?.listFiles()
                    ?.filter { it.isFile && Files.kindOf(it.name) == FileKind.IMAGE && (Prefs.showHidden || !it.name.startsWith(".")) }
                    ?.let { Files.sort(it) } ?: listOf(file)
                go(Screen.Images(siblings, siblings.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0)))
            }
            FileKind.PDF -> go(Screen.Pdf(file))
            FileKind.TEXT, FileKind.CODE -> if (file.length() < 8L * 1024 * 1024) go(Screen.Text(file)) else Files.openExternal(context, file)
            FileKind.APK -> installApk(context, file)
            else -> Files.openExternal(context, file)
        }
    }

    fun openList(context: Context, file: File, list: List<File>) {
        when (Files.kindOf(file)) {
            FileKind.IMAGE -> {
                val imgs = list.filter { Files.kindOf(it.name) == FileKind.IMAGE }
                go(Screen.Images(imgs, imgs.indexOf(file).coerceAtLeast(0)))
            }
            FileKind.VIDEO, FileKind.AUDIO -> {
                val kind = Files.kindOf(file.name)
                val media = list.filter { Files.kindOf(it.name) == kind }
                Engine.play(media.map { app.pzip.player.PlayItem.of(it) }, media.indexOf(file).coerceAtLeast(0))
                Engine.openPlayer(context)
            }
            else -> open(context, file)
        }
    }

    fun installApk(context: Context, file: File) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(Files.uriFor(context, file), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Toaster.show("Couldn't open the installer") }
    }
}

/** Per-screen navigation state that survives while the screen is in the back stack. */
class FolderNav(start: File) {
    var dir by mutableStateOf(start)
    val list = androidx.compose.foundation.lazy.LazyListState()
    var shownDir: File? = null
}

class ArchiveNav {
    var path by mutableStateOf("")
    var password by mutableStateOf<CharArray?>(null)
}
