package app.pzip.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pzip.core.FileOps
import app.pzip.core.Files
import app.pzip.core.Prefs
import app.pzip.core.SafeVault
import app.pzip.core.SortBy
import app.pzip.core.ViewMode
import app.pzip.core.tasks.TaskManager
import app.pzip.ui.ExtractRequest
import app.pzip.ui.Nav
import app.pzip.ui.PickRequest
import app.pzip.ui.components.Chip
import app.pzip.ui.components.ConfirmDialog
import app.pzip.ui.components.EmptyState
import app.pzip.ui.components.FileRow
import app.pzip.ui.components.FileThumb
import app.pzip.ui.components.Gap
import app.pzip.ui.components.IconBtn
import app.pzip.ui.components.InkButton
import app.pzip.ui.components.InputDialog
import app.pzip.ui.components.LineButton
import app.pzip.ui.components.PSwitch
import app.pzip.ui.components.Pill
import app.pzip.ui.components.SettingRow
import app.pzip.ui.components.Sheet
import app.pzip.ui.components.Toaster
import app.pzip.ui.components.TopBar
import app.pzip.ui.theme.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Files picked with Copy or Cut, waiting to be pasted. */
object Clipboard {
    val files = mutableStateListOf<File>()
    var cut by mutableStateOf(false)
    fun set(list: List<File>, isCut: Boolean) {
        files.clear()
        files.addAll(list)
        cut = isCut
    }
    fun clear() = files.clear()
}

