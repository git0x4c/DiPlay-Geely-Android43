package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

class StorageBrowserFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun listsFoldersFirstAndTextPreviewIsBoundedAndReadOnly() {
        val root = temporary.newFolder("usb")
        val text = File(root, "a.txt").apply { writeText("a".repeat(100_000)) }
        File(root, "folder").mkdir()
        val listing = StorageBrowserFiles.list(root, root)
        assertEquals(listOf("folder", "a.txt"), listing.entries.map { it.name })
        assertFalse(listing.truncated)
        assertEquals(65_536, StorageBrowserFiles.preview(root, text)!!.length)
        assertEquals(100_000, text.length().toInt())
        val apk = File(root, "test.apk").apply { writeText("not executed") }
        assertNull(StorageBrowserFiles.preview(root, apk))
    }

    @Test fun rejectsTraversalSymlinkEscapeAndRemovedVolume() {
        val root = temporary.newFolder("usb")
        val outside = temporary.newFolder("outside")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
        assertTrue(StorageBrowserFiles.list(root, root).entries.isEmpty())
        assertThrows(IOException::class.java) { StorageBrowserFiles.list(root, File(root, "..")) }
        assertThrows(IOException::class.java) { StorageBrowserFiles.list(root, File(root, "escape")) }
        val missing = File(temporary.root, "missing")
        assertThrows(IOException::class.java) { StorageBrowserFiles.list(missing, missing) }
        assertFalse(missing.exists())
    }

    @Test fun largeFoldersHaveExplicitEntryLimit() {
        val root = temporary.newFolder("usb")
        repeat(505) { File(root, "file-$it.txt").createNewFile() }
        val listing = StorageBrowserFiles.list(root, root)
        assertEquals(500, listing.entries.size)
        assertTrue(listing.truncated)
        assertEquals(505, root.listFiles()!!.size)
    }
}
