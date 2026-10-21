package app.pzip.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.pzip.core.FileKind
import app.pzip.core.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** Small, memory-bounded thumbnail loader for images, videos, music covers and APK icons. */
object Thumbs {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val failed = HashSet<String>()
    private val gate = Semaphore(4)

    fun cached(file: File, px: Int): ImageBitmap? = cache.get(key(file, px))

    private fun key(file: File, px: Int) = "${file.absolutePath}|${file.lastModified()}|$px"

    fun canThumb(file: File) = when (Files.kindOf(file.name)) {
        FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO, FileKind.APK -> true
        else -> false
    }

    suspend fun load(context: Context, file: File, px: Int): ImageBitmap? {
        val k = key(file, px)
        cache.get(k)?.let { return it }
        if (k in failed) return null
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                val bmp = runCatching { make(context, file, px) }.getOrNull()
                if (bmp == null) {
                    synchronized(failed) { failed += k }
                    null
                } else bmp.asImageBitmap().also { cache.put(k, it) }
            }
        }
    }

    private fun make(context: Context, file: File, px: Int): Bitmap? = when (Files.kindOf(file.name)) {
        FileKind.IMAGE -> if (Build.VERSION.SDK_INT >= 29) ThumbnailUtils.createImageThumbnail(file, Size(px, px), null) else decodeSampled(file, px)
        FileKind.VIDEO -> if (Build.VERSION.SDK_INT >= 29) ThumbnailUtils.createVideoThumbnail(file, Size(px, px), null)
        else @Suppress("DEPRECATION") ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
        FileKind.AUDIO -> MediaMetadataRetriever().let { r ->
            try {
                r.setDataSource(file.absolutePath)
                r.embeddedPicture?.let { bytes ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    opts.inSampleSize = sample(opts.outWidth, opts.outHeight, px)
                    opts.inJustDecodeBounds = false
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            } finally {
                r.release()
            }
        }
        FileKind.APK -> {
            val pm = context.packageManager
            pm.getPackageArchiveInfo(file.absolutePath, 0)?.applicationInfo?.let { ai ->
                ai.sourceDir = file.absolutePath
                ai.publicSourceDir = file.absolutePath
                val d = ai.loadIcon(pm)
                val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, px, px)
                d.draw(Canvas(b))
                b
            }
        }
        else -> null
    }

    fun decodeSampled(file: File, px: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0) return null
        opts.inSampleSize = sample(opts.outWidth, opts.outHeight, px)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun sample(w: Int, h: Int, px: Int): Int {
        var s = 1
        while (w / (s * 2) >= px && h / (s * 2) >= px) s *= 2
        return s
    }

    fun clear() = cache.evictAll()
}
