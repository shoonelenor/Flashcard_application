package com.example.stardeckapplication.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * ReportDao — handles Help / Report Issue trouble tickets only.
 *
 * Help / Report Issues:
 * - use report reasons of type HELP
 * - save reports with deck_id = NULL
 *
 * Content Reports:
 * - are deck-linked (deck_id > 0)
 * - are handled separately by the deck/content moderation flow
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

    fun getActiveReportReasons(): List<ReportReasonItem> {
        ensureHelpReasonsReady()

        val out = mutableListOf<ReportReasonItem>()
        readable.rawQuery(
            """
            SELECT ${DbContract.RR_ID},
                   ${DbContract.RR_NAME},
                   ${DbContract.RR_DESCRIPTION}
            FROM   ${DbContract.T_REPORT_REASONS}
            WHERE  ${DbContract.RR_IS_ACTIVE} = 1
              AND (
                    ${DbContract.RR_TYPE} = ?
                    OR ${DbContract.RR_TYPE} IS NULL
                    OR TRIM(${DbContract.RR_TYPE}) = ''
                  )
            ORDER BY ${DbContract.RR_SORT_ORDER} ASC, ${DbContract.RR_NAME} ASC
            """.trimIndent(),
            arrayOf(DbContract.RR_TYPE_HELP)
        ).use { c ->
            while (c.moveToNext()) {
                out += ReportReasonItem(
                    id = c.getLong(0),
                    name = c.getString(1),
                    description = c.getString(2)
                )
            }
        }

        Log.d(TAG, "getActiveReportReasons(help): found ${out.size} reasons")
        return out
    }

    fun submitReport(
        reporterUserId: Long,
        reasonId: Long,
        details: String?
    ): Long {
        ensureHelpReasonsReady()

        val reasonCursor = readable.rawQuery(
            """
            SELECT ${DbContract.RR_NAME}
            FROM   ${DbContract.T_REPORT_REASONS}
            WHERE  ${DbContract.RR_ID} = ?
              AND  ${DbContract.RR_IS_ACTIVE} = 1
              AND (
                    ${DbContract.RR_TYPE} = ?
                    OR ${DbContract.RR_TYPE} IS NULL
                    OR TRIM(${DbContract.RR_TYPE}) = ''
                  )
            LIMIT 1
            """.trimIndent(),
            arrayOf(reasonId.toString(), DbContract.RR_TYPE_HELP)
        )

        val selectedReasonName = reasonCursor.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

        if (selectedReasonName.isNullOrBlank()) {
            Log.w(TAG, "submitReport rejected: reasonId=$reasonId is not a valid HELP reason")
            return -1L
        }

        val cv = ContentValues().apply {
            put(DbContract.R_REPORTER_USER_ID, reporterUserId)
            putNull(DbContract.R_DECK_ID)
            put(DbContract.R_REASON_ID, reasonId)
            put(DbContract.R_REASON, selectedReasonName)
            put(DbContract.R_DETAILS, details?.trim()?.ifBlank { null })
            put(DbContract.R_STATUS, DbContract.REPORT_OPEN)
            put(DbContract.R_CREATED_AT, System.currentTimeMillis())
        }

        val newId = writable.insert(DbContract.T_REPORTS, null, cv)
        Log.d(TAG, "submitReport(help): inserted row id=$newId, userId=$reporterUserId, reasonId=$reasonId")
        return newId
    }

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

        readable.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out += TicketRow(
                    reportId = c.getLong(0),
                    reporterName = c.getString(1),
                    reporterEmail = c.getString(2),
                    reasonLabel = c.getString(3),
                    details = c.getString(4),
                    status = c.getString(5),
                    createdAt = c.getLong(6)
                )
            }
        }

        Log.d(TAG, "adminGetAllTickets: returning ${out.size} tickets")
        return out
    }

    fun resolveTicket(reportId: Long): Int {
        val cv = ContentValues().apply {
            put(DbContract.R_STATUS, DbContract.REPORT_RESOLVED)
        }

        return writable.update(
            DbContract.T_REPORTS,
            cv,
            "${DbContract.R_ID} = ?",
            arrayOf(reportId.toString())
        )
    }

    fun reopenTicket(reportId: Long): Int {
        val cv = ContentValues().apply {
            put(DbContract.R_STATUS, DbContract.REPORT_OPEN)
        }

        return writable.update(
            DbContract.T_REPORTS,
            cv,
            "${DbContract.R_ID} = ?",
            arrayOf(reportId.toString())
        )
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
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun ensureHelpReasonsReady() {
        writable.beginTransaction()
        try {
            repairLegacyHelpReasonTypes(writable)
            seedDefaultHelpReasonsIfMissing(writable)
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    private fun repairLegacyHelpReasonTypes(db: SQLiteDatabase) {
        db.execSQL(
            """
            UPDATE ${DbContract.T_REPORT_REASONS}
            SET ${DbContract.RR_TYPE} = ?
            WHERE (${DbContract.RR_TYPE} IS NULL OR TRIM(${DbContract.RR_TYPE}) = '')
              AND ${DbContract.RR_ID} IN (
                    SELECT DISTINCT ${DbContract.R_REASON_ID}
                    FROM ${DbContract.T_REPORTS}
                    WHERE (${DbContract.R_DECK_ID} IS NULL OR ${DbContract.R_DECK_ID} = 0)
                      AND ${DbContract.R_REASON_ID} IS NOT NULL
              )
            """.trimIndent(),
            arrayOf(DbContract.RR_TYPE_HELP)
        )
    }

    private fun seedDefaultHelpReasonsIfMissing(db: SQLiteDatabase) {
        insertHelpReasonIfMissing(
            db,
            "Bug / App Crash",
            "The app crashed, froze, or something stopped working unexpectedly.",
            10
        )
        insertHelpReasonIfMissing(
            db,
            "Feature Not Working",
            "A feature exists but is not behaving correctly or is broken.",
            20
        )
        insertHelpReasonIfMissing(
            db,
            "Login / Account Issue",
            "Problems with signing in, password, or account access.",
            30
        )
        insertHelpReasonIfMissing(
            db,
            "Study / Flashcard Problem",
            "Issues with the study session, card flipping, or progress not saving.",
            40
        )
        insertHelpReasonIfMissing(
            db,
            "Deck or Card Not Loading",
            "Decks or cards are missing, not loading, or showing blank content.",
            50
        )
        insertHelpReasonIfMissing(
            db,
            "Subscription / Payment Issue",
            "Problems with premium subscription, billing, or unlocking premium content.",
            60
        )
        insertHelpReasonIfMissing(
            db,
            "Performance / Speed Issue",
            "The app is slow, laggy, or takes too long to load.",
            70
        )
        insertHelpReasonIfMissing(
            db,
            "UI / Layout Issue",
            "Buttons, text, spacing, or screen layout look incorrect.",
            80
        )
        insertHelpReasonIfMissing(
            db,
            "Data / Sync Problem",
            "Saved data is missing, inconsistent, or not updating correctly.",
            90
        )
        insertHelpReasonIfMissing(
            db,
            "Other",
            "Any other problem not covered by the categories above.",
            100
        )
    }

    private fun insertHelpReasonIfMissing(
        db: SQLiteDatabase,
        name: String,
        description: String,
        sortOrder: Int
    ) {
        val existingId = db.rawQuery(
            """
            SELECT ${DbContract.RR_ID}
            FROM ${DbContract.T_REPORT_REASONS}
            WHERE ${DbContract.RR_NAME} = ?
              AND (
                    ${DbContract.RR_TYPE} = ?
                    OR ${DbContract.RR_TYPE} IS NULL
                    OR TRIM(${DbContract.RR_TYPE}) = ''
                  )
            LIMIT 1
            """.trimIndent(),
            arrayOf(name, DbContract.RR_TYPE_HELP)
        ).use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        }

        if (existingId > 0L) {
            val fix = ContentValues().apply {
                put(DbContract.RR_TYPE, DbContract.RR_TYPE_HELP)
                put(DbContract.RR_DESCRIPTION, description)
                put(DbContract.RR_IS_ACTIVE, 1)
                put(DbContract.RR_SORT_ORDER, sortOrder)
            }
            db.update(
                DbContract.T_REPORT_REASONS,
                fix,
                "${DbContract.RR_ID} = ?",
                arrayOf(existingId.toString())
            )
            return
        }

        val cv = ContentValues().apply {
            put(DbContract.RR_TYPE, DbContract.RR_TYPE_HELP)
            put(DbContract.RR_NAME, name)
            put(DbContract.RR_DESCRIPTION, description)
            put(DbContract.RR_IS_ACTIVE, 1)
            put(DbContract.RR_SORT_ORDER, sortOrder)
            put(DbContract.RR_CREATED_AT, System.currentTimeMillis())
        }
        db.insert(DbContract.T_REPORT_REASONS, null, cv)
    }
}