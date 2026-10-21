package app.pzip.core.archive

import app.pzip.core.OverwriteMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.random.Random

class ArchiveEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sampleTree(): File {
        val root = tmp.newFolder("My Files")
        File(root, "notes.txt").writeText("hello pzip ✓ हिंदी\n".repeat(2000))
        File(root, "sub/deeper").mkdirs()
        File(root, "sub/deeper/data.bin").writeBytes(Random(7).nextBytes(300_000))
        File(root, "empty").mkdirs()
        return root
    }

    private fun assertSameTree(expected: File, actual: File) {
        expected.walkTopDown().forEach { e ->
            val rel = e.relativeTo(expected).path
            val a = File(actual, rel)
            assertTrue("missing $rel", a.exists())
            if (e.isFile) assertArrayEquals("content of $rel", e.readBytes(), a.readBytes())
        }
    }

    private fun roundTrip(format: CreateFormat, password: String? = null, split: Long = 0) {
        val src = sampleTree()
        val out = File(tmp.root, "out.${format.ext}")
        ArchiveEngine.create(listOf(src), out, CreateOptions(format = format, password = password?.toCharArray(), splitSize = split))
        assertTrue(out.exists())

        val info = ArchiveEngine.open(out, password?.toCharArray())
        assertEquals(2, info.files)
        if (password != null) assertTrue(info.anyEncrypted)

        val dest = tmp.newFolder("x_${format.name}_${split}")
        val r = ArchiveEngine.extract(out, password?.toCharArray(), dest, null, OverwriteMode.OVERWRITE)
        assertEquals(2, r.files)
        assertSameTree(src, File(dest, "My Files"))
        assertTrue(ArchiveEngine.test(out, password?.toCharArray()).errors.isEmpty())
    }

    @Test fun zip() = roundTrip(CreateFormat.ZIP)
    @Test fun zipAes256() = roundTrip(CreateFormat.ZIP, "s3cret!")
    @Test fun zipSplit() = roundTrip(CreateFormat.ZIP, "s3cret!", split = 128 * 1024)
    @Test fun sevenZip() = roundTrip(CreateFormat.SEVEN_Z)
    @Test fun tar() = roundTrip(CreateFormat.TAR)
    @Test fun tarGz() = roundTrip(CreateFormat.TAR_GZ)
    @Test fun tarXz() = roundTrip(CreateFormat.TAR_XZ)
    @Test fun tarBz2() = roundTrip(CreateFormat.TAR_BZ2)

    @Test fun wrongPasswordIsDetected() {
        val src = sampleTree()
        val out = File(tmp.root, "locked.zip")
        ArchiveEngine.create(listOf(src), out, CreateOptions(password = "right".toCharArray()))
        try {
            ArchiveEngine.extract(out, "wrong".toCharArray(), tmp.newFolder("bad"))
            fail("expected wrong password")
        } catch (_: WrongPasswordException) {
        }
        try {
            ArchiveEngine.extract(out, null, tmp.newFolder("none"))
            fail("expected password required")
        } catch (_: PasswordRequiredException) {
        }
    }

    @Test fun selectiveExtractStripsPrefix() {
        val src = sampleTree()
        val out = File(tmp.root, "sel.zip")
        ArchiveEngine.create(listOf(src), out, CreateOptions())
        val dest = tmp.newFolder("sel")
        ArchiveEngine.extract(out, null, dest, Selection(setOf("My Files/sub"), "My Files"), OverwriteMode.OVERWRITE)
        assertTrue(File(dest, "sub/deeper/data.bin").exists())
        assertTrue(!File(dest, "notes.txt").exists())
    }

    @Test fun zipSlipIsBlocked() {
        // An archive entry that tries to escape the destination folder.
        val evil = File(tmp.root, "evil.zip")
        java.util.zip.ZipOutputStream(evil.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("../../escaped.txt"))
            z.write("x".toByteArray())
            z.closeEntry()
        }
        val dest = tmp.newFolder("safe")
        ArchiveEngine.extract(evil, null, dest, null, OverwriteMode.OVERWRITE)
        assertTrue(!File(tmp.root, "escaped.txt").exists())
        assertTrue(!File(dest.parentFile!!.parentFile, "escaped.txt").exists())
    }

    @Test fun failedOverwritePreservesExistingFile() {
        val dest = tmp.newFolder("overwrite")
        val existing = File(dest, "notes.txt").also { it.writeText("keep me") }
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("corrupt entry")
        }
        try {
            Output(dest, null, OverwriteMode.OVERWRITE, Progress.NONE).write("notes.txt", 0, broken)
            fail("expected read failure")
        } catch (_: IOException) {
        }
        assertEquals("keep me", existing.readText())
        assertEquals(listOf("notes.txt"), dest.listFiles()!!.map { it.name })
    }

    @Test fun editZip() {
        val src = sampleTree()
        val out = File(tmp.root, "edit.zip")
        ArchiveEngine.create(listOf(src), out, CreateOptions())
        ArchiveEngine.rename(out, "My Files/notes.txt", "My Files/readme.txt")
        ArchiveEngine.delete(out, listOf("My Files/sub"))
        val names = ArchiveEngine.open(out).entries.filter { !it.isDir }.map { it.path }
        assertEquals(listOf("My Files/readme.txt"), names)
    }

    /** Leaves an AES-256 ZIP and a 7Z behind so they can be checked with desktop 7-Zip. */
    @Test fun writeInteropSamples() {
        val dir = File(System.getProperty("pzip.interop", "build/interop")).also { it.mkdirs() }
        val src = sampleTree()
        ArchiveEngine.create(listOf(src), File(dir, "aes256.zip").also { it.delete() }, CreateOptions(password = "pzip1234".toCharArray()))
        ArchiveEngine.create(listOf(src), File(dir, "lzma2.7z").also { it.delete() }, CreateOptions(format = CreateFormat.SEVEN_Z, level = Level.BEST))
        ArchiveEngine.create(listOf(src), File(dir, "files.tar.xz").also { it.delete() }, CreateOptions(format = CreateFormat.TAR_XZ))
    }
}
