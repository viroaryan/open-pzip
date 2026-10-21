package app.pzip.ui.screens

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pzip.core.FileOps
import app.pzip.core.Files
import app.pzip.ui.Nav
import app.pzip.ui.components.IconBtn
import app.pzip.ui.components.SettingRow
import app.pzip.ui.components.Sheet
import app.pzip.ui.components.Thumbs
import app.pzip.ui.components.Toaster
import app.pzip.ui.components.TopBar
import app.pzip.ui.theme.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// ---------- Photos ----------

@Composable
fun ImageViewer(files: List<File>, start: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(files) }
    val pager = rememberPagerState(start) { list.size }
    var chrome by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<File?>(null) }
    val rotation = remember { mutableStateOf(mapOf<String, Int>()) }
    val current = list.getOrNull(pager.currentPage)

    LaunchedEffect(slideshow) {
        while (slideshow) {
            delay(3000)
            if (pager.currentPage < list.size - 1) pager.animateScrollToPage(pager.currentPage + 1) else slideshow = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { list[it].absolutePath }) { page ->
            ZoomableImage(list[page], rotation.value[list[page].absolutePath] ?: 0) { chrome = !chrome; slideshow = false }
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0x88000000)).statusBarsPadding().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { Nav.back() }, tint = Color.White)
                    Column(Modifier.weight(1f)) {
                        Text(current?.name ?: "", color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pager.currentPage + 1} of ${list.size}" + (current?.let { " · ${Files.formatDate(it.lastModified())}" } ?: ""), color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
                    }
                    IconBtn(Icons.Rounded.MoreVert, "More", { menu = true }, tint = Color.White)
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x88000000)).navigationBarsPadding().padding(8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                ) {
                    IconBtn(Icons.Rounded.Share, "Share", { current?.let { Files.share(context, listOf(it)) } }, tint = Color.White)
                    IconBtn(Icons.Rounded.RotateRight, "Rotate", {
                        current?.let { f -> rotation.value = rotation.value + (f.absolutePath to ((rotation.value[f.absolutePath] ?: 0) + 90) % 360) }
                    }, tint = Color.White)
                    IconBtn(Icons.Rounded.Slideshow, "Slideshow", { slideshow = true; chrome = false }, tint = Color.White)
                    IconBtn(Icons.Rounded.Info, "Details", { Nav.details = current }, tint = Color.White)
                    IconBtn(Icons.Rounded.Delete, "Delete", { deleting = current }, tint = Color.White)
                }
            }
        }
    }
    if (menu && current != null) Sheet({ menu = false }) {
        SettingRow("Set as wallpaper", icon = Icons.Rounded.Wallpaper, onClick = {
            menu = false
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { current.inputStream().use { WallpaperManager.getInstance(context).setStream(it) } }.isSuccess
                }
                Toaster.show(if (ok) "Wallpaper set" else "Couldn't set wallpaper")
            }
        })
        val rot = rotation.value[current.absolutePath] ?: 0
        if (rot != 0) SettingRow("Save rotation", "Rewrites the image file", Icons.Rounded.RotateRight, onClick = {
            menu = false
            scope.launch {
                val ok = withContext(Dispatchers.IO) { saveRotated(current, rot) }
                if (ok) {
                    rotation.value = rotation.value - current.absolutePath
                    Thumbs.clear()
                    FileOps.scan(current)
                }
                Toaster.show(if (ok) "Saved" else "Couldn't save")
            }
        })
        SettingRow("Open with another app", icon = Icons.Rounded.Share, onClick = { menu = false; Files.openExternal(context, current) })
    }
    deleting?.let { f ->
        DeleteDialog(listOf(f), { deleting = null }) {
            list = list - f
            if (list.isEmpty()) Nav.back()
        }
    }
}

