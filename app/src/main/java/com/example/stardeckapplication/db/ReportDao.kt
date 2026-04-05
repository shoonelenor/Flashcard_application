package com.example.stardeckapplication.db

import android.content.ContentValues

/**
 * Handles user-submitted issue/help reports (the "ticket" system).
 * Used by ReportIssueActivity (user side) and AdminTicketsActivity (admin side).
 */
class ReportDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    // ── Data classes ────────────────────────────────────────────────────

    data class ReportReasonItem(
        val id: Long,
        val name: String,
        val description: String?
    )

    data class TicketRow(
        val reportId: Long,
        val reporterName: String,
        val reporterEmail: String,
        val reasonLabel: String,
        val details: String?,
        val status: String,
        val createdAt: Long
    )

    // ── User-side: fetch reasons for spinner ────────────────────────────

    fun getActiveReportReasons(): List<ReportReasonItem> {
        val out = mutableListOf<ReportReasonItem>()
        readable.rawQuery(
            """
            SELECT ${DbContract.RR_ID}, ${DbContract.RR_NAME}, ${DbContract.RR_DESCRIPTION}
            FROM   ${DbContract.T_REPORT_REASONS}
            WHERE  ${DbContract.RR_IS_ACTIVE} = 1
            ORDER  BY ${DbContract.RR_SORT_ORDER}, ${DbContract.RR_NAME}
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += ReportReasonItem(
                    id          = c.getLong(0),
                    name        = c.getString(1),
                    description = c.getString(2)
                )
            }
        }
        return out
    }

    /**
     * Insert a help/issue ticket submitted by a user.
     * [deckId] is null for general app issues (not deck-specific).
     * Returns the new row id, or -1 on failure.
     */
    fun submitReport(
        reporterUserId: Long,
        reasonId: Long,
        details: String?,
        deckId: Long? = null
    ): Long {
        val cv = ContentValues().apply {
            put(DbContract.R_REPORTER_USER_ID, reporterUserId)
            put(DbContract.R_REASON_ID, reasonId)
            put(DbContract.R_DETAILS, details?.trim())
            put(DbContract.R_STATUS, DbContract.REPORT_OPEN)
            put(DbContract.R_CREATED_AT, System.currentTimeMillis())
            if (deckId != null) {
                put(DbContract.R_DECK_ID, deckId)
            } else {
                putNull(DbContract.R_DECK_ID)
            }
        }
        return writable.insert(DbContract.T_REPORTS, null, cv)
    }

    // ── Admin-side: list all tickets ─────────────────────────────────────

    fun adminGetAllTickets(): List<TicketRow> {
        val out = mutableListOf<TicketRow>()
        readable.rawQuery(
            """
            SELECT
                r.${DbContract.R_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                COALESCE(rr.${DbContract.RR_NAME}, r.${DbContract.R_REASON}, 'General') AS reason_label,
                r.${DbContract.R_DETAILS},
                r.${DbContract.R_STATUS},
                r.${DbContract.R_CREATED_AT}
            FROM  ${DbContract.T_REPORTS} r
            INNER JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID} = r.${DbContract.R_REPORTER_USER_ID}
            LEFT JOIN ${DbContract.T_REPORT_REASONS} rr
                ON rr.${DbContract.RR_ID} = r.${DbContract.R_REASON_ID}
            ORDER BY
                CASE WHEN r.${DbContract.R_STATUS} = '${DbContract.REPORT_OPEN}' THEN 0 ELSE 1 END,
                r.${DbContract.R_CREATED_AT} DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += TicketRow(
                    reportId     = c.getLong(0),
                    reporterName = c.getString(1),
                    reporterEmail = c.getString(2),
                    reasonLabel  = c.getString(3),
                    details      = c.getString(4),
                    status       = c.getString(5),
                    createdAt    = c.getLong(6)
                )
            }
        }
        return out
    }

    // ── Admin-side: resolve / reopen ─────────────────────────────────────

    fun resolveTicket(reportId: Long): Int {
        val cv = ContentValues().apply { put(DbContract.R_STATUS, DbContract.REPORT_RESOLVED) }
        return writable.update(
            DbContract.T_REPORTS, cv,
            "${DbContract.R_ID} = ?", arrayOf(reportId.toString())
        )
    }

    fun reopenTicket(reportId: Long): Int {
        val cv = ContentValues().apply { put(DbContract.R_STATUS, DbContract.REPORT_OPEN) }
        return writable.update(
            DbContract.T_REPORTS, cv,
            "${DbContract.R_ID} = ?", arrayOf(reportId.toString())
        )
    }

    fun getTicketCount(status: String): Int {
        readable.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_REPORTS} WHERE ${DbContract.R_STATUS} = ?",
            arrayOf(status)
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}
