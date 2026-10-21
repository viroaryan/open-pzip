package app.pzip.ui.screens

import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pzip.core.FileKind
import app.pzip.core.Files
import app.pzip.core.Prefs
import app.pzip.core.SafeVault
import app.pzip.core.tasks.TaskManager
import app.pzip.ui.Nav
import app.pzip.ui.Screen
import app.pzip.ui.components.Chip
import app.pzip.ui.components.EmptyState
import app.pzip.ui.components.FileThumb
import app.pzip.ui.components.IconBtn
import app.pzip.ui.components.TopBar
import app.pzip.ui.theme.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaItem(val file: File, val date: Long, val isVideo: Boolean, val bucket: String)

object Gallery {
    fun load(context: Context): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.MEDIA_TYPE)
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        runCatching {
            context.contentResolver.query(uri, proj, sel, args, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    if (!Prefs.showHidden && path.contains("/.")) continue
                    val f = File(path)
                    if (!f.exists()) continue
                    out += MediaItem(f, c.getLong(1) * 1000, c.getInt(2) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, f.parentFile?.name ?: "")
                }
            }
        }
        return out
    }

    data class Album(val name: String, val dir: File, val items: List<MediaItem>)

    fun albums(items: List<MediaItem>): List<Album> =
        items.groupBy { it.file.parentFile?.absolutePath ?: "" }
            .map { (dir, list) -> Album(File(dir).name.ifEmpty { "Storage" }, File(dir), list) }
            .sortedWith(compareByDescending<Album> { a -> a.dir.name.equals("Camera", true) }.thenByDescending { it.items.size })
}

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val items by produceState<List<MediaItem>?>(null, TaskManager.version, Prefs.showHidden) {
        value = withContext(Dispatchers.IO) { Gallery.load(context) }
    }
    Column(Modifier.fillMaxSize().background(P.c.bg)) {
        TopBar("Gallery", big = true, subtitle = items?.let { "${it.count { !it.isVideo }} photos · ${it.count { it.isVideo }} videos" })
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Photos", tab == 0, { tab = 0 })
            Chip("Albums", tab == 1, { tab = 1 })
            Chip("Videos", tab == 2, { tab = 2 })
        }
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = P.c.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp)) }
            list.isEmpty() -> EmptyState(Icons.Rounded.PhotoLibrary, "No photos yet", "Photos and videos on your phone will show up here.")
            tab == 1 -> AlbumGrid(Gallery.albums(list))
            else -> MediaGrid(if (tab == 2) list.filter { it.isVideo } else list, grouped = true)
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Gallery.Album>) {
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.dir.absolutePath }) { a ->
            Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable { Nav.go(Screen.Album(a.name, a.items.map { it.file })) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(P.c.card)) {
                    FileThumb(a.items.first().file, 200.dp, 20.dp)
                }
                Text(a.name, color = P.c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                Text("${a.items.size} items", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(list: List<MediaItem>, grouped: Boolean) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<File>() }
    var deleting by remember { mutableStateOf<List<File>?>(null) }
    val fmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val groups = remember(list) { if (grouped) list.groupBy { fmt.format(Date(it.date)) } else mapOf("" to list) }
    val files = remember(list) { list.map { it.file } }
    val h = app.pzip.ui.rememberHaptics()
    androidx.activity.compose.BackHandler(selected.isNotEmpty()) { selected.clear() }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Adaptive(96.dp), contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            groups.forEach { (title, group) ->
                if (title.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "h$title") {
                    Text(title, color = P.c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))
                }
                items(group, key = { it.file.absolutePath }) { m ->
                    val sel = m.file in selected
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(P.c.card).combinedClickable(
                            onClick = {
                                if (selected.isNotEmpty()) { if (sel) selected.remove(m.file) else selected.add(m.file) }
                                else if (m.isVideo) Nav.openList(context, m.file, files)
                                else Nav.go(Screen.Images(files.filter { Files.kindOf(it.name) == FileKind.IMAGE }, files.filter { Files.kindOf(it.name) == FileKind.IMAGE }.indexOf(m.file).coerceAtLeast(0)))
                            },
                            onLongClick = { h.longPress(); if (sel) selected.remove(m.file) else selected.add(m.file) },
                        ),
                    ) {
                        FileThumb(m.file, 140.dp, 0.dp)
                        if (m.isVideo) Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp).clip(CircleShape).background(Color(0x66000000)))
                        if (sel) Box(Modifier.matchParentSize().background(P.c.accent.copy(alpha = 0.35f)))
                        if (selected.isNotEmpty()) Icon(
                            if (sel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            tint = if (sel) P.c.accent else Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp),
                        )
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp, start = 16.dp, end = 16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)).background(P.c.ink).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Icons.Rounded.Close, "Clear", { selected.clear() }, tint = P.c.onInk)
                Text("${selected.size}", color = P.c.onInk, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconBtn(Icons.Rounded.Share, "Share", { Files.share(context, selected.toList()) }, tint = P.c.onInk)
                IconBtn(Icons.Rounded.Archive, "Compress", { Nav.compress = selected.toList(); selected.clear() }, tint = P.c.onInk)
                IconBtn(Icons.Rounded.Shield, "Safe folder", { SafeVault.add(selected.toList()); selected.clear() }, tint = P.c.onInk)
                IconBtn(Icons.Rounded.Delete, "Delete", { deleting = selected.toList() }, tint = P.c.onInk)
            }
        }
    }
    deleting?.let { DeleteDialog(it, { deleting = null }) { selected.clear() } }
}

@Composable
fun AlbumScreen(name: String, files: List<File>) {
    Column(Modifier.fillMaxSize().background(P.c.bg)) {
        TopBar(name, "${files.size} items", onBack = { Nav.back() }) {
            IconBtn(Icons.Rounded.Folder, "Open folder", { files.firstOrNull()?.parentFile?.let { Nav.go(Screen.Browse(it)) } })
        }
        MediaGrid(files.map { MediaItem(it, it.lastModified(), Files.kindOf(it.name) == FileKind.VIDEO, name) }, grouped = false)
    }
}
