package com.shilapi.xcertplay

import android.content.ActivityNotFoundException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AndroidSettingsLauncherTest {
    @Test fun triesPackageScopedActionAfterMissingExplicitSettings() {
        val attempted = mutableListOf<android.content.Intent>()
        assertTrue(AndroidSettingsLauncher.open(RuntimeEnvironment.getApplication()) {
            attempted.add(it)
            if (attempted.size == 1) throw ActivityNotFoundException()
        })
        assertEquals(2, attempted.size)
        assertEquals("com.android.settings.Settings", attempted.first().component!!.className)
        assertEquals("com.android.settings", attempted.last().`package`)
        assertEquals(android.provider.Settings.ACTION_SETTINGS, attempted.last().action)
    }

    @Test fun deniedSettingsDoesNotClaimSuccessOrChangeComponents() {
        var attempts = 0
        assertFalse(AndroidSettingsLauncher.open(RuntimeEnvironment.getApplication()) {
            attempts++
            throw SecurityException("OEM restriction")
        })
        assertEquals(AndroidSettingsLauncher.candidates().size, attempts)
        assertTrue(AndroidSettingsLauncher.report(RuntimeEnvironment.getApplication()).contains("permissionDenied"))
    }

    @Test fun everyPageIsExplicitOrScopedAndNeverFallsBackToOemResolver() {
        for (page in AndroidSettingsLauncher.Page.values()) {
            val intents = AndroidSettingsLauncher.candidates(page)
            assertNotNull(intents.first().component)
            assertTrue(intents.all { (it.component?.packageName ?: it.`package`) == "com.android.settings" })
        }
        assertEquals("com.android.settings.Settings" + '$' + "WifiSettingsActivity",
            AndroidSettingsLauncher.candidates(AndroidSettingsLauncher.Page.WIFI).first().component!!.className)
    }
}
