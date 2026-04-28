package com.example.stardeckapplication.db

import android.database.sqlite.SQLiteDatabase

/**
 * Read-only queries that power the Statistics screens
 * for both Admin and Manager roles.
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
        val topDecksByCards: List<Pair<String, Int>>,       // title → card count
        val sessionsByDay: List<Pair<String, Int>>          // date label → count
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

    // ── Admin ────────────────────────────────────────────────────────────────

    fun getAdminStats(): AdminStats {
        val totalUsers   = scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.UROLE}='${DbContract.ROLE_USER}'")
        val activeUsers  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.UROLE}='${DbContract.ROLE_USER}' AND ${DbContract.USTATUS}='${DbContract.STATUS_ACTIVE}'")
        val disabledUsers= scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.USTATUS}='${DbContract.STATUS_DISABLED}'")
        val premiumUsers = scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.UISPREMIUMUSER}=1")
        val managerUsers = scalarInt("SELECT COUNT(*) FROM ${DbContract.TUSERS} WHERE ${DbContract.UROLE}='${DbContract.ROLE_MANAGER}'")

        val totalDecks   = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS}")
        val activeDecks  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DSTATUS}='${DbContract.DECK_ACTIVE}'")
        val hiddenDecks  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DSTATUS}='${DbContract.DECK_HIDDEN}'")
        val publicDecks  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DISPUBLIC}=1")
        val premiumDecks = scalarInt("SELECT COUNT(*) FROM ${DbContract.TDECKS} WHERE ${DbContract.DISPREMIUM}=1")

        val totalCards   = scalarInt("SELECT COUNT(*) FROM ${DbContract.TCARDS}")

        val totalSessions  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS}")
        val knownSessions  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS} WHERE ${DbContract.SRESULT}='${DbContract.RESULT_KNOWN}'")
        val hardSessions   = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS} WHERE ${DbContract.SRESULT}='${DbContract.RESULT_HARD}'")

        val openReports     = scalarInt("SELECT COUNT(*) FROM ${DbContract.TREPORTS} WHERE ${DbContract.RSTATUS}='${DbContract.REPORT_OPEN}'")
        val resolvedReports = scalarInt("SELECT COUNT(*) FROM ${DbContract.TREPORTS} WHERE ${DbContract.RSTATUS}='${DbContract.REPORT_RESOLVED}'")

        val topDecks = topDecksByCards(5)
        val sessionDays = sessionsLast7Days()

        return AdminStats(
            totalUsers, activeUsers, disabledUsers, premiumUsers, managerUsers,
            totalDecks, activeDecks, hiddenDecks, publicDecks, premiumDecks,
            totalCards,
            totalSessions, knownSessions, hardSessions,
            openReports, resolvedReports,
            topDecks, sessionDays
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

        val totalSessions  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS}")
        val knownSessions  = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS} WHERE ${DbContract.SRESULT}='${DbContract.RESULT_KNOWN}'")
        val hardSessions   = scalarInt("SELECT COUNT(*) FROM ${DbContract.TSTUDYSESSIONS} WHERE ${DbContract.SRESULT}='${DbContract.RESULT_HARD}'")

        val openReports     = scalarInt("SELECT COUNT(*) FROM ${DbContract.TREPORTS} WHERE ${DbContract.RSTATUS}='${DbContract.REPORT_OPEN}'")
        val resolvedReports = scalarInt("SELECT COUNT(*) FROM ${DbContract.TREPORTS} WHERE ${DbContract.RSTATUS}='${DbContract.REPORT_RESOLVED}'")

        val topDecks = topDecksByCards(5)
        val sessionDays = sessionsLast7Days()

        return ManagerStats(
            totalDecks, activeDecks, hiddenDecks, publicDecks, premiumDecks,
            totalCards,
            totalSessions, knownSessions, hardSessions,
            openReports, resolvedReports,
            topDecks, sessionDays
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

    /** Returns ("Mon", count), ("Tue", count) ... for the last 7 days */
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

        // Build a map day-string → count
        val map = mutableMapOf<String, Int>()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                map[cursor.getString(0)] = cursor.getInt(1)
            }
        }

        // Fill all 7 days (including zeros)
        val result = mutableListOf<Pair<String, Int>>()
        val cal = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val labelFmt = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        cal.add(java.util.Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            val key   = sdf.format(cal.time)
            val label = labelFmt.format(cal.time)
            result += label to (map[key] ?: 0)
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private fun scalarInt(sql: String): Int {
        db.rawQuery(sql, null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}
