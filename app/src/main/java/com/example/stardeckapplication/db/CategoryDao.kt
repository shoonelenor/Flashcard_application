package com.example.stardeckapplication.db

import android.content.ContentValues

class CategoryDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    data class AdminCategoryRow(
        val id: Long,
        val name: String,
        val description: String?,
        val isActive: Boolean,
        val sortOrder: Int,
        val createdAt: Long,
        val usageCount: Int
    )

    data class SelectableCategory(
        val id: Long,
        val name: String
    )

    fun adminGetAllCategories(): List<AdminCategoryRow> {
        val out = mutableListOf<AdminCategoryRow>()

        readable.rawQuery(
            """
            SELECT
                c.${DbContract.CAT_ID},
                c.${DbContract.CAT_NAME},
                c.${DbContract.CAT_DESCRIPTION},
                COALESCE(c.${DbContract.CAT_IS_ACTIVE}, 1),
                COALESCE(c.${DbContract.CAT_SORT_ORDER}, 0),
                c.${DbContract.CAT_CREATED_AT},
                COUNT(d.${DbContract.D_ID}) AS usage_count
            FROM ${DbContract.T_CATEGORIES} c
            LEFT JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_CATEGORY_ID} = c.${DbContract.CAT_ID}
            GROUP BY
                c.${DbContract.CAT_ID},
                c.${DbContract.CAT_NAME},
                c.${DbContract.CAT_DESCRIPTION},
                c.${DbContract.CAT_IS_ACTIVE},
                c.${DbContract.CAT_SORT_ORDER},
                c.${DbContract.CAT_CREATED_AT}
            ORDER BY
                c.${DbContract.CAT_IS_ACTIVE} DESC,
                c.${DbContract.CAT_SORT_ORDER} ASC,
                c.${DbContract.CAT_NAME} ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += AdminCategoryRow(
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

    fun getSelectableCategories(includeCategoryId: Long? = null): List<SelectableCategory> {
        val out = mutableListOf<SelectableCategory>()

        val sql = buildString {
            append(
                """
                SELECT ${DbContract.CAT_ID}, ${DbContract.CAT_NAME}
                FROM ${DbContract.T_CATEGORIES}
                WHERE ${DbContract.CAT_IS_ACTIVE} = 1
                """.trimIndent()
            )
            if (includeCategoryId != null && includeCategoryId > 0L) {
                append(" OR ${DbContract.CAT_ID} = ?")
            }
            append(" ORDER BY ${DbContract.CAT_SORT_ORDER} ASC, ${DbContract.CAT_NAME} ASC")
        }

        val args = if (includeCategoryId != null && includeCategoryId > 0L) {
            arrayOf(includeCategoryId.toString())
        } else {
            emptyArray()
        }

        readable.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out += SelectableCategory(
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
            SELECT COALESCE(MAX(${DbContract.CAT_SORT_ORDER}), 0) + 10
            FROM ${DbContract.T_CATEGORIES}
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
                FROM ${DbContract.T_CATEGORIES}
                WHERE ${DbContract.CAT_NAME} = ?
                COLLATE NOCASE
                """.trimIndent()
            )
            if (excludeId != null) {
                append(" AND ${DbContract.CAT_ID} <> ?")
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

    fun createCategory(
        name: String,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Long {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Category name is required." }

        val cv = ContentValues().apply {
            put(DbContract.CAT_NAME, clean)
            put(DbContract.CAT_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.CAT_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.CAT_SORT_ORDER, sortOrder)
            put(DbContract.CAT_CREATED_AT, System.currentTimeMillis())
        }

        return writable.insertOrThrow(DbContract.T_CATEGORIES, null, cv)
    }

    fun updateCategory(
        categoryId: Long,
        name: String,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Int {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Category name is required." }

        val cv = ContentValues().apply {
            put(DbContract.CAT_NAME, clean)
            put(DbContract.CAT_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.CAT_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.CAT_SORT_ORDER, sortOrder)
        }

        return writable.update(
            DbContract.T_CATEGORIES,
            cv,
            "${DbContract.CAT_ID} = ?",
            arrayOf(categoryId.toString())
        )
    }

    fun setCategoryActive(categoryId: Long, isActive: Boolean): Int {
        val cv = ContentValues().apply {
            put(DbContract.CAT_IS_ACTIVE, if (isActive) 1 else 0)
        }

        return writable.update(
            DbContract.T_CATEGORIES,
            cv,
            "${DbContract.CAT_ID} = ?",
            arrayOf(categoryId.toString())
        )
    }

    fun deleteCategory(categoryId: Long): Int {
        if (getUsageCount(categoryId) > 0) return 0

        return writable.delete(
            DbContract.T_CATEGORIES,
            "${DbContract.CAT_ID} = ?",
            arrayOf(categoryId.toString())
        )
    }

    private fun getUsageCount(categoryId: Long): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_CATEGORY_ID} = ?
            """.trimIndent(),
            arrayOf(categoryId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }
}