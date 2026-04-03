package com.example.stardeckapplication.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.stardeckapplication.model.UserSession
import com.example.stardeckapplication.util.PasswordHasher
import java.time.LocalDate
import java.time.ZoneId

/**
 * UserDao: all queries and updates related to users.
 *
 * Usage:
 *   val dbHelper = StarDeckDbHelper(context)
 *   val userDao = UserDao(dbHelper)
 *
 *   val isPremium = userDao.isPremiumUser(userId)
 *   val lastLogin = userDao.getLastLoginAt(userId)
 *   userDao.updateLastLoginAt(userId)
 *   val mau = userDao.countMonthlyActiveUsers()
 *   val miu = userDao.countMonthlyInactiveUsers()
 */
class UserDao(private val dbHelper: StarDeckDbHelper) {

    private val readable: SQLiteDatabase
        get() = dbHelper.readableDatabase

    private val writable: SQLiteDatabase
        get() = dbHelper.writableDatabase

    // ---------- AUTH ----------

    enum class ResetPasswordResult {
        SUCCESS,
        NOT_FOUND,
        DISABLED
    }

    fun registerUser(
        name: String,
        email: String,
        password: CharArray,
        acceptedTerms: Boolean
    ): Long {
        val cv = ContentValues().apply {
            put(DbContract.U_NAME, name.trim())
            put(DbContract.U_EMAIL, email.trim().lowercase())
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(password))
            put(DbContract.U_ROLE, DbContract.ROLE_USER)
            put(DbContract.U_STATUS, DbContract.STATUS_ACTIVE)
            put(DbContract.U_ACCEPTED_TERMS, if (acceptedTerms) 1 else 0)
            put(DbContract.U_FORCE_PW_CHANGE, 0)
            put(DbContract.U_CREATED_AT, System.currentTimeMillis())
            put(DbContract.U_IS_PREMIUM_USER, 0)
        }
        return writable.insertOrThrow(DbContract.T_USERS, null, cv)
    }

    fun authenticate(email: String, password: CharArray): UserSession? {
        readable.rawQuery(
            """
            SELECT
                ${DbContract.U_ID},
                ${DbContract.U_NAME},
                ${DbContract.U_EMAIL},
                ${DbContract.U_PASSWORD_HASH},
                ${DbContract.U_ROLE},
                ${DbContract.U_STATUS},
                ${DbContract.U_FORCE_PW_CHANGE}
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_EMAIL} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(email.trim().lowercase())
        ).use { c ->
            if (!c.moveToFirst()) return null

            val id = c.getLong(0)
            val name = c.getString(1)
            val em = c.getString(2)
            val stored = c.getString(3)
            val role = c.getString(4)
            val status = c.getString(5)
            val force = c.getInt(6) == 1

            if (!PasswordHasher.verify(password, stored)) return null

            return UserSession(
                id = id,
                name = name,
                email = em,
                role = role,
                status = status,
                forcePasswordChange = force
            )
        }
    }

    fun updatePassword(
        userId: Long,
        newPassword: CharArray,
        forcePwChange: Boolean
    ) {
        val cv = ContentValues().apply {
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(newPassword))
            put(DbContract.U_FORCE_PW_CHANGE, if (forcePwChange) 1 else 0)
        }
        writable.update(
            DbContract.T_USERS,
            cv,
            "${DbContract.U_ID} = ?",
            arrayOf(userId.toString())
        )
    }

    fun resetPasswordWithIdentity(
        email: String,
        fullName: String,
        newPassword: CharArray
    ): ResetPasswordResult {
        val normalizedEmail = email.trim().lowercase()
        val normalizedName = fullName.trim().lowercase()

        if (normalizedEmail.isBlank() || normalizedName.isBlank()) {
            return ResetPasswordResult.NOT_FOUND
        }

        readable.rawQuery(
            """
            SELECT
                ${DbContract.U_ID},
                ${DbContract.U_STATUS},
                ${DbContract.U_NAME}
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_EMAIL} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(normalizedEmail)
        ).use { c ->
            if (!c.moveToFirst()) return ResetPasswordResult.NOT_FOUND

            val userId = c.getLong(0)
            val status = c.getString(1)
            val storedName = c.getString(2).trim().lowercase()

            if (status == DbContract.STATUS_DISABLED) {
                return ResetPasswordResult.DISABLED
            }

            if (storedName != normalizedName) {
                return ResetPasswordResult.NOT_FOUND
            }

            val cv = ContentValues().apply {
                put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(newPassword))
                put(DbContract.U_FORCE_PW_CHANGE, 0)
            }

            val rows = writable.update(
                DbContract.T_USERS,
                cv,
                "${DbContract.U_ID} = ?",
                arrayOf(userId.toString())
            )

            return if (rows > 0) {
                ResetPasswordResult.SUCCESS
            } else {
                ResetPasswordResult.NOT_FOUND
            }
        }
    }

    fun resetPasswordByEmailOnly(
        email: String,
        newPassword: CharArray
    ): ResetPasswordResult {
        val normalizedEmail = email.trim().lowercase()

        if (normalizedEmail.isBlank()) {
            return ResetPasswordResult.NOT_FOUND
        }

        readable.rawQuery(
            """
            SELECT
                ${DbContract.U_ID},
                ${DbContract.U_STATUS}
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_EMAIL} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(normalizedEmail)
        ).use { c ->
            if (!c.moveToFirst()) return ResetPasswordResult.NOT_FOUND

            val userId = c.getLong(0)
            val status = c.getString(1)

            if (status == DbContract.STATUS_DISABLED) {
                return ResetPasswordResult.DISABLED
            }

            val cv = ContentValues().apply {
                put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(newPassword))
                put(DbContract.U_FORCE_PW_CHANGE, 0)
            }

            val rows = writable.update(
                DbContract.T_USERS,
                cv,
                "${DbContract.U_ID} = ?",
                arrayOf(userId.toString())
            )

            return if (rows > 0) {
                ResetPasswordResult.SUCCESS
            } else {
                ResetPasswordResult.NOT_FOUND
            }
        }
    }

    /**
     * Called from LoginActivity on startup to guarantee demo/staff accounts
     * and sample decks exist (wraps SeederDao).
     */
    fun ensureDemoAccounts() {
        val db = writable
        db.beginTransaction()
        try {
            DbSeeder.seedStaffAccounts(db)
            DbSeeder.seedDemoDecksAndCards(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- PREMIUM FLAG ----------

    /**
     * Returns true if the user has is_premium_user = 1.
     */
    fun isPremiumUser(userId: Long): Boolean {
        readable.rawQuery(
            """
            SELECT COALESCE(${DbContract.U_IS_PREMIUM_USER}, 0)
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ID} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return false
            return c.getInt(0) == 1
        }
    }

    /**
     * Backwards-compatible name (if old code calls isUserPremium).
     */
    fun isUserPremium(userId: Long): Boolean = isPremiumUser(userId)

    /**
     * Sets or clears the premium flag for a user.
     */
    fun setUserPremium(userId: Long, isPremium: Boolean): Int {
        val cv = ContentValues().apply {
            put(DbContract.U_IS_PREMIUM_USER, if (isPremium) 1 else 0)
        }
        return writable.update(
            DbContract.T_USERS,
            cv,
            "${DbContract.U_ID} = ?",
            arrayOf(userId.toString())
        )
    }

    // ---------- LAST LOGIN ----------

    /**
     * Returns last_login_at (epoch millis) or null if never logged in.
     */
    fun getLastLoginAt(userId: Long): Long? {
        readable.rawQuery(
            """
            SELECT ${DbContract.U_LAST_LOGIN_AT}
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ID} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            return if (c.isNull(0)) null else c.getLong(0)
        }
    }

    /**
     * Updates last_login_at for a user. Returns number of rows updated.
     * By default uses current time.
     */
    fun updateLastLoginAt(userId: Long, whenMs: Long = System.currentTimeMillis()): Int {
        val cv = ContentValues().apply {
            put(DbContract.U_LAST_LOGIN_AT, whenMs)
        }
        return writable.update(
            DbContract.T_USERS,
            cv,
            "${DbContract.U_ID} = ?",
            arrayOf(userId.toString())
        )
    }

    // ---------- MONTHLY ACTIVE / INACTIVE USERS ----------

    /**
     * Monthly Active Users (MAU):
     * users with role = user, status = active and last_login_at within this month.
     */
    fun countMonthlyActiveUsers(): Int {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        val start = now.withDayOfMonth(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = now.plusMonths(1)
            .withDayOfMonth(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ROLE} = ?
              AND ${DbContract.U_STATUS} = ?
              AND ${DbContract.U_LAST_LOGIN_AT} IS NOT NULL
              AND ${DbContract.U_LAST_LOGIN_AT} >= ?
              AND ${DbContract.U_LAST_LOGIN_AT} < ?
            """.trimIndent(),
            arrayOf(
                DbContract.ROLE_USER,
                DbContract.STATUS_ACTIVE,
                start.toString(),
                end.toString()
            )
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /**
     * Monthly Inactive Users:
     * users with role = user, status = active and
     * (never logged in OR last_login_at before this month start).
     */
    fun countMonthlyInactiveUsers(): Int {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        val start = now.withDayOfMonth(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ROLE} = ?
              AND ${DbContract.U_STATUS} = ?
              AND (
                    ${DbContract.U_LAST_LOGIN_AT} IS NULL
                 OR ${DbContract.U_LAST_LOGIN_AT} < ?
              )
            """.trimIndent(),
            arrayOf(
                DbContract.ROLE_USER,
                DbContract.STATUS_ACTIVE,
                start.toString()
            )
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}