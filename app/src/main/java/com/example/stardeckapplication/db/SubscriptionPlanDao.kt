package com.example.stardeckapplication.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

class SubscriptionPlanDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    data class BillingCycleOption(
        val key: String,
        val label: String
    )

    data class AdminPlanRow(
        val id: Long,
        val name: String,
        val billingCycle: String,
        val billingLabel: String,
        val priceText: String,
        val durationDays: Int,
        val description: String?,
        val isActive: Boolean,
        val sortOrder: Int,
        val createdAt: Long,
        val subscriberCount: Int
    )

    data class PlanOption(
        val id: Long,
        val name: String,
        val billingCycle: String,
        val billingLabel: String,
        val priceText: String,
        val durationDays: Int,
        val description: String?,
        val isActive: Boolean
    )

    data class CurrentSubscription(
        val planId: Long,
        val planName: String,
        val billingCycle: String,
        val billingLabel: String,
        val priceText: String,
        val description: String?,
        val purchasedAt: Long,
        val expiresAt: Long?
    )

    fun ensureDefaultsInserted() {
        readable.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.TSUBSCRIPTIONPLANS}",
            null
        ).use { c ->
            if (c.moveToFirst() && c.getInt(0) > 0) return
        }

        val now = System.currentTimeMillis()
        val defaults = listOf(
            ContentValues().apply {
                put(DbContract.SP_NAME, "Monthly Premium")
                put(DbContract.SP_BILLING_CYCLE, DbContract.BILLING_MONTHLY)
                put(DbContract.SP_PRICE_TEXT, "$2.99 / month")
                put(DbContract.SP_DURATION_DAYS, 30)
                put(DbContract.SP_DESCRIPTION, "Unlock premium decks and future premium features.")
                put(DbContract.SP_IS_ACTIVE, 1)
                put(DbContract.SP_SORT_ORDER, 10)
                put(DbContract.SP_CREATED_AT, now)
            },
            ContentValues().apply {
                put(DbContract.SP_NAME, "Yearly Premium")
                put(DbContract.SP_BILLING_CYCLE, DbContract.BILLING_YEARLY)
                put(DbContract.SP_PRICE_TEXT, "$19.99 / year")
                put(DbContract.SP_DURATION_DAYS, 365)
                put(DbContract.SP_DESCRIPTION, "Best value plan for long-term learning.")
                put(DbContract.SP_IS_ACTIVE, 1)
                put(DbContract.SP_SORT_ORDER, 20)
                put(DbContract.SP_CREATED_AT, now)
            }
        )

        defaults.forEach { cv ->
            writable.insertWithOnConflict(
                DbContract.TSUBSCRIPTIONPLANS,
                null,
                cv,
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    fun getBillingCycleOptions(): List<BillingCycleOption> = listOf(
        BillingCycleOption(DbContract.BILLING_MONTHLY, "Monthly"),
        BillingCycleOption(DbContract.BILLING_YEARLY, "Yearly")
    )

    fun getBillingLabel(cycle: String): String {
        return when (cycle) {
            DbContract.BILLING_MONTHLY -> "Monthly"
            DbContract.BILLING_YEARLY -> "Yearly"
            else -> cycle.replaceFirstChar { it.uppercase() }
        }
    }

    fun adminGetAllPlans(): List<AdminPlanRow> {
        ensureDefaultsInserted()

        val out = mutableListOf<AdminPlanRow>()
        readable.rawQuery(
            """
            SELECT
                p.${DbContract.SP_ID},
                p.${DbContract.SP_NAME},
                p.${DbContract.SP_BILLING_CYCLE},
                p.${DbContract.SP_PRICE_TEXT},
                p.${DbContract.SP_DURATION_DAYS},
                p.${DbContract.SP_DESCRIPTION},
                COALESCE(p.${DbContract.SP_IS_ACTIVE}, 1),
                COALESCE(p.${DbContract.SP_SORT_ORDER}, 0),
                p.${DbContract.SP_CREATED_AT},
                COUNT(DISTINCT CASE WHEN us.${DbContract.US_IS_ACTIVE} = 1 THEN us.${DbContract.US_USER_ID} END) AS subscriber_count
            FROM ${DbContract.TSUBSCRIPTIONPLANS} p
            LEFT JOIN ${DbContract.TUSERSUBSCRIPTIONS} us
                ON us.${DbContract.US_PLAN_ID} = p.${DbContract.SP_ID}
            GROUP BY
                p.${DbContract.SP_ID},
                p.${DbContract.SP_NAME},
                p.${DbContract.SP_BILLING_CYCLE},
                p.${DbContract.SP_PRICE_TEXT},
                p.${DbContract.SP_DURATION_DAYS},
                p.${DbContract.SP_DESCRIPTION},
                p.${DbContract.SP_IS_ACTIVE},
                p.${DbContract.SP_SORT_ORDER},
                p.${DbContract.SP_CREATED_AT}
            ORDER BY
                p.${DbContract.SP_IS_ACTIVE} DESC,
                p.${DbContract.SP_SORT_ORDER} ASC,
                p.${DbContract.SP_NAME} ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val cycle = c.getString(2)
                out += AdminPlanRow(
                    id = c.getLong(0),
                    name = c.getString(1),
                    billingCycle = cycle,
                    billingLabel = getBillingLabel(cycle),
                    priceText = c.getString(3),
                    durationDays = c.getInt(4),
                    description = c.getString(5),
                    isActive = c.getInt(6) == 1,
                    sortOrder = c.getInt(7),
                    createdAt = c.getLong(8),
                    subscriberCount = c.getInt(9)
                )
            }
        }
        return out
    }

    fun getActivePlans(): List<PlanOption> {
        ensureDefaultsInserted()

        val out = mutableListOf<PlanOption>()
        readable.rawQuery(
            """
            SELECT
                ${DbContract.SP_ID},
                ${DbContract.SP_NAME},
                ${DbContract.SP_BILLING_CYCLE},
                ${DbContract.SP_PRICE_TEXT},
                ${DbContract.SP_DURATION_DAYS},
                ${DbContract.SP_DESCRIPTION},
                ${DbContract.SP_IS_ACTIVE}
            FROM ${DbContract.TSUBSCRIPTIONPLANS}
            WHERE ${DbContract.SP_IS_ACTIVE} = 1
            ORDER BY ${DbContract.SP_SORT_ORDER} ASC, ${DbContract.SP_NAME} ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val cycle = c.getString(2)
                out += PlanOption(
                    id = c.getLong(0),
                    name = c.getString(1),
                    billingCycle = cycle,
                    billingLabel = getBillingLabel(cycle),
                    priceText = c.getString(3),
                    durationDays = c.getInt(4),
                    description = c.getString(5),
                    isActive = c.getInt(6) == 1
                )
            }
        }
        return out
    }

    fun getPlanByBillingCycle(cycle: String): PlanOption? {
        return getActivePlans().firstOrNull { it.billingCycle == cycle }
    }

    fun getCurrentPlanForUser(userId: Long): CurrentSubscription? {
        val now = System.currentTimeMillis()
        readable.rawQuery(
            """
            SELECT
                p.${DbContract.SP_ID},
                p.${DbContract.SP_NAME},
                p.${DbContract.SP_BILLING_CYCLE},
                p.${DbContract.SP_PRICE_TEXT},
                p.${DbContract.SP_DESCRIPTION},
                us.${DbContract.US_PURCHASED_AT},
                us.${DbContract.US_EXPIRES_AT}
            FROM ${DbContract.TUSERSUBSCRIPTIONS} us
            INNER JOIN ${DbContract.TSUBSCRIPTIONPLANS} p
                ON p.${DbContract.SP_ID} = us.${DbContract.US_PLAN_ID}
            WHERE us.${DbContract.US_USER_ID} = ?
              AND us.${DbContract.US_IS_ACTIVE} = 1
              AND (us.${DbContract.US_EXPIRES_AT} IS NULL OR us.${DbContract.US_EXPIRES_AT} >= ?)
            ORDER BY us.${DbContract.USPURCHASEDAT} DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), now.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            val cycle = c.getString(2)
            return CurrentSubscription(
                planId = c.getLong(0),
                planName = c.getString(1),
                billingCycle = cycle,
                billingLabel = getBillingLabel(cycle),
                priceText = c.getString(3),
                description = c.getString(4),
                purchasedAt = c.getLong(5),
                expiresAt = if (c.isNull(6)) null else c.getLong(6)
            )
        }
    }

    fun getNextSortOrder(): Int {
        readable.rawQuery(
            """
            SELECT COALESCE(MAX(${DbContract.SP_SORT_ORDER}), 0) + 10
            FROM ${DbContract.TSUBSCRIPTIONPLANS}
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
                FROM ${DbContract.TSUBSCRIPTIONPLANS}
                WHERE ${DbContract.SP_NAME} = ?
                COLLATE NOCASE
                """.trimIndent()
            )
            if (excludeId != null) {
                append(" AND ${DbContract.SP_ID} <> ?")
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

    fun createPlan(
        name: String,
        billingCycle: String,
        priceText: String,
        durationDays: Int,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Long {
        require(durationDays > 0) { "Duration must be greater than zero." }

        val cv = ContentValues().apply {
            put(DbContract.SP_NAME, name.trim())
            put(DbContract.SP_BILLING_CYCLE, billingCycle)
            put(DbContract.SP_PRICE_TEXT, priceText.trim())
            put(DbContract.SP_DURATION_DAYS, durationDays)
            put(DbContract.SP_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.SP_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.SP_SORT_ORDER, sortOrder)
            put(DbContract.SP_CREATED_AT, System.currentTimeMillis())
        }
        return writable.insertOrThrow(DbContract.TSUBSCRIPTIONPLANS, null, cv)
    }

    fun updatePlan(
        planId: Long,
        name: String,
        billingCycle: String,
        priceText: String,
        durationDays: Int,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Int {
        require(durationDays > 0) { "Duration must be greater than zero." }

        val cv = ContentValues().apply {
            put(DbContract.SP_NAME, name.trim())
            put(DbContract.SP_BILLING_CYCLE, billingCycle)
            put(DbContract.SP_PRICE_TEXT, priceText.trim())
            put(DbContract.SP_DURATION_DAYS, durationDays)
            put(DbContract.SP_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.SP_IS_ACTIVE, if (isActive) 1 else 0)
            put(DbContract.SP_SORT_ORDER, sortOrder)
        }
        return writable.update(
            DbContract.TSUBSCRIPTIONPLANS,
            cv,
            "${DbContract.SP_ID} = ?",
            arrayOf(planId.toString())
        )
    }

    fun setPlanActive(planId: Long, isActive: Boolean): Int {
        val cv = ContentValues().apply {
            put(DbContract.SP_IS_ACTIVE, if (isActive) 1 else 0)
        }
        return writable.update(
            DbContract.TSUBSCRIPTIONPLANS,
            cv,
            "${DbContract.SP_ID} = ?",
            arrayOf(planId.toString())
        )
    }

    fun deletePlan(planId: Long): Int {
        if (getHistoryCount(planId) > 0) return 0
        return writable.delete(
            DbContract.TSUBSCRIPTIONPLANS,
            "${DbContract.SP_ID} = ?",
            arrayOf(planId.toString())
        )
    }

    fun activatePlanForUser(userId: Long, planId: Long): Boolean {
        val db = writable
        val now = System.currentTimeMillis()
        val durationDays = getDurationDays(planId) ?: return false
        val expiresAt = now + durationDays * 24L * 60L * 60L * 1000L

        db.beginTransaction()
        return try {
            val clearCv = ContentValues().apply { put(DbContract.US_IS_ACTIVE, 0) }
            db.update(
                DbContract.TUSERSUBSCRIPTIONS,
                clearCv,
                "${DbContract.US_USER_ID} = ? AND ${DbContract.US_IS_ACTIVE} = 1",
                arrayOf(userId.toString())
            )

            val cv = ContentValues().apply {
                put(DbContract.US_USER_ID, userId)
                put(DbContract.US_PLAN_ID, planId)
                put(DbContract.US_PURCHASED_AT, now)
                put(DbContract.US_EXPIRES_AT, expiresAt)
                put(DbContract.US_IS_ACTIVE, 1)
            }
            val subId = db.insert(DbContract.TUSERSUBSCRIPTIONS, null, cv)
            if (subId == -1L) return false

            val userCv = ContentValues().apply { put(DbContract.U_IS_PREMIUM_USER, 1) }
            db.update(
                DbContract.TUSERS,
                userCv,
                "${DbContract.U_ID} = ?",
                arrayOf(userId.toString())
            )

            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    fun cancelPremiumForUser(userId: Long): Boolean {
        val db = writable
        db.beginTransaction()
        return try {
            val subCv = ContentValues().apply { put(DbContract.US_IS_ACTIVE, 0) }
            db.update(
                DbContract.TUSERSUBSCRIPTIONS,
                subCv,
                "${DbContract.US_USER_ID} = ? AND ${DbContract.US_IS_ACTIVE} = 1",
                arrayOf(userId.toString())
            )

            val userCv = ContentValues().apply { put(DbContract.U_IS_PREMIUM_USER, 0) }
            db.update(
                DbContract.TUSERS,
                userCv,
                "${DbContract.U_ID} = ?",
                arrayOf(userId.toString())
            )

            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    private fun getDurationDays(planId: Long): Int? {
        readable.rawQuery(
            """
            SELECT ${DbContract.SP_DURATION_DAYS}
            FROM ${DbContract.TSUBSCRIPTIONPLANS}
            WHERE ${DbContract.SP_ID} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(planId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            return c.getInt(0)
        }
    }

    private fun getHistoryCount(planId: Long): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.TUSERSUBSCRIPTIONS}
            WHERE ${DbContract.US_PLAN_ID} = ?
            """.trimIndent(),
            arrayOf(planId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }
}