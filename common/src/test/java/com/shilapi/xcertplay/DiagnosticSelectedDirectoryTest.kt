package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

class DiagnosticSelectedDirectoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun writesAndVerifiesUtf8InChosenVolumeOnly() {
        val root = temporary.newFolder("usb")
        val report = "帝豪 GL 诊断\nAP=13\n"
        val file = DiagnosticExportStore.writeSelectedDirectory(root, "report.txt", report)
        assertEquals(File(root, "DiPlay-diagnostics/report.txt"), file)
        assertEquals(report, file.readText(Charsets.UTF_8))
    }

    @Test fun neverRecreatesRemovedVolumeRoot() {
        val missing = File(temporary.root, "removed-usb")
        assertThrows(IOException::class.java) {
            DiagnosticExportStore.writeSelectedDirectory(missing, "report.txt", "report")
        }
        assertFalse(missing.exists())
    }

    @Test fun neverOverwritesExistingReport() {
        val root = temporary.newFolder("usb")
        val original = DiagnosticExportStore.writeSelectedDirectory(root, "report.txt", "original")
        assertThrows(IOException::class.java) {
            DiagnosticExportStore.writeSelectedDirectory(root, "report.txt", "replacement")
        }
        assertEquals("original", original.readText())
    }

    @Test fun rejectsTraversalAndInvalidReportNames() {
        for (name in listOf("../escape.txt", "", ".", "..")) {
            assertThrows(IllegalArgumentException::class.java) {
                DiagnosticExportStore.writeSelectedDirectory(temporary.root, name, "report")
            }
        }
    }

    @Test fun rejectsDiagnosticFolderSymlinkOutsideSelectedRoot() {
        val root = temporary.newFolder("usb")
        val outside = temporary.newFolder("outside")
        Files.createSymbolicLink(File(root, "DiPlay-diagnostics").toPath(), outside.toPath())
        assertThrows(IOException::class.java) {
            DiagnosticExportStore.writeSelectedDirectory(root, "report.txt", "report")
        }
        assertFalse(File(outside, "report.txt").exists())
    }
}