fun rootLabel(dir: File): String {
    val root = Files.primaryRoot.absolutePath
    return when {
        dir.absolutePath == root -> "Internal storage"
        dir.absolutePath.count { it == '/' } <= 2 && !dir.absolutePath.startsWith(root) -> "SD card"
        else -> dir.name
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(start: File, nav: app.pzip.ui.FolderNav, picker: PickRequest? = null) {
    val context = LocalContext.current
    var dir by nav::dir
    var items by remember { mutableStateOf<List<File>?>(null) }
    val selected = remember { mutableStateListOf<File>() }
    var filter by remember { mutableStateOf<String?>(null) }
    var sheetFor by remember { mutableStateOf<File?>(null) }
    var showSort by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showSelMore by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var newFile by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<List<File>?>(null) }
    val listState = nav.list
    val selecting = selected.isNotEmpty()

    LaunchedEffect(dir, TaskManager.version, Prefs.sortBy, Prefs.sortDesc, Prefs.showHidden, Prefs.foldersFirst) {
        items = withContext(Dispatchers.IO) { if (dir.canRead()) Files.list(dir) else null }
    }
    LaunchedEffect(dir) {
        if (nav.shownDir?.absolutePath != dir.absolutePath) {
            selected.clear()
            filter = null
            if (nav.shownDir != null) listState.scrollToItem(0)
            nav.shownDir = dir
        }
    }

    val canGoUp = dir.absolutePath != start.absolutePath && dir.parentFile != null && dir.absolutePath.startsWith(start.absolutePath)
    BackHandler(enabled = selecting || filter != null || canGoUp) {
        when {
            selecting -> selected.clear()
            filter != null -> filter = null
            else -> dir = dir.parentFile!!
        }
    }

    val shown = items?.let { list -> filter?.takeIf { it.isNotBlank() }?.let { q -> list.filter { it.name.contains(q, true) } } ?: list }

    fun openItem(f: File) {
        if (selecting) {
            if (f in selected) selected.remove(f) else selected.add(f)
            return
        }
        when {
            f.isDirectory -> dir = f
            picker != null -> if (!picker.folder) {
                picker.onPick(f)
                Nav.back()
            }
            else -> Nav.openList(context, f, shown ?: listOf(f))
        }
    }

    Box(Modifier.fillMaxSize().background(P.c.bg)) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.Close, "Clear selection", { selected.clear() })
                    Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, color = P.c.text, modifier = Modifier.weight(1f).padding(start = 6.dp))
                    IconBtn(Icons.Rounded.SelectAll, "Select all", {
                        val all = shown.orEmpty()
                        if (selected.size == all.size) selected.clear() else {
                            selected.clear()
                            selected.addAll(all)
                        }
                    })
                }
            } else if (filter != null) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.Close, "Close search", { filter = null })
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(P.c.card).padding(horizontal = 16.dp, vertical = 11.dp)) {
                        if (filter!!.isEmpty()) Text("Search in ${rootLabel(dir)}", color = P.c.textFaint)
                        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        BasicTextField(
                            filter!!, { filter = it }, singleLine = true, cursorBrush = SolidColor(P.c.ink),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = P.c.text),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
            } else {
                TopBar(
                    title = picker?.title ?: rootLabel(dir),
                    subtitle = if (picker != null) Files.displayPath(dir) else items?.let { "${it.size} items" },
                    onBack = { if (canGoUp) dir = dir.parentFile!! else Nav.back() },
                ) {
                    IconBtn(Icons.Rounded.Search, "Search", { filter = "" })
                    IconBtn(Icons.AutoMirrored.Rounded.Sort, "Sort", { showSort = true })
                    if (picker == null) IconBtn(Icons.Rounded.MoreVert, "More", { showMore = true })
                }
            }
            Breadcrumbs(dir, start) { dir = it }

            when {
                shown == null && items == null && !dir.canRead() -> EmptyState(Icons.Rounded.FolderOff, "Can't open this folder", "pzip doesn't have permission to read it.")
                shown == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = P.c.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)) }
                shown.isEmpty() -> EmptyState(Icons.Rounded.FolderOff, if (filter.isNullOrEmpty()) "This folder is empty" else "No matches", if (filter.isNullOrEmpty()) "Add files here by pasting, extracting or creating new ones." else "Try another name.")
                Prefs.viewMode == ViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.absolutePath }) { f ->
                        GridTile(f, f in selected, selecting, onClick = { openItem(f) }, onLong = { if (picker == null) { if (f in selected) selected.remove(f) else selected.add(f) } })
                    }
                }
                else -> LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(shown, key = { it.absolutePath }) { f ->
                        FileRow(
                            f, selected = f in selected, selecting = selecting,
                            onClick = { openItem(f) },
                            onLongClick = { if (picker == null) { if (f in selected) selected.remove(f) else selected.add(f) } },
                            trailing = if (selecting || picker != null) null else ({ IconBtn(Icons.Rounded.MoreVert, "Options", { sheetFor = f }, tint = P.c.textMuted, size = 40.dp) }),
                        )
                    }
                }
            }
        }

        // Bottom areas
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (picker != null) {
                Row(Modifier.fillMaxWidth().background(P.c.bg).navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LineButton("New folder", { newFolder = true }, Modifier.weight(1f), Icons.Rounded.CreateNewFolder)
                    if (picker.folder) InkButton("Use this folder", { picker.onPick(dir); Nav.back() }, Modifier.weight(1.3f), Icons.Rounded.CheckCircle)
                }
            } else if (selecting) {
                SelectionBar(
                    onCompress = { Nav.compress = selected.toList(); selected.clear() },
                    onCopy = { Clipboard.set(selected.toList(), false); selected.clear(); Toaster.show("Go to a folder and tap Paste") },
                    onMove = { Clipboard.set(selected.toList(), true); selected.clear(); Toaster.show("Go to a folder and tap Paste") },
                    onDelete = { deleting = selected.toList() },
                    onMore = { showSelMore = true },
                )
            } else {
                if (Clipboard.files.isNotEmpty()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.c.ink).padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${Clipboard.files.size} item(s) to ${if (Clipboard.cut) "move" else "copy"}", color = P.c.onInk, style = MaterialTheme.typography.titleSmall)
                            Text("Paste into ${rootLabel(dir)}", color = P.c.onInk.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                        }
                        IconBtn(Icons.Rounded.Close, "Cancel", { Clipboard.clear() }, tint = P.c.onInk)
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(P.c.accentSoft).clickable {
                                val list = Clipboard.files.toList()
                                if (Clipboard.cut) FileOps.move(list, dir) else FileOps.copy(list, dir)
                                Clipboard.clear()
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ContentPaste, null, tint = P.c.onAccentSoft, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paste", color = P.c.onAccentSoft, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(end = 20.dp, bottom = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(P.c.ink).clickable { newFolder = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, "New folder", tint = P.c.onInk, modifier = Modifier.size(26.dp)) }
                }
            }
        }
    }

    // ---------- Sheets & dialogs ----------
    sheetFor?.let { f ->
        ItemSheet(f, onDismiss = { sheetFor = null }, onRename = { renaming = f }, onDelete = { deleting = listOf(f) })
    }
    if (showSort) SortSheet { showSort = false }
    if (showMore) Sheet({ showMore = false }) {
        SettingRow("New folder", icon = Icons.Rounded.CreateNewFolder, onClick = { showMore = false; newFolder = true })
        SettingRow("New text file", icon = Icons.Rounded.NoteAdd, onClick = { showMore = false; newFile = true })
        SettingRow("Select all", icon = Icons.Rounded.SelectAll, onClick = { showMore = false; selected.clear(); selected.addAll(shown.orEmpty()) })
        val fav = dir.absolutePath in Prefs.favoriteList()
        SettingRow(if (fav) "Remove from favorites" else "Add to favorites", icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, onClick = { Prefs.toggleFavorite(dir.absolutePath); showMore = false })
        SettingRow(if (Prefs.viewMode == ViewMode.LIST) "Grid view" else "List view", icon = if (Prefs.viewMode == ViewMode.LIST) Icons.Rounded.GridView else Icons.Rounded.ViewList, onClick = {
            Prefs.viewMode = if (Prefs.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
            showMore = false
        })
        SettingRow("Show hidden files", icon = Icons.Rounded.Visibility, onClick = { Prefs.showHidden = !Prefs.showHidden }) { PSwitch(Prefs.showHidden) { Prefs.showHidden = it } }
        SettingRow("Folder details", icon = Icons.Rounded.Info, onClick = { showMore = false; Nav.details = dir })
    }
    if (showSelMore) Sheet({ showSelMore = false }) {
        val list = selected.toList()
        if (list.size == 1) SettingRow("Rename", icon = Icons.Rounded.DriveFileRenameOutline, onClick = { showSelMore = false; renaming = list[0]; selected.clear() })
        if (list.any { Files.isArchive(it) }) SettingRow("Extract here", icon = Icons.Rounded.Unarchive, onClick = {
            showSelMore = false
            list.filter { Files.isArchive(it) }.forEach { Nav.extract = ExtractRequest(it) }
            selected.clear()
        })
        SettingRow("Share", icon = Icons.Rounded.Share, onClick = { showSelMore = false; Files.share(context, list) })
        SettingRow("Move to Safe folder", "Encrypt and hide from other apps", Icons.Rounded.Shield, onClick = {
            showSelMore = false
            SafeVault.add(list)
            selected.clear()
        })
        SettingRow("Shred", "Overwrite, then delete — can't be recovered", Icons.Rounded.LocalFireDepartment, onClick = {
            showSelMore = false
            FileOps.shred(list, 3)
            selected.clear()
        })
        SettingRow("Details", icon = Icons.Rounded.Info, onClick = { showSelMore = false; Nav.details = list.first() })
    }
    if (newFolder) InputDialog("New folder", "", confirm = "Create", validate = { if (File(dir, it).exists()) "Already exists" else null }, onDismiss = { newFolder = false }) {
        newFolder = false
        if (FileOps.newFolder(dir, it) == null) Toaster.show("Couldn't create folder") else TaskManager.refresh()
    }
    if (newFile) InputDialog("New text file", "notes.txt", confirm = "Create", validate = { if (File(dir, it).exists()) "Already exists" else null }, onDismiss = { newFile = false }) {
        newFile = false
        if (FileOps.newFile(dir, it) == null) Toaster.show("Couldn't create file") else TaskManager.refresh()
    }
    renaming?.let { f ->
        InputDialog("Rename", f.name, confirm = "Rename", validate = { if (it != f.name && File(f.parentFile, it).exists()) "A file with this name exists" else if (!Files.isValidName(it)) "Invalid name" else null }, onDismiss = { renaming = null }) {
            renaming = null
            if (it != f.name && FileOps.rename(f, it) == null) Toaster.show("Couldn't rename") else TaskManager.refresh()
        }
    }
    deleting?.let { list ->
        DeleteDialog(list, onDismiss = { deleting = null }) { selected.clear() }
    }
}

