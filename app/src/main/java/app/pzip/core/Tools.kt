package app.pzip.core

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object Tools {
    data class Hashes(val crc32: String, val md5: String, val sha1: String, val sha256: String, val sha512: String)

    /** Computes all common checksums in one pass. */
    fun hashes(file: File, onProgress: (Float) -> Unit = {}, cancelled: () -> Boolean = { false }): Hashes {
        val crc = CRC32()
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val sha512 = MessageDigest.getInstance("SHA-512")
        val total = file.length().coerceAtLeast(1)
        var done = 0L
        FileInputStream(file).use { input ->
            val buf = ByteArray(512 * 1024)
            while (true) {
                if (cancelled()) throw app.pzip.core.archive.CancelledException()
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
                md5.update(buf, 0, n)
                sha1.update(buf, 0, n)
                sha256.update(buf, 0, n)
                sha512.update(buf, 0, n)
                done += n
                onProgress(done.toFloat() / total)
            }
        }
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        return Hashes("%08x".format(crc.value), hex(md5.digest()), hex(sha1.digest()), hex(sha256.digest()), hex(sha512.digest()))
    }

    data class BenchResult(val name: String, val compressMBs: Double, val decompressMBs: Double, val ratio: Double)

    /** Compresses realistic mixed data in memory with each codec. */
    fun benchmark(sizeMb: Int = 16, onStep: (String) -> Unit = {}): List<BenchResult> {
        val data = sampleData(sizeMb * 1024 * 1024)
        val codecs = listOf<Triple<String, (OutputStream) -> OutputStream, (InputStream) -> InputStream>>(
            Triple("ZIP · Deflate", { DeflaterOutputStream(it, Deflater(6), 64 * 1024) }, { InflaterInputStream(it) }),
            Triple("7Z · LZMA2", { XZOutputStream(it, LZMA2Options(5)) }, { XZInputStream(it) }),
            Triple("TAR.BZ2 · BZip2", { BZip2CompressorOutputStream(it, 9) }, { BZip2CompressorInputStream(it) }),
            Triple("Store", { it }, { it }),
        )
        return codecs.map { (name, comp, decomp) ->
            onStep(name)
            val bos = ByteArrayOutputStream(data.size / 2)
            val t0 = System.nanoTime()
            comp(bos).use { it.write(data) }
            val t1 = System.nanoTime()
            val packed = bos.toByteArray()
            val buf = ByteArray(256 * 1024)
            val t2 = System.nanoTime()
            decomp(ByteArrayInputStream(packed)).use { while (it.read(buf) >= 0) Unit }
            val t3 = System.nanoTime()
            val mb = data.size / 1048576.0
            BenchResult(name, mb / ((t1 - t0) / 1e9), mb / ((t3 - t2) / 1e9), packed.size.toDouble() / data.size)
        }
    }

    private fun sampleData(size: Int): ByteArray {
        val rnd = Random(42)
        val words = listOf("pzip", "archive", "compress", "the", "quick", "brown", "fox", "data", "file", "android", "video", "music", "folder", "secure", "light", "{\"id\":", "</div>", "0x00ff", "\n")
        val out = ByteArray(size)
        var i = 0
        while (i < size) {
            if (rnd.nextInt(10) < 2) {
                // Some incompressible noise, like media inside real archives.
                val n = minOf(size - i, 256 + rnd.nextInt(2048))
                for (k in 0 until n) out[i + k] = rnd.nextInt(256).toByte()
                i += n
            } else {
                val w = words[rnd.nextInt(words.size)].toByteArray()
                val n = minOf(size - i, w.size)
                System.arraycopy(w, 0, out, i, n)
                i += n
                if (i < size) out[i++] = ' '.code.toByte()
            }
        }
        return out
    }
}
