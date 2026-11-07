package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Opens exported settings activities only; never enables components or changes system policy. */
internal object AndroidSettingsLauncher {
    enum class Page(val action: String, val classes: List<String>) {
        MAIN(Settings.ACTION_SETTINGS, listOf("Settings")),
        WIFI(Settings.ACTION_WIFI_SETTINGS, listOf("Settings" + '$' + "WifiSettingsActivity", "wifi.WifiSettings")),
        WIRELESS(Settings.ACTION_WIRELESS_SETTINGS, listOf("Settings" + '$' + "WirelessSettingsActivity", "WirelessSettings")),
        APPLICATIONS(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, listOf("Settings" + '$' + "ManageApplicationsActivity", "Settings" + '$' + "ApplicationsSettingsActivity")),
        DEVELOPMENT(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("Settings" + '$' + "DevelopmentSettingsActivity", "DevelopmentSettings")),
        DEVICE_INFO(Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("Settings" + '$' + "DeviceInfoSettingsActivity", "DeviceInfoSettings")),
    }

    fun candidates(page: Page = Page.MAIN): List<Intent> = page.classes.map { name ->
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.settings", "com.android.settings.$name"))
    } + Intent(page.action).setPackage("com.android.settings")

    fun open(context: Context, page: Page = Page.MAIN, launch: (Intent) -> Unit = { context.startActivity(it) }): Boolean {
        val attempts = mutableListOf<String>()
        for (intent in candidates(page)) {
            val target = intent.component?.flattenToShortString() ?: "${intent.action}@${intent.`package`}"
            try {
                val resolved = runCatching { intent.resolveActivity(context.packageManager)?.flattenToShortString() }.getOrNull()
                launch(intent)
                attempts.add("target=$target resolved=${resolved ?: "unknown"} result=launchRequested")
                saveAttempts(context, page, attempts)
                return true // A successful launch request is not proof the OEM leaves the page open.
            } catch (error: android.content.ActivityNotFoundException) {
                attempts.add("target=$target result=missing")
            } catch (error: SecurityException) {
                attempts.add("target=$target result=permissionDenied")
            }
        }
        saveAttempts(context, page, attempts)
        return false
    }

    private fun saveAttempts(context: Context, page: Page, attempts: List<String>) {
        val text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()) +
            " page=${page.name}\n" + attempts.joinToString("\n")
        context.getSharedPreferences("android_settings_diagnostic", Context.MODE_PRIVATE).edit()
            .putString("last_attempt", text).apply()
    }

    fun report(context: Context): String = context.getSharedPreferences("android_settings_diagnostic", Context.MODE_PRIVATE)
        .getString("last_attempt", null) ?: "No native Android settings launch attempted"
}
