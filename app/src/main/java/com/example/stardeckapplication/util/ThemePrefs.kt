package com.example.stardeckapplication.util

import android.content.Context

object ThemePrefs {
    private const val PREF_NAME = "stardeck_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    const val DARK = "dark"
    const val LIGHT = "light"

    fun getThemeMode(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, DARK) ?: DARK
    }

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
    }
}