private fun saveRotated(f: File, degrees: Int): Boolean = runCatching {
    val src = BitmapFactory.decodeFile(f.absolutePath) ?: return false
    val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    val format = when (Files.ext(f.name)) {
        "png" -> Bitmap.CompressFormat.PNG
        "webp" -> Bitmap.CompressFormat.WEBP
        else -> Bitmap.CompressFormat.JPEG
    }
    FileOutputStream(f).use { rotated.compress(format, 95, it) }
    true
}.getOrDefault(false)

@Composable
private fun ZoomableImage(file: File, rotation: Int, onTap: () -> Unit) {
    val screen = LocalConfiguration.current.let { maxOf(it.screenWidthDp, it.screenHeightDp) } * 3
    val bmp by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { Thumbs.decodeSampled(file, screen.coerceAtMost(4096))?.asImageBitmap() }.getOrNull() }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else scale = 2.5f
                    },
                )
            }
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 8f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b == null) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        else Image(
            b, file.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
                rotationZ = rotation.toFloat()
            },
        )
    }
}

// ---------- PDF ----------

@Composable
fun PdfViewer(file: File) {
    val context = LocalContext.current
    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    val width = LocalConfiguration.current.screenWidthDp * 2
    Column(Modifier.fillMaxSize().background(P.c.card)) {
        TopBar(file.name, renderer?.let { "${it.pageCount} pages" } ?: "", onBack = { Nav.back() }) {
            IconBtn(Icons.Rounded.Share, "Share", { Files.share(context, listOf(file)) })
        }
        if (renderer == null) {
            app.pzip.ui.components.EmptyState(Icons.Rounded.Info, "Can't open this PDF", "It may be password protected or damaged.") {
                app.pzip.ui.components.LineButton("Open with another app", { Files.openExternal(context, file) })
            }
            return
        }
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            itemsIndexed(List(renderer.pageCount) { it }) { _, i ->
                val page by produceState<ImageBitmap?>(null, i) {
                    value = withContext(Dispatchers.IO) {
                        synchronized(renderer) {
                            runCatching {
                                renderer.openPage(i).use { p ->
                                    val h = width * p.height / p.width
                                    val b = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                                    b.eraseColor(android.graphics.Color.WHITE)
                                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    b.asImageBitmap()
                                }
                            }.getOrNull()
                        }
                    }
                }
                val b = page
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).aspectRatio(b?.let { it.width.toFloat() / it.height } ?: 0.707f).background(Color.White)) {
                    if (b != null) Image(b, "Page ${i + 1}", Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ---------- Text ----------

@Composable
fun TextViewer(file: File) {
    val context = LocalContext.current
    var wrap by remember { mutableStateOf(true) }
    var size by remember { mutableIntStateOf(14) }
    val text by produceState<String?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = file.readBytes()
                String(bytes, Charsets.UTF_8)
            }.getOrDefault("Couldn't read this file.")
        }
    }
    Column(Modifier.fillMaxSize().background(P.c.bg)) {
        TopBar(file.name, Files.formatSize(file.length()), onBack = { Nav.back() }) {
            IconBtn(Icons.Rounded.WrapText, "Wrap lines", { wrap = !wrap })
            IconBtn(Icons.Rounded.Share, "Share", { Files.share(context, listOf(file)) })
        }
        Row {
            app.pzip.ui.components.TextLink("A−", { size = (size - 1).coerceAtLeast(9) }, P.c.text)
            app.pzip.ui.components.TextLink("A+", { size = (size + 1).coerceAtMost(26) }, P.c.text)
        }
        val t = text
        if (t == null) Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = P.c.accent) }
        else SelectionContainer {
            val scroll = rememberScrollState()
            val h = rememberScrollState()
            Text(
                t, color = P.c.text, fontFamily = FontFamily.Monospace, fontSize = size.sp, lineHeight = (size * 1.5).sp, softWrap = wrap,
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).then(if (wrap) Modifier else Modifier.horizontalScroll(h)).padding(16.dp),
            )
        }
    }
}

