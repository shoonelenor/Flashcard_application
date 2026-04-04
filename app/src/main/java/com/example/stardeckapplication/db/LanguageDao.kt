package com.example.stardeckapplication.db

import android.content.ContentValues

class LanguageDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    data class AdminLanguageRow(
        val id: Long,
        val name: String,
        val description: String?,
        val isActive: Boolean,
        val sortOrder: Int,
        val createdAt: Long,
        val usageCount: Int
    )

    data class SelectableLanguage(
        val id: Long,
        val name: String
    )

    fun adminGetAllLanguages(): List<AdminLanguageRow> {
        val out = mutableListOf<AdminLanguageRow>()

        readable.rawQuery(
            """
            SELECT
                l.${DbContract.LANG_ID},
                l.${DbContract.LANG_NAME},
                l.${DbContract.LANG_DESCRIPTION},
                COALESCE(l.${DbContract.LANG_IS_ACTIVE}, 1),
                COALESCE(l.${DbContract.LANG_SORT_ORDER}, 0),
                l.${DbContract.LANG_CREATED_AT},
                COUNT(d.${DbContract.D_ID}) AS usage_count
            FROM ${DbContract.T_LANGUAGES} l
            LEFT JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_LANGUAGE_ID} = l.${DbContract.LANG_ID}
            GROUP BY
                l.${DbContract.LANG_ID},
                l.${DbContract.LANG_NAME},
                l.${DbContract.LANG_DESCRIPTION},
                l.${DbContract.LANG_IS_ACTIVE},
                l.${DbContract.LANG_SORT_ORDER},
                l.${DbContract.LANG_CREATED_AT}
            ORDER BY
                l.${DbContract.LANG_IS_ACTIVE} DESC,
                l.${DbContract.LANG_SORT_ORDER} ASC,
                l.${DbContract.LANG_NAME} ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += AdminLanguageRow(
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

    fun getSelectableLanguages(includeLanguageId: Long? = null): List<SelectableLanguage> {
        val out = mutableListOf<SelectableLanguage>()
        val sql: String
        val args: Array<String>

        if (includeLanguageId != null && includeLanguageId > 0L) {
            sql = """
                SELECT
                    ${DbContract.LANG_ID},
                    ${DbContract.LANG_NAME}
                FROM ${DbContract.T_LANGUAGES}
                WHERE ${DbContract.LANG_IS_ACTIVE} = 1
                   OR ${DbContract.LANG_ID} = ?
                ORDER BY ${DbContract.LANG_SORT_ORDER} ASC, ${DbContract.LANG_NAME} ASC
            """.trimIndent()
            args = arrayOf(includeLanguageId.toString())
        } else {
            sql = """
                SELECT
                    ${DbContract.LANG_ID},
                    ${DbContract.LANG_NAME}
                FROM ${DbContract.T_LANGUAGES}
                WHERE ${DbContract.LANG_IS_ACTIVE} = 1
                ORDER BY ${DbContract.LANG_SORT_ORDER} ASC, ${DbContract.LANG_NAME} ASC
            """.trimIndent()
            args = emptyArray()
        }

        readable.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out += SelectableLanguage(
                    id = c.getLong(0),
                    name = c.getString(1)
                )
            }
        }

        return out.distinctBy { it.id }
    }

    fun getNextSortOrder(): Int {
        readable.rawQuery(
            """
            SELECT COALESCE(MAX(${DbContract.LANG_SORT_ORDER}), 0) + 10
            FROM ${DbContract.T_LANGUAGES}
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
                FROM ${DbContract.T_LANGUAGES}
                WHERE ${DbContract.LANG_NAME} = ?
                COLLATE NOCASE
                """.trimIndent()
            )
            if (excludeId != null) {
                append(" AND ${DbContract.LANG_ID} <> ?")
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

    fun createLanguage(
        name: String,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Long {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Language name is required." }

        val cv = ContentValues().apply {
            put(DbContract.LANG_NAME, clean)
            put(DbContract.LANG_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.LANG_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.LANG_SORT_ORDER, sortOrder)
            put(DbContract.LANG_CREATED_AT, System.currentTimeMillis())
        }

        return writable.insertOrThrow(DbContract.T_LANGUAGES, null, cv)
    }

    fun updateLanguage(
        languageId: Long,
        name: String,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Int {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Language name is required." }

        val cv = ContentValues().apply {
            put(DbContract.LANG_NAME, clean)
            put(DbContract.LANG_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.LANG_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.LANG_SORT_ORDER, sortOrder)
        }

        return writable.update(
            DbContract.T_LANGUAGES,
            cv,
            "${DbContract.LANG_ID} = ?",
            arrayOf(languageId.toString())
        )
    }

    fun setLanguageActive(languageId: Long, isActive: Boolean): Int {
        val cv = ContentValues().apply {
            put(DbContract.LANG_IS_ACTIVE, if (isActive) 1 else 0)
        }

        return writable.update(
            DbContract.T_LANGUAGES,
            cv,
            "${DbContract.LANG_ID} = ?",
            arrayOf(languageId.toString())
        )
    }

    fun deleteLanguage(languageId: Long): Int {
        if (getUsageCount(languageId) > 0) return 0

        return writable.delete(
            DbContract.T_LANGUAGES,
            "${DbContract.LANG_ID} = ?",
            arrayOf(languageId.toString())
        )
    }

    private fun getUsageCount(languageId: Long): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_LANGUAGE_ID} = ?
            """.trimIndent(),
            arrayOf(languageId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }
}