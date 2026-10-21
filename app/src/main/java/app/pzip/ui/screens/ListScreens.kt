package app.pzip.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pzip.core.Category
import app.pzip.core.Files
import app.pzip.core.MediaIndex
import app.pzip.core.Prefs
import app.pzip.core.SafeVault
import app.pzip.core.tasks.TaskManager
import app.pzip.ui.Nav
import app.pzip.ui.components.EmptyState
import app.pzip.ui.components.FileRow
import app.pzip.ui.components.IconBtn
import app.pzip.ui.components.TopBar
import app.pzip.ui.theme.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** A flat list of files with multi-select, used by categories and search results. */
@Composable
fun FileListBody(files: List<File>?, emptyTitle: String, emptyText: String, showPath: Boolean = true) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<File>() }
    var sheetFor by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<List<File>?>(null) }
    var renaming by remember { mutableStateOf<File?>(null) }
    BackHandler(selected.isNotEmpty()) { selected.clear() }

    Box(Modifier.fillMaxSize()) {
        when {
            files == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = P.c.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)) }
            files.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, emptyTitle, emptyText)
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(files, key = { it.absolutePath }) { f ->
                    val sel = f in selected
                    FileRow(
                        f, selected = sel, selecting = selected.isNotEmpty(),
                        subtitle = if (showPath) "${if (f.isDirectory) "Folder" else Files.formatSize(f.length())} · ${f.parentFile?.name ?: ""}" else null,
                        onClick = { if (selected.isNotEmpty()) { if (sel) selected.remove(f) else selected.add(f) } else Nav.openList(context, f, files) },
                        onLongClick = { if (sel) selected.remove(f) else selected.add(f) },
                        trailing = if (selected.isEmpty()) ({ IconBtn(Icons.Rounded.MoreVert, "Options", { sheetFor = f }, tint = P.c.textMuted, size = 40.dp) }) else null,
                    )
                }
            }
        }
        if (selected.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)).background(P.c.ink).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Icons.Rounded.Close, "Clear", { selected.clear() }, tint = P.c.onInk)
                Text("${selected.size} selected", color = P.c.onInk, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    IconBtn(Icons.Rounded.SelectAll, "Select all", { selected.clear(); selected.addAll(files.orEmpty()) }, tint = P.c.onInk)
                    IconBtn(Icons.Rounded.Share, "Share", { Files.share(context, selected.toList()) }, tint = P.c.onInk)
                    IconBtn(Icons.Rounded.Archive, "Compress", { Nav.compress = selected.toList(); selected.clear() }, tint = P.c.onInk)
                    IconBtn(Icons.Rounded.Shield, "Safe", { SafeVault.add(selected.toList()); selected.clear() }, tint = P.c.onInk)
                }
                IconBtn(Icons.Rounded.Delete, "Delete", { deleting = selected.toList() }, tint = P.c.onInk)
            }
        }
    }
    sheetFor?.let { f -> ItemSheet(f, { sheetFor = null }, onRename = { renaming = f }, onDelete = { deleting = listOf(f) }) }
    deleting?.let { DeleteDialog(it, { deleting = null }) { selected.clear() } }
    renaming?.let { f ->
        app.pzip.ui.components.InputDialog("Rename", f.name, confirm = "Rename", onDismiss = { renaming = null }) {
            renaming = null
            if (app.pzip.core.FileOps.rename(f, it) == null) app.pzip.ui.components.Toaster.show("Couldn't rename") else TaskManager.refresh()
        }
    }
}

@Composable
fun CategoryScreen(cat: Category) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<File>?>(null) }
    var showSort by remember { mutableStateOf(false) }
    LaunchedEffect(cat, TaskManager.version, Prefs.sortBy, Prefs.sortDesc) {
        val raw = withContext(Dispatchers.IO) { MediaIndex.category(context, cat) }
        files = if (cat == Category.RECENT || cat == Category.DOWNLOADS) raw else Files.sort(raw, foldersFirst = false)
    }
    Column(Modifier.fillMaxSize().background(P.c.bg)) {
        TopBar(cat.label, files?.let { "${it.size} items · ${Files.formatSize(it.sumOf { f -> f.length() })}" }, onBack = { Nav.back() }) {
            IconBtn(Icons.AutoMirrored.Rounded.Sort, "Sort", { showSort = true })
        }
        FileListBody(files, "Nothing here yet", "Files of this type on your phone will appear here.")
    }
    if (showSort) SortSheet { showSort = false }
}

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<File>?>(emptyList()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        results = null
        results = withContext(Dispatchers.IO) { MediaIndex.search(context, query) }
    }
    Column(Modifier.fillMaxSize().background(P.c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { Nav.back() })
            Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(P.c.card).padding(horizontal = 18.dp, vertical = 12.dp)) {
                if (query.isEmpty()) Text("Search all files", color = P.c.textFaint, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    query, { query = it }, singleLine = true, cursorBrush = SolidColor(P.c.ink),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = P.c.text),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) IconBtn(Icons.Rounded.Close, "Clear", { query = "" })
        }
        FileListBody(results, if (query.length < 2) "Find anything" else "No results", if (query.length < 2) "Type at least 2 letters to search by name." else "Try a different name.")
    }
}
