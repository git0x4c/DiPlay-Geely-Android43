package com.shilapi.xcertplay

import android.content.Context
import android.os.Environment
import java.io.File

/** Legacy head units often have no DocumentsUI. Discover volumes without mounting or probing writes. */
internal object DiagnosticStorageTargets {
    data class Target(val directory: File, val primary: Boolean, val readOnly: Boolean, val identity: String?)
    internal data class Mount(val source: String, val path: String, val type: String, val readOnly: Boolean)

    fun discover(context: Context): List<Target> {
        val mounts = runCatching { File("/proc/mounts").readLines().mapNotNull(::parseMount) }.getOrDefault(emptyList())
        val primary = Environment.getExternalStorageDirectory().canonicalFile
        val found = linkedMapOf<String, Target>()
        fun add(directory: File, readOnly: Boolean) {
            val root = runCatching { directory.canonicalFile }.getOrNull() ?: return
            if (!root.isDirectory) return
            val mount = mounts.filter { root.path == it.path || root.path.startsWith(it.path.trimEnd('/') + "/") }
                .maxByOrNull { it.path.length }
            val target = Target(root, root == primary, readOnly || mount?.readOnly == true,
                mount?.let { "${it.source}|${it.path}|${it.type}" })
            found[root.path] = target
        }
        val state = Environment.getExternalStorageState()
        if (state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY) {
            add(primary, state == Environment.MEDIA_MOUNTED_READ_ONLY)
        }
        // API18/19 OEM StorageManager/StorageVolume hidden getters. No privileged writes.
        runCatching {
            val manager = context.getSystemService("storage") ?: return@runCatching
            val volumes = manager.javaClass.getMethod("getVolumeList").invoke(manager) as? Array<*>
                ?: return@runCatching
            for (volume in volumes.filterNotNull()) {
                val path = volume.javaClass.getMethod("getPath").invoke(volume) as? String ?: continue
                val volumeState = runCatching {
                    manager.javaClass.getMethod("getVolumeState", String::class.java).invoke(manager, path) as? String
                }.getOrNull() ?: runCatching { volume.javaClass.getMethod("getState").invoke(volume) as? String }.getOrNull()
                if (volumeState == Environment.MEDIA_MOUNTED || volumeState == Environment.MEDIA_MOUNTED_READ_ONLY) {
                    add(File(path), volumeState == Environment.MEDIA_MOUNTED_READ_ONLY)
                }
            }
        }
        // Supplement OEM volumes absent from StorageManager. Never scan arbitrary subdirectories.
        for (mount in mounts) {
            if (isSharedStorageMount(mount)) add(File(mount.path), mount.readOnly)
        }
        return found.values.sortedWith(compareBy<Target> { !it.primary }.thenBy { it.directory.path })
    }

    internal fun parseMount(line: String): Mount? {
        val fields = line.trim().split(Regex("\\s+"))
        if (fields.size < 4) return null
        fun unescape(value: String) = Regex("\\\\([0-7]{3})").replace(value) {
            it.groupValues[1].toInt(8).toChar().toString()
        }
        return Mount(unescape(fields[0]), unescape(fields[1]), fields[2], "ro" in fields[3].split(','))
    }

    internal fun isSharedStorageMount(mount: Mount): Boolean {
        val path = mount.path
        if (path.startsWith("/mnt/runtime/") || path.startsWith("/mnt/secure/") ||
            path.startsWith("/mnt/asec/") || path.startsWith("/storage/emulated/")) return false
        if (mount.type !in setOf("vfat", "exfat", "texfat", "ntfs", "ntfs3", "fuse", "fuseblk", "sdcardfs", "ext4")) return false
        return (path.startsWith("/storage/") && path !in setOf("/storage/emulated", "/storage/self")) ||
            path.startsWith("/mnt/media_rw/") ||
            Regex("^/mnt/(?:usb[^/]*|udisk[^/]*|sdcard[^/]*|external_sd[^/]*|extSdCard[^/]*)(?:/[^/]+)?$", RegexOption.IGNORE_CASE).matches(path)
    }
}
