package com.example.documentscanner.util

import android.content.Context
import com.example.documentscanner.export.ExportQuality

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark")
}

/**
 * Small SharedPreferences wrapper for user-facing settings. Nothing here is
 * synced anywhere — it's a local prefs file like any other Android app.
 */
object AppSettings {
    private const val PREFS_NAME = "scanner_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_AUTO_CAPTURE = "auto_capture_default"
    private const val KEY_QUALITY = "default_quality"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode {
        val raw = prefs(context).getString(KEY_THEME, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
    }

    fun getAutoCaptureDefault(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CAPTURE, true)

    fun setAutoCaptureDefault(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CAPTURE, enabled).apply()
    }

    fun getDefaultQuality(context: Context): ExportQuality {
        val raw = prefs(context).getString(KEY_QUALITY, ExportQuality.HIGH.name)
        return runCatching { ExportQuality.valueOf(raw ?: ExportQuality.HIGH.name) }.getOrDefault(ExportQuality.HIGH)
    }

    fun setDefaultQuality(context: Context, quality: ExportQuality) {
        prefs(context).edit().putString(KEY_QUALITY, quality.name).apply()
    }
}