@Composable
fun DeleteDialog(list: List<File>, onDismiss: () -> Unit, onDone: () -> Unit = {}) {
    var permanent by remember { mutableStateOf(!Prefs.useTrash) }
    app.pzip.ui.components.PDialog(
        if (list.size == 1) "Delete ${list[0].name}?" else "Delete ${list.size} items?", onDismiss,
        if (permanent) "Delete forever" else "Move to Trash",
        { FileOps.delete(list, permanent); onDone(); onDismiss() }, danger = true,
    ) {
        Column {
            Text(
                if (permanent) {
                    if (list.size == 1) "This item will be removed permanently." else "These items will be removed permanently."
                } else {
                    "You can restore ${if (list.size == 1) "it" else "them"} from Trash for ${app.pzip.core.Trash.KEEP_DAYS} days."
                },
                color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium,
            )
            Gap(10)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { permanent = !permanent }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (permanent) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (permanent) P.c.danger else P.c.textFaint)
                Spacer(Modifier.width(10.dp))
                Text("Delete permanently", color = P.c.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Breadcrumbs(dir: File, start: File, onGo: (File) -> Unit) {
    val chain = remember(dir) {
        val list = mutableListOf<File>()
        var f: File? = dir
        while (f != null) {
            list.add(0, f)
            if (f.absolutePath == start.absolutePath || f.absolutePath == Files.primaryRoot.absolutePath) break
            f = f.parentFile
        }
        list
    }
    val scroll = rememberScrollState()
    LaunchedEffect(chain.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        chain.forEachIndexed { i, f ->
            val last = i == chain.lastIndex
            Text(
                if (i == 0) rootLabel(f) else f.name,
                color = if (last) P.c.text else P.c.textMuted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (last) P.c.card else Color.Transparent).clickable { onGo(f) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            if (!last) Icon(Icons.Rounded.ChevronRight, null, tint = P.c.textFaint, modifier = Modifier.size(16.dp))
        }
    }
    Gap(4)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(f: File, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val h = app.pzip.ui.rememberHaptics()
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) P.c.accentSoft else P.c.card).combinedClickable(onClick = onClick, onLongClick = { h.longPress(); onLong() }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            FileThumb(f, size = 84.dp, corner = 14.dp)
            if (selecting) Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) P.c.accent else P.c.textFaint, modifier = Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(P.c.bg),
            )
        }
        Gap(6)
        Text(f.name, color = P.c.text, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun SelectionBar(onCompress: () -> Unit, onCopy: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(P.c.bg).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)).background(P.c.ink).padding(vertical = 6.dp),
    ) {
        BarAction(Icons.Rounded.Archive, "Compress", onCompress, Modifier.weight(1f))
        BarAction(Icons.Rounded.ContentCopy, "Copy", onCopy, Modifier.weight(1f))
        BarAction(Icons.AutoMirrored.Rounded.DriveFileMove, "Move", onMove, Modifier.weight(1f))
        BarAction(Icons.Rounded.Delete, "Delete", onDelete, Modifier.weight(1f))
        BarAction(Icons.Rounded.MoreVert, "More", onMore, Modifier.weight(1f))
    }
}

