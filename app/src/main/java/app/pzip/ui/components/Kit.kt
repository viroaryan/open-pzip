package app.pzip.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pzip.core.FileKind
import app.pzip.core.Files
import app.pzip.ui.theme.P
import app.pzip.ui.theme.kindTint
import kotlinx.coroutines.delay
import java.io.File

// ---------- Bars ----------

@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    big: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 60.dp).padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp)) {
            Text(
                title,
                style = if (big) androidx.compose.material3.MaterialTheme.typography.headlineMedium else androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = P.c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = P.c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = P.c.text, size: Dp = 44.dp, enabled: Boolean = true) {
    val h = app.pzip.ui.rememberHaptics()
    Box(
        Modifier.size(size).clip(CircleShape).clickable(enabled = enabled) { h.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
    }
}

// ---------- Buttons ----------

@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val h = app.pzip.ui.rememberHaptics()
    Row(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(50)).background(if (enabled) P.c.ink else P.c.cardStrong)
            .clickable(enabled = enabled) { h.tap(); onClick() }.padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (enabled) P.c.onInk else P.c.textFaint
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, danger: Boolean = false) {
    val h = app.pzip.ui.rememberHaptics()
    val bg = if (danger) P.c.dangerSoft else P.c.accentSoft
    val fg = if (danger) P.c.danger else P.c.onAccentSoft
    Row(
        modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(50)).background(bg).clickable { h.tap(); onClick() }.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = fg, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val h = app.pzip.ui.rememberHaptics()
    Row(
        modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(50)).border(1.dp, P.c.border, RoundedCornerShape(50))
            .clickable { h.tap(); onClick() }.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = P.c.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = P.c.text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, color: Color = P.c.accent) {
    val h = app.pzip.ui.rememberHaptics()
    Text(
        text, color = color, style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { h.tap(); onClick() }.padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

// ---------- Surfaces ----------

@Composable
fun PCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp), color: Color = P.c.card, content: @Composable () -> Unit) {
    val h = app.pzip.ui.rememberHaptics()
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(color)
            .then(if (onClick != null) Modifier.clickable { h.tap(); onClick() } else Modifier)
            .padding(padding),
    ) { content() }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = P.c.text, modifier = Modifier.weight(1f))
        if (action != null) TextLink(action, onAction)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, bg: Color = P.c.accentSoft, fg: Color = P.c.onAccentSoft, icon: ImageVector? = null) {
    Row(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val h = app.pzip.ui.rememberHaptics()
    Row(
        modifier.clip(RoundedCornerShape(50))
            .background(if (selected) P.c.ink else Color.Transparent)
            .border(1.dp, if (selected) P.c.ink else P.c.border, RoundedCornerShape(50))
            .clickable { h.tap(); onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) P.c.onInk else P.c.text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) P.c.onInk else P.c.text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = P.c.accent, track: Color = P.c.cardStrong, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = P.c.border)

// ---------- Files ----------

fun kindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Rounded.Folder
    FileKind.ARCHIVE -> Icons.Rounded.FolderZip
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.VIDEO -> Icons.Rounded.Movie
    FileKind.AUDIO -> Icons.Rounded.MusicNote
    FileKind.PDF -> Icons.Rounded.PictureAsPdf
    FileKind.DOCUMENT -> Icons.Rounded.Description
    FileKind.SHEET -> Icons.Rounded.TableChart
    FileKind.SLIDES -> Icons.Rounded.Slideshow
    FileKind.TEXT -> Icons.AutoMirrored.Rounded.Article
    FileKind.CODE -> Icons.Rounded.Code
    FileKind.APK -> Icons.Rounded.Android
    FileKind.FONT -> Icons.Rounded.FontDownload
    FileKind.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

@Composable
fun KindTile(kind: FileKind, size: Dp = 44.dp, corner: Dp = 13.dp) {
    val (fg, bg) = kindTint(kind, P.c)
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(bg), contentAlignment = Alignment.Center) {
        Icon(kindIcon(kind), null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun FileThumb(file: File, size: Dp = 44.dp, corner: Dp = 13.dp) {
    val kind = Files.kindOf(file)
    if (kind == FileKind.FOLDER || !Thumbs.canThumb(file)) {
        KindTile(kind, size, corner)
        return
    }
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx().coerceIn(96, 512) }
    val bmp by produceState(Thumbs.cached(file, px), file.absolutePath) { value = Thumbs.load(context, file, px) }
    val b = bmp
    if (b == null) {
        KindTile(kind, size, corner)
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(P.c.card)) {
            Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (kind == FileKind.VIDEO) {
                Box(Modifier.align(Alignment.BottomStart).padding(3.dp).size(14.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Movie, null, tint = Color.White, modifier = Modifier.size(9.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    file: File,
    selected: Boolean = false,
    selecting: Boolean = false,
    subtitle: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
) {
    val isDir = file.isDirectory
    val h = app.pzip.ui.rememberHaptics()
    val sub = subtitle ?: androidx.compose.runtime.remember(file, file.lastModified()) {
        if (isDir) "${Files.childCount(file).let { n -> if (n == 1) "1 item" else "$n items" }} · ${Files.formatDate(file.lastModified())}"
        else "${Files.formatSize(file.length())} · ${Files.formatDate(file.lastModified())}"
    }
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) P.c.accentSoft else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = { h.longPress(); onLongClick() })
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            FileThumb(file)
            if (selecting) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(0.dp).size(18.dp).clip(CircleShape).background(P.c.bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                        tint = if (selected) P.c.accent else P.c.textFaint, modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) {
            Text(file.name, color = P.c.text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, color = P.c.textMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
    }
}

// ---------- Settings rows ----------

@Composable
fun SettingRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    val h = app.pzip.ui.rememberHaptics()
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { h.tap(); onClick() } else Modifier).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(P.c.card), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = P.c.text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = P.c.text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, color = P.c.textMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String? = null, icon: ImageVector? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val h = app.pzip.ui.rememberHaptics()
    SettingRow(title, subtitle, icon, onClick = { h.toggle(!checked); onChange(!checked) }) {
        PSwitch(checked, onChange)
    }
}

@Composable
fun PSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val h = app.pzip.ui.rememberHaptics()
    Switch(
        checked = checked, onCheckedChange = { h.toggle(it); onChange(it) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = P.c.onInk, checkedTrackColor = P.c.ink, checkedBorderColor = P.c.ink,
            uncheckedThumbColor = P.c.textFaint, uncheckedTrackColor = P.c.card, uncheckedBorderColor = P.c.border,
        ),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(P.c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = P.c.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = P.c.text, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(text, color = P.c.textMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun LockBadge() = Pill("Encrypted", icon = Icons.Rounded.Lock)

// ---------- Toasts ----------

object Toaster {
    var message by mutableStateOf<String?>(null)
        private set
    private var stamp = 0L
    fun show(text: String) {
        message = text
        stamp = System.nanoTime()
    }
    internal fun clear() {
        message = null
    }
    internal val id get() = stamp
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val msg = Toaster.message
    LaunchedEffect(Toaster.id) {
        if (msg != null) {
            delay(2600)
            Toaster.clear()
        }
    }
    AnimatedVisibility(msg != null, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(
                msg ?: "", color = P.c.onInk, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(P.c.ink).padding(horizontal = 18.dp, vertical = 13.dp),
            )
        }
    }
}

/** Chips that wrap onto new lines instead of scrolling off the edge, so no label is ever cut. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
