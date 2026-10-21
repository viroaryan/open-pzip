package app.pzip.core

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CleanerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun reportCountsOverlappingFilesOnceAndEmptyFoldersAsZero() {
        val file = File(tmp.root, "copy.bin").also { it.writeBytes(ByteArray(128)) }
        val empty = tmp.newFolder("empty")
        val duplicate = Cleaner.Group(Cleaner.Kind.DUPLICATES, listOf(file))
        val large = Cleaner.Group(Cleaner.Kind.LARGE, listOf(file))
        val folders = Cleaner.Group(Cleaner.Kind.EMPTY, listOf(empty))

        assertEquals(0L, folders.size)
        assertEquals(128L, Cleaner.Report(listOf(duplicate, large, folders)).total)
    }
}
