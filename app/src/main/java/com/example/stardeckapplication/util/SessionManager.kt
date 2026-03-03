package com.example.stardeckapplication.util

import android.content.Context
import com.example.stardeckapplication.model.UserSession

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("stardeck_session", Context.MODE_PRIVATE)

    fun save(session: UserSession) {
        prefs.edit()
            .putLong("id", session.id)
            .putString("name", session.name)
            .putString("email", session.email)
            .putString("role", session.role)
            .putString("status", session.status)
            .putBoolean("force_pw", session.forcePasswordChange)
            .apply()
    }

    fun load(): UserSession? {
        val id = prefs.getLong("id", -1L)
        if (id <= 0) return null
        return UserSession(
            id = id,
            name = prefs.getString("name", "") ?: "",
            email = prefs.getString("email", "") ?: "",
            role = prefs.getString("role", "") ?: "",
            status = prefs.getString("status", "active") ?: "active",
            forcePasswordChange = prefs.getBoolean("force_pw", false)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}