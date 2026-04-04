package com.example.stardeckapplication.db

import android.content.ContentValues

class UserDeckDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    data class DeckRow(
        val id: Long,
        val title: String,
        val description: String?,
        val isPremium: Boolean,
        val isPublic: Boolean,
        val status: String,
        val createdAt: Long,
        val categoryId: Long?,
        val categoryName: String?,
        val subjectId: Long?,
        val subjectName: String?,
        val languageId: Long?,
        val languageName: String?
    )

    fun getDecksForOwner(userId: Long): List<DeckRow> {
        val out = mutableListOf<DeckRow>()
        readable.rawQuery(
            """
            SELECT
                d.${DbContract.D_ID},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                COALESCE(d.${DbContract.D_IS_PREMIUM}, 0),
                COALESCE(d.${DbContract.D_IS_PUBLIC}, 0),
                d.${DbContract.D_STATUS},
                d.${DbContract.D_CREATED_AT},
                d.${DbContract.D_CATEGORY_ID},
                c.${DbContract.CAT_NAME},
                d.${DbContract.D_SUBJECT_ID},
                s.${DbContract.SUBJ_NAME},
                d.${DbContract.D_LANGUAGE_ID},
                l.${DbContract.LANG_NAME}
            FROM ${DbContract.T_DECKS} d
            LEFT JOIN ${DbContract.T_CATEGORIES} c
                ON c.${DbContract.CAT_ID} = d.${DbContract.D_CATEGORY_ID}
            LEFT JOIN ${DbContract.T_SUBJECTS} s
                ON s.${DbContract.SUBJ_ID} = d.${DbContract.D_SUBJECT_ID}
            LEFT JOIN ${DbContract.T_LANGUAGES} l
                ON l.${DbContract.LANG_ID} = d.${DbContract.D_LANGUAGE_ID}
            WHERE d.${DbContract.D_OWNER_USER_ID} = ?
            ORDER BY d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += DeckRow(
                    id = c.getLong(0),
                    title = c.getString(1),
                    description = c.getString(2),
                    isPremium = c.getInt(3) == 1,
                    isPublic = c.getInt(4) == 1,
                    status = c.getString(5),
                    createdAt = c.getLong(6),
                    categoryId = if (c.isNull(7)) null else c.getLong(7),
                    categoryName = c.getString(8),
                    subjectId = if (c.isNull(9)) null else c.getLong(9),
                    subjectName = c.getString(10),
                    languageId = if (c.isNull(11)) null else c.getLong(11),
                    languageName = c.getString(12)
                )
            }
        }
        return out
    }

    fun getTotalDeckCountAllStatuses(userId: Long): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID} = ?
            """.trimIndent(),
            arrayOf(userId.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun createDeck(
        ownerUserId: Long,
        title: String,
        description: String?,
        isPremium: Boolean,
        isPublic: Boolean,
        categoryId: Long? = null,
        subjectId: Long? = null,
        languageId: Long? = null
    ): Long {
        val cv = ContentValues().apply {
            put(DbContract.D_OWNER_USER_ID, ownerUserId)
            put(DbContract.D_TITLE, title)
            put(DbContract.D_DESCRIPTION, description)
            put(DbContract.D_IS_PREMIUM, if (isPremium) 1 else 0)
            put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)
            put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
            put(DbContract.D_CREATED_AT, System.currentTimeMillis())

            if (categoryId != null && categoryId > 0L) put(DbContract.D_CATEGORY_ID, categoryId)
            else putNull(DbContract.D_CATEGORY_ID)

            if (subjectId != null && subjectId > 0L) put(DbContract.D_SUBJECT_ID, subjectId)
            else putNull(DbContract.D_SUBJECT_ID)

            if (languageId != null && languageId > 0L) put(DbContract.D_LANGUAGE_ID, languageId)
            else putNull(DbContract.D_LANGUAGE_ID)
        }
        return writable.insert(DbContract.T_DECKS, null, cv)
    }

    fun createPremiumDemoDeckForUser(userId: Long): Long {
        val db = writable
        db.beginTransaction()
        return try {
            val deckCv = ContentValues().apply {
                put(DbContract.D_OWNER_USER_ID, userId)
                put(DbContract.D_TITLE, "Premium Sample Deck")
                put(DbContract.D_DESCRIPTION, "A demo deck showing premium content.")
                put(DbContract.D_IS_PREMIUM, 1)
                put(DbContract.D_IS_PUBLIC, 0)
                put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                put(DbContract.D_CREATED_AT, System.currentTimeMillis())
                putNull(DbContract.D_CATEGORY_ID)
                putNull(DbContract.D_SUBJECT_ID)
                putNull(DbContract.D_LANGUAGE_ID)
            }
            val deckId = db.insert(DbContract.T_DECKS, null, deckCv)
            if (deckId == -1L) return -1L

            val sampleCards = listOf(
                "What is spaced repetition?" to "A learning technique that increases intervals of review over time.",
                "What is active recall?" to "Actively stimulating memory during the learning process.",
                "What is the Leitner system?" to "A flashcard method using boxes to schedule reviews by difficulty.",
                "What is a premium deck?" to "A deck with exclusive content available to premium subscribers.",
                "What is the forgetting curve?" to "A theory showing how information is lost over time without review."
            )

            for ((front, back) in sampleCards) {
                val cardCv = ContentValues().apply {
                    put(DbContract.C_DECK_ID, deckId)
                    put(DbContract.C_FRONT, front)
                    put(DbContract.C_BACK, back)
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
        deckId: Long,
        title: String,
        description: String?,
        isPublic: Boolean,
        categoryId: Long? = null,
        subjectId: Long? = null,
        languageId: Long? = null
    ): Int {
        val cv = ContentValues().apply {
            put(DbContract.D_TITLE, title)
            put(DbContract.D_DESCRIPTION, description)
            put(DbContract.D_IS_PUBLIC, if (isPublic) 1 else 0)

            if (categoryId != null && categoryId > 0L) put(DbContract.D_CATEGORY_ID, categoryId)
            else putNull(DbContract.D_CATEGORY_ID)

            if (subjectId != null && subjectId > 0L) put(DbContract.D_SUBJECT_ID, subjectId)
            else putNull(DbContract.D_SUBJECT_ID)

            if (languageId != null && languageId > 0L) put(DbContract.D_LANGUAGE_ID, languageId)
            else putNull(DbContract.D_LANGUAGE_ID)
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