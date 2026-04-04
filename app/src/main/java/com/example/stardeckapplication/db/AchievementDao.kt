package com.example.stardeckapplication.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

class AchievementDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase
    private val statsDao by lazy { StatsDao(dbHelper) }

    data class MetricOption(
        val key: String,
        val label: String,
        val hint: String
    )

    data class AdminAchievementRow(
        val id: Long,
        val title: String,
        val description: String?,
        val metricKey: String,
        val metricLabel: String,
        val targetValue: Int,
        val isActive: Boolean,
        val sortOrder: Int,
        val createdAt: Long,
        val unlockCount: Int
    )

    data class UserAchievementRow(
        val id: Long,
        val title: String,
        val description: String?,
        val metricKey: String,
        val metricLabel: String,
        val targetValue: Int,
        val currentValue: Int,
        val isUnlocked: Boolean,
        val unlockedAt: Long?
    )

    private data class MetricsSnapshot(
        val deckCount: Int,
        val cardCount: Int,
        val totalStudy: Int,
        val todayStudy: Int,
        val streakDays: Int
    )

    fun ensureDefaultsInserted() {
        readable.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.TACHIEVEMENTS}",
            null
        ).use { c ->
            if (c.moveToFirst() && c.getInt(0) > 0) return
        }

        val defaults = listOf(
            DefaultAchievement("First Deck", "Create your first deck.", DbContract.ACH_METRIC_DECKS_CREATED, 1, 10),
            DefaultAchievement("First Study", "Complete your first study review.", DbContract.ACH_METRIC_TOTAL_STUDY, 1, 20),
            DefaultAchievement("Card Creator", "Create 25 cards.", DbContract.ACH_METRIC_CARDS_CREATED, 25, 30),
            DefaultAchievement("Consistent Learner", "Maintain a 3-day study streak.", DbContract.ACH_METRIC_STREAK_DAYS, 3, 40),
            DefaultAchievement("Week Streak", "Maintain a 7-day study streak.", DbContract.ACH_METRIC_STREAK_DAYS, 7, 50),
            DefaultAchievement("Study 50", "Complete 50 study reviews in total.", DbContract.ACH_METRIC_TOTAL_STUDY, 50, 60),
            DefaultAchievement("Study 200", "Complete 200 study reviews in total.", DbContract.ACH_METRIC_TOTAL_STUDY, 200, 70),
            DefaultAchievement("Daily Push", "Complete 20 study reviews in one day.", DbContract.ACH_METRIC_TODAY_STUDY, 20, 80)
        )

        defaults.forEach { def ->
            val cv = ContentValues().apply {
                put(DbContract.A_TITLE, def.title)
                put(DbContract.A_DESCRIPTION, def.description)
                put(DbContract.A_METRIC_KEY, def.metricKey)
                put(DbContract.A_TARGET_VALUE, def.targetValue)
                put(DbContract.A_IS_ACTIVE, 1)
                put(DbContract.A_SORT_ORDER, def.sortOrder)
                put(DbContract.A_CREATED_AT, System.currentTimeMillis())
            }
            writable.insertWithOnConflict(
                DbContract.T_ACHIEVEMENTS,
                null,
                cv,
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    fun getMetricOptions(): List<MetricOption> = metricOptions

    fun getMetricLabel(metricKey: String): String {
        return metricOptions.firstOrNull { it.key == metricKey }?.label ?: metricKey
    }

    fun adminGetAllAchievements(): List<AdminAchievementRow> {
        ensureDefaultsInserted()

        val out = mutableListOf<AdminAchievementRow>()
        readable.rawQuery(
            """
            SELECT
                a.${DbContract.A_ID},
                a.${DbContract.A_TITLE},
                a.${DbContract.A_DESCRIPTION},
                a.${DbContract.A_METRIC_KEY},
                a.${DbContract.A_TARGET_VALUE},
                COALESCE(a.${DbContract.A_IS_ACTIVE}, 1),
                COALESCE(a.${DbContract.A_SORT_ORDER}, 0),
                a.${DbContract.A_CREATED_AT},
                COUNT(ua.${DbContract.UA_USER_ID}) AS unlock_count
            FROM ${DbContract.T_ACHIEVEMENTS} a
            LEFT JOIN ${DbContract.T_USER_ACHIEVEMENTS} ua
                ON ua.${DbContract.UA_ACHIEVEMENT_ID} = a.${DbContract.A_ID}
            GROUP BY
                a.${DbContract.A_ID},
                a.${DbContract.A_TITLE},
                a.${DbContract.A_DESCRIPTION},
                a.${DbContract.A_METRIC_KEY},
                a.${DbContract.A_TARGET_VALUE},
                a.${DbContract.A_IS_ACTIVE},
                a.${DbContract.A_SORT_ORDER},
                a.${DbContract.A_CREATED_AT}
            ORDER BY
                a.${DbContract.A_IS_ACTIVE} DESC,
                a.${DbContract.A_SORT_ORDER} ASC,
                a.${DbContract.A_TITLE} ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val metricKey = c.getString(3)
                out += AdminAchievementRow(
                    id = c.getLong(0),
                    title = c.getString(1),
                    description = c.getString(2),
                    metricKey = metricKey,
                    metricLabel = getMetricLabel(metricKey),
                    targetValue = c.getInt(4),
                    isActive = c.getInt(5) == 1,
                    sortOrder = c.getInt(6),
                    createdAt = c.getLong(7),
                    unlockCount = c.getInt(8)
                )
            }
        }
        return out
    }

    fun getUserAchievements(userId: Long): List<UserAchievementRow> {
        ensureDefaultsInserted()
        syncUserAchievements(userId)

        val metrics = getMetricsSnapshot(userId)
        val out = mutableListOf<UserAchievementRow>()

        readable.rawQuery(
            """
            SELECT
                a.${DbContract.A_ID},
                a.${DbContract.A_TITLE},
                a.${DbContract.A_DESCRIPTION},
                a.${DbContract.A_METRIC_KEY},
                a.${DbContract.A_TARGET_VALUE},
                ua.${DbContract.UA_UNLOCKED_AT}
            FROM ${DbContract.T_ACHIEVEMENTS} a
            LEFT JOIN ${DbContract.T_USER_ACHIEVEMENTS} ua
                ON ua.${DbContract.UA_ACHIEVEMENT_ID} = a.${DbContract.A_ID}
               AND ua.${DbContract.UA_USER_ID} = ?
            WHERE a.${DbContract.A_IS_ACTIVE} = 1
            ORDER BY a.${DbContract.A_SORT_ORDER} ASC, a.${DbContract.A_TITLE} ASC
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val metricKey = c.getString(3)
                out += UserAchievementRow(
                    id = c.getLong(0),
                    title = c.getString(1),
                    description = c.getString(2),
                    metricKey = metricKey,
                    metricLabel = getMetricLabel(metricKey),
                    targetValue = c.getInt(4),
                    currentValue = currentFor(metricKey, metrics),
                    isUnlocked = !c.isNull(5),
                    unlockedAt = if (c.isNull(5)) null else c.getLong(5)
                )
            }
        }

        return out
    }

    fun syncUserAchievements(userId: Long): Int {
        ensureDefaultsInserted()

        val metrics = getMetricsSnapshot(userId)
        var inserted = 0

        readable.rawQuery(
            """
            SELECT ${DbContract.A_ID}, ${DbContract.A_METRIC_KEY}, ${DbContract.A_TARGET_VALUE}
            FROM ${DbContract.T_ACHIEVEMENTS}
            WHERE ${DbContract.A_IS_ACTIVE} = 1
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val achievementId = c.getLong(0)
                val metricKey = c.getString(1)
                val targetValue = c.getInt(2)

                if (currentFor(metricKey, metrics) >= targetValue) {
                    val cv = ContentValues().apply {
                        put(DbContract.UA_USER_ID, userId)
                        put(DbContract.UA_ACHIEVEMENT_ID, achievementId)
                        put(DbContract.UA_UNLOCKED_AT, System.currentTimeMillis())
                    }

                    val rowId = writable.insertWithOnConflict(
                        DbContract.T_USER_ACHIEVEMENTS,
                        null,
                        cv,
                        SQLiteDatabase.CONFLICT_IGNORE
                    )
                    if (rowId != -1L) inserted++
                }
            }
        }

        return inserted
    }

    fun getNextSortOrder(): Int {
        ensureDefaultsInserted()
        readable.rawQuery(
            "SELECT COALESCE(MAX(${DbContract.A_SORT_ORDER}), 0) + 10 FROM ${DbContract.T_ACHIEVEMENTS}",
            null
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 10
    }

    fun isTitleTaken(title: String, excludeId: Long? = null): Boolean {
        val clean = title.trim()
        if (clean.isBlank()) return false

        val sql = buildString {
            append("SELECT 1 FROM ${DbContract.T_ACHIEVEMENTS} WHERE ${DbContract.A_TITLE} = ? COLLATE NOCASE")
            if (excludeId != null) append(" AND ${DbContract.A_ID} <> ?")
            append(" LIMIT 1")
        }

        val args = if (excludeId != null) arrayOf(clean, excludeId.toString()) else arrayOf(clean)

        readable.rawQuery(sql, args).use { c -> return c.moveToFirst() }
    }

    fun createAchievement(
        title: String,
        description: String?,
        metricKey: String,
        targetValue: Int,
        isActive: Boolean,
        sortOrder: Int
    ): Long {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotBlank()) { "Achievement title is required." }
        require(targetValue > 0) { "Target value must be greater than zero." }

        val cv = ContentValues().apply {
            put(DbContract.A_TITLE, cleanTitle)
            put(DbContract.A_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.A_METRIC_KEY, metricKey)
            put(DbContract.A_TARGET_VALUE, targetValue)
            put(DbContract.A_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.A_SORT_ORDER, sortOrder)
            put(DbContract.A_CREATED_AT, System.currentTimeMillis())
        }

        return writable.insertOrThrow(DbContract.T_ACHIEVEMENTS, null, cv)
    }

    fun updateAchievement(
        achievementId: Long,
        title: String,
        description: String?,
        metricKey: String,
        targetValue: Int,
        isActive: Boolean,
        sortOrder: Int
    ): Int {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotBlank()) { "Achievement title is required." }
        require(targetValue > 0) { "Target value must be greater than zero." }

        val cv = ContentValues().apply {
            put(DbContract.A_TITLE, cleanTitle)
            put(DbContract.A_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.A_METRIC_KEY, metricKey)
            put(DbContract.A_TARGET_VALUE, targetValue)
            put(DbContract.A_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.A_SORT_ORDER, sortOrder)
        }

        return writable.update(
            DbContract.T_ACHIEVEMENTS,
            cv,
            "${DbContract.A_ID} = ?",
            arrayOf(achievementId.toString())
        )
    }

    fun setAchievementActive(achievementId: Long, isActive: Boolean): Int {
        val cv = ContentValues().apply {
            put(DbContract.A_IS_ACTIVE, if (isActive) 1 else 0)
        }
        return writable.update(
            DbContract.T_ACHIEVEMENTS,
            cv,
            "${DbContract.A_ID} = ?",
            arrayOf(achievementId.toString())
        )
    }

    fun deleteAchievement(achievementId: Long): Int {
        if (getUnlockCount(achievementId) > 0) return 0
        return writable.delete(
            DbContract.T_ACHIEVEMENTS,
            "${DbContract.A_ID} = ?",
            arrayOf(achievementId.toString())
        )
    }

    private fun getUnlockCount(achievementId: Long): Int {
        readable.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_USER_ACHIEVEMENTS} WHERE ${DbContract.UA_ACHIEVEMENT_ID} = ?",
            arrayOf(achievementId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    private fun getMetricsSnapshot(userId: Long): MetricsSnapshot {
        return MetricsSnapshot(
            deckCount = getDeckCount(userId),
            cardCount = getCardCount(userId),
            totalStudy = statsDao.getTotalStudyCount(userId),
            todayStudy = statsDao.getTodayStudyCount(userId),
            streakDays = statsDao.getStudyStreakDays(userId)
        )
    }

    private fun currentFor(metricKey: String, metrics: MetricsSnapshot): Int {
        return when (metricKey) {
            DbContract.ACH_METRIC_DECKS_CREATED -> metrics.deckCount
            DbContract.ACH_METRIC_CARDS_CREATED -> metrics.cardCount
            DbContract.ACH_METRIC_TOTAL_STUDY -> metrics.totalStudy
            DbContract.ACH_METRIC_TODAY_STUDY -> metrics.todayStudy
            DbContract.ACH_METRIC_STREAK_DAYS -> metrics.streakDays
            else -> 0
        }
    }

    private fun getDeckCount(userId: Long): Int {
        readable.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_OWNER_USER_ID} = ?",
            arrayOf(userId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    private fun getCardCount(userId: Long): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_CARDS} c
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID} = c.${DbContract.C_DECK_ID}
            WHERE d.${DbContract.D_OWNER_USER_ID} = ?
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    private data class DefaultAchievement(
        val title: String,
        val description: String,
        val metricKey: String,
        val targetValue: Int,
        val sortOrder: Int
    )

    private companion object {
        val metricOptions = listOf(
            MetricOption(DbContract.ACH_METRIC_DECKS_CREATED, "Decks Created", "Unlock based on total decks created by the user."),
            MetricOption(DbContract.ACH_METRIC_CARDS_CREATED, "Cards Created", "Unlock based on total cards created by the user."),
            MetricOption(DbContract.ACH_METRIC_TOTAL_STUDY, "Total Study Reviews", "Unlock based on total study reviews completed."),
            MetricOption(DbContract.ACH_METRIC_TODAY_STUDY, "Today Study Reviews", "Unlock based on reviews completed today."),
            MetricOption(DbContract.ACH_METRIC_STREAK_DAYS, "Study Streak Days", "Unlock based on continuous study streak days.")
        )
    }
}
