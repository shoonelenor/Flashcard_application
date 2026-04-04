package com.example.stardeckapplication.util

import com.example.stardeckapplication.db.AchievementDao
import com.example.stardeckapplication.db.StarDeckDbHelper

class AchievementSyncHelper(dbHelper: StarDeckDbHelper) {

    private val achievementDao by lazy { AchievementDao(dbHelper) }

    fun syncForUser(userId: Long): Int {
        return runCatching {
            achievementDao.ensureDefaultsInserted()
            achievementDao.syncUserAchievements(userId)
        }.getOrDefault(0)
    }
}