package app.pzip.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pzip.core.Cleaner
import app.pzip.core.FileOps
import app.pzip.core.Files
import app.pzip.core.Prefs
import app.pzip.core.tasks.TaskManager
import app.pzip.ui.Nav
import app.pzip.ui.components.EmptyState
import app.pzip.ui.components.FileRow
import app.pzip.ui.components.Gap
import app.pzip.ui.components.InkButton
import app.pzip.ui.components.PCard
import app.pzip.ui.components.ProgressLine
import app.pzip.ui.components.SectionHeader
import app.pzip.ui.components.TopBar
import app.pzip.ui.components.Toaster
import app.pzip.ui.theme.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Cached between visits so the tab opens instantly after the first scan. */
private object CleanState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var report by mutableStateOf<Cleaner.Report?>(null)
    var scanning by mutableStateOf(false)
    var status by mutableStateOf("")
}

private fun iconFor(k: Cleaner.Kind) = when (k) {
    Cleaner.Kind.JUNK -> Icons.Rounded.AutoDelete
    Cleaner.Kind.EMPTY -> Icons.Rounded.FolderOff
    Cleaner.Kind.DUPLICATES -> Icons.Rounded.ContentCopy
    Cleaner.Kind.LARGE -> Icons.Rounded.Straighten
    Cleaner.Kind.OLD_DOWNLOADS -> Icons.Rounded.Download
    Cleaner.Kind.SCREENSHOTS -> Icons.Rounded.Screenshot
    Cleaner.Kind.APKS -> Icons.Rounded.Android
}

