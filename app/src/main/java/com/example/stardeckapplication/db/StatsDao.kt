package com.example.stardeckapplication.db

import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Read-only queries that power the Statistics screens
 * for both Admin and Manager roles, plus user-facing study stats.
 */
class StatsDao(private val helper: StarDeckDbHelper) {

    private val db: SQLiteDatabase get() = helper.readableDatabase

    // ── Data classes ─────────────────────────────────────────────────────────

    data class AdminStats(
        val totalUsers: Int,
        val activeUsers: Int,
        val disabledUsers: Int,
        val premiumUsers: Int,
        val managerUsers: Int,
        val totalDecks: Int,
        val activeDecks: Int,
        val hiddenDecks: Int,
        val publicDecks: Int,
        val premiumDecks: Int,
        val totalCards: Int,
        val totalSessions: Int,
        val knownSessions: Int,
        val hardSessions: Int,
        val openReports: Int,
        val resolvedReports: Int,
        val topDecksByCards: List<Pair<String, Int>>,
        val sessionsByDay: List<Pair<String, Int>>
    )

    data class ManagerStats(
        val totalDecks: Int,
        val activeDecks: Int,
        val hiddenDecks: Int,
        val publicDecks: Int,
        val premiumDecks: Int,
        val totalCards: Int,
        val totalSessions: Int,
        val knownSessions: Int,
        val hardSessions: Int,
        val openReports: Int,
        val resolvedReports: Int,
        val topDecksByCards: List<Pair<String, Int>>,
        val sessionsByDay: List<Pair<String, Int>>
    )

    /** One bar in the 7-day study chart */
    data class DayCount(
        val label: String,      // e.g. "Mon"
        val count: Int,
        val isToday: Boolean
    )

    /** Row in the "recently studied" list */
    data class RecentDeckRow(
        val deckId: Long,
        val title: String,
        val lastStudiedAt: Long   // epoch ms
    )

    /** Row in the "most studied" list */
    data class MostStudiedDeckRow(
        val deckId: Long,
        val title: String,
        val studyCount: Int
    )

    /**
     * Row in the leaderboard.
     * Email removed for privacy — never expose other users' emails in UI.
     */
    data class LeaderboardRow(
        val userId: Long,
        val name: String,
        val streakDays: Int,
        val totalStudy: Int
    )

    // ── Admin convenience scalars (used by ManagerDashboardFragment) ─────────

    fun adminCountOpenReports(): Int =
        scalarInt("SELECT COUNT(*) FROM ${DbContract.TREPORTS} WHERE ${DbContract.RSTATUS}='${DbContract.REPORT_OPEN}'")