@Composable
private fun BarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = P.c.onInk, modifier = Modifier.size(22.dp))
        Text(label, color = P.c.onInk, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SortSheet(onDismiss: () -> Unit) {
    Sheet(onDismiss, "Sort by") {
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            SortBy.entries.forEach { s -> Chip(s.label, Prefs.sortBy == s, { Prefs.sortBy = s }) }
        }
        app.pzip.ui.components.ChipFlow(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Chip(if (Prefs.sortBy == SortBy.NAME) "A → Z" else "Oldest / smallest", !Prefs.sortDesc, { Prefs.sortDesc = false })
            Chip(if (Prefs.sortBy == SortBy.NAME) "Z → A" else "Newest / largest", Prefs.sortDesc, { Prefs.sortDesc = true })
        }
        app.pzip.ui.components.SettingSwitch("Folders first", checked = Prefs.foldersFirst, onChange = { Prefs.foldersFirst = it })
        app.pzip.ui.components.SettingSwitch("Show hidden files", checked = Prefs.showHidden, onChange = { Prefs.showHidden = it })
    }
}

/** Actions for a single file or folder. */
@Composable
fun ItemSheet(f: File, onDismiss: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, extra: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    Sheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumb(f, 52.dp)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(f.name, color = P.c.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (f.isDirectory) "Folder" else "${Files.formatSize(f.length())} · ${Files.formatDate(f.lastModified())}", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (Files.isArchive(f)) Pill(app.pzip.core.archive.ArchiveType.detect(f)?.label ?: "Archive")
        }
        Gap(6)
        val act: (() -> Unit) -> () -> Unit = { a -> { onDismiss(); a() } }
        app.pzip.ui.components.ActionGrid(
            listOfNotNull(
                Triple(Icons.Rounded.Share, "Share", act { Files.share(context, listOf(f)) }),
                Triple(Icons.Rounded.ContentCopy, "Copy", act { Clipboard.set(listOf(f), false); Toaster.show("Go to a folder and tap Paste") }),
                Triple(Icons.AutoMirrored.Rounded.DriveFileMove, "Move", act { Clipboard.set(listOf(f), true); Toaster.show("Go to a folder and tap Paste") }),
                Triple(Icons.Rounded.Edit, "Rename", act(onRename)),
                Triple(Icons.Rounded.Archive, "Compress", act { Nav.compress = listOf(f) }),
                if (Files.isArchive(f) || Files.ext(f.name) == "apk") Triple(Icons.Rounded.Unarchive, "Extract", act { Nav.extract = ExtractRequest(f) }) else null,
                Triple(Icons.Rounded.Info, "Details", act { Nav.details = f }),
                Triple(Icons.Rounded.Delete, "Delete", act(onDelete)),
            ),
        )
        Gap(4)
        if (!f.isDirectory) SettingRow("Open with another app", icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = act { Files.openExternal(context, f) })
        if (Files.ext(f.name) == "apk") SettingRow("Browse APK contents", icon = Icons.Rounded.Archive, onClick = act { Nav.go(app.pzip.ui.Screen.Archive(f)) })
        if (f.isDirectory) {
            val fav = f.absolutePath in Prefs.favoriteList()
            SettingRow(if (fav) "Remove from favorites" else "Add to favorites", icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, onClick = act { Prefs.toggleFavorite(f.absolutePath) })
        }
        SettingRow("Move to Safe folder", "Encrypt with AES-256 and hide", Icons.Rounded.Shield, onClick = act { SafeVault.add(listOf(f)) })
        SettingRow("Shred", "Overwrite 3 times, then delete", Icons.Rounded.DeleteForever, onClick = act { FileOps.shred(listOf(f), 3) })
        extra()
    }
}
