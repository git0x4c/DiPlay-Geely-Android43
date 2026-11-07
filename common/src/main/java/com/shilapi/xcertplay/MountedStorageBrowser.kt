package com.shilapi.xcertplay

import android.app.Activity
import android.app.AlertDialog
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Read-only, selected-volume confined browsing. Never executes, installs, deletes or writes files. */
internal object StorageBrowserFiles {
    data class Listing(val directory: File, val entries: List<File>, val truncated: Boolean)
    const val MAX_ENTRIES = 500
    const val MAX_PREVIEW_BYTES = 64 * 1024
    private val textExtensions = setOf("txt", "log", "json", "xml", "csv", "md", "ini", "conf")

    fun checked(root: File, file: File): File {
        if (!root.isDirectory) throw IOException("Selected storage is unavailable")
        val base = root.canonicalFile
        val target = file.canonicalFile
        if (target != base && !target.path.startsWith(base.path.trimEnd('/') + "/")) {
            throw IOException("Outside selected storage")
        }
        return target
    }

    fun list(root: File, directory: File): Listing {
        val current = checked(root, directory)
        val files = current.listFiles() ?: throw IOException("Directory unavailable or permission denied")
        val safe = files.filter { runCatching { checked(root, it); true }.getOrDefault(false) }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return Listing(current, safe.take(MAX_ENTRIES), safe.size > MAX_ENTRIES)
    }

    fun preview(root: File, file: File): String? {
        val current = checked(root, file)
        if (!current.isFile || current.extension.lowercase() !in textExtensions) return null
        return current.inputStream().use { stream ->
            val bytes = ByteArray(MAX_PREVIEW_BYTES)
            var count = 0
            while (count < bytes.size) {
                val read = stream.read(bytes, count, bytes.size - count)
                if (read <= 0) break
                count += read
            }
            String(bytes, 0, count, Charsets.UTF_8)
        }
    }
}

/** No dependency on an OEM file manager or DocumentsUI. All volume I/O stays off the UI thread. */
internal class MountedStorageBrowser(
    private val activity: Activity,
    private val target: DiagnosticStorageTargets.Target,
    private val export: () -> Unit,
) {
    private val generation = AtomicInteger()
    private var closed = false
    private var current = target.directory
    private var entries = emptyList<File>()
    private val path = TextView(activity).apply { setPadding(18, 12, 18, 12) }
    private val list = ListView(activity)
    private lateinit var dialog: AlertDialog

    fun show() {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(path, LinearLayout.LayoutParams(-1, -2))
            val height = (activity.resources.displayMetrics.heightPixels * 0.48).toInt()
            addView(list, LinearLayout.LayoutParams(-1, height))
        }
        dialog = AlertDialog.Builder(activity).setTitle(R.string.browse_storage_title).setView(content)
            .setNegativeButton(R.string.done, null)
            .setNeutralButton(R.string.browse_storage_up, null)
            .setPositiveButton(R.string.browse_storage_export) { _, _ -> export() }.create()
        dialog.setOnDismissListener { closed = true; generation.incrementAndGet() }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !target.readOnly
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (current != target.directory) load(current.parentFile ?: target.directory)
        }
        list.setOnItemClickListener { _, _, index, _ ->
            entries.getOrNull(index)?.let { file -> load(file) }
        }
        load(target.directory)
    }

    private fun load(file: File) {
        val request = generation.incrementAndGet()
        list.isEnabled = false
        path.text = activity.getString(R.string.browse_storage_loading)
        Thread({
            val result = runCatching {
                val mounted = DiagnosticStorageTargets.discover(activity.applicationContext).any {
                    it.directory == target.directory && it.identity == target.identity
                }
                if (!mounted) throw IOException("Selected storage is no longer mounted")
                val checked = StorageBrowserFiles.checked(target.directory, file)
                if (checked.isDirectory) StorageBrowserFiles.list(target.directory, checked) to null
                else null to (checked to StorageBrowserFiles.preview(target.directory, checked))
            }
            activity.runOnUiThread {
                if (closed || request != generation.get() || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                list.isEnabled = true
                result.onSuccess { (listing, preview) ->
                    if (listing != null) {
                        current = listing.directory
                        entries = listing.entries
                        path.text = current.path + when {
                            listing.truncated -> "\n" + activity.getString(R.string.browse_storage_truncated)
                            entries.isEmpty() -> "\n" + activity.getString(R.string.browse_storage_empty)
                            else -> ""
                        }
                        list.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, entries.map {
                            (if (it.isDirectory) "[DIR] " else "[FILE] ") + it.name
                        })
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = current != target.directory
                    } else if (preview != null) {
                        path.text = current.path
                        val (selected, text) = preview
                        val view = TextView(activity).apply {
                            setPadding(18, 12, 18, 12)
                            setTextIsSelectable(true)
                            this.text = text ?: activity.getString(R.string.browse_storage_file_info, selected.path)
                        }
                        AlertDialog.Builder(activity).setTitle(selected.name)
                            .setMessage(activity.getString(R.string.browse_storage_preview_hint))
                            .setView(ScrollView(activity).apply { addView(view, ViewGroup.LayoutParams(-1, -2)) })
                            .setPositiveButton(R.string.done, null).show()
                    }
                }.onFailure {
                    entries = emptyList()
                    list.adapter = null
                    path.text = activity.getString(R.string.browse_storage_failed)
                }
            }
        }, "diplay-storage-browser").start()
    }
}