@Composable
fun CleanScreen() {
    val context = LocalContext.current
    var open by remember { mutableStateOf<Cleaner.Group?>(null) }
    val vol = remember { Files.volumes(context).first() }

    fun scan() {
        if (CleanState.scanning) return
        CleanState.scanning = true
        CleanState.status = ""
        CleanState.scope.launch {
            try {
                CleanState.report = withContext(Dispatchers.IO) { Cleaner.scan { s -> CleanState.status = s } }
            } catch (_: Exception) {
                Toaster.show("Couldn't scan storage")
            } finally {
                CleanState.scanning = false
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (CleanState.report == null) scan() }

    open?.let { g ->
        CleanGroupScreen(g, onBack = { open = null }) {
            open = null
            scan()
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(P.c.bg), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { TopBar("Clean", big = true) }
        item {
            PCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), color = P.c.accentSoft) {
                Column {
                    Text("${Files.formatSize(vol.free)} free", style = MaterialTheme.typography.headlineSmall, color = P.c.onAccentSoft)
                    Text("of ${Files.formatSize(vol.total)} on internal storage", style = MaterialTheme.typography.bodyMedium, color = P.c.onAccentSoft.copy(alpha = 0.75f))
                    Gap(12)
                    ProgressLine(if (vol.total > 0) vol.used.toFloat() / vol.total else 0f, color = P.c.onAccentSoft, track = P.c.surface, height = 8.dp)
                    Gap(12)
                    val r = CleanState.report
                    when {
                        CleanState.scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = P.c.onAccentSoft, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            val status = CleanState.status
                            Text(
                                when {
                                    status.isBlank() -> "Scanning files…"
                                    status.startsWith("Looking") -> "$status…"
                                    else -> "Scanning $status…"
                                },
                                color = P.c.onAccentSoft, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            )
                        }
                        r != null -> Text("You can free up to ${Files.formatSize(r.total)}", color = P.c.onAccentSoft, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        val r = CleanState.report
        if (r != null && r.groups.isEmpty() && !CleanState.scanning) item {
            EmptyState(Icons.Rounded.CheckCircle, "All clean", "Nothing to clean up right now.")
        }
        if (r != null) {
            item { SectionHeader("Suggestions", action = "Rescan") { scan() } }
            items(r.groups, key = { it.kind.name }) { g ->
                PCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth(), onClick = { open = g }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(P.c.surface), contentAlignment = Alignment.Center) {
                            Icon(iconFor(g.kind), null, tint = P.c.text)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(g.kind.title, color = P.c.text, style = MaterialTheme.typography.titleMedium)
                            Text(g.kind.hint, color = P.c.textMuted, style = MaterialTheme.typography.bodySmall)
                            Text("${g.files.size} items · ${Files.formatSize(g.size)}", color = P.c.accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("More ways to save space")
            app.pzip.ui.components.SettingRow("Storage analyzer", "See which folders take the most space", Icons.Rounded.Straighten, onClick = { Nav.go(app.pzip.ui.Screen.Analyzer) })
            app.pzip.ui.components.SettingRow("Trash", "${app.pzip.core.Trash.items.size} items · ${Files.formatSize(app.pzip.core.Trash.totalSize())}", Icons.Rounded.CleaningServices, onClick = { Nav.go(app.pzip.ui.Screen.Trash) })
        }
    }
}

@Composable
private fun CleanGroupScreen(g: Cleaner.Group, onBack: () -> Unit, onCleaned: () -> Unit) {
    val context = LocalContext.current
    // Junk and duplicate copies are pre-selected; personal files are left for you to choose.
    val preselect = g.kind in setOf(Cleaner.Kind.JUNK, Cleaner.Kind.EMPTY, Cleaner.Kind.DUPLICATES)
    val selected = remember { mutableStateListOf<File>().apply { if (preselect) addAll(g.files) } }
    var confirm by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(P.c.bg)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                TopBar(g.kind.title, "${g.files.size} items · ${Files.formatSize(g.size)}", onBack = onBack) {
                    app.pzip.ui.components.TextLink(if (selected.size == g.files.size) "None" else "All", {
                        if (selected.size == g.files.size) selected.clear() else { selected.clear(); selected.addAll(g.files) }
                    })
                }
            }
            if (g.kind == Cleaner.Kind.DUPLICATES) {
                g.duplicateSets.forEachIndexed { i, set ->
                    item(key = "s$i") {
                        Text("${set.size} copies · ${Files.formatSize(set.first().length())} each", color = P.c.textMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
                    }
                    items(set, key = { "d$i" + it.absolutePath }) { f -> CleanRow(f, f in selected, keep = f == set.first()) { if (f in selected) selected.remove(f) else selected.add(f) } }
                }
            } else {
                items(g.files, key = { it.absolutePath }) { f -> CleanRow(f, f in selected, false) { if (f in selected) selected.remove(f) else selected.add(f) } }
            }
        }
        InkButton(
            if (g.kind == Cleaner.Kind.EMPTY) "Remove ${selected.size} empty folders"
            else "Delete ${selected.size} · ${Files.formatSize(selected.sumOf { if (it.isFile) it.length() else 0L })}",
            { confirm = true },
            Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 8.dp).fillMaxWidth(),
            enabled = selected.isNotEmpty(),
        )
    }
    if (confirm) app.pzip.ui.components.ConfirmDialog(
        if (g.kind == Cleaner.Kind.EMPTY) "Remove ${selected.size} empty folders?"
        else "Free up ${Files.formatSize(selected.sumOf { if (it.isFile) it.length() else 0L })}?",
        if (Prefs.useTrash) "${selected.size} items will be moved to Trash. You can restore them for 30 days."
        else "${selected.size} items will be deleted permanently.",
        if (Prefs.useTrash) "Move to Trash" else "Delete forever", danger = true, onDismiss = { confirm = false },
    ) {
        FileOps.delete(selected.toList(), permanent = !Prefs.useTrash) { onCleaned() }
    }
}


@Composable
private fun CleanRow(f: File, selected: Boolean, keep: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    FileRow(
        f, selected = selected, onClick = onToggle, onLongClick = { Nav.open(context, f) },
        subtitle = "${if (f.isDirectory) "Empty folder" else Files.formatSize(f.length())} · ${Files.displayPath(f.parentFile ?: f)}" + if (keep) " · original" else "",
        trailing = {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) P.c.accent else P.c.textFaint, modifier = Modifier.padding(end = 6.dp).clickable(onClick = onToggle),
            )
        },
    )
}
