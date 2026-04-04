package com.example.stardeckapplication.db

import android.content.ContentValues

class SubjectDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    data class AdminSubjectRow(
        val id: Long,
        val categoryId: Long,
        val categoryName: String,
        val name: String,
        val description: String?,
        val isActive: Boolean,
        val sortOrder: Int,
        val createdAt: Long,
        val usageCount: Int
    )

    data class SelectableSubject(
        val id: Long,
        val categoryId: Long,
        val name: String
    )

    fun adminGetAllSubjects(): List<AdminSubjectRow> {
        val out = mutableListOf<AdminSubjectRow>()

        readable.rawQuery(
            """
            SELECT
                s.${DbContract.SUBJ_ID},
                s.${DbContract.SUBJ_CATEGORY_ID},
                c.${DbContract.CAT_NAME},
                s.${DbContract.SUBJ_NAME},
                s.${DbContract.SUBJ_DESCRIPTION},
                COALESCE(s.${DbContract.SUBJ_IS_ACTIVE}, 1),
                COALESCE(s.${DbContract.SUBJ_SORT_ORDER}, 0),
                s.${DbContract.SUBJ_CREATED_AT},
                COUNT(d.${DbContract.D_ID}) AS usage_count
            FROM ${DbContract.T_SUBJECTS} s
            INNER JOIN ${DbContract.T_CATEGORIES} c
                ON c.${DbContract.CAT_ID} = s.${DbContract.SUBJ_CATEGORY_ID}
            LEFT JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_SUBJECT_ID} = s.${DbContract.SUBJ_ID}
            GROUP BY
                s.${DbContract.SUBJ_ID},
                s.${DbContract.SUBJ_CATEGORY_ID},
                c.${DbContract.CAT_NAME},
                s.${DbContract.SUBJ_NAME},
                s.${DbContract.SUBJ_DESCRIPTION},
                s.${DbContract.SUBJ_IS_ACTIVE},
                s.${DbContract.SUBJ_SORT_ORDER},
                s.${DbContract.SUBJ_CREATED_AT}
            ORDER BY
                s.${DbContract.SUBJ_IS_ACTIVE} DESC,
                c.${DbContract.CAT_NAME} ASC,
                s.${DbContract.SUBJ_SORT_ORDER} ASC,
                s.${DbContract.SUBJ_NAME} ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += AdminSubjectRow(
                    id = c.getLong(0),
                    categoryId = c.getLong(1),
                    categoryName = c.getString(2),
                    name = c.getString(3),
                    description = c.getString(4),
                    isActive = c.getInt(5) == 1,
                    sortOrder = c.getInt(6),
                    createdAt = c.getLong(7),
                    usageCount = c.getInt(8)
                )
            }
        }

        return out
    }

    fun getSelectableSubjects(
        categoryId: Long?,
        includeSubjectId: Long? = null
    ): List<SelectableSubject> {
        if (categoryId == null || categoryId <= 0L) return emptyList()

        val out = mutableListOf<SelectableSubject>()

        val sql: String
        val args: Array<String>

        if (includeSubjectId != null && includeSubjectId > 0L) {
            sql = """
                SELECT
                    ${DbContract.SUBJ_ID},
                    ${DbContract.SUBJ_CATEGORY_ID},
                    ${DbContract.SUBJ_NAME}
                FROM ${DbContract.T_SUBJECTS}
                WHERE
                    (
                        ${DbContract.SUBJ_CATEGORY_ID} = ?
                        AND ${DbContract.SUBJ_IS_ACTIVE} = 1
                    )
                    OR ${DbContract.SUBJ_ID} = ?
                ORDER BY ${DbContract.SUBJ_SORT_ORDER} ASC, ${DbContract.SUBJ_NAME} ASC
            """.trimIndent()
            args = arrayOf(categoryId.toString(), includeSubjectId.toString())
        } else {
            sql = """
                SELECT
                    ${DbContract.SUBJ_ID},
                    ${DbContract.SUBJ_CATEGORY_ID},
                    ${DbContract.SUBJ_NAME}
                FROM ${DbContract.T_SUBJECTS}
                WHERE
                    ${DbContract.SUBJ_CATEGORY_ID} = ?
                    AND ${DbContract.SUBJ_IS_ACTIVE} = 1
                ORDER BY ${DbContract.SUBJ_SORT_ORDER} ASC, ${DbContract.SUBJ_NAME} ASC
            """.trimIndent()
            args = arrayOf(categoryId.toString())
        }

        readable.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out += SelectableSubject(
                    id = c.getLong(0),
                    categoryId = c.getLong(1),
                    name = c.getString(2)
                )
            }
        }

        return out.distinctBy { it.id }
    }

    fun getNextSortOrder(categoryId: Long): Int {
        readable.rawQuery(
            """
            SELECT COALESCE(MAX(${DbContract.SUBJ_SORT_ORDER}), 0) + 10
            FROM ${DbContract.T_SUBJECTS}
            WHERE ${DbContract.SUBJ_CATEGORY_ID} = ?
            """.trimIndent(),
            arrayOf(categoryId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 10
    }

    fun isNameTaken(
        categoryId: Long,
        name: String,
        excludeId: Long? = null
    ): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false

        val sql = buildString {
            append(
                """
                SELECT 1
                FROM ${DbContract.T_SUBJECTS}
                WHERE ${DbContract.SUBJ_CATEGORY_ID} = ?
                  AND ${DbContract.SUBJ_NAME} = ?
                """.trimIndent()
            )
            if (excludeId != null) {
                append(" AND ${DbContract.SUBJ_ID} <> ?")
            }
            append(" LIMIT 1")
        }

        val args = if (excludeId != null) {
            arrayOf(categoryId.toString(), clean, excludeId.toString())
        } else {
            arrayOf(categoryId.toString(), clean)
        }

        readable.rawQuery(sql, args).use { c ->
            return c.moveToFirst()
        }
    }

    fun createSubject(
        categoryId: Long,
        name: String,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Long {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Subject name is required." }

        val cv = ContentValues().apply {
            put(DbContract.SUBJ_CATEGORY_ID, categoryId)
            put(DbContract.SUBJ_NAME, clean)
            put(DbContract.SUBJ_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.SUBJ_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.SUBJ_SORT_ORDER, sortOrder)
            put(DbContract.SUBJ_CREATED_AT, System.currentTimeMillis())
        }

        return writable.insertOrThrow(DbContract.T_SUBJECTS, null, cv)
    }

    fun updateSubject(
        subjectId: Long,
        categoryId: Long,
        name: String,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Int {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Subject name is required." }

        val cv = ContentValues().apply {
            put(DbContract.SUBJ_CATEGORY_ID, categoryId)
            put(DbContract.SUBJ_NAME, clean)
            put(DbContract.SUBJ_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.SUBJ_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.SUBJ_SORT_ORDER, sortOrder)
        }

        return writable.update(
            DbContract.T_SUBJECTS,
            cv,
            "${DbContract.SUBJ_ID} = ?",
            arrayOf(subjectId.toString())
        )
    }

    fun setSubjectActive(subjectId: Long, isActive: Boolean): Int {
        val cv = ContentValues().apply {
            put(DbContract.SUBJ_IS_ACTIVE, if (isActive) 1 else 0)
        }

        return writable.update(
            DbContract.T_SUBJECTS,
            cv,
            "${DbContract.SUBJ_ID} = ?",
            arrayOf(subjectId.toString())
        )
    }

    fun deleteSubject(subjectId: Long): Int {
        if (getUsageCount(subjectId) > 0) return 0

        return writable.delete(
            DbContract.T_SUBJECTS,
            "${DbContract.SUBJ_ID} = ?",
            arrayOf(subjectId.toString())
        )
    }

    private fun getUsageCount(subjectId: Long): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_SUBJECT_ID} = ?
            """.trimIndent(),
            arrayOf(subjectId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }
}