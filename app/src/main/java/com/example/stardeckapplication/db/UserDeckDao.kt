package com.example.stardeckapplication.db

import android.content.ContentValues

class UserDeckDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    data class DeckRow(
        val id          : Long,
        val title       : String,
        val description : String?,
        val isPremium   : Boolean,
        val isPublic    : Boolean,
        val status      : String,
        val createdAt   : Long
    )

    fun getDecksForOwner(userId: Long): List<DeckRow> {
        val out = mutableListOf<DeckRow>()
        readable.rawQuery(
            """
            SELECT
                ${DbContract.D_ID},
                ${DbContract.D_TITLE},
                ${DbContract.D_DESCRIPTION},
                COALESCE(${DbContract.D_IS_PREMIUM}, 0),
                COALESCE(${DbContract.D_IS_PUBLIC},  0),
                ${DbContract.D_STATUS},
                ${DbContract.D_CREATED_AT}
            FROM  ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID} = ?
            ORDER BY ${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += DeckRow(
                    id          = c.getLong(0),
                    title       = c.getString(1),
                    description = c.getString(2),
                    isPremium   = c.getInt(3) == 1,
                    isPublic    = c.getInt(4) == 1,
                    status      = c.getString(5),
                    createdAt   = c.getLong(6)
                )
            }
        }
        return out
    }

    fun getTotalDeckCountAllStatuses(userId: Long): Int {
        readable.rawQuery(
            """
        SELECT COUNT(*)
        FROM   ${DbContract.T_DECKS}
        WHERE  ${DbContract.D_OWNER_USER_ID} = ?
        """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun createDeck(
        ownerUserId : Long,
        title       : String,
        description : String?,
        isPremium   : Boolean,
        isPublic    : Boolean
    ): Long {
        val cv = ContentValues().apply {
            put(DbContract.D_OWNER_USER_ID, ownerUserId)
            put(DbContract.D_TITLE,         title)
            put(DbContract.D_DESCRIPTION,   description)
            put(DbContract.D_IS_PREMIUM,    if (isPremium) 1 else 0)
            put(DbContract.D_IS_PUBLIC,     if (isPublic)  1 else 0)
            put(DbContract.D_STATUS,        DbContract.DECK_ACTIVE)
            put(DbContract.D_CREATED_AT,    System.currentTimeMillis())
        }
        return writable.insert(DbContract.T_DECKS, null, cv)
    }

    /**
     * Creates a locked premium demo deck with sample cards for the given user.
     * Returns the new deck ID, or -1 on failure.
     */
    fun createPremiumDemoDeckForUser(userId: Long): Long {
        val db = writable
        db.beginTransaction()
        return try {
            val deckCv = ContentValues().apply {
                put(DbContract.D_OWNER_USER_ID, userId)
                put(DbContract.D_TITLE,         "Premium Sample Deck")
                put(DbContract.D_DESCRIPTION,   "A demo deck showing premium content.")
                put(DbContract.D_IS_PREMIUM,    1)
                put(DbContract.D_IS_PUBLIC,     0)
                put(DbContract.D_STATUS,        DbContract.DECK_ACTIVE)
                put(DbContract.D_CREATED_AT,    System.currentTimeMillis())
            }
            val deckId = db.insert(DbContract.T_DECKS, null, deckCv)
            if (deckId == -1L) return -1L

            val sampleCards = listOf(
                "What is spaced repetition?"  to "A learning technique that increases intervals of review over time.",
                "What is active recall?"      to "Actively stimulating memory during the learning process.",
                "What is the Leitner system?" to "A flashcard method using boxes to schedule reviews by difficulty.",
                "What is a premium deck?"     to "A deck with exclusive content available to premium subscribers.",
                "What is the forgetting curve?" to "A theory showing how information is lost over time without review."
            )

            for ((front, back) in sampleCards) {
                val cardCv = ContentValues().apply {
                    put(DbContract.C_DECK_ID,    deckId)
                    put(DbContract.C_FRONT,      front)
                    put(DbContract.C_BACK,       back)
                    put(DbContract.C_CREATED_AT, System.currentTimeMillis())
                }
                db.insert(DbContract.T_CARDS, null, cardCv)
            }

            db.setTransactionSuccessful()
            deckId
        } finally {
            db.endTransaction()
        }
    }

    fun updateDeck(
        deckId      : Long,
        title       : String,
        description : String?,
        isPublic    : Boolean
    ): Int {
        val cv = ContentValues().apply {
            put(DbContract.D_TITLE,       title)
            put(DbContract.D_DESCRIPTION, description)
            put(DbContract.D_IS_PUBLIC,   if (isPublic) 1 else 0)
        }
        return writable.update(
            DbContract.T_DECKS,
            cv,
            "${DbContract.D_ID} = ?",
            arrayOf(deckId.toString())
        )
    }

    fun deleteDeck(deckId: Long): Int {
        return writable.delete(
            DbContract.T_DECKS,
            "${DbContract.D_ID} = ?",
            arrayOf(deckId.toString())
        )
    }
}