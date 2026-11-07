package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class DiagnosticStorageTargetsTest {
    @Test fun parsesEscapedUsbMountAndReadOnlyFlag() {
        val mount = DiagnosticStorageTargets.parseMount("/dev/block/vold/8:1 /mnt/usb/My\\040Disk vfat ro,nosuid,nodev 0 0")!!
        assertEquals("/mnt/usb/My Disk", mount.path)
        assertTrue(mount.readOnly)
        assertTrue(DiagnosticStorageTargets.isSharedStorageMount(mount))
    }

    @Test fun acceptsOemUsbAndAndroidVolumeRootsWithoutFixedUsbName() {
        for (path in listOf("/storage/1234-ABCD", "/mnt/udisk1", "/mnt/usb_storage/USB_DISK0", "/mnt/media_rw/ABCD")) {
            val mount = DiagnosticStorageTargets.parseMount("/dev/block/vold/8:1 $path vfat rw 0 0")!!
            assertTrue(path, DiagnosticStorageTargets.isSharedStorageMount(mount))
            assertFalse(mount.readOnly)
        }
    }

    @Test fun excludesSystemAndInternalRuntimeAliases() {
        for (path in listOf("/system", "/data", "/mnt/asec/pkg", "/mnt/runtime/write/ABCD", "/storage/emulated/0", "/storage/self")) {
            val mount = DiagnosticStorageTargets.parseMount("/dev/block/test $path ext4 rw 0 0")!!
            assertFalse(path, DiagnosticStorageTargets.isSharedStorageMount(mount))
        }
        assertNull(DiagnosticStorageTargets.parseMount("malformed"))
    }
}
