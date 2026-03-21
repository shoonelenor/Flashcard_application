package com.example.stardeckapplication.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.stardeckapplication.model.UserSession
import com.example.stardeckapplication.util.PasswordHasher
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
        createCardProgressTable(db)
        createStudySessionsTable(db)
        createReportsTable(db)
        seedStaffAccounts(db)
        seedDemoDecksAndCards(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createDecksTable(db)
        if (oldVersion < 3) createCardsTable(db)
        if (oldVersion < 4) createStudySessionsTable(db)
        if (oldVersion < 5) addDeckStatusColumn(db)
        if (oldVersion < 6) createReportsTable(db)
        if (oldVersion < 7) addPremiumColumns(db)
        if (oldVersion < 8) addLastLoginColumn(db)
        if (oldVersion < 9) migrateToVersion9(db)
        if (oldVersion < 10) addDeckPublicColumn(db)

        seedStaffAccounts(db)
        seedDemoDecksAndCards(db)
    }

    // ---------- SMALL HELPERS ----------
    private fun deckExistsAny(deckId: Long): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_ID}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString())
        ).use { c ->
            return c.moveToFirst()
        }
    }

    private fun activeAdminExists(userId: Long): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ID}=?
              AND ${DbContract.U_ROLE}=?
              AND ${DbContract.U_STATUS}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                userId.toString(),
                DbContract.ROLE_ADMIN,
                DbContract.STATUS_ACTIVE
            )
        ).use { c ->
            return c.moveToFirst()
        }
    }

    private fun activeUserExists(userId: Long): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ID}=?
              AND ${DbContract.U_ROLE}=?
              AND ${DbContract.U_STATUS}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                userId.toString(),
                DbContract.ROLE_USER,
                DbContract.STATUS_ACTIVE
            )
        ).use { c ->
            return c.moveToFirst()
        }
    }

    private fun adminOwnsDeck(adminUserId: Long, deckId: Long): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID}=d.${DbContract.D_OWNER_USER_ID}
            WHERE d.${DbContract.D_ID}=?
              AND d.${DbContract.D_OWNER_USER_ID}=?
              AND u.${DbContract.U_ROLE}=?
              AND u.${DbContract.U_STATUS}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                deckId.toString(),
                adminUserId.toString(),
                DbContract.ROLE_ADMIN,
                DbContract.STATUS_ACTIVE
            )
        ).use { c ->
            return c.moveToFirst()
        }
    }

    fun isPremiumUser(userId: Long): Boolean {
        readableDatabase.rawQuery(
            """
        SELECT COALESCE(${DbContract.U_IS_PREMIUM_USER}, 0)
        FROM ${DbContract.T_USERS}
        WHERE ${DbContract.U_ID}=?
        LIMIT 1
        """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            return c.moveToFirst() && c.getInt(0) == 1
        }
    }

    /**
     * Backward-compatible name used by older UI files.
     */
    fun isUserPremium(userId: Long): Boolean {
        return isPremiumUser(userId)
    }

    // ---------- ADMIN CONTENT OWNERS (kept for compatibility) ----------
    data class AdminContentOwnerRow(
        val id: Long,
        val name: String,
        val email: String
    )

    fun adminGetContentOwners(): List<AdminContentOwnerRow> {
        val out = mutableListOf<AdminContentOwnerRow>()

        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.U_ID}, ${DbContract.U_NAME}, ${DbContract.U_EMAIL}
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ROLE}=?
            ORDER BY ${DbContract.U_NAME} COLLATE NOCASE ASC, ${DbContract.U_EMAIL} COLLATE NOCASE ASC
            """.trimIndent(),
            arrayOf(DbContract.ROLE_USER)
        ).use { c ->
            while (c.moveToNext()) {
                out += AdminContentOwnerRow(
                    id = c.getLong(0),
                    name = c.getString(1),
                    email = c.getString(2)
                )
            }
        }

        return out
    }

    // ---------- ADMIN CONTENT SETUP ----------
    data class AdminDeckContentRow(
        val id: Long,
        val ownerUserId: Long,
        val ownerName: String,
        val ownerEmail: String,
        val title: String,
        val description: String?,
        val createdAt: Long,
        val status: String,
        val isPremium: Boolean,
        val isPublic: Boolean,
        val cardCount: Int
    )

    data class PublicDeckCatalogRow(
        val deckId: Long,
        val title: String,
        val description: String?,
        val ownerName: String,
        val isPremium: Boolean,
        val isLocked: Boolean,
        val cardCount: Int
    )

    fun adminGetOwnDeckContent(adminUserId: Long): List<AdminDeckContentRow> {
        if (!activeAdminExists(adminUserId)) return emptyList()

        val out = mutableListOf<AdminDeckContentRow>()
        readableDatabase.rawQuery(
            """
            SELECT
                d.${DbContract.D_ID},
                d.${DbContract.D_OWNER_USER_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                d.${DbContract.D_CREATED_AT},
                d.${DbContract.D_STATUS},
                COALESCE(d.${DbContract.D_IS_PREMIUM}, 0),
                COALESCE(d.${DbContract.D_IS_PUBLIC}, 0),
                COUNT(c.${DbContract.C_ID}) AS card_count
            FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID}=d.${DbContract.D_OWNER_USER_ID}
            LEFT JOIN ${DbContract.T_CARDS} c
                ON c.${DbContract.C_DECK_ID}=d.${DbContract.D_ID}
            WHERE d.${DbContract.D_OWNER_USER_ID}=?
            GROUP BY
                d.${DbContract.D_ID},
                d.${DbContract.D_OWNER_USER_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                d.${DbContract.D_CREATED_AT},
                d.${DbContract.D_STATUS},
                d.${DbContract.D_IS_PREMIUM},
                d.${DbContract.D_IS_PUBLIC}
            ORDER BY d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(adminUserId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += AdminDeckContentRow(
                    id = c.getLong(0),
                    ownerUserId = c.getLong(1),
                    ownerName = c.getString(2),
                    ownerEmail = c.getString(3),
                    title = c.getString(4),
                    description = c.getString(5),
                    createdAt = c.getLong(6),
                    status = c.getString(7),
                    isPremium = c.getInt(8) == 1,
                    isPublic = c.getInt(9) == 1,
                    cardCount = c.getInt(10)
                )
            }
        }
        return out
    }

    fun adminGetAllDeckContent(): List<AdminDeckContentRow> {
        val out = mutableListOf<AdminDeckContentRow>()

        readableDatabase.rawQuery(
            """
            SELECT
                d.${DbContract.D_ID},
                d.${DbContract.D_OWNER_USER_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                d.${DbContract.D_CREATED_AT},
                d.${DbContract.D_STATUS},
                COALESCE(d.${DbContract.D_IS_PREMIUM}, 0),
                COALESCE(d.${DbContract.D_IS_PUBLIC}, 0),
                COUNT(c.${DbContract.C_ID}) AS card_count
            FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID}=d.${DbContract.D_OWNER_USER_ID}
            LEFT JOIN ${DbContract.T_CARDS} c
                ON c.${DbContract.C_DECK_ID}=d.${DbContract.D_ID}
            GROUP BY
                d.${DbContract.D_ID},
                d.${DbContract.D_OWNER_USER_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                d.${DbContract.D_CREATED_AT},
                d.${DbContract.D_STATUS},
                d.${DbContract.D_IS_PREMIUM},
                d.${DbContract.D_IS_PUBLIC}
            ORDER BY d.${DbContract.D_IS_PREMIUM} DESC, d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += AdminDeckContentRow(
                    id = c.getLong(0),
                    ownerUserId = c.getLong(1),
                    ownerName = c.getString(2),
                    ownerEmail = c.getString(3),
                    title = c.getString(4),
                    description = c.getString(5),
                    createdAt = c.getLong(6),
                    status = c.getString(7),
                    isPremium = c.getInt(8) == 1,
                    isPublic = c.getInt(9) == 1,
                    cardCount = c.getInt(10)
                )
            }
        }

        return out
    }

    fun adminCreateDeckContentForAdmin(
        adminUserId: Long,
        title: String,
        description: String?,
        isPremium: Boolean,
        isPublic: Boolean,
        isHidden: Boolean
    ): Long {
        if (!activeAdminExists(adminUserId)) return -1L
        return adminCreateDeckContent(
            ownerUserId = adminUserId,
            title = title,
            description = description,
            isPremium = isPremium,
            isHidden = isHidden,
            isPublic = isPublic
        )
    }

    fun adminCreateDeckContent(
        ownerUserId: Long,
        title: String,
        description: String?,
        isPremium: Boolean,
        isHidden: Boolean,
        isPublic: Boolean = true
    ): Long {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return -1L
        if (!activeAdminExists(ownerUserId) && !activeUserExists(ownerUserId)) return -1L

        val cv = ContentValues().apply {
            put(DbContract.D_OWNER_USER_ID, ownerUserId)
            put(DbContract.D_TITLE, cleanTitle)
            put(DbContract.D_DESCRIPTION, description?.trim().orEmpty().ifBlank { null })
            put(DbContract.D_CREATED_AT, System.currentTimeMillis())
            put(DbContract.D_STATUS, if (isHidden) DbContract.DECK_HIDDEN else DbContract.DECK_ACTIVE)
            put(DbContract.D_IS_PREMIUM, if (isPremium) 1 else 0)
            put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
        }

        return writableDatabase.insertOrThrow(DbContract.T_DECKS, null, cv)
    }

    fun adminUpdateDeckContentForAdmin(
        adminUserId: Long,
        deckId: Long,
        title: String,
        description: String?,
        isPremium: Boolean,
        isPublic: Boolean,
        isHidden: Boolean
    ): Int {
        if (!adminOwnsDeck(adminUserId, deckId)) return 0

        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return 0

        val cv = ContentValues().apply {
            put(DbContract.D_TITLE, cleanTitle)
            put(DbContract.D_DESCRIPTION, description?.trim().orEmpty().ifBlank { null })
            put(DbContract.D_IS_PREMIUM, if (isPremium) 1 else 0)
            put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
            put(DbContract.D_STATUS, if (isHidden) DbContract.DECK_HIDDEN else DbContract.DECK_ACTIVE)
        }

        return writableDatabase.update(
            DbContract.T_DECKS,
            cv,
            "${DbContract.D_ID}=? AND ${DbContract.D_OWNER_USER_ID}=?",
            arrayOf(deckId.toString(), adminUserId.toString())
        )
    }

    // keep old call compatible
    fun adminUpdateDeckContent(
        deckId: Long,
        title: String,
        description: String?,
        isPremium: Boolean,
        isHidden: Boolean,
        isPublic: Boolean
    ): Int {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return 0

        val cv = ContentValues().apply {
            put(DbContract.D_TITLE, cleanTitle)
            put(DbContract.D_DESCRIPTION, description?.trim().orEmpty().ifBlank { null })
            put(DbContract.D_IS_PREMIUM, if (isPremium) 1 else 0)
            put(DbContract.D_STATUS, if (isHidden) DbContract.DECK_HIDDEN else DbContract.DECK_ACTIVE)
            put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
        }

        return writableDatabase.update(
            DbContract.T_DECKS,
            cv,
            "${DbContract.D_ID}=?",
            arrayOf(deckId.toString())
        )
    }

    fun adminDeleteDeckContentForAdmin(adminUserId: Long, deckId: Long): Int {
        if (!adminOwnsDeck(adminUserId, deckId)) return 0

        val db = writableDatabase
        db.beginTransaction()
        return try {
            db.delete(
                DbContract.T_CARDS,
                "${DbContract.C_DECK_ID}=?",
                arrayOf(deckId.toString())
            )

            val rows = db.delete(
                DbContract.T_DECKS,
                "${DbContract.D_ID}=? AND ${DbContract.D_OWNER_USER_ID}=?",
                arrayOf(deckId.toString(), adminUserId.toString())
            )

            if (rows == 1) db.setTransactionSuccessful()
            rows
        } finally {
            db.endTransaction()
        }
    }

    fun adminDeleteDeckContent(deckId: Long): Int {
        return writableDatabase.delete(
            DbContract.T_DECKS,
            "${DbContract.D_ID}=?",
            arrayOf(deckId.toString())
        )
    }

    fun getDeckTitleAny(deckId: Long): String? {
        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.D_TITLE}
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_ID}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun adminGetCardsForDeck(adminUserId: Long, deckId: Long): List<CardRow> {
        if (!adminOwnsDeck(adminUserId, deckId)) return emptyList()

        val out = mutableListOf<CardRow>()
        readableDatabase.rawQuery(
            """
            SELECT
                ${DbContract.C_ID},
                ${DbContract.C_FRONT},
                ${DbContract.C_BACK},
                ${DbContract.C_CREATED_AT}
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

    fun adminCreateCard(
        adminUserId: Long,
        deckId: Long,
        front: String,
        back: String
    ): Long {
        if (!adminOwnsDeck(adminUserId, deckId)) return -1L

        val cleanFront = front.trim()
        val cleanBack = back.trim()
        if (cleanFront.isBlank() || cleanBack.isBlank()) return -1L

        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID, deckId)
            put(DbContract.C_FRONT, cleanFront)
            put(DbContract.C_BACK, cleanBack)
            put(DbContract.C_CREATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    fun adminUpdateCard(
        adminUserId: Long,
        deckId: Long,
        cardId: Long,
        front: String,
        back: String
    ): Int {
        if (!adminOwnsDeck(adminUserId, deckId)) return 0

        val cleanFront = front.trim()
        val cleanBack = back.trim()
        if (cleanFront.isBlank() || cleanBack.isBlank()) return 0

        val cv = ContentValues().apply {
            put(DbContract.C_FRONT, cleanFront)
            put(DbContract.C_BACK, cleanBack)
        }
        return writableDatabase.update(
            DbContract.T_CARDS,
            cv,
            "${DbContract.C_ID}=? AND ${DbContract.C_DECK_ID}=?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    fun adminDeleteCard(
        adminUserId: Long,
        deckId: Long,
        cardId: Long
    ): Int {
        if (!adminOwnsDeck(adminUserId, deckId)) return 0

        return writableDatabase.delete(
            DbContract.T_CARDS,
            "${DbContract.C_ID}=? AND ${DbContract.C_DECK_ID}=?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    // old "any" methods kept for compatibility
    fun createCardAny(deckId: Long, front: String, back: String): Long {
        if (!deckExistsAny(deckId)) return -1L

        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID, deckId)
            put(DbContract.C_FRONT, front.trim())
            put(DbContract.C_BACK, back.trim())
            put(DbContract.C_CREATED_AT, System.currentTimeMillis())
        }

        return writableDatabase.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    fun updateCardAny(deckId: Long, cardId: Long, front: String, back: String): Int {
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

    fun deleteCardAny(deckId: Long, cardId: Long): Int {
        return writableDatabase.delete(
            DbContract.T_CARDS,
            "${DbContract.C_ID}=? AND ${DbContract.C_DECK_ID}=?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    fun adminCreateCardContent(deckId: Long, front: String, back: String): Long {
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        if (cleanFront.isBlank() || cleanBack.isBlank()) return -1L
        if (!deckExistsAny(deckId)) return -1L
        return createCardAny(deckId, cleanFront, cleanBack)
    }

    fun adminUpdateCardContent(deckId: Long, cardId: Long, front: String, back: String): Int {
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        if (cleanFront.isBlank() || cleanBack.isBlank()) return 0
        if (!deckExistsAny(deckId)) return 0
        return updateCardAny(deckId, cardId, cleanFront, cleanBack)
    }

    fun adminDeleteCardContent(deckId: Long, cardId: Long): Int {
        if (!deckExistsAny(deckId)) return 0
        return deleteCardAny(deckId, cardId)
    }

    fun adminEnsurePremiumSeedContent(): Int {
        val db = writableDatabase
        var insertedDecks = 0

        db.beginTransaction()
        try {
            val shoonId = ensureSeedUserId(
                db = db,
                name = "Shoon",
                email = "shoon@gmail.com",
                password = "shoon@1234",
                role = DbContract.ROLE_USER
            )

            val noraId = ensureSeedUserId(
                db = db,
                name = "Nora",
                email = "nora@gmail.com",
                password = "nora@1234",
                role = DbContract.ROLE_USER
            )

            premiumSeedDecksForShoon().forEach { bundle ->
                if (ensureSeedPremiumDeck(db, shoonId, bundle)) insertedDecks++
            }

            premiumSeedDecksForNora().forEach { bundle ->
                if (ensureSeedPremiumDeck(db, noraId, bundle)) insertedDecks++
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return insertedDecks
    }

    fun getPublicDeckCatalogForUser(userId: Long): List<PublicDeckCatalogRow> {
        val out = mutableListOf<PublicDeckCatalogRow>()

        readableDatabase.rawQuery(
            """
            SELECT
                d.${DbContract.D_ID},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                owner.${DbContract.U_NAME},
                COALESCE(d.${DbContract.D_IS_PREMIUM}, 0),
                CASE
                    WHEN COALESCE(d.${DbContract.D_IS_PREMIUM}, 0)=1
                         AND COALESCE(viewer.${DbContract.U_IS_PREMIUM_USER}, 0)=0
                    THEN 1 ELSE 0
                END AS is_locked,
                COUNT(c.${DbContract.C_ID}) AS card_count
            FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_USERS} owner
                ON owner.${DbContract.U_ID}=d.${DbContract.D_OWNER_USER_ID}
            LEFT JOIN ${DbContract.T_USERS} viewer
                ON viewer.${DbContract.U_ID}=?
            LEFT JOIN ${DbContract.T_CARDS} c
                ON c.${DbContract.C_DECK_ID}=d.${DbContract.D_ID}
            WHERE d.${DbContract.D_STATUS}=?
              AND COALESCE(d.${DbContract.D_IS_PUBLIC}, 0)=1
            GROUP BY
                d.${DbContract.D_ID},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                owner.${DbContract.U_NAME},
                d.${DbContract.D_IS_PREMIUM},
                viewer.${DbContract.U_IS_PREMIUM_USER}
            ORDER BY d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(userId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            while (c.moveToNext()) {
                out += PublicDeckCatalogRow(
                    deckId = c.getLong(0),
                    title = c.getString(1),
                    description = c.getString(2),
                    ownerName = c.getString(3),
                    isPremium = c.getInt(4) == 1,
                    isLocked = c.getInt(5) == 1,
                    cardCount = c.getInt(6)
                )
            }
        }

        return out
    }

    fun getPublicDeckTitleForUser(userId: Long, deckId: Long): String? {
        readableDatabase.rawQuery(
            """
            SELECT d.${DbContract.D_TITLE}
            FROM ${DbContract.T_DECKS} d
            WHERE d.${DbContract.D_ID}=?
              AND d.${DbContract.D_STATUS}=?
              AND COALESCE(d.${DbContract.D_IS_PUBLIC}, 0)=1
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun isDeckLockedForUser(userId: Long, deckId: Long): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT
                CASE
                    WHEN COALESCE(d.${DbContract.D_IS_PREMIUM}, 0)=1
                         AND COALESCE(u.${DbContract.U_IS_PREMIUM_USER}, 0)=0
                    THEN 1 ELSE 0
                END
            FROM ${DbContract.T_DECKS} d
            LEFT JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID}=?
            WHERE d.${DbContract.D_ID}=?
              AND d.${DbContract.D_STATUS}=?
              AND COALESCE(d.${DbContract.D_IS_PUBLIC}, 0)=1
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), deckId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            return c.moveToFirst() && c.getInt(0) == 1
        }
    }

    fun getPublicDeckCardsForUser(userId: Long, deckId: Long): List<CardRow> {
        if (isDeckLockedForUser(userId, deckId)) return emptyList()

        val out = mutableListOf<CardRow>()
        readableDatabase.rawQuery(
            """
            SELECT
                c.${DbContract.C_ID},
                c.${DbContract.C_FRONT},
                c.${DbContract.C_BACK},
                c.${DbContract.C_CREATED_AT}
            FROM ${DbContract.T_CARDS} c
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
            WHERE d.${DbContract.D_ID}=?
              AND d.${DbContract.D_STATUS}=?
              AND COALESCE(d.${DbContract.D_IS_PUBLIC}, 0)=1
            ORDER BY c.${DbContract.C_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(deckId.toString(), DbContract.DECK_ACTIVE)
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

    // ---------- LAST LOGIN ----------
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
            arrayOf(
                DbContract.ROLE_USER,
                DbContract.STATUS_ACTIVE,
                start.toString()
            )
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
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
                ${DbContract.D_IS_PUBLIC} INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(${DbContract.D_OWNER_USER_ID})
                    REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_owner ON ${DbContract.T_DECKS}(${DbContract.D_OWNER_USER_ID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_status ON ${DbContract.T_DECKS}(${DbContract.D_STATUS})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_public ON ${DbContract.T_DECKS}(${DbContract.D_IS_PUBLIC})")
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
                FOREIGN KEY(${DbContract.C_DECK_ID})
                    REFERENCES ${DbContract.T_DECKS}(${DbContract.D_ID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cards_deck ON ${DbContract.T_CARDS}(${DbContract.C_DECK_ID})")
    }

    private fun createCardProgressTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.T_CARD_PROGRESS}(
                ${DbContract.P_USER_ID} INTEGER NOT NULL,
                ${DbContract.P_CARD_ID} INTEGER NOT NULL,
                ${DbContract.P_DUE_AT} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.P_LAST_REVIEWED_AT} INTEGER,
                ${DbContract.P_INTERVAL_DAYS} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.P_EASE_FACTOR} REAL NOT NULL DEFAULT 2.5,
                ${DbContract.P_REVIEW_COUNT} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.P_LAPSE_COUNT} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.P_LAST_RESULT} TEXT,
                PRIMARY KEY(${DbContract.P_USER_ID}, ${DbContract.P_CARD_ID}),
                FOREIGN KEY(${DbContract.P_USER_ID})
                    REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID})
                    ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.P_CARD_ID})
                    REFERENCES ${DbContract.T_CARDS}(${DbContract.C_ID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_card_progress_due ON ${DbContract.T_CARD_PROGRESS}(${DbContract.P_USER_ID}, ${DbContract.P_DUE_AT})"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_card_progress_card ON ${DbContract.T_CARD_PROGRESS}(${DbContract.P_CARD_ID})"
        )
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
                FOREIGN KEY(${DbContract.S_USER_ID})
                    REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID})
                    ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.S_DECK_ID})
                    REFERENCES ${DbContract.T_DECKS}(${DbContract.D_ID})
                    ON DELETE CASCADE
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
                FOREIGN KEY(${DbContract.R_REPORTER_USER_ID})
                    REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID})
                    ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.R_DECK_ID})
                    REFERENCES ${DbContract.T_DECKS}(${DbContract.D_ID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_status ON ${DbContract.T_REPORTS}(${DbContract.R_STATUS})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_deck ON ${DbContract.T_REPORTS}(${DbContract.R_DECK_ID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_created ON ${DbContract.T_REPORTS}(${DbContract.R_CREATED_AT})")
    }

    private fun addDeckStatusColumn(db: SQLiteDatabase) {
        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.T_DECKS} ADD COLUMN ${DbContract.D_STATUS} TEXT NOT NULL DEFAULT '${DbContract.DECK_ACTIVE}'"
            )
        } catch (_: Exception) {
        }
        try {
            db.execSQL(
                "UPDATE ${DbContract.T_DECKS} SET ${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}' WHERE ${DbContract.D_STATUS} IS NULL"
            )
        } catch (_: Exception) {
        }
    }

    private fun addPremiumColumns(db: SQLiteDatabase) {
        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.T_USERS} ADD COLUMN ${DbContract.U_IS_PREMIUM_USER} INTEGER NOT NULL DEFAULT 0"
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.T_DECKS} ADD COLUMN ${DbContract.D_IS_PREMIUM} INTEGER NOT NULL DEFAULT 0"
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "UPDATE ${DbContract.T_USERS} SET ${DbContract.U_IS_PREMIUM_USER}=0 WHERE ${DbContract.U_IS_PREMIUM_USER} IS NULL"
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "UPDATE ${DbContract.T_DECKS} SET ${DbContract.D_IS_PREMIUM}=0 WHERE ${DbContract.D_IS_PREMIUM} IS NULL"
            )
        } catch (_: Exception) {
        }
    }

    private fun addLastLoginColumn(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE ${DbContract.T_USERS} ADD COLUMN ${DbContract.U_LAST_LOGIN_AT} INTEGER")
        } catch (_: Exception) {
        }
    }

    private fun addDeckPublicColumn(db: SQLiteDatabase) {
        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.T_DECKS} ADD COLUMN ${DbContract.D_IS_PUBLIC} INTEGER NOT NULL DEFAULT 0"
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "UPDATE ${DbContract.T_DECKS} SET ${DbContract.D_IS_PUBLIC}=0 WHERE ${DbContract.D_IS_PUBLIC} IS NULL"
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_decks_public ON ${DbContract.T_DECKS}(${DbContract.D_IS_PUBLIC})"
            )
        } catch (_: Exception) {
        }
    }

    private fun migrateToVersion9(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS decks_new(
                    ${DbContract.D_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.D_OWNER_USER_ID} INTEGER NOT NULL,
                    ${DbContract.D_TITLE} TEXT NOT NULL,
                    ${DbContract.D_DESCRIPTION} TEXT,
                    ${DbContract.D_CREATED_AT} INTEGER NOT NULL,
                    ${DbContract.D_STATUS} TEXT NOT NULL DEFAULT '${DbContract.DECK_ACTIVE}',
                    ${DbContract.D_IS_PREMIUM} INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(${DbContract.D_OWNER_USER_ID})
                        REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID})
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO decks_new(
                    ${DbContract.D_ID},
                    ${DbContract.D_OWNER_USER_ID},
                    ${DbContract.D_TITLE},
                    ${DbContract.D_DESCRIPTION},
                    ${DbContract.D_CREATED_AT},
                    ${DbContract.D_STATUS},
                    ${DbContract.D_IS_PREMIUM}
                )
                SELECT
                    d.${DbContract.D_ID},
                    d.${DbContract.D_OWNER_USER_ID},
                    d.${DbContract.D_TITLE},
                    d.${DbContract.D_DESCRIPTION},
                    d.${DbContract.D_CREATED_AT},
                    COALESCE(d.${DbContract.D_STATUS}, '${DbContract.DECK_ACTIVE}'),
                    COALESCE(d.${DbContract.D_IS_PREMIUM}, 0)
                FROM ${DbContract.T_DECKS} d
                INNER JOIN ${DbContract.T_USERS} u
                    ON u.${DbContract.U_ID}=d.${DbContract.D_OWNER_USER_ID}
                """.trimIndent()
            )

            db.execSQL("DROP TABLE ${DbContract.T_DECKS}")
            db.execSQL("ALTER TABLE decks_new RENAME TO ${DbContract.T_DECKS}")

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_owner ON ${DbContract.T_DECKS}(${DbContract.D_OWNER_USER_ID})")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_status ON ${DbContract.T_DECKS}(${DbContract.D_STATUS})")

            createCardProgressTable(db)
            cleanupOrphanRowsAfterVersion9(db)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    private fun cleanupOrphanRowsAfterVersion9(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM ${DbContract.T_CARDS}
            WHERE ${DbContract.C_DECK_ID} NOT IN (
                SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS}
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            DELETE FROM ${DbContract.T_STUDY_SESSIONS}
            WHERE ${DbContract.S_DECK_ID} NOT IN (
                SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS}
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            DELETE FROM ${DbContract.T_REPORTS}
            WHERE ${DbContract.R_DECK_ID} NOT IN (
                SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS}
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            DELETE FROM ${DbContract.T_CARD_PROGRESS}
            WHERE ${DbContract.P_USER_ID} NOT IN (
                SELECT ${DbContract.U_ID} FROM ${DbContract.T_USERS}
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            DELETE FROM ${DbContract.T_CARD_PROGRESS}
            WHERE ${DbContract.P_CARD_ID} NOT IN (
                SELECT ${DbContract.C_ID} FROM ${DbContract.T_CARDS}
            )
            """.trimIndent()
        )
    }

    // ---------- SRS ----------
    data class CardProgressSnapshot(
        val dueAt: Long,
        val lastReviewedAt: Long?,
        val intervalDays: Int,
        val easeFactor: Float,
        val reviewCount: Int,
        val lapseCount: Int,
        val lastResult: String?
    )

    data class DueCardRow(
        val id: Long,
        val front: String,
        val back: String,
        val createdAt: Long,
        val dueAt: Long,
        val intervalDays: Int,
        val reviewCount: Int,
        val lastResult: String?
    )

    private data class SrsProgressRow(
        val dueAt: Long,
        val lastReviewedAt: Long?,
        val intervalDays: Int,
        val easeFactor: Float,
        val reviewCount: Int,
        val lapseCount: Int,
        val lastResult: String?
    )

    private data class NextSrsState(
        val dueAt: Long,
        val lastReviewedAt: Long,
        val intervalDays: Int,
        val easeFactor: Float,
        val reviewCount: Int,
        val lapseCount: Int,
        val lastResult: String
    )

    fun getCardProgressSnapshot(userId: Long, cardId: Long): CardProgressSnapshot? {
        readableDatabase.rawQuery(
            """
            SELECT
                ${DbContract.P_DUE_AT},
                ${DbContract.P_LAST_REVIEWED_AT},
                ${DbContract.P_INTERVAL_DAYS},
                ${DbContract.P_EASE_FACTOR},
                ${DbContract.P_REVIEW_COUNT},
                ${DbContract.P_LAPSE_COUNT},
                ${DbContract.P_LAST_RESULT}
            FROM ${DbContract.T_CARD_PROGRESS}
            WHERE ${DbContract.P_USER_ID}=?
              AND ${DbContract.P_CARD_ID}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), cardId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null

            return CardProgressSnapshot(
                dueAt = c.getLong(0),
                lastReviewedAt = if (c.isNull(1)) null else c.getLong(1),
                intervalDays = c.getInt(2),
                easeFactor = c.getFloat(3),
                reviewCount = c.getInt(4),
                lapseCount = c.getInt(5),
                lastResult = if (c.isNull(6)) null else c.getString(6)
            )
        }
    }

    fun restoreCardProgressSnapshot(
        userId: Long,
        cardId: Long,
        snapshot: CardProgressSnapshot?
    ): Boolean {
        val db = writableDatabase

        if (snapshot == null) {
            db.delete(
                DbContract.T_CARD_PROGRESS,
                "${DbContract.P_USER_ID}=? AND ${DbContract.P_CARD_ID}=?",
                arrayOf(userId.toString(), cardId.toString())
            )
            return true
        }

        val cv = ContentValues().apply {
            put(DbContract.P_USER_ID, userId)
            put(DbContract.P_CARD_ID, cardId)
            put(DbContract.P_DUE_AT, snapshot.dueAt)

            if (snapshot.lastReviewedAt == null) putNull(DbContract.P_LAST_REVIEWED_AT)
            else put(DbContract.P_LAST_REVIEWED_AT, snapshot.lastReviewedAt)

            put(DbContract.P_INTERVAL_DAYS, snapshot.intervalDays)
            put(DbContract.P_EASE_FACTOR, snapshot.easeFactor)
            put(DbContract.P_REVIEW_COUNT, snapshot.reviewCount)
            put(DbContract.P_LAPSE_COUNT, snapshot.lapseCount)

            if (snapshot.lastResult == null) putNull(DbContract.P_LAST_RESULT)
            else put(DbContract.P_LAST_RESULT, snapshot.lastResult)
        }

        val updated = db.update(
            DbContract.T_CARD_PROGRESS,
            cv,
            "${DbContract.P_USER_ID}=? AND ${DbContract.P_CARD_ID}=?",
            arrayOf(userId.toString(), cardId.toString())
        )

        if (updated == 0) {
            db.insert(DbContract.T_CARD_PROGRESS, null, cv)
        }

        return true
    }

    private fun daysToMillis(days: Int): Long {
        return days.toLong() * 24L * 60L * 60L * 1000L
    }

    private fun ensureCardProgressRowsForDeck(ownerUserId: Long, deckId: Long) {
        writableDatabase.execSQL(
            """
            INSERT OR IGNORE INTO ${DbContract.T_CARD_PROGRESS}(
                ${DbContract.P_USER_ID},
                ${DbContract.P_CARD_ID},
                ${DbContract.P_DUE_AT},
                ${DbContract.P_LAST_REVIEWED_AT},
                ${DbContract.P_INTERVAL_DAYS},
                ${DbContract.P_EASE_FACTOR},
                ${DbContract.P_REVIEW_COUNT},
                ${DbContract.P_LAPSE_COUNT},
                ${DbContract.P_LAST_RESULT}
            )
            SELECT
                ?, c.${DbContract.C_ID}, 0, NULL, 0, 2.5, 0, 0, NULL
            FROM ${DbContract.T_CARDS} c
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
            WHERE c.${DbContract.C_DECK_ID}=?
              AND d.${DbContract.D_OWNER_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            """.trimIndent(),
            arrayOf(ownerUserId, deckId, ownerUserId)
        )
    }

    private fun ensureCardProgressRowForCard(
        db: SQLiteDatabase,
        ownerUserId: Long,
        deckId: Long,
        cardId: Long
    ) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO ${DbContract.T_CARD_PROGRESS}(
                ${DbContract.P_USER_ID},
                ${DbContract.P_CARD_ID},
                ${DbContract.P_DUE_AT},
                ${DbContract.P_LAST_REVIEWED_AT},
                ${DbContract.P_INTERVAL_DAYS},
                ${DbContract.P_EASE_FACTOR},
                ${DbContract.P_REVIEW_COUNT},
                ${DbContract.P_LAPSE_COUNT},
                ${DbContract.P_LAST_RESULT}
            )
            SELECT
                ?, c.${DbContract.C_ID}, 0, NULL, 0, 2.5, 0, 0, NULL
            FROM ${DbContract.T_CARDS} c
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
            WHERE c.${DbContract.C_ID}=?
              AND c.${DbContract.C_DECK_ID}=?
              AND d.${DbContract.D_OWNER_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            """.trimIndent(),
            arrayOf(ownerUserId, cardId, deckId, ownerUserId)
        )
    }

    private fun ownsCardForStudy(ownerUserId: Long, deckId: Long, cardId: Long): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_CARDS} c
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
            WHERE c.${DbContract.C_ID}=?
              AND c.${DbContract.C_DECK_ID}=?
              AND d.${DbContract.D_OWNER_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            LIMIT 1
            """.trimIndent(),
            arrayOf(cardId.toString(), deckId.toString(), ownerUserId.toString())
        ).use { c ->
            return c.moveToFirst()
        }
    }

    private fun readCardProgressRow(
        db: SQLiteDatabase,
        userId: Long,
        cardId: Long
    ): SrsProgressRow? {
        db.rawQuery(
            """
            SELECT
                ${DbContract.P_DUE_AT},
                ${DbContract.P_LAST_REVIEWED_AT},
                ${DbContract.P_INTERVAL_DAYS},
                ${DbContract.P_EASE_FACTOR},
                ${DbContract.P_REVIEW_COUNT},
                ${DbContract.P_LAPSE_COUNT},
                ${DbContract.P_LAST_RESULT}
            FROM ${DbContract.T_CARD_PROGRESS}
            WHERE ${DbContract.P_USER_ID}=?
              AND ${DbContract.P_CARD_ID}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), cardId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            return SrsProgressRow(
                dueAt = c.getLong(0),
                lastReviewedAt = if (c.isNull(1)) null else c.getLong(1),
                intervalDays = c.getInt(2),
                easeFactor = c.getFloat(3),
                reviewCount = c.getInt(4),
                lapseCount = c.getInt(5),
                lastResult = if (c.isNull(6)) null else c.getString(6)
            )
        }
    }

    private fun computeNextSrsState(
        current: SrsProgressRow?,
        result: String,
        reviewedAt: Long
    ): NextSrsState {
        val currentEase = max(1.30f, current?.easeFactor ?: 2.50f)
        val currentInterval = current?.intervalDays ?: 0
        val currentReviewCount = current?.reviewCount ?: 0
        val currentLapseCount = current?.lapseCount ?: 0

        return when (result) {
            DbContract.RESULT_KNOWN -> {
                val nextInterval = when {
                    currentInterval <= 0 -> 1
                    currentInterval == 1 -> 3
                    else -> max(1, (currentInterval * currentEase).roundToInt())
                }
                val nextEase = min(2.80f, currentEase + 0.05f)

                NextSrsState(
                    dueAt = reviewedAt + daysToMillis(nextInterval),
                    lastReviewedAt = reviewedAt,
                    intervalDays = nextInterval,
                    easeFactor = nextEase,
                    reviewCount = currentReviewCount + 1,
                    lapseCount = currentLapseCount,
                    lastResult = result
                )
            }

            DbContract.RESULT_HARD -> {
                val nextEase = max(1.30f, currentEase - 0.20f)

                NextSrsState(
                    dueAt = reviewedAt + (10L * 60L * 1000L),
                    lastReviewedAt = reviewedAt,
                    intervalDays = 0,
                    easeFactor = nextEase,
                    reviewCount = currentReviewCount + 1,
                    lapseCount = currentLapseCount + 1,
                    lastResult = result
                )
            }

            else -> throw IllegalArgumentException("Unsupported study result: $result")
        }
    }

    private fun insertStudySessionRow(
        db: SQLiteDatabase,
        userId: Long,
        deckId: Long,
        result: String,
        createdAt: Long
    ): Long {
        val cv = ContentValues().apply {
            put(DbContract.S_USER_ID, userId)
            put(DbContract.S_DECK_ID, deckId)
            put(DbContract.S_RESULT, result)
            put(DbContract.S_CREATED_AT, createdAt)
        }
        return db.insert(DbContract.T_STUDY_SESSIONS, null, cv)
    }

    fun getDueCountForDeck(
        ownerUserId: Long,
        deckId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        ensureCardProgressRowsForDeck(ownerUserId, deckId)

        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_CARDS} c
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
            INNER JOIN ${DbContract.T_CARD_PROGRESS} p
                ON p.${DbContract.P_CARD_ID}=c.${DbContract.C_ID}
               AND p.${DbContract.P_USER_ID}=?
            WHERE c.${DbContract.C_DECK_ID}=?
              AND d.${DbContract.D_OWNER_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
              AND p.${DbContract.P_DUE_AT}<=?
            """.trimIndent(),
            arrayOf(
                ownerUserId.toString(),
                deckId.toString(),
                ownerUserId.toString(),
                nowMs.toString()
            )
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getDueCountForUser(
        userId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_CARD_PROGRESS} p
            INNER JOIN ${DbContract.T_CARDS} c
                ON c.${DbContract.C_ID}=p.${DbContract.P_CARD_ID}
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
            WHERE p.${DbContract.P_USER_ID}=?
              AND d.${DbContract.D_OWNER_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
              AND p.${DbContract.P_DUE_AT}<=?
            """.trimIndent(),
            arrayOf(userId.toString(), userId.toString(), nowMs.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getDueCardsForDeck(
        ownerUserId: Long,
        deckId: Long,
        nowMs: Long = System.currentTimeMillis(),
        limit: Int = 999
    ): List<DueCardRow> {
        ensureCardProgressRowsForDeck(ownerUserId, deckId)

        val out = mutableListOf<DueCardRow>()
        readableDatabase.rawQuery(
            """
            SELECT
                c.${DbContract.C_ID},
                c.${DbContract.C_FRONT},
                c.${DbContract.C_BACK},
                c.${DbContract.C_CREATED_AT},
                p.${DbContract.P_DUE_AT},
                p.${DbContract.P_INTERVAL_DAYS},
                p.${DbContract.P_REVIEW_COUNT},
                p.${DbContract.P_LAST_RESULT}
            FROM ${DbContract.T_CARDS} c
            INNER JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
            INNER JOIN ${DbContract.T_CARD_PROGRESS} p
                ON p.${DbContract.P_CARD_ID}=c.${DbContract.C_ID}
               AND p.${DbContract.P_USER_ID}=?
            WHERE c.${DbContract.C_DECK_ID}=?
              AND d.${DbContract.D_OWNER_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
              AND p.${DbContract.P_DUE_AT}<=?
            ORDER BY p.${DbContract.P_DUE_AT} ASC, c.${DbContract.C_CREATED_AT} ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                ownerUserId.toString(),
                deckId.toString(),
                ownerUserId.toString(),
                nowMs.toString(),
                limit.toString()
            )
        ).use { c ->
            while (c.moveToNext()) {
                out += DueCardRow(
                    id = c.getLong(0),
                    front = c.getString(1),
                    back = c.getString(2),
                    createdAt = c.getLong(3),
                    dueAt = c.getLong(4),
                    intervalDays = c.getInt(5),
                    reviewCount = c.getInt(6),
                    lastResult = if (c.isNull(7)) null else c.getString(7)
                )
            }
        }

        return out
    }

    fun applySrsReview(
        ownerUserId: Long,
        deckId: Long,
        cardId: Long,
        result: String,
        reviewedAt: Long = System.currentTimeMillis()
    ): Long {
        if (result != DbContract.RESULT_KNOWN && result != DbContract.RESULT_HARD) return -1L
        if (!ownsCardForStudy(ownerUserId, deckId, cardId)) return -1L

        val db = writableDatabase
        db.beginTransaction()
        try {
            ensureCardProgressRowForCard(db, ownerUserId, deckId, cardId)

            val current = readCardProgressRow(db, ownerUserId, cardId)
            val next = computeNextSrsState(current, result, reviewedAt)

            val cv = ContentValues().apply {
                put(DbContract.P_DUE_AT, next.dueAt)
                put(DbContract.P_LAST_REVIEWED_AT, next.lastReviewedAt)
                put(DbContract.P_INTERVAL_DAYS, next.intervalDays)
                put(DbContract.P_EASE_FACTOR, next.easeFactor)
                put(DbContract.P_REVIEW_COUNT, next.reviewCount)
                put(DbContract.P_LAPSE_COUNT, next.lapseCount)
                put(DbContract.P_LAST_RESULT, next.lastResult)
            }

            db.update(
                DbContract.T_CARD_PROGRESS,
                cv,
                "${DbContract.P_USER_ID}=? AND ${DbContract.P_CARD_ID}=?",
                arrayOf(ownerUserId.toString(), cardId.toString())
            )

            val sessionId = insertStudySessionRow(
                db = db,
                userId = ownerUserId,
                deckId = deckId,
                result = result,
                createdAt = reviewedAt
            )

            db.setTransactionSuccessful()
            return sessionId
        } finally {
            db.endTransaction()
        }
    }

    // ---------- SEED HELPERS ----------
    private data class SeedDeckBundle(
        val title: String,
        val description: String,
        val cards: List<Pair<String, String>>
    )

    private fun ensureSeedUserId(
        db: SQLiteDatabase,
        name: String,
        email: String,
        password: String,
        role: String
    ): Long {
        val normalized = email.trim().lowercase()

        db.rawQuery(
            """
            SELECT ${DbContract.U_ID}
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_EMAIL}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(normalized)
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }

        val cv = ContentValues().apply {
            put(DbContract.U_NAME, name.trim())
            put(DbContract.U_EMAIL, normalized)
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(password.toCharArray()))
            put(DbContract.U_ROLE, role)
            put(DbContract.U_STATUS, DbContract.STATUS_ACTIVE)
            put(DbContract.U_ACCEPTED_TERMS, 1)
            put(DbContract.U_FORCE_PW_CHANGE, 0)
            put(DbContract.U_CREATED_AT, System.currentTimeMillis())
            put(DbContract.U_IS_PREMIUM_USER, 0)
        }

        return db.insertOrThrow(DbContract.T_USERS, null, cv)
    }

    private fun ensureSeedPremiumDeck(
        db: SQLiteDatabase,
        ownerUserId: Long,
        bundle: SeedDeckBundle
    ): Boolean {
        val existingDeckId = db.rawQuery(
            """
            SELECT ${DbContract.D_ID}
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID}=?
              AND ${DbContract.D_TITLE}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(ownerUserId.toString(), bundle.title)
        ).use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        }

        val deckId: Long
        val insertedNewDeck: Boolean

        if (existingDeckId > 0L) {
            deckId = existingDeckId
            insertedNewDeck = false

            val cv = ContentValues().apply {
                put(DbContract.D_DESCRIPTION, bundle.description)
                put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM, 1)
                put(DbContract.D_IS_PUBLIC, 1)
            }

            db.update(
                DbContract.T_DECKS,
                cv,
                "${DbContract.D_ID}=?",
                arrayOf(deckId.toString())
            )
        } else {
            insertedNewDeck = true
            deckId = db.insertOrThrow(
                DbContract.T_DECKS,
                null,
                ContentValues().apply {
                    put(DbContract.D_OWNER_USER_ID, ownerUserId)
                    put(DbContract.D_TITLE, bundle.title)
                    put(DbContract.D_DESCRIPTION, bundle.description)
                    put(DbContract.D_CREATED_AT, System.currentTimeMillis())
                    put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                    put(DbContract.D_IS_PREMIUM, 1)
                    put(DbContract.D_IS_PUBLIC, 1)
                }
            )
        }

        bundle.cards.forEach { (front, back) ->
            ensureSeedCard(db, deckId, front, back)
        }

        return insertedNewDeck
    }

    private fun ensureSeedCard(
        db: SQLiteDatabase,
        deckId: Long,
        front: String,
        back: String
    ) {
        val exists = db.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_CARDS}
            WHERE ${DbContract.C_DECK_ID}=?
              AND ${DbContract.C_FRONT}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), front)
        ).use { it.moveToFirst() }

        if (exists) return

        db.insertOrThrow(
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

    private fun premiumSeedDecksForShoon(): List<SeedDeckBundle> {
        return listOf(
            SeedDeckBundle(
                title = "Premium English Idioms",
                description = "Advanced idioms for fluent English communication.",
                cards = listOf(
                    "Break the ice" to "Start a conversation in a relaxed way",
                    "Hit the sack" to "Go to sleep",
                    "Piece of cake" to "Very easy",
                    "Under the weather" to "Feeling unwell"
                )
            ),
            SeedDeckBundle(
                title = "Premium SQL Essentials",
                description = "Higher-value SQL queries and database concepts.",
                cards = listOf(
                    "JOIN" to "Combines rows from related tables",
                    "GROUP BY" to "Groups rows for aggregation",
                    "HAVING" to "Filters grouped results",
                    "INDEX" to "Improves query lookup speed"
                )
            ),
            SeedDeckBundle(
                title = "Premium Kotlin Patterns",
                description = "Important Kotlin concepts for Android learners.",
                cards = listOf(
                    "data class" to "Class mainly used to hold data",
                    "sealed class" to "Restricted class hierarchy",
                    "lazy" to "Initializes value only when needed",
                    "when" to "Powerful conditional expression"
                )
            ),
            SeedDeckBundle(
                title = "Premium Android UI",
                description = "Useful concepts for better Android interface building.",
                cards = listOf(
                    "RecyclerView" to "Displays scrollable lists efficiently",
                    "ViewBinding" to "Type-safe access to layout views",
                    "Fragment" to "Reusable part of UI in an activity",
                    "MaterialCardView" to "Card-style container using Material Design"
                )
            ),
            SeedDeckBundle(
                title = "Premium UX Heuristics",
                description = "Common usability concepts for better design thinking.",
                cards = listOf(
                    "Visibility of status" to "Keep users informed",
                    "Consistency" to "Use familiar patterns and behavior",
                    "Error prevention" to "Design to reduce mistakes",
                    "User control" to "Let users undo or exit actions"
                )
            ),
            SeedDeckBundle(
                title = "Premium Biology Concepts",
                description = "Biology revision deck with higher-value core concepts.",
                cards = listOf(
                    "Osmosis" to "Movement of water across a membrane",
                    "Diffusion" to "Particles move from high to low concentration",
                    "Enzyme" to "Protein that speeds up reactions",
                    "Homeostasis" to "Maintaining internal stability"
                )
            ),
            SeedDeckBundle(
                title = "Premium Japanese Starter",
                description = "Beginner Japanese words and meanings.",
                cards = listOf(
                    "友達" to "Friend",
                    "時間" to "Time",
                    "勉強" to "Study",
                    "電車" to "Train"
                )
            ),
            SeedDeckBundle(
                title = "Premium Agile Delivery",
                description = "Agile concepts for project and software teams.",
                cards = listOf(
                    "Sprint Goal" to "Main objective for the sprint",
                    "Velocity" to "Amount of work completed in a sprint",
                    "Refinement" to "Clarifying and preparing backlog items",
                    "Definition of Done" to "Quality checklist for completed work"
                )
            ),
            SeedDeckBundle(
                title = "Premium Networking Security",
                description = "Networking and security starter concepts.",
                cards = listOf(
                    "Firewall" to "Filters network traffic",
                    "VPN" to "Encrypted connection over a public network",
                    "TLS" to "Protects data in transit",
                    "Phishing" to "Fake communication used to steal information"
                )
            ),
            SeedDeckBundle(
                title = "Premium Exam Vocabulary",
                description = "Academic words useful for exams and essays.",
                cards = listOf(
                    "Analyze" to "Examine something in detail",
                    "Evaluate" to "Judge value or quality",
                    "Synthesize" to "Combine ideas into a whole",
                    "Interpret" to "Explain meaning"
                )
            )
        )
    }

    private fun premiumSeedDecksForNora(): List<SeedDeckBundle> {
        return listOf(
            SeedDeckBundle(
                title = "Premium World Capitals",
                description = "Country and capital revision with extra practice.",
                cards = listOf(
                    "Germany" to "Berlin",
                    "Italy" to "Rome",
                    "South Korea" to "Seoul",
                    "Argentina" to "Buenos Aires"
                )
            ),
            SeedDeckBundle(
                title = "Premium Math Revision",
                description = "Math formulas and quick rules for revision.",
                cards = listOf(
                    "Quadratic formula" to "(-b ± √(b²-4ac)) / 2a",
                    "Slope" to "(y2 - y1) / (x2 - x1)",
                    "Simple interest" to "P × R × T / 100",
                    "Area of circle" to "πr²"
                )
            ),
            SeedDeckBundle(
                title = "Premium Human Anatomy",
                description = "Core anatomy content for quick review.",
                cards = listOf(
                    "Skull" to "Protects the brain",
                    "Spine" to "Supports the body and protects the spinal cord",
                    "Liver" to "Processes nutrients and detoxifies blood",
                    "Kidney" to "Filters waste from blood"
                )
            ),
            SeedDeckBundle(
                title = "Premium Computer Terms",
                description = "Useful computer and IT concepts.",
                cards = listOf(
                    "Algorithm" to "Step-by-step procedure to solve a problem",
                    "Compiler" to "Translates code into machine-readable form",
                    "Cache" to "Fast temporary storage",
                    "API" to "Set of rules for software communication"
                )
            ),
            SeedDeckBundle(
                title = "Premium Business English",
                description = "Professional vocabulary for workplace communication.",
                cards = listOf(
                    "Agenda" to "List of topics for a meeting",
                    "Deadline" to "Final time limit for a task",
                    "Proposal" to "Formal suggestion or plan",
                    "Stakeholder" to "Person affected by a project or decision"
                )
            ),
            SeedDeckBundle(
                title = "Premium Data Structures",
                description = "Important programming data structure concepts.",
                cards = listOf(
                    "Array" to "Ordered collection of items",
                    "Stack" to "Last in, first out structure",
                    "Queue" to "First in, first out structure",
                    "Tree" to "Hierarchical node-based structure"
                )
            ),
            SeedDeckBundle(
                title = "Premium Chemistry Core",
                description = "Starter chemistry facts and definitions.",
                cards = listOf(
                    "Atom" to "Smallest unit of an element",
                    "Molecule" to "Two or more atoms bonded together",
                    "pH" to "Measure of acidity or alkalinity",
                    "Catalyst" to "Substance that speeds up a reaction"
                )
            ),
            SeedDeckBundle(
                title = "Premium History Dates",
                description = "Important dates for general history review.",
                cards = listOf(
                    "1914" to "Start of World War I",
                    "1918" to "End of World War I",
                    "1939" to "Start of World War II",
                    "1945" to "End of World War II"
                )
            ),
            SeedDeckBundle(
                title = "Premium Project Management",
                description = "Core project management language and ideas.",
                cards = listOf(
                    "Scope" to "Boundaries of project work",
                    "Risk" to "Possible event that can affect objectives",
                    "Milestone" to "Important checkpoint in a project",
                    "Deliverable" to "Output produced by the project"
                )
            ),
            SeedDeckBundle(
                title = "Premium Cybersecurity Basics",
                description = "Basic security terms for digital safety.",
                cards = listOf(
                    "Malware" to "Software designed to harm a system",
                    "Ransomware" to "Malware that locks data for payment",
                    "2FA" to "Two-factor authentication",
                    "Password manager" to "Tool for storing strong passwords securely"
                )
            )
        )
    }

    // ---------- DEMO / SEED ----------
    fun ensureDemoAccounts() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            seedStaffAccounts(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private data class SeedDeck(
        val title: String,
        val description: String,
        val isPublic: Boolean,
        val cards: List<Pair<String, String>>
    )

    private fun ensureUserId(
        db: SQLiteDatabase,
        name: String,
        email: String,
        password: String,
        role: String,
        forcePwChange: Boolean,
        isPremiumUser: Boolean
    ): Long {
        val normalized = email.trim().lowercase()

        db.rawQuery(
            "SELECT ${DbContract.U_ID} FROM ${DbContract.T_USERS} WHERE ${DbContract.U_EMAIL}=? LIMIT 1",
            arrayOf(normalized)
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }

        val cv = ContentValues().apply {
            put(DbContract.U_NAME, name.trim())
            put(DbContract.U_EMAIL, normalized)
            put(DbContract.U_PASSWORD_HASH, PasswordHasher.hash(password.toCharArray()))
            put(DbContract.U_ROLE, role)
            put(DbContract.U_STATUS, DbContract.STATUS_ACTIVE)
            put(DbContract.U_ACCEPTED_TERMS, 1)
            put(DbContract.U_FORCE_PW_CHANGE, if (forcePwChange) 1 else 0)
            put(DbContract.U_CREATED_AT, System.currentTimeMillis())
            put(DbContract.U_IS_PREMIUM_USER, if (isPremiumUser) 1 else 0)
        }

        return db.insertOrThrow(DbContract.T_USERS, null, cv)
    }

    private fun seedDemoDecksAndCards(db: SQLiteDatabase) {
        val shoonId = ensureUserId(
            db = db,
            name = "Shoon",
            email = "shoon@gmail.com",
            password = "shoon@1234",
            role = DbContract.ROLE_USER,
            forcePwChange = false,
            isPremiumUser = false
        )

        val noraId = ensureUserId(
            db = db,
            name = "Nora",
            email = "nora@gmail.com",
            password = "nora@1234",
            role = DbContract.ROLE_USER,
            forcePwChange = false,
            isPremiumUser = false
        )

        val shoonDecks = listOf(
            SeedDeck(
                title = "English Verbs",
                description = "Common verbs for daily communication.",
                isPublic = true,
                cards = listOf(
                    "Go" to "To move from one place to another",
                    "Eat" to "To consume food",
                    "Read" to "To look at and understand words",
                    "Write" to "To form letters or words",
                    "Speak" to "To say words aloud",
                    "Learn" to "To gain knowledge or skill"
                )
            ),
            SeedDeck(
                title = "Basic SQL",
                description = "Starter queries and key terms for SQL.",
                isPublic = true,
                cards = listOf(
                    "SELECT" to "Used to retrieve data from a table",
                    "WHERE" to "Used to filter rows",
                    "INSERT" to "Used to add new rows",
                    "UPDATE" to "Used to modify existing rows",
                    "DELETE" to "Used to remove rows",
                    "PRIMARY KEY" to "Unique identifier for each record"
                )
            ),
            SeedDeck(
                title = "Biology Basics",
                description = "Quick biology revision deck.",
                isPublic = false,
                cards = listOf(
                    "Cell" to "Basic unit of life",
                    "Nucleus" to "Controls activities of the cell",
                    "Mitochondria" to "Produces energy for the cell",
                    "Tissue" to "Group of similar cells",
                    "Organ" to "Structure made of multiple tissues",
                    "Photosynthesis" to "Process plants use to make food"
                )
            ),
            SeedDeck(
                title = "Agile Terms",
                description = "Common terms used in agile projects.",
                isPublic = true,
                cards = listOf(
                    "Sprint" to "A short time-boxed period of development",
                    "Backlog" to "Ordered list of work items",
                    "Scrum Master" to "Facilitates the scrum process",
                    "User Story" to "Short description of a feature from a user view",
                    "Stand-up" to "Short daily team meeting",
                    "Retrospective" to "Meeting to reflect and improve"
                )
            ),
            SeedDeck(
                title = "Japanese N5",
                description = "Starter Japanese vocabulary.",
                isPublic = false,
                cards = listOf(
                    "水" to "Water",
                    "火" to "Fire",
                    "山" to "Mountain",
                    "川" to "River",
                    "学校" to "School",
                    "先生" to "Teacher"
                )
            )
        )

        val noraDecks = listOf(
            SeedDeck(
                title = "World Capitals",
                description = "Countries and their capitals.",
                isPublic = true,
                cards = listOf(
                    "France" to "Paris",
                    "Japan" to "Tokyo",
                    "Thailand" to "Bangkok",
                    "Australia" to "Canberra",
                    "Canada" to "Ottawa",
                    "Brazil" to "Brasilia"
                )
            ),
            SeedDeck(
                title = "Math Formulas",
                description = "Useful formulas for quick revision.",
                isPublic = false,
                cards = listOf(
                    "Area of rectangle" to "length × width",
                    "Area of triangle" to "1/2 × base × height",
                    "Perimeter of square" to "4 × side",
                    "Circumference of circle" to "2πr",
                    "Pythagoras" to "a² + b² = c²",
                    "Average" to "sum / count"
                )
            ),
            SeedDeck(
                title = "UI UX Terms",
                description = "Key design and usability terms.",
                isPublic = true,
                cards = listOf(
                    "Wireframe" to "Basic layout of a screen",
                    "Prototype" to "Interactive model of a design",
                    "Usability" to "How easy a product is to use",
                    "Consistency" to "Similar elements behave the same way",
                    "Accessibility" to "Design usable by more people",
                    "Feedback" to "System response to user action"
                )
            ),
            SeedDeck(
                title = "Human Anatomy",
                description = "Simple anatomy revision deck.",
                isPublic = false,
                cards = listOf(
                    "Heart" to "Pumps blood through the body",
                    "Lungs" to "Help with breathing",
                    "Brain" to "Controls body functions",
                    "Femur" to "Longest bone in the body",
                    "Skin" to "Largest organ of the body",
                    "Rib cage" to "Protects the heart and lungs"
                )
            ),
            SeedDeck(
                title = "Networking Basics",
                description = "Starter concepts for computer networking.",
                isPublic = true,
                cards = listOf(
                    "IP Address" to "Unique address for a device on a network",
                    "Router" to "Connects networks and forwards data",
                    "Switch" to "Connects devices in a local network",
                    "LAN" to "Local Area Network",
                    "WAN" to "Wide Area Network",
                    "DNS" to "Translates domain names to IP addresses"
                )
            )
        )

        shoonDecks.forEach { deck ->
            ensureDeckWithCards(
                db = db,
                ownerUserId = shoonId,
                title = deck.title,
                description = deck.description,
                isPublic = deck.isPublic,
                cards = deck.cards
            )
        }

        noraDecks.forEach { deck ->
            ensureDeckWithCards(
                db = db,
                ownerUserId = noraId,
                title = deck.title,
                description = deck.description,
                isPublic = deck.isPublic,
                cards = deck.cards
            )
        }

        premiumSeedDecksForShoon().forEach { bundle ->
            ensureSeedPremiumDeck(db, shoonId, bundle)
        }

        premiumSeedDecksForNora().forEach { bundle ->
            ensureSeedPremiumDeck(db, noraId, bundle)
        }
    }

    private fun ensureDeckWithCards(
        db: SQLiteDatabase,
        ownerUserId: Long,
        title: String,
        description: String,
        isPublic: Boolean,
        cards: List<Pair<String, String>>
    ) {
        val existingDeckId = db.rawQuery(
            """
            SELECT ${DbContract.D_ID}
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID}=?
              AND ${DbContract.D_TITLE}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(ownerUserId.toString(), title)
        ).use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        }

        val deckId = if (existingDeckId > 0L) {
            val cv = ContentValues().apply {
                put(DbContract.D_DESCRIPTION, description)
                put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM, 0)
                put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
            }
            db.update(
                DbContract.T_DECKS,
                cv,
                "${DbContract.D_ID}=?",
                arrayOf(existingDeckId.toString())
            )
            existingDeckId
        } else {
            val cv = ContentValues().apply {
                put(DbContract.D_OWNER_USER_ID, ownerUserId)
                put(DbContract.D_TITLE, title)
                put(DbContract.D_DESCRIPTION, description)
                put(DbContract.D_CREATED_AT, System.currentTimeMillis())
                put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM, 0)
                put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
            }
            db.insertOrThrow(DbContract.T_DECKS, null, cv)
        }

        cards.forEach { (front, back) ->
            ensureCard(db, deckId, front, back)
        }
    }

    private fun ensureCard(
        db: SQLiteDatabase,
        deckId: Long,
        front: String,
        back: String
    ) {
        val exists = db.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_CARDS}
            WHERE ${DbContract.C_DECK_ID}=?
              AND ${DbContract.C_FRONT}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), front)
        ).use { it.moveToFirst() }

        if (exists) return

        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID, deckId)
            put(DbContract.C_FRONT, front)
            put(DbContract.C_BACK, back)
            put(DbContract.C_CREATED_AT, System.currentTimeMillis())
        }
        db.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    private fun seedStaffAccounts(db: SQLiteDatabase) {
        ensureUserId(db, "Admin", "admin@stardeck.local", "Admin@1234", DbContract.ROLE_ADMIN, true, false)
        ensureUserId(db, "Manager", "manager@stardeck.local", "Manager@1234", DbContract.ROLE_MANAGER, true, false)
        ensureUserId(db, "Shoon", "shoon@gmail.com", "shoon@1234", DbContract.ROLE_USER, false, false)
        ensureUserId(db, "Nora", "nora@gmail.com", "nora@1234", DbContract.ROLE_USER, false, false)
    }

    // ---------- PREMIUM ----------
    fun setUserPremium(userId: Long, enabled: Boolean): Int {
        val cv = ContentValues().apply {
            put(DbContract.U_IS_PREMIUM_USER, if (enabled) 1 else 0)
        }
        return writableDatabase.update(
            DbContract.T_USERS,
            cv,
            "${DbContract.U_ID}=?",
            arrayOf(userId.toString())
        )
    }

    fun createPremiumDemoDeckForUser(ownerUserId: Long): Long {
        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.D_ID}
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID}=?
              AND ${DbContract.D_IS_PREMIUM}=1
              AND ${DbContract.D_TITLE}='Premium Demo Deck'
            LIMIT 1
            """.trimIndent(),
            arrayOf(ownerUserId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }

        val deckId = writableDatabase.insertOrThrow(
            DbContract.T_DECKS,
            null,
            ContentValues().apply {
                put(DbContract.D_OWNER_USER_ID, ownerUserId)
                put(DbContract.D_TITLE, "Premium Demo Deck")
                put(DbContract.D_DESCRIPTION, "Demo premium content (locked until you upgrade).")
                put(DbContract.D_CREATED_AT, System.currentTimeMillis())
                put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM, 1)
                put(DbContract.D_IS_PUBLIC, 0)
            }
        )

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
            SELECT
                ${DbContract.U_ID},
                ${DbContract.U_NAME},
                ${DbContract.U_EMAIL},
                ${DbContract.U_PASSWORD_HASH},
                ${DbContract.U_ROLE},
                ${DbContract.U_STATUS},
                ${DbContract.U_FORCE_PW_CHANGE}
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
            DbContract.T_USERS,
            cv,
            "${DbContract.U_ID}=?",
            arrayOf(userId.toString())
        )
    }

    fun resetPasswordByEmail(email: String, newPassword: CharArray): Boolean {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return false

        val newHash = PasswordHasher.hash(newPassword)

        val cv = ContentValues().apply {
            put(DbContract.U_PASSWORD_HASH, newHash)
            put(DbContract.U_FORCE_PW_CHANGE, 0)
        }

        val rows = writableDatabase.update(
            DbContract.T_USERS,
            cv,
            "${DbContract.U_EMAIL}=?",
            arrayOf(normalized)
        )

        return rows > 0
    }

    // ---------- ADMIN ----------
    data class SimpleUserRow(
        val id: Long,
        val name: String,
        val email: String,
        val role: String,
        val status: String
    )

    data class LeaderboardRow(
        val userId: Long,
        val name: String,
        val email: String,
        val totalStudy: Int,
        val streakDays: Int
    )

    fun getLocalLeaderboard(): List<LeaderboardRow> {
        val users = adminGetAllUsers()
        val out = mutableListOf<LeaderboardRow>()

        for (u in users) {
            if (u.role != DbContract.ROLE_USER) continue
            if (u.status != DbContract.STATUS_ACTIVE) continue

            val total = getTotalStudyCount(u.id)
            val streak = getStudyStreakDays(u.id)

            if (total <= 0 && streak <= 0) continue

            out += LeaderboardRow(
                userId = u.id,
                name = u.name,
                email = u.email,
                totalStudy = total,
                streakDays = streak
            )
        }

        return out.sortedWith(
            compareByDescending<LeaderboardRow> { it.totalStudy }
                .thenByDescending { it.streakDays }
                .thenBy { it.name.lowercase() }
        )
    }



    data class AdminUserDependencyRow(
        val deckCount: Int,
        val studyCount: Int,
        val reportCount: Int,
        val progressCount: Int
    )

    fun adminCountAllUsers(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM ${DbContract.T_USERS}", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun adminCountDisabledUsers(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_USERS} WHERE ${DbContract.U_STATUS}=?",
            arrayOf(DbContract.STATUS_DISABLED)
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun adminCountPremiumUsers(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_USERS} WHERE ${DbContract.U_IS_PREMIUM_USER}=1",
            null
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
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
        readableDatabase.rawQuery("SELECT COUNT(*) FROM ${DbContract.T_DECKS}", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun adminCountHiddenDecks(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_STATUS}=?",
            arrayOf(DbContract.DECK_HIDDEN)
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun adminCountOpenReports(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.T_REPORTS} WHERE ${DbContract.R_STATUS}=?",
            arrayOf(DbContract.REPORT_OPEN)
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun adminGetAllUsers(): List<SimpleUserRow> {
        val out = mutableListOf<SimpleUserRow>()
        readableDatabase.rawQuery(
            """
            SELECT
                ${DbContract.U_ID},
                ${DbContract.U_NAME},
                ${DbContract.U_EMAIL},
                ${DbContract.U_ROLE},
                ${DbContract.U_STATUS}
            FROM ${DbContract.T_USERS}
            ORDER BY ${DbContract.U_CREATED_AT} DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += SimpleUserRow(
                    c.getLong(0),
                    c.getString(1),
                    c.getString(2),
                    c.getString(3),
                    c.getString(4)
                )
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
        writableDatabase.update(
            DbContract.T_USERS,
            cv,
            "${DbContract.U_ID}=?",
            arrayOf(id.toString())
        )
    }

    fun adminResetPassword(userId: Long, tempPassword: CharArray) {
        updatePassword(userId, tempPassword, forcePwChange = true)
    }

    fun adminGetUserDependencies(userId: Long): AdminUserDependencyRow {
        fun count(table: String, where: String): Int {
            readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM $table WHERE $where",
                arrayOf(userId.toString())
            ).use { c ->
                return if (c.moveToFirst()) c.getInt(0) else 0
            }
        }

        return AdminUserDependencyRow(
            deckCount = count(DbContract.T_DECKS, "${DbContract.D_OWNER_USER_ID}=?"),
            studyCount = count(DbContract.T_STUDY_SESSIONS, "${DbContract.S_USER_ID}=?"),
            reportCount = count(DbContract.T_REPORTS, "${DbContract.R_REPORTER_USER_ID}=?"),
            progressCount = count(DbContract.T_CARD_PROGRESS, "${DbContract.P_USER_ID}=?")
        )
    }

    fun adminDeleteUserIfSafe(currentAdminUserId: Long, userId: Long): Int {
        if (userId <= 0L) return 0
        if (userId == currentAdminUserId) return 0

        readableDatabase.rawQuery(
            """
            SELECT ${DbContract.U_ROLE}
            FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ID}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return 0
        }

        val deps = adminGetUserDependencies(userId)
        if (deps.deckCount > 0 || deps.studyCount > 0 || deps.reportCount > 0 || deps.progressCount > 0) {
            return 0
        }

        return writableDatabase.delete(
            DbContract.T_USERS,
            "${DbContract.U_ID}=?",
            arrayOf(userId.toString())
        )
    }

    // ---------- DECKS ----------
    data class DeckRow(
        val id: Long,
        val title: String,
        val description: String?,
        val createdAt: Long,
        val isPremium: Boolean,
        val isPublic: Boolean
    )

    private fun ownerOwnsActiveDeck(ownerUserId: Long, deckId: Long): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_ID}=?
              AND ${DbContract.D_OWNER_USER_ID}=?
              AND ${DbContract.D_STATUS}=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), ownerUserId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            return c.moveToFirst()
        }
    }

    private fun ownerHasDeckTitle(ownerUserId: Long, title: String, excludeDeckId: Long? = null): Boolean {
        val sql = StringBuilder().apply {
            append(
                """
                SELECT 1
                FROM ${DbContract.T_DECKS}
                WHERE ${DbContract.D_OWNER_USER_ID}=?
                  AND LOWER(${DbContract.D_TITLE})=LOWER(?)
                """.trimIndent()
            )
            if (excludeDeckId != null) append(" AND ${DbContract.D_ID}<>?")
            append(" LIMIT 1")
        }.toString()

        val args = mutableListOf(ownerUserId.toString(), title.trim())
        if (excludeDeckId != null) args += excludeDeckId.toString()

        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            return c.moveToFirst()
        }
    }

    fun getDecksForOwner(ownerUserId: Long): List<DeckRow> {
        val out = mutableListOf<DeckRow>()

        readableDatabase.rawQuery(
            """
            SELECT
                ${DbContract.D_ID},
                ${DbContract.D_TITLE},
                ${DbContract.D_DESCRIPTION},
                ${DbContract.D_CREATED_AT},
                ${DbContract.D_IS_PREMIUM},
                ${DbContract.D_IS_PUBLIC}
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID}=?
              AND ${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
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
                    isPremium = c.getInt(4) == 1,
                    isPublic = c.getInt(5) == 1
                )
            }
        }

        return out
    }

    fun createDeck(
        ownerUserId: Long,
        title: String,
        description: String?,
        isPublic: Boolean = false
    ): Long {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return -1L
        if (ownerHasDeckTitle(ownerUserId, cleanTitle)) return -1L

        val cv = ContentValues().apply {
            put(DbContract.D_OWNER_USER_ID, ownerUserId)
            put(DbContract.D_TITLE, cleanTitle)
            put(DbContract.D_DESCRIPTION, description?.trim().orEmpty().ifBlank { null })
            put(DbContract.D_CREATED_AT, System.currentTimeMillis())
            put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
            put(DbContract.D_IS_PREMIUM, 0)
            put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
        }

        return writableDatabase.insertOrThrow(DbContract.T_DECKS, null, cv)
    }

    fun updateDeck(
        ownerUserId: Long,
        deckId: Long,
        title: String,
        description: String?,
        isPublic: Boolean = false
    ): Int {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return 0
        if (!ownerOwnsActiveDeck(ownerUserId, deckId)) return 0
        if (ownerHasDeckTitle(ownerUserId, cleanTitle, excludeDeckId = deckId)) return 0

        val cv = ContentValues().apply {
            put(DbContract.D_TITLE, cleanTitle)
            put(DbContract.D_DESCRIPTION, description?.trim().orEmpty().ifBlank { null })
            put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
        }

        return writableDatabase.update(
            DbContract.T_DECKS,
            cv,
            "${DbContract.D_ID}=? AND ${DbContract.D_OWNER_USER_ID}=?",
            arrayOf(deckId.toString(), ownerUserId.toString())
        )
    }

    fun deleteDeck(ownerUserId: Long, deckId: Long): Int {
        if (!ownerOwnsActiveDeck(ownerUserId, deckId)) return 0
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
            WHERE ${DbContract.D_ID}=?
              AND ${DbContract.D_OWNER_USER_ID}=?
              AND ${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), ownerUserId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    // ---------- CARDS ----------
    data class CardRow(
        val id: Long,
        val front: String,
        val back: String,
        val createdAt: Long
    )

    fun getCardsForDeck(ownerUserId: Long, deckId: Long): List<CardRow> {
        val out = mutableListOf<CardRow>()
        readableDatabase.rawQuery(
            """
            SELECT
                c.${DbContract.C_ID},
                c.${DbContract.C_FRONT},
                c.${DbContract.C_BACK},
                c.${DbContract.C_CREATED_AT}
            FROM ${DbContract.T_CARDS} c
            JOIN ${DbContract.T_DECKS} d
                ON d.${DbContract.D_ID}=c.${DbContract.C_DECK_ID}
            WHERE c.${DbContract.C_DECK_ID}=?
              AND d.${DbContract.D_OWNER_USER_ID}=?
              AND d.${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
            ORDER BY c.${DbContract.C_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(deckId.toString(), ownerUserId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += CardRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            }
        }
        return out
    }

    fun getCardsForDeckAny(deckId: Long): List<CardRow> {
        val out = mutableListOf<CardRow>()
        readableDatabase.rawQuery(
            """
            SELECT
                ${DbContract.C_ID},
                ${DbContract.C_FRONT},
                ${DbContract.C_BACK},
                ${DbContract.C_CREATED_AT}
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
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        if (cleanFront.isBlank() || cleanBack.isBlank()) return -1L
        if (!ownerOwnsActiveDeck(ownerUserId, deckId)) return -1L

        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID, deckId)
            put(DbContract.C_FRONT, cleanFront)
            put(DbContract.C_BACK, cleanBack)
            put(DbContract.C_CREATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    fun updateCard(ownerUserId: Long, deckId: Long, cardId: Long, front: String, back: String): Int {
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        if (cleanFront.isBlank() || cleanBack.isBlank()) return 0
        if (!ownerOwnsActiveDeck(ownerUserId, deckId)) return 0

        val cv = ContentValues().apply {
            put(DbContract.C_FRONT, cleanFront)
            put(DbContract.C_BACK, cleanBack)
        }
        return writableDatabase.update(
            DbContract.T_CARDS,
            cv,
            "${DbContract.C_ID}=? AND ${DbContract.C_DECK_ID}=?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    fun deleteCard(ownerUserId: Long, deckId: Long, cardId: Long): Int {
        if (!ownerOwnsActiveDeck(ownerUserId, deckId)) return 0
        return writableDatabase.delete(
            DbContract.T_CARDS,
            "${DbContract.C_ID}=? AND ${DbContract.C_DECK_ID}=?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    // ---------- STUDY ANALYTICS ----------
    data class DeckStudySummary(
        val deckId: Long,
        val title: String,
        val studyCount: Int,
        val lastStudiedAt: Long
    )

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
            return if (c.moveToFirst()) {
                DeckStudySummary(c.getLong(0), c.getString(1), c.getInt(2), c.getLong(3))
            } else null
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
            return if (c.moveToFirst()) {
                DeckStudySummary(c.getLong(0), c.getString(1), c.getInt(2), c.getLong(3))
            } else null
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

    fun getStudyStreakDays(userId: Long): Int {
        val zone = java.time.ZoneId.systemDefault()

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

        val uniqueDays = mutableListOf<Long>()
        var lastDay: Long? = null
        for (ms in times) {
            val day = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
            if (lastDay == null || day != lastDay) {
                uniqueDays += day
                lastDay = day
            }
        }

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
            DbContract.T_DECKS,
            cv,
            "${DbContract.D_ID}=?",
            arrayOf(deckId.toString())
        )
    }

    fun managerGetCardsForDeck(deckId: Long): List<CardRow> {
        return getCardsForDeckAny(deckId)
    }

    // ---------- REPORTS ----------
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

    fun createDeckReport(reporterUserId: Long, deckId: Long, reason: String, details: String?): Long {
        if (reason.trim().length < 3) return -1L

        val canReport = readableDatabase.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_ID}=?
              AND ${DbContract.D_STATUS}=?
              AND (${DbContract.D_OWNER_USER_ID}=? OR ${DbContract.D_IS_PUBLIC}=1)
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), DbContract.DECK_ACTIVE, reporterUserId.toString())
        ).use { c ->
            c.moveToFirst()
        }

        if (!canReport) return -1L

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
            DbContract.T_REPORTS,
            cv,
            "${DbContract.R_ID}=?",
            arrayOf(reportId.toString())
        )
    }
}