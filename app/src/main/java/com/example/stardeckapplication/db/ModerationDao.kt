package com.example.stardeckapplication.db

import android.content.ContentValues

class ModerationDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    // ══════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ══════════════════════════════════════════════════════════════════════

    data class ManagerDeckRow(
        val deckId: Long,
        val ownerUserId: Long,
        val ownerName: String,
        val ownerEmail: String,
        val title: String,
        val description: String?,
        val status: String,
        val isPremium: Boolean,
        val isPublic: Boolean,
        val cardCount: Int
    )

    data class ReportRow(
        val reportId: Long,
        val deckId: Long,
        val deckTitle: String,
        val deckStatus: String,
        val ownerName: String,
        val ownerEmail: String,
        val reporterName: String,
        val reporterEmail: String,
        val reasonId: Long?,
        val reasonLabel: String,
        val reasonDescription: String?,
        val details: String?,
        val status: String,
        val createdAt: Long
    )

    // ══════════════════════════════════════════════════════════════════════
    //  DECK MODERATION
    // ══════════════════════════════════════════════════════════════════════

    fun managerGetAllDecks(): List<ManagerDeckRow> {
        val out = mutableListOf<ManagerDeckRow>()

        readable.rawQuery(
            """
            SELECT
                d.${DbContract.D_ID},
                d.${DbContract.D_OWNER_USER_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                d.${DbContract.D_STATUS},
                COALESCE(d.${DbContract.D_IS_PREMIUM}, 0),
                COALESCE(d.${DbContract.D_IS_PUBLIC}, 0),
                COUNT(c.${DbContract.C_ID}) AS card_count
            FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID} = d.${DbContract.D_OWNER_USER_ID}
            LEFT JOIN ${DbContract.T_CARDS} c
                ON c.${DbContract.C_DECK_ID} = d.${DbContract.D_ID}
            GROUP BY
                d.${DbContract.D_ID},
                d.${DbContract.D_OWNER_USER_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                d.${DbContract.D_STATUS},
                d.${DbContract.D_IS_PREMIUM},
                d.${DbContract.D_IS_PUBLIC}
            ORDER BY d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += ManagerDeckRow(
                    deckId = c.getLong(0),
                    ownerUserId = c.getLong(1),
                    ownerName = c.getString(2),
                    ownerEmail = c.getString(3),
                    title = c.getString(4),
                    description = c.getString(5),
                    status = c.getString(6),
                    isPremium = c.getInt(7) == 1,
                    isPublic = c.getInt(8) == 1,
                    cardCount = c.getInt(9)
                )
            }
        }

        return out
    }

    fun managerSetDeckStatus(deckId: Long, newStatus: String): Int {
        val cv = ContentValues().apply {
            put(DbContract.D_STATUS, newStatus)
        }
        return writable.update(
            DbContract.T_DECKS,
            cv,
            "${DbContract.D_ID} = ?",
            arrayOf(deckId.toString())
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REPORT MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════

    fun managerGetReports(): List<ReportRow> {
        val out = mutableListOf<ReportRow>()

        readable.rawQuery(
            """
            SELECT
                r.${DbContract.R_ID},
                r.${DbContract.R_DECK_ID},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_STATUS},
                owner.${DbContract.U_NAME},
                owner.${DbContract.U_EMAIL},
                reporter.${DbContract.U_NAME},
                reporter.${DbContract.U_EMAIL},
                r.${DbContract.R_REASON_ID},
                COALESCE(rr.${DbContract.RR_NAME}, r.${DbContract.R_REASON}) AS reason_label,
                rr.${DbContract.RR_DESCRIPTION},
                r.${DbContract.R_DETAILS},
                r.${DbContract.R_STATUS},
                r.${DbContract.R_CREATED_AT}
            FROM ${DbContract.T_REPORTS} r
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID} = r.${DbContract.R_DECK_ID}
            INNER JOIN ${DbContract.T_USERS} owner
                ON owner.${DbContract.U_ID} = d.${DbContract.D_OWNER_USER_ID}
            INNER JOIN ${DbContract.T_USERS} reporter
                ON reporter.${DbContract.U_ID} = r.${DbContract.R_REPORTER_USER_ID}
            LEFT JOIN ${DbContract.T_REPORT_REASONS} rr
                ON rr.${DbContract.RR_ID} = r.${DbContract.R_REASON_ID}
            ORDER BY
                CASE
                    WHEN r.${DbContract.R_STATUS} = '${DbContract.REPORT_OPEN}' THEN 0
                    ELSE 1
                END,
                r.${DbContract.R_CREATED_AT} DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += ReportRow(
                    reportId = c.getLong(0),
                    deckId = c.getLong(1),
                    deckTitle = c.getString(2),
                    deckStatus = c.getString(3),
                    ownerName = c.getString(4),
                    ownerEmail = c.getString(5),
                    reporterName = c.getString(6),
                    reporterEmail = c.getString(7),
                    reasonId = if (c.isNull(8)) null else c.getLong(8),
                    reasonLabel = c.getString(9),
                    reasonDescription = c.getString(10),
                    details = c.getString(11),
                    status = c.getString(12),
                    createdAt = c.getLong(13)
                )
            }
        }

        return out
    }

    fun managerSetReportStatus(reportId: Long, newStatus: String): Int {
        val cv = ContentValues().apply {
            put(DbContract.R_STATUS, newStatus)
        }

        return writable.update(
            DbContract.T_REPORTS,
            cv,
            "${DbContract.R_ID} = ?",
            arrayOf(reportId.toString())
        )
    }

    fun managerResolveReport(reportId: Long): Int {
        return managerSetReportStatus(reportId, DbContract.REPORT_RESOLVED)
    }

    fun managerReopenReport(reportId: Long): Int {
        return managerSetReportStatus(reportId, DbContract.REPORT_OPEN)
    }
}