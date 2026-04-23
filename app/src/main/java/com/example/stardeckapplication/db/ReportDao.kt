package com.example.stardeckapplication.db

import android.content.ContentValues
import android.util.Log

/**
 * ReportDao — handles Help / Report Issue trouble tickets.
 * Tickets are identified by deck_id IS NULL (or 0).
 * Content reports (deck_id IS NOT NULL and > 0) are handled by ModerationDao/DeckDao.
 */
class ReportDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    companion object {
        private const val TAG = "ReportDao"
    }

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

    // ── User side: fetch issue categories for spinner ─────────────────────

    fun getActiveReportReasons(): List<ReportReasonItem> {
        val out = mutableListOf<ReportReasonItem>()
        readable.rawQuery(
            """
            SELECT ${DbContract.RR_ID},
                   ${DbContract.RR_NAME},
                   ${DbContract.RR_DESCRIPTION}
            FROM   ${DbContract.T_REPORT_REASONS}
            WHERE  ${DbContract.RR_IS_ACTIVE} = 1
            ORDER BY ${DbContract.RR_SORT_ORDER}, ${DbContract.RR_NAME}
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
        Log.d(TAG, "getActiveReportReasons: found ${out.size} reasons")
        return out
    }

    // ── User side: submit a trouble ticket (NO deck_id = app-level issue) ─

    fun submitReport(
        reporterUserId: Long,
        reasonId: Long,
        details: String?
    ): Long {
        val selectedReasonName = readable.rawQuery(
            """
            SELECT ${DbContract.RR_NAME}
            FROM   ${DbContract.T_REPORT_REASONS}
            WHERE  ${DbContract.RR_ID} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(reasonId.toString())
        ).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

        val cv = ContentValues().apply {
            put(DbContract.R_REPORTER_USER_ID, reporterUserId)
            putNull(DbContract.R_DECK_ID)
            put(DbContract.R_REASON_ID, reasonId)
            put(DbContract.R_REASON, selectedReasonName ?: "General")
            put(DbContract.R_DETAILS, details?.trim())
            put(DbContract.R_STATUS, DbContract.REPORT_OPEN)
            put(DbContract.R_CREATED_AT, System.currentTimeMillis())
        }

        val newId = writable.insert(DbContract.T_REPORTS, null, cv)
        Log.d(TAG, "submitReport: inserted row id=$newId, userId=$reporterUserId, reasonId=$reasonId")
        return newId
    }

    // ── Admin side: list all trouble tickets (deck_id is null or 0) ────────

    fun adminGetAllTickets(): List<TicketRow> {
        val out = mutableListOf<TicketRow>()
        val sql = """
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
            WHERE (r.${DbContract.R_DECK_ID} IS NULL OR r.${DbContract.R_DECK_ID} = 0)
            ORDER BY
                CASE WHEN r.${DbContract.R_STATUS} = '${DbContract.REPORT_OPEN}' THEN 0 ELSE 1 END,
                r.${DbContract.R_CREATED_AT} DESC
        """.trimIndent()

        Log.d(TAG, "adminGetAllTickets SQL:\n$sql")

        readable.rawQuery(sql, null).use { c ->
            Log.d(TAG, "adminGetAllTickets: cursor count = ${c.count}")
            while (c.moveToNext()) {
                out += TicketRow(
                    reportId      = c.getLong(0),
                    reporterName  = c.getString(1),
                    reporterEmail = c.getString(2),
                    reasonLabel   = c.getString(3),
                    details       = c.getString(4),
                    status        = c.getString(5),
                    createdAt     = c.getLong(6)
                )
            }
        }
        Log.d(TAG, "adminGetAllTickets: returning ${out.size} tickets")
        return out
    }

    // ── Admin side: resolve / reopen ───────────────────────────────────────

    fun resolveTicket(reportId: Long): Int {
        val cv = ContentValues().apply {
            put(DbContract.R_STATUS, DbContract.REPORT_RESOLVED)
        }
        val rows = writable.update(
            DbContract.T_REPORTS,
            cv,
            "${DbContract.R_ID} = ?",
            arrayOf(reportId.toString())
        )
        Log.d(TAG, "resolveTicket: id=$reportId, rows updated=$rows")
        return rows
    }

    fun reopenTicket(reportId: Long): Int {
        val cv = ContentValues().apply {
            put(DbContract.R_STATUS, DbContract.REPORT_OPEN)
        }
        val rows = writable.update(
            DbContract.T_REPORTS,
            cv,
            "${DbContract.R_ID} = ?",
            arrayOf(reportId.toString())
        )
        Log.d(TAG, "reopenTicket: id=$reportId, rows updated=$rows")
        return rows
    }

    fun getTicketCount(status: String): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM  ${DbContract.T_REPORTS}
            WHERE ${DbContract.R_STATUS} = ?
              AND (${DbContract.R_DECK_ID} IS NULL OR ${DbContract.R_DECK_ID} = 0)
            """.trimIndent(),
            arrayOf(status)
        ).use { c ->
            val count = if (c.moveToFirst()) c.getInt(0) else 0
            Log.d(TAG, "getTicketCount: status=$status, count=$count")
            return count
        }
    }
}