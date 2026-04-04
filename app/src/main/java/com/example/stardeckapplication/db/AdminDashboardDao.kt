package com.example.stardeckapplication.db

class AdminDashboardDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val achievementDao by lazy { AchievementDao(dbHelper) }
    private val userDao by lazy { UserDao(dbHelper) }

    data class SummarySnapshot(
        val totalUsers: Int,
        val disabledUsers: Int,
        val premiumUsers: Int,
        val totalDecks: Int,
        val hiddenDecks: Int,
        val totalCards: Int,
        val totalStudySessions: Int,
        val openReports: Int,
        val activeThisMonth: Int,
        val inactiveThisMonth: Int,
        val configuredAchievements: Int,
        val unlockedAchievements: Int
    )

    data class BreakdownRow(
        val label: String,
        val count: Int
    )

    fun getSummarySnapshot(): SummarySnapshot {
        achievementDao.ensureDefaultsInserted()
        return SummarySnapshot(
            totalUsers = countInt(
                """
                SELECT COUNT(*) FROM ${DbContract.T_USERS}
                WHERE ${DbContract.U_ROLE} = ?
                  AND ${DbContract.U_STATUS} = ?
                """.trimIndent(),
                arrayOf(DbContract.ROLE_USER, DbContract.STATUS_ACTIVE)
            ),
            disabledUsers = countInt(
                """
                SELECT COUNT(*) FROM ${DbContract.T_USERS}
                WHERE ${DbContract.U_ROLE} = ?
                  AND ${DbContract.U_STATUS} = ?
                """.trimIndent(),
                arrayOf(DbContract.ROLE_USER, DbContract.STATUS_DISABLED)
            ),
            premiumUsers = countInt(
                """
                SELECT COUNT(*) FROM ${DbContract.T_USERS}
                WHERE ${DbContract.U_ROLE} = ?
                  AND ${DbContract.U_STATUS} = ?
                  AND COALESCE(${DbContract.U_IS_PREMIUM_USER}, 0) = 1
                """.trimIndent(),
                arrayOf(DbContract.ROLE_USER, DbContract.STATUS_ACTIVE)
            ),
            totalDecks = countInt(
                "SELECT COUNT(*) FROM ${DbContract.T_DECKS}",
                null
            ),
            hiddenDecks = countInt(
                """
                SELECT COUNT(*) FROM ${DbContract.T_DECKS}
                WHERE ${DbContract.D_STATUS} = ?
                """.trimIndent(),
                arrayOf(DbContract.DECK_HIDDEN)
            ),
            totalCards = countInt(
                "SELECT COUNT(*) FROM ${DbContract.T_CARDS}",
                null
            ),
            totalStudySessions = countInt(
                "SELECT COUNT(*) FROM ${DbContract.T_STUDY_SESSIONS}",
                null
            ),
            openReports = countInt(
                """
                SELECT COUNT(*) FROM ${DbContract.T_REPORTS}
                WHERE ${DbContract.R_STATUS} = ?
                """.trimIndent(),
                arrayOf(DbContract.REPORT_OPEN)
            ),
            activeThisMonth = userDao.countMonthlyActiveUsers(),
            inactiveThisMonth = userDao.countMonthlyInactiveUsers(),
            configuredAchievements = countInt(
                "SELECT COUNT(*) FROM ${DbContract.T_ACHIEVEMENTS}",
                null
            ),
            unlockedAchievements = countInt(
                "SELECT COUNT(*) FROM ${DbContract.T_USER_ACHIEVEMENTS}",
                null
            )
        )
    }

    fun getTopCategories(limit: Int = 5): List<BreakdownRow> {
        val rows = mutableListOf<BreakdownRow>()

        readable.rawQuery(
            """
            SELECT c.${DbContract.CAT_NAME}, COUNT(d.${DbContract.D_ID}) AS cnt
            FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_CATEGORIES} c
                ON c.${DbContract.CAT_ID} = d.${DbContract.D_CATEGORY_ID}
            GROUP BY c.${DbContract.CAT_ID}, c.${DbContract.CAT_NAME}
            ORDER BY cnt DESC, c.${DbContract.CAT_NAME} ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                rows += BreakdownRow(
                    label = c.getString(0),
                    count = c.getInt(1)
                )
            }
        }

        val uncategorized = countInt(
            """
            SELECT COUNT(*) FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_CATEGORY_ID} IS NULL
            """.trimIndent(),
            null
        )
        if (uncategorized > 0) rows += BreakdownRow("Uncategorized", uncategorized)

        return rows
            .sortedWith(compareByDescending<BreakdownRow> { it.count }.thenBy { it.label.lowercase() })
            .take(limit)
    }

    fun getTopLanguages(limit: Int = 5): List<BreakdownRow> {
        val rows = mutableListOf<BreakdownRow>()

        readable.rawQuery(
            """
            SELECT l.${DbContract.LANG_NAME}, COUNT(d.${DbContract.D_ID}) AS cnt
            FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_LANGUAGES} l
                ON l.${DbContract.LANG_ID} = d.${DbContract.D_LANGUAGE_ID}
            GROUP BY l.${DbContract.LANG_ID}, l.${DbContract.LANG_NAME}
            ORDER BY cnt DESC, l.${DbContract.LANG_NAME} ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                rows += BreakdownRow(
                    label = c.getString(0),
                    count = c.getInt(1)
                )
            }
        }

        val noLanguage = countInt(
            """
            SELECT COUNT(*) FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_LANGUAGE_ID} IS NULL
            """.trimIndent(),
            null
        )
        if (noLanguage > 0) rows += BreakdownRow("No Language", noLanguage)

        return rows
            .sortedWith(compareByDescending<BreakdownRow> { it.count }.thenBy { it.label.lowercase() })
            .take(limit)
    }

    fun getTopSubjects(limit: Int = 5): List<BreakdownRow> {
        val rows = mutableListOf<BreakdownRow>()

        readable.rawQuery(
            """
            SELECT s.${DbContract.SUBJ_NAME}, COUNT(d.${DbContract.D_ID}) AS cnt
            FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_SUBJECTS} s
                ON s.${DbContract.SUBJ_ID} = d.${DbContract.D_SUBJECT_ID}
            GROUP BY s.${DbContract.SUBJ_ID}, s.${DbContract.SUBJ_NAME}
            ORDER BY cnt DESC, s.${DbContract.SUBJ_NAME} ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                rows += BreakdownRow(
                    label = c.getString(0),
                    count = c.getInt(1)
                )
            }
        }

        val noSubject = countInt(
            """
            SELECT COUNT(*) FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_SUBJECT_ID} IS NULL
            """.trimIndent(),
            null
        )
        if (noSubject > 0) rows += BreakdownRow("No Subject", noSubject)

        return rows
            .sortedWith(compareByDescending<BreakdownRow> { it.count }.thenBy { it.label.lowercase() })
            .take(limit)
    }

    private fun countInt(sql: String, args: Array<String>?): Int {
        readable.rawQuery(sql, args).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}