    fun adminCountHiddenDecks(): Int =
        scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DSTATUS}='${DbContract.DECK_HIDDEN}'")

    fun adminCountAllDecks(): Int =
        scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS}")

    // ── User study stats ─────────────────────────────────────────────────────

    /** Total number of study sessions the user has ever completed */
    fun getTotalStudyCount(userId: Long): Int =
        scalarInt(
            "SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS} WHERE ${DbContract.SUSERID} = $userId"
        )

    /** Number of study sessions the user completed today */
    fun getTodayStudyCount(userId: Long): Int {
        val sql = """
            SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS}
            WHERE ${DbContract.SUSERID} = ?
              AND ${DbContract.SCREATEDAT} >= strftime('%s','now','start of day') * 1000
        """.trimIndent()
        db.rawQuery(sql, arrayOf(userId.toString())).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /**
     * Number of consecutive days (streak) the user has studied.
     *
     * FIX: Both the SQL date formatting and the Java calendar now use UTC
     * so they always agree, even in UTC+6:30 (Yangon) near midnight.
     */
    fun getStudyStreakDays(userId: Long): Int {
        val sql = """
            SELECT DISTINCT strftime('%Y-%m-%d', ${DbContract.SCREATEDAT} / 1000, 'unixepoch') AS day
            FROM ${DbContract.TSTUDYSESSIONS}
            WHERE ${DbContract.SUSERID} = ?
            ORDER BY day DESC
        """.trimIndent()
        // Use UTC for both SQL ('unixepoch' is UTC) and Java Calendar
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        var streak = 0
        db.rawQuery(sql, arrayOf(userId.toString())).use { c ->
            while (c.moveToNext()) {
                val dayStr   = c.getString(0)
                val expected = sdf.format(cal.time)
                if (dayStr == expected) {
                    streak++
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
        }
        return streak
    }

    /** Most recently studied deck for the user */
    fun getRecentlyStudiedDeck(userId: Long): RecentDeckRow? {
        val sql = """
            SELECT d.${DbContract.DID}, d.${DbContract.DTITLE}, MAX(s.${DbContract.SCREATEDAT})
            FROM ${DbContract.TSTUDYSESSIONS} s
            JOIN ${DbContract.TDECKS} d ON d.${DbContract.DID} = s.${DbContract.SDECKID}
            WHERE s.${DbContract.SUSERID} = ?
            GROUP BY d.${DbContract.DID}
            ORDER BY MAX(s.${DbContract.SCREATEDAT}) DESC
            LIMIT 1
        """.trimIndent()
        db.rawQuery(sql, arrayOf(userId.toString())).use { c ->
            if (!c.moveToFirst()) return null
            return RecentDeckRow(
                deckId        = c.getLong(0),
                title         = c.getString(1),
                lastStudiedAt = c.getLong(2)
            )
        }
    }

    /** Deck the user has studied the most (by session count) */
    fun getMostStudiedDeck(userId: Long): MostStudiedDeckRow? {
        val sql = """
            SELECT d.${DbContract.DID}, d.${DbContract.DTITLE}, COUNT(*) AS cnt
            FROM ${DbContract.TSTUDYSESSIONS} s
            JOIN ${DbContract.TDECKS} d ON d.${DbContract.DID} = s.${DbContract.SDECKID}
            WHERE s.${DbContract.SUSERID} = ?
            GROUP BY d.${DbContract.DID}
            ORDER BY cnt DESC
            LIMIT 1
        """.trimIndent()
        db.rawQuery(sql, arrayOf(userId.toString())).use { c ->
            if (!c.moveToFirst()) return null
            return MostStudiedDeckRow(
                deckId     = c.getLong(0),
                title      = c.getString(1),
                studyCount = c.getInt(2)
            )
        }
    }

    /** Study counts per day for the last 7 days for this user */
    fun getLast7DaysStudyCounts(userId: Long): List<DayCount> {
        val sql = """
            SELECT
                strftime('%Y-%m-%d', ${DbContract.SCREATEDAT} / 1000, 'unixepoch') AS day,
                COUNT(*) AS cnt
            FROM ${DbContract.TSTUDYSESSIONS}
            WHERE ${DbContract.SUSERID} = ?
              AND ${DbContract.SCREATEDAT} >= strftime('%s','now','-6 days','start of day') * 1000
            GROUP BY day
            ORDER BY day ASC
        """.trimIndent()
        val map = mutableMapOf<String, Int>()
        db.rawQuery(sql, arrayOf(userId.toString())).use { c ->
            while (c.moveToNext()) map[c.getString(0)] = c.getInt(1)
        }

        val result   = mutableListOf<DayCount>()
        // Use UTC to match SQLite 'unixepoch'
        val cal      = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val sdf      = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val labelFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val todayStr = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            val key = sdf.format(cal.time)
            result += DayCount(
                label   = labelFmt.format(cal.time),
                count   = map[key] ?: 0,
                isToday = key == todayStr
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    /**
     * Leaderboard: top 50 active users ranked by live streak, then total sessions.
     *
     * FIX 1 — Streak is now calculated LIVE from study_sessions using a correlated
     *          subquery instead of reading a stale cached column.
     * FIX 2 — Email removed from the result (privacy).
     * FIX 3 — LIMIT 50 added to avoid loading all users into memory.
     *
     * How the live streak subquery works:
     *   For each user, it counts how many consecutive UTC days (starting from today
     *   and going backwards) appear in their study_sessions.
     *   SQLite does not have a window LAG(), so we do this in two steps:
     *     Step A: get all distinct study days for this user as a numbered list
     *             (row_number via a self-join count).
     *     Step B: a day is "in a consecutive run" when
     *             (today_epoch_days - day_epoch_days) == (row_number - 1).
     *   We count rows that satisfy that condition starting from today.
     */
    fun getLocalLeaderboard(): List<LeaderboardRow> {
        val sql = """
            SELECT
                u.${DbContract.UID},
                u.${DbContract.UNAME},
                (
                    SELECT COUNT(*)
                    FROM (
                        SELECT
                            CAST(strftime('%s', day) / 86400 AS INTEGER) AS day_num,
                            (
                                SELECT COUNT(*)
                                FROM (
                                    SELECT DISTINCT
                                        strftime('%Y-%m-%d', ${DbContract.SCREATEDAT} / 1000, 'unixepoch') AS d2
                                    FROM ${DbContract.TSTUDYSESSIONS}
                                    WHERE ${DbContract.SUSERID} = u.${DbContract.UID}
                                ) AS inner2
                                WHERE inner2.d2 >= day
                            ) AS rn
                        FROM (
                            SELECT DISTINCT
                                strftime('%Y-%m-%d', ${DbContract.SCREATEDAT} / 1000, 'unixepoch') AS day
                            FROM ${DbContract.TSTUDYSESSIONS}
                            WHERE ${DbContract.SUSERID} = u.${DbContract.UID}
                        ) AS days
                    ) AS numbered
                    WHERE CAST(strftime('%s','now') / 86400 AS INTEGER) - day_num = rn - 1
                ) AS streak,
                COUNT(s.${DbContract.SID}) AS total
            FROM ${DbContract.TUSERS} u
            LEFT JOIN ${DbContract.TSTUDYSESSIONS} s
                   ON s.${DbContract.SUSERID} = u.${DbContract.UID}
            WHERE u.${DbContract.UROLE}  = '${DbContract.ROLE_USER}'
              AND u.${DbContract.USTATUS} = '${DbContract.STATUS_ACTIVE}'
            GROUP BY u.${DbContract.UID}
            ORDER BY streak DESC, total DESC
            LIMIT 50
        """.trimIndent()

        val result = mutableListOf<LeaderboardRow>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                result += LeaderboardRow(
                    userId     = c.getLong(0),
                    name       = c.getString(1),
                    streakDays = c.getInt(2),
                    totalStudy = c.getInt(3)
                )
            }
        }
        return result
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    fun getAdminStats(): AdminStats {
        val totalUsers    = scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.UROLE}='${DbContract.ROLE_USER}'")
        val activeUsers   = scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.UROLE}='${DbContract.ROLE_USER}' AND ${DbContract.USTATUS}='${DbContract.STATUS_ACTIVE}'")
        val disabledUsers = scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.USTATUS}='${DbContract.STATUS_DISABLED}'")
        val premiumUsers  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.UISPREMIUMUSER}=1")
        val managerUsers  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.UROLE}='${DbContract.ROLE_MANAGER}'")

        val totalDecks   = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS}")
        val activeDecks  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DSTATUS}='${DbContract.DECK_ACTIVE}'")
        val hiddenDecks  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DSTATUS}='${DbContract.DECK_HIDDEN}'")
        val publicDecks  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DISPUBLIC}=1")
        val premiumDecks = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DISPREMIUM}=1")

        val totalCards    = scalarInt("SELECT COUNT(*) FROM ${DbContract.TCARDS}")
        val totalSessions = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS}")
        val knownSessions = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS} WHERE ${DbContract.SRESULT}='${DbContract.RESULT_KNOWN}'")
        val hardSessions  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS} WHERE ${DbContract.SRESULT}='${DbContract.RESULT_HARD}'")

        val openReports     = scalarInt("SELECT COUNT(*) FROM ${DbContract.TREPORTS} WHERE ${DbContract.RSTATUS}='${DbContract.REPORT_OPEN}'")
        val resolvedReports = scalarInt("SELECT COUNT(*) FROM ${DbContract.TREPORTS} WHERE ${DbContract.RSTATUS}='${DbContract.REPORT_RESOLVED}'")

        return AdminStats(
            totalUsers, activeUsers, disabledUsers, premiumUsers, managerUsers,
            totalDecks, activeDecks, hiddenDecks, publicDecks, premiumDecks,
            totalCards, totalSessions, knownSessions, hardSessions,
            openReports, resolvedReports,
            topDecksByCards(5), sessionsLast7Days()
        )
    }

    // ── Manager ──────────────────────────────────────────────────────────────

    fun getManagerStats(): ManagerStats {
        val totalDecks   = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS}")
        val activeDecks  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DSTATUS}='${DbContract.DECK_ACTIVE}'")
        val hiddenDecks  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DSTATUS}='${DbContract.DECK_HIDDEN}'")
        val publicDecks  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DISPUBLIC}=1")
        val premiumDecks = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DISPREMIUM}=1")
        val totalCards   = scalarInt("SELECT COUNT(*) FROM ${DbContract.TCARDS}")

        val totalSessions = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS}")
        val knownSessions = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS} WHERE ${DbContract.SRESULT}='${DbContract.RESULT_KNOWN}'")
        val hardSessions  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS} WHERE ${DbContract.SRESULT}='${DbContract.RESULT_HARD}'")

        val openReports     = scalarInt("SELECT COUNT(*) FROM ${DbContract.TREPORTS} WHERE ${DbContract.RSTATUS}='${DbContract.REPORT_OPEN}'")
        val resolvedReports = scalarInt("SELECT COUNT(*) FROM ${DbContract.TREPORTS} WHERE ${DbContract.RSTATUS}='${DbContract.REPORT_RESOLVED}'")

        return ManagerStats(
            totalDecks, activeDecks, hiddenDecks, publicDecks, premiumDecks,
            totalCards, totalSessions, knownSessions, hardSessions,
            openReports, resolvedReports,
            topDecksByCards(5), sessionsLast7Days()
        )
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun topDecksByCards(limit: Int): List<Pair<String, Int>> {
        val sql = """
            SELECT d.${DbContract.DTITLE}, COUNT(c.${DbContract.CID}) AS card_count
            FROM ${DbContract.TDECKS} d
            LEFT JOIN ${DbContract.TCARDS} c ON c.${DbContract.CDECKID} = d.${DbContract.DID}
            GROUP BY d.${DbContract.DID}
            ORDER BY card_count DESC
            LIMIT $limit
        """.trimIndent()
        val result = mutableListOf<Pair<String, Int>>()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                result += cursor.getString(0) to cursor.getInt(1)
            }
        }
        return result
    }

    private fun sessionsLast7Days(): List<Pair<String, Int>> {
        val sql = """
            SELECT
                strftime('%Y-%m-%d', ${DbContract.SCREATEDAT} / 1000, 'unixepoch') AS day,
                COUNT(*) AS cnt
            FROM ${DbContract.TSTUDYSESSIONS}
            WHERE ${DbContract.SCREATEDAT} >= strftime('%s','now','-6 days','start of day') * 1000
            GROUP BY day
            ORDER BY day ASC
        """.trimIndent()
        val map = mutableMapOf<String, Int>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) map[c.getString(0)] = c.getInt(1)
        }
        val result   = mutableListOf<Pair<String, Int>>()
        val cal      = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val sdf      = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val labelFmt = SimpleDateFormat("EEE", Locale.getDefault())
        cal.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            val key = sdf.format(cal.time)
            result += labelFmt.format(cal.time) to (map[key] ?: 0)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private fun scalarInt(sql: String): Int {
        db.rawQuery(sql, null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}
