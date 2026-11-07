package com.shilapi.xcertplay

import android.content.Context
import java.io.File
import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.FileOutputStream

/** Saves an app-owned report without depending on an OEM's document-picker activity. */
internal object DiagnosticExportStore {
    fun saveSelected(context: Context, target: DiagnosticStorageTargets.Target, fileName: String, report: String): Uri {
        val current = DiagnosticStorageTargets.discover(context).firstOrNull {
            it.directory == target.directory && it.identity == target.identity
        } ?: throw IOException("Selected storage is no longer mounted")
        if (current.readOnly) throw IOException("Selected storage is read-only")
        return Uri.fromFile(writeSelectedDirectory(current.directory, fileName, report))
    }

    /** Never recreate a vanished volume root or overwrite an existing report. */
    internal fun writeSelectedDirectory(root: File, fileName: String, report: String): File {
        require(fileName.isNotBlank() && fileName != "." && fileName != ".." && File(fileName).name == fileName)
        if (!root.isDirectory) throw IOException("Selected storage is unavailable")
        val folder = File(root, "DiPlay-diagnostics")
        if (!folder.isDirectory && !folder.mkdir()) throw IOException("Selected storage is not writable")
        if (folder.canonicalFile.parentFile != root.canonicalFile) throw IOException("Invalid diagnostic directory")
        val file = File(folder, fileName)
        if (!file.createNewFile()) throw IOException("Report already exists; no file was overwritten")
        try {
            val bytes = report.toByteArray(Charsets.UTF_8)
            FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
            if (!file.readBytes().contentEquals(bytes)) throw IOException("Report verification failed")
        } catch (error: Exception) {
            file.delete() // Only the file created by this operation.
            throw error
        }
        return file
    }

    /** API18: save directly to the shared user-storage root without DocumentsUI. */
    fun saveLegacy(context: Context, fileName: String, report: String): Uri {
        require(File(fileName).name == fileName)
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            throw IOException("User storage is not mounted or writable")
        }
        return Uri.fromFile(writeLegacyFile(Environment.getExternalStorageDirectory(), fileName, report))
    }

    private fun writeLegacyFile(directory: File, fileName: String, report: String): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Report directory unavailable")
        val file = File(directory, fileName)
        try { file.outputStream().bufferedWriter(Charsets.UTF_8).use { it.write(report) } }
        catch (error: Exception) { file.delete(); throw error }
        return file
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveToDownloads(resolver: ContentResolver, fileName: String, report: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DiPlay")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Downloads could not create the report")
        try {
            write(resolver, uri, report)
            val published = resolver.update(uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
            if (published != 1) throw IOException("Downloads could not publish the report")
            return uri
        } catch (error: Exception) {
            // Only remove the entry created by this call; never leave a partial report behind.
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    fun write(resolver: ContentResolver, uri: Uri, report: String) {
        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw IOException("Report destination is unavailable")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
    }
}
