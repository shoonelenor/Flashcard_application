package com.example.stardeckapplication.db

import android.content.ContentValues

class ReportReasonDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    data class ReportReasonRow(
        val id: Long,
        val name: String,
        val description: String?,
        val isActive: Boolean,
        val sortOrder: Int,
        val createdAt: Long,
        val usageCount: Int
    )

    data class ActiveReason(
        val id: Long,
        val name: String
    )

    fun adminGetAllReportReasons(): List<ReportReasonRow> {
        val out = mutableListOf<ReportReasonRow>()

        readable.rawQuery(
            """
            SELECT
                rr.${DbContract.RR_ID},
                rr.${DbContract.RR_NAME},
                rr.${DbContract.RR_DESCRIPTION},
                COALESCE(rr.${DbContract.RR_IS_ACTIVE}, 1),
                COALESCE(rr.${DbContract.RR_SORT_ORDER}, 0),
                rr.${DbContract.RR_CREATED_AT},
                COUNT(r.${DbContract.R_ID}) AS usage_count
            FROM ${DbContract.T_REPORT_REASONS} rr
            LEFT JOIN ${DbContract.T_REPORTS} r
                ON r.${DbContract.R_REASON_ID} = rr.${DbContract.RR_ID}
               AND r.${DbContract.R_DECK_ID} IS NULL
            GROUP BY
                rr.${DbContract.RR_ID},
                rr.${DbContract.RR_NAME},
                rr.${DbContract.RR_DESCRIPTION},
                rr.${DbContract.RR_IS_ACTIVE},
                rr.${DbContract.RR_SORT_ORDER},
                rr.${DbContract.RR_CREATED_AT}
            ORDER BY
                rr.${DbContract.RR_IS_ACTIVE} DESC,
                rr.${DbContract.RR_SORT_ORDER} ASC,
                rr.${DbContract.RR_NAME} ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += ReportReasonRow(
                    id = c.getLong(0),
                    name = c.getString(1),
                    description = c.getString(2),
                    isActive = c.getInt(3) == 1,
                    sortOrder = c.getInt(4),
                    createdAt = c.getLong(5),
                    usageCount = c.getInt(6)
                )
            }
        }

        return out
    }

    fun getActiveReasons(): List<ActiveReason> {
        val out = mutableListOf<ActiveReason>()

        readable.rawQuery(
            """
            SELECT ${DbContract.RR_ID}, ${DbContract.RR_NAME}
            FROM ${DbContract.T_REPORT_REASONS}
            WHERE ${DbContract.RR_IS_ACTIVE} = 1
            ORDER BY ${DbContract.RR_SORT_ORDER} ASC, ${DbContract.RR_NAME} ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += ActiveReason(
                    id = c.getLong(0),
                    name = c.getString(1)
                )
            }
        }

        return out
    }

    fun getNextSortOrder(): Int {
        readable.rawQuery(
            """
            SELECT COALESCE(MAX(${DbContract.RR_SORT_ORDER}), 0) + 10
            FROM ${DbContract.T_REPORT_REASONS}
            """.trimIndent(),
            null
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 10
    }

    fun isNameTaken(name: String, excludeId: Long? = null): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false

        val sql = buildString {
            append(
                """
                SELECT 1
                FROM ${DbContract.T_REPORT_REASONS}
                WHERE ${DbContract.RR_NAME} = ?
                COLLATE NOCASE
                """.trimIndent()
            )
            if (excludeId != null) {
                append(" AND ${DbContract.RR_ID} <> ?")
            }
            append(" LIMIT 1")
        }

        val args = if (excludeId != null) {
            arrayOf(clean, excludeId.toString())
        } else {
            arrayOf(clean)
        }

        readable.rawQuery(sql, args).use { c ->
            return c.moveToFirst()
        }
    }

    fun createReason(
        name: String,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Long {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Reason name is required." }

        val cv = ContentValues().apply {
            put(DbContract.RR_NAME, cleanName)
            put(DbContract.RR_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.RR_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.RR_SORT_ORDER, sortOrder)
            put(DbContract.RR_CREATED_AT, System.currentTimeMillis())
        }

        return writable.insertOrThrow(DbContract.T_REPORT_REASONS, null, cv)
    }

    fun updateReason(
        reasonId: Long,
        name: String,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Int {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Reason name is required." }

        val cv = ContentValues().apply {
            put(DbContract.RR_NAME, cleanName)
            put(DbContract.RR_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.RR_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.RR_SORT_ORDER, sortOrder)
        }

        return writable.update(
            DbContract.T_REPORT_REASONS,
            cv,
            "${DbContract.RR_ID} = ?",
            arrayOf(reasonId.toString())
        )
    }

    fun setReasonActive(reasonId: Long, isActive: Boolean): Int {
        val cv = ContentValues().apply {
            put(DbContract.RR_IS_ACTIVE, if (isActive) 1 else 0)
        }

        return writable.update(
            DbContract.T_REPORT_REASONS,
            cv,
            "${DbContract.RR_ID} = ?",
            arrayOf(reasonId.toString())
        )
    }

    fun deleteReason(reasonId: Long): Int {
        if (getUsageCount(reasonId) > 0) return 0

        return writable.delete(
            DbContract.T_REPORT_REASONS,
            "${DbContract.RR_ID} = ?",
            arrayOf(reasonId.toString())
        )
    }

    private fun getUsageCount(reasonId: Long): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_REPORTS}
            WHERE ${DbContract.R_REASON_ID} = ?
              AND ${DbContract.R_DECK_ID} IS NULL
            """.trimIndent(),
            arrayOf(reasonId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}