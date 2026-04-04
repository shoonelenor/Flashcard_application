package com.example.stardeckapplication.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    fun applySavedTheme(context: Context) {
        when (ThemePrefs.getThemeMode(context)) {
            ThemePrefs.LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    fun saveAndApplyTheme(context: Context, mode: String) {
        ThemePrefs.setThemeMode(context, mode)
        applySavedTheme(context)
    }
}