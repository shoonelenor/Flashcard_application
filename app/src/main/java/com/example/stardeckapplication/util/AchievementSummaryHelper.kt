package com.example.stardeckapplication.util

import com.example.stardeckapplication.db.AchievementDao
import com.example.stardeckapplication.db.StarDeckDbHelper

class AchievementSummaryHelper(dbHelper: StarDeckDbHelper) {

    private val achievementDao by lazy { AchievementDao(dbHelper) }

    data class Summary(
        val unlockedCount: Int,
        val totalCount: Int,
        val nextTitle: String?,
        val nextProgressText: String?,
        val hasAny: Boolean
    )

    fun getSummary(userId: Long): Summary {
        achievementDao.ensureDefaultsInserted()
        achievementDao.syncUserAchievements(userId)
        val rows = achievementDao.getUserAchievements(userId)

        if (rows.isEmpty()) {
            return Summary(
                unlockedCount = 0,
                totalCount = 0,
                nextTitle = null,
                nextProgressText = null,
                hasAny = false
            )
        }

        val unlockedCount = rows.count { it.isUnlocked }
        val nextLocked = rows.firstOrNull { !it.isUnlocked }

        return Summary(
            unlockedCount = unlockedCount,
            totalCount = rows.size,
            nextTitle = nextLocked?.title,
            nextProgressText = nextLocked?.let { "${it.currentValue} / ${it.targetValue}" },
            hasAny = true
        )
    }
}