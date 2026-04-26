package com.example.stardeckapplication.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

class CardDao(private val dbHelper: StarDeckDbHelper) {

    private val readable: SQLiteDatabase get() = dbHelper.readableDatabase
    private val writable: SQLiteDatabase get() = dbHelper.writableDatabase

    // ---------- MODEL ----------

    data class CardRow(
        val id              : Long,
        val front           : String,
        val back            : String,
        val createdAt       : Long,
        val frontImagePath  : String? = null,
        val backImagePath   : String? = null
    )

    // ---------- HELPERS ----------

    private fun ownerOwnsActiveDeck(ownerUserId: Long, deckId: Long): Boolean {
        readable.rawQuery(
            """
            SELECT 1 FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID} = ?
              AND ${DbContract.D_ID} = ?
              AND ${DbContract.D_STATUS} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(ownerUserId.toString(), deckId.toString(), DbContract.DECK_ACTIVE)
        ).use { return it.moveToFirst() }
    }

    private fun deckExistsAny(deckId: Long): Boolean {
        readable.rawQuery(
            "SELECT 1 FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_ID} = ? LIMIT 1",
            arrayOf(deckId.toString())
        ).use { return it.moveToFirst() }
    }

    // ---------- OWNER CARD CRUD ----------

    fun createCard(
        ownerUserId: Long,
        deckId: Long,
        front: String,
        back: String,
        frontImagePath: String? = null,
        backImagePath: String? = null
    ): Long {
        val cleanFront = front.trim()
        val cleanBack  = back.trim()
        if (cleanFront.isBlank() && cleanBack.isBlank()) return -1L
        if (!ownerOwnsActiveDeck(ownerUserId, deckId)) return -1L

        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID,         deckId)
            put(DbContract.C_FRONT,           cleanFront)
            put(DbContract.C_BACK,            cleanBack)
            put(DbContract.C_CREATED_AT,      System.currentTimeMillis())
            if (frontImagePath != null) put(DbContract.C_FRONT_IMAGE_PATH, frontImagePath)
            if (backImagePath  != null) put(DbContract.C_BACK_IMAGE_PATH,  backImagePath)
        }
        return writable.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    fun updateCard(
        ownerUserId: Long,
        deckId: Long,
        cardId: Long,
        front: String,
        back: String,
        frontImagePath: String? = null,
        backImagePath: String? = null,
        clearFrontImage: Boolean = false,
        clearBackImage: Boolean = false
    ): Int {
        val cleanFront = front.trim()
        val cleanBack  = back.trim()
        if (cleanFront.isBlank() && cleanBack.isBlank()) return 0
        if (!ownerOwnsActiveDeck(ownerUserId, deckId)) return 0

        val cv = ContentValues().apply {
            put(DbContract.C_FRONT, cleanFront)
            put(DbContract.C_BACK,  cleanBack)
            when {
                clearFrontImage             -> putNull(DbContract.C_FRONT_IMAGE_PATH)
                frontImagePath != null      -> put(DbContract.C_FRONT_IMAGE_PATH, frontImagePath)
            }
            when {
                clearBackImage              -> putNull(DbContract.C_BACK_IMAGE_PATH)
                backImagePath  != null      -> put(DbContract.C_BACK_IMAGE_PATH, backImagePath)
            }
        }
        return writable.update(
            DbContract.T_CARDS, cv,
            "${DbContract.C_ID} = ? AND ${DbContract.C_DECK_ID} = ?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    fun deleteCard(ownerUserId: Long, deckId: Long, cardId: Long): Int {
        if (!ownerOwnsActiveDeck(ownerUserId, deckId)) return 0
        return writable.delete(
            DbContract.T_CARDS,
            "${DbContract.C_ID} = ? AND ${DbContract.C_DECK_ID} = ?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    fun getCardsForDeck(ownerUserId: Long, deckId: Long): List<CardRow> {
        val out = mutableListOf<CardRow>()
        readable.rawQuery(
            """
            SELECT c.${DbContract.C_ID}, c.${DbContract.C_FRONT},
                   c.${DbContract.C_BACK}, c.${DbContract.C_CREATED_AT},
                   c.${DbContract.C_FRONT_IMAGE_PATH}, c.${DbContract.C_BACK_IMAGE_PATH}
            FROM   ${DbContract.T_CARDS} c
            JOIN   ${DbContract.T_DECKS} d ON d.${DbContract.D_ID} = c.${DbContract.C_DECK_ID}
            WHERE  c.${DbContract.C_DECK_ID}       = ?
              AND  d.${DbContract.D_OWNER_USER_ID}  = ?
              AND  d.${DbContract.D_STATUS}         = ?
            ORDER  BY c.${DbContract.C_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(deckId.toString(), ownerUserId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            while (c.moveToNext()) {
                out += CardRow(
                    id             = c.getLong(0),
                    front          = c.getString(1),
                    back           = c.getString(2),
                    createdAt      = c.getLong(3),
                    frontImagePath = c.getString(4),
                    backImagePath  = c.getString(5)
                )
            }
        }
        return out
    }

    // ---------- PUBLIC LIBRARY ----------

    fun getPublicDeckCardsForUser(userId: Long, deckId: Long): List<CardRow> {
        val isLocked = readable.rawQuery(
            """
            SELECT CASE
                WHEN COALESCE(d.${DbContract.D_IS_PREMIUM}, 0) = 1
                     AND COALESCE(u.${DbContract.U_IS_PREMIUM_USER}, 0) = 0
                THEN 1 ELSE 0 END
            FROM   ${DbContract.T_DECKS} d
            LEFT   JOIN ${DbContract.T_USERS} u ON u.${DbContract.U_ID} = ?
            WHERE  d.${DbContract.D_ID}     = ?
              AND  d.${DbContract.D_STATUS} = ?
              AND  COALESCE(d.${DbContract.D_IS_PUBLIC}, 0) = 1
            LIMIT  1
            """.trimIndent(),
            arrayOf(userId.toString(), deckId.toString(), DbContract.DECK_ACTIVE)
        ).use { c -> c.moveToFirst() && c.getInt(0) == 1 }

        if (isLocked) return emptyList()

        val out = mutableListOf<CardRow>()
        readable.rawQuery(
            """
            SELECT c.${DbContract.C_ID}, c.${DbContract.C_FRONT},
                   c.${DbContract.C_BACK}, c.${DbContract.C_CREATED_AT},
                   c.${DbContract.C_FRONT_IMAGE_PATH}, c.${DbContract.C_BACK_IMAGE_PATH}
            FROM   ${DbContract.T_CARDS} c
            INNER  JOIN ${DbContract.T_DECKS} d ON d.${DbContract.D_ID} = c.${DbContract.C_DECK_ID}
            WHERE  d.${DbContract.D_ID}     = ?
              AND  d.${DbContract.D_STATUS} = ?
              AND  COALESCE(d.${DbContract.D_IS_PUBLIC}, 0) = 1
            ORDER  BY c.${DbContract.C_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(deckId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            while (c.moveToNext()) {
                out += CardRow(
                    id             = c.getLong(0),
                    front          = c.getString(1),
                    back           = c.getString(2),
                    createdAt      = c.getLong(3),
                    frontImagePath = c.getString(4),
                    backImagePath  = c.getString(5)
                )
            }
        }
        return out
    }

    // ---------- GENERIC / COMPAT METHODS ----------

    fun getCardsForDeckAny(deckId: Long): List<CardRow> {
        val out = mutableListOf<CardRow>()
        readable.rawQuery(
            """
            SELECT ${DbContract.C_ID}, ${DbContract.C_FRONT},
                   ${DbContract.C_BACK}, ${DbContract.C_CREATED_AT},
                   ${DbContract.C_FRONT_IMAGE_PATH}, ${DbContract.C_BACK_IMAGE_PATH}
            FROM   ${DbContract.T_CARDS}
            WHERE  ${DbContract.C_DECK_ID} = ?
            ORDER  BY ${DbContract.C_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(deckId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += CardRow(
                    id             = c.getLong(0),
                    front          = c.getString(1),
                    back           = c.getString(2),
                    createdAt      = c.getLong(3),
                    frontImagePath = c.getString(4),
                    backImagePath  = c.getString(5)
                )
            }
        }
        return out
    }

    fun managerGetCardsForDeck(deckId: Long): List<CardRow> = getCardsForDeckAny(deckId)

    fun getCardCountForDeck(deckId: Long): Int {
        readable.rawQuery(
            """
        SELECT COUNT(*) FROM ${DbContract.T_CARDS}
        WHERE  ${DbContract.C_DECK_ID} = ?
        """.trimIndent(),
            arrayOf(deckId.toString())
        ).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun createCardAny(
        deckId: Long,
        front: String,
        back: String,
        frontImagePath: String? = null,
        backImagePath: String? = null
    ): Long {
        if (!deckExistsAny(deckId)) return -1L
        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID,    deckId)
            put(DbContract.C_FRONT,      front.trim())
            put(DbContract.C_BACK,       back.trim())
            put(DbContract.C_CREATED_AT, System.currentTimeMillis())
            if (frontImagePath != null) put(DbContract.C_FRONT_IMAGE_PATH, frontImagePath)
            if (backImagePath  != null) put(DbContract.C_BACK_IMAGE_PATH,  backImagePath)
        }
        return writable.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    fun updateCardAny(
        deckId: Long,
        cardId: Long,
        front: String,
        back: String,
        frontImagePath: String? = null,
        backImagePath: String? = null
    ): Int {
        val cv = ContentValues().apply {
            put(DbContract.C_FRONT, front.trim())
            put(DbContract.C_BACK,  back.trim())
            if (frontImagePath != null) put(DbContract.C_FRONT_IMAGE_PATH, frontImagePath)
            if (backImagePath  != null) put(DbContract.C_BACK_IMAGE_PATH,  backImagePath)
        }
        return writable.update(
            DbContract.T_CARDS, cv,
            "${DbContract.C_ID} = ? AND ${DbContract.C_DECK_ID} = ?",
            arrayOf(cardId.toString(), deckId.toString())
        )
    }

    fun deleteCardAny(deckId: Long, cardId: Long): Int =
        writable.delete(
            DbContract.T_CARDS,
            "${DbContract.C_ID} = ? AND ${DbContract.C_DECK_ID} = ?",
            arrayOf(cardId.toString(), deckId.toString())
        )

    fun adminCreateCardContent(deckId: Long, front: String, back: String): Long {
        val f = front.trim(); val b = back.trim()
        if (f.isBlank() && b.isBlank()) return -1L
        if (!deckExistsAny(deckId)) return -1L
        return createCardAny(deckId, f, b)
    }

    fun adminUpdateCardContent(deckId: Long, cardId: Long, front: String, back: String): Int {
        val f = front.trim(); val b = back.trim()
        if (f.isBlank() && b.isBlank()) return 0
        if (!deckExistsAny(deckId)) return 0
        return updateCardAny(deckId, cardId, f, b)
    }

    fun adminDeleteCardContent(deckId: Long, cardId: Long): Int {
        if (!deckExistsAny(deckId)) return 0
        return deleteCardAny(deckId, cardId)
    }

    fun getTotalCardCountForOwnerAllStatuses(ownerUserId: Long): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*) FROM ${DbContract.T_CARDS} c
            JOIN ${DbContract.T_DECKS} d ON d.${DbContract.D_ID} = c.${DbContract.C_DECK_ID}
            WHERE d.${DbContract.D_OWNER_USER_ID} = ?
            """.trimIndent(),
            arrayOf(ownerUserId.toString())
        ).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}
