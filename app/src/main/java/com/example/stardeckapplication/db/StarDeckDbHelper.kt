package com.example.stardeckapplication.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.stardeckapplication.model.UserSession
import com.example.stardeckapplication.util.PasswordHasher
import kotlin.math.max

class StarDeckDbHelper(context: Context) :
    SQLiteOpenHelper(context, DbContract.DB_NAME, null, DbContract.DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createUsersTable(db)
        createDecksTable(db)
        createCardsTable(db)
        createStudySessionsTable(db)
        createReportsTable(db)
        seedStaffAccounts(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createDecksTable(db)
        if (oldVersion < 3) createCardsTable(db)
        if (oldVersion < 4) createStudySessionsTable(db)
        if (oldVersion < 5) addDeckStatusColumn(db)
        if (oldVersion < 6) createReportsTable(db)
        if (oldVersion < 7) addPremiumColumns(db)
        if (oldVersion < 8) addLastLoginColumn(db)
    }

    private fun addLastLoginColumn(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE ${DbContract.T_USERS} ADD COLUMN ${DbContract.U_LAST_LOGIN_AT} INTEGER")
        } catch (_: Exception) {
            // already exists
        }
    }

    fun getLastLoginAt(userId: Long): Long? {
        readableDatabase.rawQuery(
            """
        SELECT ${DbContract.U_LAST_LOGIN_AT}
        FROM ${DbContract.T_USERS}
        WHERE ${DbContract.U_ID}=?
        LIMIT 1
        """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            return if (c.isNull(0)) null else c.getLong(0)
        }
    }

    fun updateLastLoginAt(userId: Long, whenMs: Long): Int {
        val cv = ContentValues().apply { put(DbContract.U_LAST_LOGIN_AT, whenMs) }
        return writableDatabase.update(
            DbContract.T_USERS,
            cv,
            "${DbContract.U_ID}=?",
            arrayOf(userId.toString())
        )
    }

    /**
     * Monthly Active Users (MAU) for PRODUCT = normal users who logged in this month.
     * Only counts ROLE_USER and STATUS_ACTIVE accounts.
     */
    fun countMonthlyActiveUsers(): Int {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.now(zone)
        val start = now.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

        readableDatabase.rawQuery(
            """
        SELECT COUNT(*)
        FROM ${DbContract.T_USERS}
        WHERE ${DbContract.U_ROLE}=?
          AND ${DbContract.U_STATUS}=?
          AND ${DbContract.U_LAST_LOGIN_AT} IS NOT NULL
          AND ${DbContract.U_LAST_LOGIN_AT}>=?
          AND ${DbContract.U_LAST_LOGIN_AT}<?
        """.trimIndent(),
            arrayOf(DbContract.ROLE_USER, DbContract.STATUS_ACTIVE, start.toString(), end.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun countMonthlyInactiveUsers(): Int {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.now(zone)
        val start = now.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

        readableDatabase.rawQuery(
            """
        SELECT COUNT(*)
        FROM ${DbContract.T_USERS}
        WHERE ${DbContract.U_ROLE}=?
          AND ${DbContract.U_STATUS}=?
          AND (${DbContract.U_LAST_LOGIN_AT} IS NULL OR ${DbContract.U_LAST_LOGIN_AT}<?)
        """.trimIndent(),
            arrayOf(DbContract.ROLE_USER, DbContract.STATUS_ACTIVE, start.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    // ---------- TABLES ----------
    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.T_USERS}(
                ${DbContract.U_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.U_NAME} TEXT NOT NULL,
                ${DbContract.U_EMAIL} TEXT NOT NULL UNIQUE,
                ${DbContract.U_PASSWORD_HASH} TEXT NOT NULL,
                ${DbContract.U_ROLE} TEXT NOT NULL,
                ${DbContract.U_STATUS} TEXT NOT NULL,
                ${DbContract.U_ACCEPTED_TERMS} INTEGER NOT NULL,
                ${DbContract.U_FORCE_PW_CHANGE} INTEGER NOT NULL,
                ${DbContract.U_CREATED_AT} INTEGER NOT NULL,
                ${DbContract.U_IS_PREMIUM_USER} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.U_LAST_LOGIN_AT} INTEGER
            )
            """.trimIndent()
        )
    }

    private fun createDecksTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.T_DECKS}(
                ${DbContract.D_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.D_OWNER_USER_ID} INTEGER NOT NULL,
                ${DbContract.D_TITLE} TEXT NOT NULL,
                ${DbContract.D_DESCRIPTION} TEXT,
                ${DbContract.D_CREATED_AT} INTEGER NOT NULL,
                ${DbContract.D_STATUS} TEXT NOT NULL DEFAULT '${DbContract.DECK_ACTIVE}',
                ${DbContract.D_IS_PREMIUM} INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(${DbContract.D_OWNER_USER_ID}) REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID})
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_owner ON ${DbContract.T_DECKS}(${DbContract.D_OWNER_USER_ID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_status ON ${DbContract.T_DECKS}(${DbContract.D_STATUS})")
    }

    private fun addDeckStatusColumn(db: SQLiteDatabase) {
        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.T_DECKS} ADD COLUMN ${DbContract.D_STATUS} TEXT NOT NULL DEFAULT '${DbContract.DECK_ACTIVE}'"
            )
        } catch (_: Exception) {}
        try {
            db.execSQL(
                "UPDATE ${DbContract.T_DECKS} SET ${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}' WHERE ${DbContract.D_STATUS} IS NULL"
            )
        } catch (_: Exception) {}
    }

    private fun addPremiumColumns(db: SQLiteDatabase) {
        // users.is_premium_user
        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.T_USERS} ADD COLUMN ${DbContract.U_IS_PREMIUM_USER} INTEGER NOT NULL DEFAULT 0"
            )
        } catch (_: Exception) {}
        // decks.is_premium
        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.T_DECKS} ADD COLUMN ${DbContract.D_IS_PREMIUM} INTEGER NOT NULL DEFAULT 0"
            )
        } catch (_: Exception) {}

        // Safety cleanup
        try { db.execSQL("UPDATE ${DbContract.T_USERS} SET ${DbContract.U_IS_PREMIUM_USER}=0 WHERE ${DbContract.U_IS_PREMIUM_USER} IS NULL") } catch (_: Exception) {}
        try { db.execSQL("UPDATE ${DbContract.T_DECKS} SET ${DbContract.D_IS_PREMIUM}=0 WHERE ${DbContract.D_IS_PREMIUM} IS NULL") } catch (_: Exception) {}
    }

    private fun createCardsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.T_CARDS}(
                ${DbContract.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.C_DECK_ID} INTEGER NOT NULL,
                ${DbContract.C_FRONT} TEXT NOT NULL,
                ${DbContract.C_BACK} TEXT NOT NULL,
                ${DbContract.C_CREATED_AT} INTEGER NOT NULL,
                FOREIGN KEY(${DbContract.C_DECK_ID}) REFERENCES ${DbContract.T_DECKS}(${DbContract.D_ID}) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cards_deck ON ${DbContract.T_CARDS}(${DbContract.C_DECK_ID})")
    }

    private fun createStudySessionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.T_STUDY_SESSIONS}(
                ${DbContract.S_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.S_USER_ID} INTEGER NOT NULL,
                ${DbContract.S_DECK_ID} INTEGER NOT NULL,
                ${DbContract.S_RESULT} TEXT NOT NULL,
                ${DbContract.S_CREATED_AT} INTEGER NOT NULL,
                FOREIGN KEY(${DbContract.S_USER_ID}) REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.S_DECK_ID}) REFERENCES ${DbContract.T_DECKS}(${DbContract.D_ID}) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_user ON ${DbContract.T_STUDY_SESSIONS}(${DbContract.S_USER_ID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_deck ON ${DbContract.T_STUDY_SESSIONS}(${DbContract.S_DECK_ID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_created ON ${DbContract.T_STUDY_SESSIONS}(${DbContract.S_CREATED_AT})")
    }

    private fun createReportsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.T_REPORTS}(
                ${DbContract.R_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.R_REPORTER_USER_ID} INTEGER NOT NULL,
                ${DbContract.R_DECK_ID} INTEGER NOT NULL,
                ${DbContract.R_REASON} TEXT NOT NULL,
                ${DbContract.R_DETAILS} TEXT,
                ${DbContract.R_STATUS} TEXT NOT NULL DEFAULT '${DbContract.REPORT_OPEN}',
                ${DbContract.R_CREATED_AT} INTEGER NOT NULL,
                FOREIGN KEY(${DbContract.R_REPORTER_USER_ID}) REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.R_DECK_ID}) REFERENCES ${DbContract.T_DECKS}(${DbContract.D_ID}) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_status ON ${DbContract.T_REPORTS}(${DbContract.R_STATUS})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_deck ON ${DbContract.T_REPORTS}(${DbContract.R_DECK_ID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_created ON ${DbContract.T_REPORTS}(${DbContract.R_CREATED_AT})")
    }

    // ---------- SEED ----------
    private fun seedStaffAccounts(db: SQLiteDatabase) {
        ensureUser(db, "Admin", "admin@stardeck.local", "Admin@1234", DbContract.ROLE_ADMIN, true)
        ensureUser(db, "Manager", "manager@stardeck.local", "Manager@1234", DbContract.ROLE_MANAGER, true)
    }

    private fun ensureUser(
        db: SQLiteDatabase,
        name: String,
        email: String,
        password: String,
        role: String,
        forcePwChange: Boolean
    ) {
        val exists = db.rawQuery(
            "SELECT 1 FROM ${DbContract.T_USERS} WHERE ${DbContract.U_EMAIL}=? LIMIT 1",
            arrayOf(email.trim().lowercase())
        ).use { it.moveToFirst() }
        if (exists) return

        val cv = ContentValues().apply {
            put(DbContract.U_NAME, name.trim())
            put(DbContract.U_EMAIL, email.trim().lowercase())
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(password.toCharArray()))
            put(DbContract.U_ROLE, role)
            put(DbContract.U_STATUS, DbContract.STATUS_ACTIVE)
            put(DbContract.U_ACCEPTED_TERMS, 1)
            put(DbContract.U_FORCE_PW_CHANGE, if (forcePwChange) 1 else 0)
            put(DbContract.U_CREATED_AT, System.currentTimeMillis())
            put(DbContract.U_IS_PREMIUM_USER, 0)
        }
        db.insertOrThrow(DbContract.T_USERS, null, cv)
    }

    // ---------- PREMIUM (NEW) ----------
    fun isUserPremium(userId: Long): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.U_IS_PREMIUM_USER}
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ID}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            return c.moveToFirst() && c.getInt(0) == 1
        }
    }

    fun setUserPremium(userId: Long, enabled: Boolean): Int {
        val cv = ContentValues().apply { put(DbContract.U_IS_PREMIUM_USER, if (enabled) 1 else 0) }
        return writableDatabase.update(
            DbContract.T_USERS,
            cv,
            "${DbContract.U_ID}=?",
            arrayOf(userId.toString())
        )
    }

    /**
     * Creates a premium demo deck (once) for the user, with a few cards.
     * This deck appears locked until the user upgrades.
     */
    fun createPremiumDemoDeckForUser(ownerUserId: Long): Long {
        // If exists, return existing id
        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.D_ID}
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID}=? AND ${DbContract.D_IS_PREMIUM}=1
              AND ${DbContract.D_TITLE}='Premium Demo Deck'
            LIMIT 1
            """.trimIndent(),
            arrayOf(ownerUserId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }

        val now = System.currentTimeMillis()
        val deckId = writableDatabase.insertOrThrow(
            DbContract.T_DECKS,
            null,
            ContentValues().apply {
                put(DbContract.D_OWNER_USER_ID, ownerUserId)
                put(DbContract.D_TITLE, "Premium Demo Deck")
                put(DbContract.D_DESCRIPTION, "Demo premium content (locked until you upgrade).")
                put(DbContract.D_CREATED_AT, now)
                put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM, 1)
            }
        )

        // Insert a few sample cards (direct insert = safe)
        fun add(front: String, back: String) {
            writableDatabase.insertOrThrow(
                DbContract.T_CARDS,
                null,
                ContentValues().apply {
                    put(DbContract.C_DECK_ID, deckId)
                    put(DbContract.C_FRONT, front)
                    put(DbContract.C_BACK, back)
                    put(DbContract.C_CREATED_AT, System.currentTimeMillis())
                }
            )
        }

        add("Serendipity", "Finding something good without looking for it.")
        add("Ubiquitous", "Present everywhere.")
        add("Meticulous", "Very careful and precise.")

        return deckId
    }

    // ---------- AUTH ----------
    fun registerUser(name: String, email: String, password: CharArray, acceptedTerms: Boolean): Long {
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
        return writableDatabase.insertOrThrow(DbContract.T_USERS, null, cv)
    }

    fun authenticate(email: String, password: CharArray): UserSession? {
        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.U_ID}, ${DbContract.U_NAME}, ${DbContract.U_EMAIL}, ${DbContract.U_PASSWORD_HASH},
                   ${DbContract.U_ROLE}, ${DbContract.U_STATUS}, ${DbContract.U_FORCE_PW_CHANGE}
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_EMAIL}=?
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
            return UserSession(id, name, em, role, status, force)
        }
    }

    fun updatePassword(userId: Long, newPassword: CharArray, forcePwChange: Boolean) {
        val cv = ContentValues().apply {
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(newPassword))
            put(DbContract.U_FORCE_PW_CHANGE, if (forcePwChange) 1 else 0)
        }
        writableDatabase.update(
            DbContract.T_USERS, cv,
            "${DbContract.U_ID}=?",
            arrayOf(userId.toString())
        )
    }

    // ---------- ADMIN ----------
    data class SimpleUserRow(val id: Long, val name: String, val email: String, val role: String, val status: String)

    fun adminCountAllUsers(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_USERS}",
            null
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun adminCountDisabledUsers(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_USERS} WHERE ${DbContract.U_STATUS}=?",
            arrayOf(DbContract.STATUS_DISABLED)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun adminCountPremiumUsers(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_USERS} WHERE ${DbContract.U_IS_PREMIUM_USER}=1",
            null
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun adminGetPremiumUserIds(): Set<Long> {
        val out = mutableSetOf<Long>()
        readableDatabase.rawQuery(
            "SELECT ${DbContract.U_ID} FROM ${DbContract.T_USERS} WHERE ${DbContract.U_IS_PREMIUM_USER}=1",
            null
        ).use { c ->
            while (c.moveToNext()) out += c.getLong(0)
        }
        return out
    }

    fun adminCountAllDecks(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_DECKS}",
            null
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun adminCountHiddenDecks(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_STATUS}=?",
            arrayOf(DbContract.DECK_HIDDEN)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun adminCountOpenReports(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_REPORTS} WHERE ${DbContract.R_STATUS}=?",
            arrayOf(DbContract.REPORT_OPEN)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun adminGetAllUsers(): List<SimpleUserRow> {
        val out = mutableListOf<SimpleUserRow>()
        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.U_ID}, ${DbContract.U_NAME}, ${DbContract.U_EMAIL}, ${DbContract.U_ROLE}, ${DbContract.U_STATUS}
            FROM ${DbContract.T_USERS}
            ORDER BY ${DbContract.U_CREATED_AT} DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += SimpleUserRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
            }
        }
        return out
    }

    fun adminCreateStaff(
        name: String,
        email: String,
        tempPassword: CharArray,
        role: String,
        status: String = DbContract.STATUS_ACTIVE
    ): Long {
        val cv = ContentValues().apply {
            put(DbContract.U_NAME, name.trim())
            put(DbContract.U_EMAIL, email.trim().lowercase())
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(tempPassword))
            put(DbContract.U_ROLE, role)
            put(DbContract.U_STATUS, status)
            put(DbContract.U_ACCEPTED_TERMS, 1)
            put(DbContract.U_FORCE_PW_CHANGE, 1)
            put(DbContract.U_CREATED_AT, System.currentTimeMillis())
            put(DbContract.U_IS_PREMIUM_USER, 0)
        }
        return writableDatabase.insertOrThrow(DbContract.T_USERS, null, cv)
    }

    fun adminUpdateUser(id: Long, name: String, role: String, status: String) {
        val cv = ContentValues().apply {
            put(DbContract.U_NAME, name.trim())
            put(DbContract.U_ROLE, role)
            put(DbContract.U_STATUS, status)
        }
        writableDatabase.update(DbContract.T_USERS, cv, "${DbContract.U_ID}=?", arrayOf(id.toString()))
    }

    fun adminResetPassword(userId: Long, tempPassword: CharArray) {
        updatePassword(userId, tempPassword, forcePwChange = true)
    }

    // ---------- DECKS ----------
    data class DeckRow(
        val id: Long,
        val title: String,
        val description: String?,
        val createdAt: Long,
        val isPremium: Boolean
    )

    fun getDecksForOwner(ownerUserId: Long): List<DeckRow> {
        val out = mutableListOf<DeckRow>()
        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.D_ID}, ${DbContract.D_TITLE}, ${DbContract.D_DESCRIPTION}, ${DbContract.D_CREATED_AT}, ${DbContract.D_IS_PREMIUM}
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID}=? AND ${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            ORDER BY ${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(ownerUserId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += DeckRow(
                    id = c.getLong(0),
                    title = c.getString(1),
                    description = c.getString(2),
                    createdAt = c.getLong(3),
                    isPremium = c.getInt(4) == 1
                )
            }
        }
        return out
    }

    fun createDeck(ownerUserId: Long, title: String, description: String?): Long {
        val cv = ContentValues().apply {
            put(DbContract.D_OWNER_USER_ID, ownerUserId)
            put(DbContract.D_TITLE, title.trim())
            put(DbContract.D_DESCRIPTION, description?.trim().orEmpty().ifBlank { null })
            put(DbContract.D_CREATED_AT, System.currentTimeMillis())
            put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
            put(DbContract.D_IS_PREMIUM, 0)
        }
        return writableDatabase.insertOrThrow(DbContract.T_DECKS, null, cv)
    }

    fun updateDeck(ownerUserId: Long, deckId: Long, title: String, description: String?): Int {
        val cv = ContentValues().apply {
            put(DbContract.D_TITLE, title.trim())
            put(DbContract.D_DESCRIPTION, description?.trim().orEmpty().ifBlank { null })
        }
        return writableDatabase.update(
            DbContract.T_DECKS,
            cv,
            "${DbContract.D_ID}=? AND ${DbContract.D_OWNER_USER_ID}=?",
            arrayOf(deckId.toString(), ownerUserId.toString())
        )
    }

    fun deleteDeck(ownerUserId: Long, deckId: Long): Int {
        return writableDatabase.delete(
            DbContract.T_DECKS,
            "${DbContract.D_ID}=? AND ${DbContract.D_OWNER_USER_ID}=?",
            arrayOf(deckId.toString(), ownerUserId.toString())
        )
    }

    fun getDeckTitleForOwner(ownerUserId: Long, deckId: Long): String? {
        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.D_TITLE}
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_ID}=? AND ${DbContract.D_OWNER_USER_ID}=?
              AND ${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), ownerUserId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    // ---------- CARDS ----------
    data class CardRow(val id: Long, val front: String, val back: String, val createdAt: Long)

    fun getCardsForDeck(ownerUserId: Long, deckId: Long): List<CardRow> {
        val out = mutableListOf<CardRow>()
        readableDatabase.rawQuery(
            """
            SELECT c.${DbContract.C_ID}, c.${DbContract.C_FRONT}, c.${DbContract.C_BACK}, c.${DbContract.C_CREATED_AT}
            FROM ${DbContract.T_CARDS} c
            JOIN ${DbContract.T_DECKS} d ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
            WHERE c.${DbContract.C_DECK_ID}=? AND d.${DbContract.D_OWNER_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            ORDER BY c.${DbContract.C_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(deckId.toString(), ownerUserId.toString())
        ).use { c ->
            while (c.moveToNext()) out += CardRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
        }
        return out
    }

    fun getCardsForDeckAny(deckId: Long): List<CardRow> {
        val out = mutableListOf<CardRow>()
        readableDatabase.rawQuery(
            """
        SELECT ${DbContract.C_ID}, ${DbContract.C_FRONT}, ${DbContract.C_BACK}, ${DbContract.C_CREATED_AT}
        FROM ${DbContract.T_CARDS}
        WHERE ${DbContract.C_DECK_ID}=?
        ORDER BY ${DbContract.C_CREATED_AT} DESC
        """.trimIndent(),
            arrayOf(deckId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += CardRow(
                    id = c.getLong(0),
                    front = c.getString(1),
                    back = c.getString(2),
                    createdAt = c.getLong(3)
                )
            }
        }
        return out
    }

    fun createCard(ownerUserId: Long, deckId: Long, front: String, back: String): Long {
        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID, deckId)
            put(DbContract.C_FRONT, front.trim())
            put(DbContract.C_BACK, back.trim())
            put(DbContract.C_CREATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    fun updateCard(ownerUserId: Long, deckId: Long, cardId: Long, front: String, back: String): Int {
        val cv = ContentValues().apply {
            put(DbContract.C_FRONT, front.trim())
            put(DbContract.C_BACK, back.trim())
        }
        return writableDatabase.update(
            DbContract.T_CARDS,
            cv,
            "${DbContract.C_ID}=? AND ${DbContract.C_DECK_ID}=?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    fun deleteCard(ownerUserId: Long, deckId: Long, cardId: Long): Int {
        return writableDatabase.delete(
            DbContract.T_CARDS,
            "${DbContract.C_ID}=? AND ${DbContract.C_DECK_ID}=?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    // ---------- STUDY ANALYTICS ----------
    data class DeckStudySummary(val deckId: Long, val title: String, val studyCount: Int, val lastStudiedAt: Long)

    fun logStudyResult(userId: Long, deckId: Long, result: String): Long {
        if (result != DbContract.RESULT_KNOWN && result != DbContract.RESULT_HARD) return -1L
        if (getDeckTitleForOwner(userId, deckId) == null) return -1L
        val cv = ContentValues().apply {
            put(DbContract.S_USER_ID, userId)
            put(DbContract.S_DECK_ID, deckId)
            put(DbContract.S_RESULT, result)
            put(DbContract.S_CREATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.insert(DbContract.T_STUDY_SESSIONS, null, cv)
    }

    fun deleteStudySession(userId: Long, sessionId: Long): Int {
        return writableDatabase.delete(
            DbContract.T_STUDY_SESSIONS,
            "${DbContract.S_ID}=? AND ${DbContract.S_USER_ID}=?",
            arrayOf(sessionId.toString(), userId.toString())
        )
    }

    fun getMostStudiedDeck(userId: Long): DeckStudySummary? {
        readableDatabase.rawQuery(
            """
            SELECT d.${DbContract.D_ID}, d.${DbContract.D_TITLE},
                   COUNT(s.${DbContract.S_ID}) AS cnt,
                   MAX(s.${DbContract.S_CREATED_AT}) AS last_at
            FROM ${DbContract.T_DECKS} d
            JOIN ${DbContract.T_STUDY_SESSIONS} s ON s.${DbContract.S_DECK_ID}=d.${DbContract.D_ID}
            WHERE d.${DbContract.D_OWNER_USER_ID}=? AND s.${DbContract.S_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            GROUP BY d.${DbContract.D_ID}
            ORDER BY cnt DESC, last_at DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), userId.toString())
        ).use { c ->
            return if (c.moveToFirst()) DeckStudySummary(c.getLong(0), c.getString(1), c.getInt(2), c.getLong(3)) else null
        }
    }

    fun getRecentlyStudiedDeck(userId: Long): DeckStudySummary? {
        readableDatabase.rawQuery(
            """
            SELECT d.${DbContract.D_ID}, d.${DbContract.D_TITLE},
                   (SELECT COUNT(*) FROM ${DbContract.T_STUDY_SESSIONS} ss
                    WHERE ss.${DbContract.S_USER_ID}=? AND ss.${DbContract.S_DECK_ID}=d.${DbContract.D_ID}) AS cnt,
                   MAX(s.${DbContract.S_CREATED_AT}) AS last_at
            FROM ${DbContract.T_DECKS} d
            JOIN ${DbContract.T_STUDY_SESSIONS} s ON s.${DbContract.S_DECK_ID}=d.${DbContract.D_ID}
            WHERE d.${DbContract.D_OWNER_USER_ID}=? AND s.${DbContract.S_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            GROUP BY d.${DbContract.D_ID}
            ORDER BY last_at DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), userId.toString(), userId.toString())
        ).use { c ->
            return if (c.moveToFirst()) DeckStudySummary(c.getLong(0), c.getString(1), c.getInt(2), c.getLong(3)) else null
        }
    }

    fun getTodayStudyCount(userId: Long): Int {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        readableDatabase.rawQuery(
            """
        SELECT COUNT(*) 
        FROM ${DbContract.T_STUDY_SESSIONS}
        WHERE ${DbContract.S_USER_ID}=? 
          AND ${DbContract.S_CREATED_AT}>=?
          AND ${DbContract.S_CREATED_AT}<?
        """.trimIndent(),
            arrayOf(userId.toString(), start.toString(), end.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /**
     * Streak = consecutive days with at least 1 study action, ending on the most recent study day.
     * (If user studied yesterday but not yet today, streak still shows and motivates them to keep it.)
     */
    fun getStudyStreakDays(userId: Long): Int {
        val zone = java.time.ZoneId.systemDefault()

        // Pull recent study timestamps (limit keeps it fast)
        val times = mutableListOf<Long>()
        readableDatabase.rawQuery(
            """
        SELECT ${DbContract.S_CREATED_AT}
        FROM ${DbContract.T_STUDY_SESSIONS}
        WHERE ${DbContract.S_USER_ID}=?
        ORDER BY ${DbContract.S_CREATED_AT} DESC
        LIMIT 800
        """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            while (c.moveToNext()) times += c.getLong(0)
        }

        if (times.isEmpty()) return 0

        // Convert to unique local "epochDay" list (descending)
        val uniqueDays = mutableListOf<Long>()
        var lastDay: Long? = null
        for (ms in times) {
            val day = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
            if (lastDay == null || day != lastDay) {
                uniqueDays += day
                lastDay = day
            }
        }

        // Count consecutive days: day, day-1, day-2...
        var streak = 1
        var expected = uniqueDays[0] - 1
        for (i in 1 until uniqueDays.size) {
            val d = uniqueDays[i]
            if (d == expected) {
                streak++
                expected--
            } else if (d < expected) {
                break
            }
        }
        return streak
    }

    fun getTotalStudyCount(userId: Long): Int {
        readableDatabase.rawQuery(
            """
        SELECT COUNT(*)
        FROM ${DbContract.T_STUDY_SESSIONS}
        WHERE ${DbContract.S_USER_ID}=?
        """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getTotalDeckCountAllStatuses(ownerUserId: Long): Int {
        readableDatabase.rawQuery(
            """
        SELECT COUNT(*)
        FROM ${DbContract.T_DECKS}
        WHERE ${DbContract.D_OWNER_USER_ID}=?
        """.trimIndent(),
            arrayOf(ownerUserId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getTotalCardCountForOwnerAllStatuses(ownerUserId: Long): Int {
        readableDatabase.rawQuery(
            """
        SELECT COUNT(*)
        FROM ${DbContract.T_CARDS} c
        JOIN ${DbContract.T_DECKS} d ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
        WHERE d.${DbContract.D_OWNER_USER_ID}=?
        """.trimIndent(),
            arrayOf(ownerUserId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    // ---------- MANAGER MODERATION ----------
    data class ManagerDeckRow(
        val deckId: Long,
        val title: String,
        val description: String?,
        val status: String,
        val ownerName: String,
        val ownerEmail: String,
        val cardCount: Int,
        val createdAt: Long
    )

    fun managerGetAllDecks(): List<ManagerDeckRow> {
        val out = mutableListOf<ManagerDeckRow>()
        readableDatabase.rawQuery(
            """
            SELECT d.${DbContract.D_ID}, d.${DbContract.D_TITLE}, d.${DbContract.D_DESCRIPTION}, d.${DbContract.D_STATUS},
                   u.${DbContract.U_NAME}, u.${DbContract.U_EMAIL}, d.${DbContract.D_CREATED_AT},
                   (SELECT COUNT(*) FROM ${DbContract.T_CARDS} c WHERE c.${DbContract.C_DECK_ID}=d.${DbContract.D_ID}) AS card_cnt
            FROM ${DbContract.T_DECKS} d
            JOIN ${DbContract.T_USERS} u ON u.${DbContract.U_ID}=d.${DbContract.D_OWNER_USER_ID}
            ORDER BY d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += ManagerDeckRow(
                    deckId = c.getLong(0),
                    title = c.getString(1),
                    description = c.getString(2),
                    status = c.getString(3),
                    ownerName = c.getString(4),
                    ownerEmail = c.getString(5),
                    createdAt = c.getLong(6),
                    cardCount = max(0, c.getInt(7))
                )
            }
        }
        return out
    }

    fun managerSetDeckStatus(deckId: Long, status: String): Int {
        if (status != DbContract.DECK_ACTIVE && status != DbContract.DECK_HIDDEN) return 0
        val cv = ContentValues().apply { put(DbContract.D_STATUS, status) }
        return writableDatabase.update(
            DbContract.T_DECKS, cv,
            "${DbContract.D_ID}=?",
            arrayOf(deckId.toString())
        )
    }

    // ---------- REPORTS ----------
    fun createDeckReport(reporterUserId: Long, deckId: Long, reason: String, details: String?): Long {
        if (reason.trim().length < 3) return -1L
        if (getDeckTitleForOwner(reporterUserId, deckId) == null) return -1L

        val cv = ContentValues().apply {
            put(DbContract.R_REPORTER_USER_ID, reporterUserId)
            put(DbContract.R_DECK_ID, deckId)
            put(DbContract.R_REASON, reason.trim())
            put(DbContract.R_DETAILS, details?.trim().orEmpty().ifBlank { null })
            put(DbContract.R_STATUS, DbContract.REPORT_OPEN)
            put(DbContract.R_CREATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.insert(DbContract.T_REPORTS, null, cv)
    }

    data class ReportRow(
        val reportId: Long,
        val deckId: Long,
        val deckTitle: String,
        val deckStatus: String,
        val ownerEmail: String,
        val reporterEmail: String,
        val reason: String,
        val details: String?,
        val createdAt: Long,
        val status: String
    )

    fun managerGetReports(): List<ReportRow> {
        val out = mutableListOf<ReportRow>()
        readableDatabase.rawQuery(
            """
            SELECT r.${DbContract.R_ID},
                   d.${DbContract.D_ID},
                   d.${DbContract.D_TITLE},
                   d.${DbContract.D_STATUS},
                   owner.${DbContract.U_EMAIL} AS owner_email,
                   reporter.${DbContract.U_EMAIL} AS reporter_email,
                   r.${DbContract.R_REASON},
                   r.${DbContract.R_DETAILS},
                   r.${DbContract.R_CREATED_AT},
                   r.${DbContract.R_STATUS}
            FROM ${DbContract.T_REPORTS} r
            JOIN ${DbContract.T_DECKS} d ON d.${DbContract.D_ID}=r.${DbContract.R_DECK_ID}
            JOIN ${DbContract.T_USERS} owner ON owner.${DbContract.U_ID}=d.${DbContract.D_OWNER_USER_ID}
            JOIN ${DbContract.T_USERS} reporter ON reporter.${DbContract.U_ID}=r.${DbContract.R_REPORTER_USER_ID}
            ORDER BY r.${DbContract.R_CREATED_AT} DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += ReportRow(
                    reportId = c.getLong(0),
                    deckId = c.getLong(1),
                    deckTitle = c.getString(2),
                    deckStatus = c.getString(3),
                    ownerEmail = c.getString(4),
                    reporterEmail = c.getString(5),
                    reason = c.getString(6),
                    details = c.getString(7),
                    createdAt = c.getLong(8),
                    status = c.getString(9)
                )
            }
        }
        return out
    }

    fun managerResolveReport(reportId: Long): Int {
        val cv = ContentValues().apply { put(DbContract.R_STATUS, DbContract.REPORT_RESOLVED) }
        return writableDatabase.update(
            DbContract.T_REPORTS, cv,
            "${DbContract.R_ID}=?",
            arrayOf(reportId.toString())
        )
    }
}