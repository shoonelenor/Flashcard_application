package com.example.stardeckapplication

import android.app.Application
import com.example.stardeckapplication.util.ThemeManager

class StarDeckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applySavedTheme(this)
    }
}