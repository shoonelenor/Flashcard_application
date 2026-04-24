package com.example.stardeckapplication.db

class StatsDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase

    // ══════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ══════════════════════════════════════════════════════════════════════

    data class RecentDeckRow(
        val deckId        : Long,
        val title         : String,
        val lastStudiedAt : Long
    )

    data class MostStudiedDeckRow(
        val deckId     : Long,
        val title      : String,
        val studyCount : Int
    )

    // ══════════════════════════════════════════════════════════════════════
    //  STUDY STATS  (home screen)
    // ══════════════════════════════════════════════════════════════════════

    fun getTodayStudyCount(userId: Long): Int {
        val zone  = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end   = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM   ${DbContract.T_STUDY_SESSIONS}
            WHERE  ${DbContract.S_USER_ID}    = ?
              AND  ${DbContract.S_CREATED_AT} >= ?
              AND  ${DbContract.S_CREATED_AT}  < ?
            """.trimIndent(),
            arrayOf(userId.toString(), start.toString(), end.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun getStudyStreakDays(userId: Long): Int {
        val zone  = java.time.ZoneId.systemDefault()
        val times = mutableListOf<Long>()
        readable.rawQuery(
            """
            SELECT ${DbContract.S_CREATED_AT}
            FROM   ${DbContract.T_STUDY_SESSIONS}
            WHERE  ${DbContract.S_USER_ID} = ?
            ORDER  BY ${DbContract.S_CREATED_AT} DESC
            LIMIT  800
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c -> while (c.moveToNext()) times += c.getLong(0) }

        if (times.isEmpty()) return 0

        val uniqueDays = mutableListOf<Long>()
        var lastDay: Long? = null
        for (ms in times) {
            val day = java.time.Instant.ofEpochMilli(ms)
                .atZone(zone).toLocalDate().toEpochDay()
            if (lastDay == null || day != lastDay) { uniqueDays += day; lastDay = day }
        }

        var streak   = 1
        var expected = uniqueDays[0] - 1
        for (i in 1 until uniqueDays.size) {
            if (uniqueDays[i] == expected) { streak++; expected-- } else break
        }
        return streak
    }

    fun getTotalStudyCount(userId: Long): Int {
        readable.rawQuery(
            """
        SELECT COUNT(*)
        FROM   ${DbContract.T_STUDY_SESSIONS}
        WHERE  ${DbContract.S_USER_ID} = ?
        """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    // ══════════════════════════════════════════════════════════════════════
//  LEADERBOARD
// ══════════════════════════════════════════════════════════════════════

    data class LeaderboardRow(
        val userId     : Long,
        val name       : String,
        val email      : String,
        val streakDays : Int,
        val totalStudy : Int
    )

    /**
     * Returns all active regular users ranked by total study count descending.
     * Streak is computed in-memory the same way getStudyStreakDays() does it.
     */
    fun getLocalLeaderboard(): List<LeaderboardRow> {
        val zone = java.time.ZoneId.systemDefault()

        // 1. Get all active users
        val users = mutableListOf<Triple<Long, String, String>>() // id, name, email
        readable.rawQuery(
            """
        SELECT ${DbContract.U_ID}, ${DbContract.U_NAME}, ${DbContract.U_EMAIL}
        FROM   ${DbContract.T_USERS}
        WHERE  ${DbContract.U_ROLE}   = ?
          AND  ${DbContract.U_STATUS} = ?
        ORDER  BY ${DbContract.U_NAME} ASC
        """.trimIndent(),
            arrayOf(DbContract.ROLE_USER, DbContract.STATUS_ACTIVE)
        ).use { c ->
            while (c.moveToNext()) users += Triple(c.getLong(0), c.getString(1), c.getString(2))
        }

        if (users.isEmpty()) return emptyList()

        // 2. For each user compute totalStudy + streakDays
        val out = mutableListOf<LeaderboardRow>()

        for ((userId, name, email) in users) {
            // total study count
            val totalStudy: Int = readable.rawQuery(
                """
            SELECT COUNT(*)
            FROM   ${DbContract.T_STUDY_SESSIONS}
            WHERE  ${DbContract.S_USER_ID} = ?
            """.trimIndent(),
                arrayOf(userId.toString())
            ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

            // streak
            val times = mutableListOf<Long>()
            readable.rawQuery(
                """
            SELECT ${DbContract.S_CREATED_AT}
            FROM   ${DbContract.T_STUDY_SESSIONS}
            WHERE  ${DbContract.S_USER_ID} = ?
            ORDER  BY ${DbContract.S_CREATED_AT} DESC
            LIMIT  800
            """.trimIndent(),
                arrayOf(userId.toString())
            ).use { c -> while (c.moveToNext()) times += c.getLong(0) }

            val streakDays: Int = if (times.isEmpty()) 0 else {
                val uniqueDays = mutableListOf<Long>()
                var lastDay: Long? = null
                for (ms in times) {
                    val day = java.time.Instant.ofEpochMilli(ms)
                        .atZone(zone).toLocalDate().toEpochDay()
                    if (lastDay == null || day != lastDay) { uniqueDays += day; lastDay = day }
                }
                var streak   = 1
                var expected = uniqueDays[0] - 1
                for (i in 1 until uniqueDays.size) {
                    if (uniqueDays[i] == expected) { streak++; expected-- } else break
                }
                streak
            }

            out += LeaderboardRow(
                userId     = userId,
                name       = name,
                email      = email,
                streakDays = streakDays,
                totalStudy = totalStudy
            )
        }

        // 3. Rank by totalStudy desc, then streakDays desc
        return out.sortedWith(
            compareByDescending<LeaderboardRow> { it.totalStudy }
                .thenByDescending { it.streakDays }
        )
    }

    fun getRecentlyStudiedDeck(userId: Long): RecentDeckRow? {
        readable.rawQuery(
            """
            SELECT d.${DbContract.D_ID},
                   d.${DbContract.D_TITLE},
                   MAX(s.${DbContract.S_CREATED_AT}) AS last_at
            FROM   ${DbContract.T_STUDY_SESSIONS} s
            INNER  JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID} = s.${DbContract.S_DECK_ID}
            WHERE  s.${DbContract.S_USER_ID}       = ?
              AND  d.${DbContract.D_OWNER_USER_ID} = ?
              AND  d.${DbContract.D_STATUS}         = ?
            GROUP  BY d.${DbContract.D_ID}, d.${DbContract.D_TITLE}
            ORDER  BY last_at DESC
            LIMIT  1
            """.trimIndent(),
            arrayOf(userId.toString(), userId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            if (!c.moveToFirst()) return null
            return RecentDeckRow(c.getLong(0), c.getString(1), c.getLong(2))
        }
    }

    fun getMostStudiedDeck(userId: Long): MostStudiedDeckRow? {
        readable.rawQuery(
            """
            SELECT d.${DbContract.D_ID},
                   d.${DbContract.D_TITLE},
                   COUNT(s.${DbContract.S_ID}) AS cnt
            FROM   ${DbContract.T_STUDY_SESSIONS} s
            INNER  JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID} = s.${DbContract.S_DECK_ID}
            WHERE  s.${DbContract.S_USER_ID}       = ?
              AND  d.${DbContract.D_OWNER_USER_ID} = ?
              AND  d.${DbContract.D_STATUS}         = ?
            GROUP  BY d.${DbContract.D_ID}, d.${DbContract.D_TITLE}
            ORDER  BY cnt DESC
            LIMIT  1
            """.trimIndent(),
            arrayOf(userId.toString(), userId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            if (!c.moveToFirst()) return null
            return MostStudiedDeckRow(c.getLong(0), c.getString(1), c.getInt(2))
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ADMIN STATS
    // ══════════════════════════════════════════════════════════════════════

    fun adminCountAllUsers(): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*) FROM ${DbContract.T_USERS}
            WHERE  ${DbContract.U_ROLE}   = ?
              AND  ${DbContract.U_STATUS} = ?
            """.trimIndent(),
            arrayOf(DbContract.ROLE_USER, DbContract.STATUS_ACTIVE)
        ).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun adminCountDisabledUsers(): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*) FROM ${DbContract.T_USERS}
            WHERE  ${DbContract.U_ROLE}   = ?
              AND  ${DbContract.U_STATUS} = ?
            """.trimIndent(),
            arrayOf(DbContract.ROLE_USER, DbContract.STATUS_DISABLED)
        ).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun adminCountPremiumUsers(): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*) FROM ${DbContract.T_USERS}
            WHERE  ${DbContract.U_ROLE}           = ?
              AND  ${DbContract.U_IS_PREMIUM_USER} = 1
              AND  ${DbContract.U_STATUS}          = ?
            """.trimIndent(),
            arrayOf(DbContract.ROLE_USER, DbContract.STATUS_ACTIVE)
        ).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun adminCountAllDecks(): Int {
        readable.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_DECKS}",
            null
        ).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun adminCountHiddenDecks(): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*) FROM ${DbContract.T_DECKS}
            WHERE  ${DbContract.D_STATUS} = ?
            """.trimIndent(),
            arrayOf(DbContract.DECK_HIDDEN)
        ).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun adminCountOpenReports(): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*) FROM ${DbContract.T_REPORTS}
            WHERE  ${DbContract.R_STATUS} = ?
            """.trimIndent(),
            arrayOf(DbContract.REPORT_OPEN)
        ).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // ══════════════════════════════════════════════════════════════════════
//  7-DAY CHART DATA
// ══════════════════════════════════════════════════════════════════════

    data class DayCount(
        val label: String,   // e.g. "Mon", "Tue"
        val count: Int,
        val isToday: Boolean
    )

    fun getLast7DaysStudyCounts(userId: Long): List<DayCount> {
        val zone  = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val result = mutableListOf<DayCount>()

        for (i in 6 downTo 0) {
            val day   = today.minusDays(i.toLong())
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end   = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val count = readable.rawQuery(
                """
            SELECT COUNT(*)
            FROM   ${DbContract.T_STUDY_SESSIONS}
            WHERE  ${DbContract.S_USER_ID}    = ?
              AND  ${DbContract.S_CREATED_AT} >= ?
              AND  ${DbContract.S_CREATED_AT}  < ?
            """.trimIndent(),
                arrayOf(userId.toString(), start.toString(), end.toString())
            ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

            val label = day.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()
            )
            result.add(DayCount(label = label, count = count, isToday = i == 0))
        }
        return result
    }
}