package app.pzip.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pzip.core.Category
import app.pzip.core.FileKind
import app.pzip.core.Files
import app.pzip.core.MediaIndex
import app.pzip.core.Prefs
import app.pzip.core.tasks.TaskManager
import app.pzip.player.Engine
import app.pzip.player.PlayItem
import app.pzip.ui.Nav
import app.pzip.ui.Screen
import app.pzip.ui.Tab
import app.pzip.ui.components.FileRow
import app.pzip.ui.components.FileThumb
import app.pzip.ui.components.Gap
import app.pzip.ui.components.IconBtn
import app.pzip.ui.components.InputDialog
import app.pzip.ui.components.PCard
import app.pzip.ui.components.ProgressLine
import app.pzip.ui.components.SectionHeader
import app.pzip.ui.components.Toaster
import app.pzip.ui.components.Wordmark
import app.pzip.ui.theme.P
import app.pzip.ui.theme.kindTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CatTile(val cat: Category, val icon: ImageVector, val kind: FileKind)

val categoryTiles = listOf(
    CatTile(Category.IMAGES, Icons.Rounded.Image, FileKind.IMAGE),
    CatTile(Category.VIDEOS, Icons.Rounded.Movie, FileKind.VIDEO),
    CatTile(Category.AUDIO, Icons.Rounded.MusicNote, FileKind.AUDIO),
    CatTile(Category.DOCUMENTS, Icons.Rounded.Description, FileKind.DOCUMENT),
    CatTile(Category.ARCHIVES, Icons.Rounded.FolderZip, FileKind.ARCHIVE),
    CatTile(Category.APKS, Icons.Rounded.Android, FileKind.APK),
    CatTile(Category.DOWNLOADS, Icons.Rounded.Download, FileKind.FOLDER),
)

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var streamDialog by remember { mutableStateOf(false) }
    val volumes = remember { Files.volumes(context) }
    val recentFiles by produceState<List<File>>(emptyList(), TaskManager.version) {
        value = withContext(Dispatchers.IO) { MediaIndex.recent(context, 16) }
    }
    val recentArchives = remember(Prefs.recents, TaskManager.version) { Prefs.recentList().map { File(it) }.filter { it.exists() }.take(5) }
    val favorites = remember(Prefs.favorites) { Prefs.favoriteList().map { File(it) }.filter { it.exists() } }

    LazyColumn(Modifier.fillMaxSize().background(P.c.bg), contentPadding = PaddingValues(bottom = 110.dp)) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(Modifier.weight(1f))
                Box {
                    IconBtn(Icons.Rounded.SwapVert, "Operations", { Nav.go(Screen.Tasks) })
                    val n = TaskManager.running.size
                    if (n > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp).clip(CircleShape).background(P.c.accent), contentAlignment = Alignment.Center) {
                        Text("$n", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconBtn(Icons.Rounded.Settings, "Settings", { Nav.go(Screen.Settings) })
            }
            Text(
                "Everything in one place.", style = MaterialTheme.typography.headlineMedium, color = P.c.text,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
            )
            Text(
                "Files, gallery, player and archives — private and fast.", style = MaterialTheme.typography.bodyMedium, color = P.c.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Gap(14)
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(P.c.card)
                    .clickable { Nav.go(Screen.Search) }.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = P.c.textMuted)
                Spacer(Modifier.width(12.dp))
                Text("Search your files", color = P.c.textFaint, style = MaterialTheme.typography.bodyLarge)
            }
        }

        item {
            Gap(16)
            volumes.forEach { v ->
                PCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth(), onClick = { Nav.go(Screen.Browse(v.root)) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(P.c.accentSoft), contentAlignment = Alignment.Center) {
                                Icon(if (v.removable) Icons.Rounded.SdCard else Icons.Rounded.Smartphone, null, tint = P.c.accent)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(v.label, color = P.c.text, style = MaterialTheme.typography.titleMedium)
                                Text("${Files.formatSize(v.free)} free of ${Files.formatSize(v.total)}", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${if (v.total > 0) v.used * 100 / v.total else 0}%", color = P.c.text, style = MaterialTheme.typography.titleLarge)
                        }
                        Gap(12)
                        ProgressLine(if (v.total > 0) v.used.toFloat() / v.total else 0f, height = 8.dp)
                        if (!v.removable) {
                            Gap(10)
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(P.c.surface).clickable { Nav.switchTab(Tab.CLEAN) }.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.CleaningServices, null, tint = P.c.text, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Free up space", color = P.c.text, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Quick actions")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quick(Icons.Rounded.Archive, "Compress", Modifier.weight(1f)) {
                    Nav.switchTab(Tab.FILES)
                    Toaster.show("Long-press files to select them, then tap Compress")
                }
                Quick(Icons.Rounded.Unarchive, "Extract", Modifier.weight(1f)) { Nav.go(Screen.CategoryView(Category.ARCHIVES)) }
                Quick(Icons.Rounded.Shield, "Safe folder", Modifier.weight(1f)) { Nav.go(Screen.Safe) }
                Quick(Icons.Rounded.Link, "Stream", Modifier.weight(1f)) { streamDialog = true }
            }
        }

        item {
            SectionHeader("Categories")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val perRow = if (app.pzip.ui.isWide()) 4 else 2
                categoryTiles.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t -> CategoryCard(t, Modifier.weight(1f)) }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (recentFiles.isNotEmpty()) item {
            SectionHeader("Recent", action = "See all") { Nav.go(Screen.CategoryView(Category.RECENT)) }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recentFiles, key = { it.absolutePath }) { f ->
                    Column(Modifier.width(96.dp).clip(RoundedCornerShape(16.dp)).clickable { Nav.open(context, f) }) {
                        FileThumb(f, 96.dp, 16.dp)
                        Text(f.name, color = P.c.text, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) item {
            SectionHeader("Favorites")
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favorites, key = { it.absolutePath }) { f ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(P.c.card).clickable { Nav.go(Screen.Browse(f)) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = P.c.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(f.name.ifEmpty { "Storage" }, color = P.c.text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (recentArchives.isNotEmpty()) {
            item { SectionHeader("Recent archives", action = "Clear") { Prefs.recents = "" } }
            items(recentArchives, key = { "ra" + it.absolutePath }) { f ->
                FileRow(f, onClick = { Nav.open(context, f) })
            }
        }

        item {
            Gap(20)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = P.c.textFaint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("No ads · No tracking · Your files never leave your phone", color = P.c.textFaint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (streamDialog) InputDialog(
        "Open network stream", "", label = "URL (http, rtsp, rtmp, m3u8…)", confirm = "Play", selectBase = false,
        keyboard = androidx.compose.ui.text.input.KeyboardType.Uri,
        validate = { if (it.isNotBlank() && !it.contains("://")) "Enter a full URL" else null },
        onDismiss = { streamDialog = false },
    ) { url ->
        streamDialog = false
        Engine.play(listOf(PlayItem(Uri.parse(url), url.substringAfterLast('/').ifEmpty { url }, false)))
        Engine.openPlayer(context)
    }
}

@Composable
private fun Quick(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(P.c.card).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(P.c.surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = P.c.text, modifier = Modifier.size(20.dp))
        }
        Gap(6)
        Text(label, color = P.c.text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun CategoryCard(t: CatTile, modifier: Modifier) {
    val context = LocalContext.current
    val stats by produceState<Pair<Int, Long>?>(null, t.cat, TaskManager.version) {
        value = withContext(Dispatchers.IO) { MediaIndex.stats(context, t.cat) }
    }
    val (fg, bg) = kindTint(t.kind, P.c)
    // Icon on top, label below: long names like "Documents" never break mid-word on narrow phones.
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(P.c.card).clickable { Nav.go(Screen.CategoryView(t.cat)) }.padding(14.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(if (t.cat == Category.DOWNLOADS) Icons.Rounded.Download else t.icon, null, tint = if (t.cat == Category.DOWNLOADS) P.c.accent else fg, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.padding(top = 10.dp)) {
            Text(t.cat.label, color = P.c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            Text(stats?.let { "${it.first} · ${Files.formatSize(it.second)}" } ?: " ", color = P.c.textMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
fun FilesRootScreen() {
    val context = LocalContext.current
    val volumes = remember { Files.volumes(context) }
    val favorites = remember(Prefs.favorites) { Prefs.favoriteList().map { File(it) }.filter { it.exists() } }
    val root = Files.primaryRoot
    val common = remember {
        listOf("Download", "DCIM", "Pictures", "Movies", "Music", "Documents", "WhatsApp", "Android/media", "Telegram")
            .map { File(root, it) }.filter { it.exists() }
    }
    LazyColumn(Modifier.fillMaxSize().background(P.c.bg), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { app.pzip.ui.components.TopBar("Files", big = true) { IconBtn(Icons.Rounded.Search, "Search", { Nav.go(Screen.Search) }) } }
        item { SectionHeader("Storage") }
        items(volumes, key = { it.root.absolutePath }) { v ->
            app.pzip.ui.components.SettingRow(
                v.label, "${Files.formatSize(v.free)} free of ${Files.formatSize(v.total)}",
                if (v.removable) Icons.Rounded.SdCard else Icons.Rounded.Smartphone, onClick = { Nav.go(Screen.Browse(v.root)) },
            )
        }
        if (favorites.isNotEmpty()) {
            item { SectionHeader("Favorites") }
            items(favorites, key = { "f" + it.absolutePath }) { f -> FileRow(f, onClick = { Nav.go(Screen.Browse(f)) }) }
        }
        item { SectionHeader("Folders") }
        items(common, key = { "c" + it.absolutePath }) { f -> FileRow(f, onClick = { Nav.go(Screen.Browse(f)) }) }
        item { SectionHeader("More") }
        item {
            app.pzip.ui.components.SettingRow("Safe folder", "Encrypted, PIN protected", Icons.Rounded.Shield, onClick = { Nav.go(Screen.Safe) })
            app.pzip.ui.components.SettingRow("Trash", "${app.pzip.core.Trash.items.size} items · kept ${app.pzip.core.Trash.KEEP_DAYS} days", Icons.Rounded.Folder, onClick = { Nav.go(Screen.Trash) })
        }
    }
}
