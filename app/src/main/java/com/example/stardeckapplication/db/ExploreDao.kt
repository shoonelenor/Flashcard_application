package com.example.stardeckapplication.db

import android.content.ContentValues

class ExploreDao(
    private val dbHelper: StarDeckDbHelper,
    private val userDao: UserDao = UserDao(dbHelper)
) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    data class PublicDeckCatalogRow(
        val deckId: Long,
        val title: String,
        val description: String?,
        val ownerName: String,
        val ownerEmail: String,
        val isPremium: Boolean,
        val cardCount: Int,
        val isLocked: Boolean,
        val categoryId: Long?,
        val categoryName: String?,
        val subjectId: Long?,
        val subjectName: String?,
        val languageId: Long?,
        val languageName: String?
    )

    data class PublicCardRow(
        val cardId: Long,
        val question: String,
        val answer: String
    )

    fun getPublicDeckCatalogForUser(userId: Long): List<PublicDeckCatalogRow> {
        val isPremiumUser = userDao.isUserPremium(userId)
        val out = mutableListOf<PublicDeckCatalogRow>()

        readable.rawQuery(
            """
            SELECT
                d.${DbContract.D_ID},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                COALESCE(d.${DbContract.D_IS_PREMIUM}, 0),
                COUNT(c.${DbContract.C_ID}) AS card_count,
                d.${DbContract.D_CATEGORY_ID},
                cat.${DbContract.CAT_NAME},
                d.${DbContract.D_SUBJECT_ID},
                subj.${DbContract.SUBJ_NAME},
                d.${DbContract.D_LANGUAGE_ID},
                lang.${DbContract.LANG_NAME}
            FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID} = d.${DbContract.D_OWNER_USER_ID}
            LEFT JOIN ${DbContract.T_CARDS} c
                ON c.${DbContract.C_DECK_ID} = d.${DbContract.D_ID}
            LEFT JOIN ${DbContract.T_CATEGORIES} cat
                ON cat.${DbContract.CAT_ID} = d.${DbContract.D_CATEGORY_ID}
            LEFT JOIN ${DbContract.T_SUBJECTS} subj
                ON subj.${DbContract.SUBJ_ID} = d.${DbContract.D_SUBJECT_ID}
            LEFT JOIN ${DbContract.T_LANGUAGES} lang
                ON lang.${DbContract.LANG_ID} = d.${DbContract.D_LANGUAGE_ID}
            WHERE d.${DbContract.D_IS_PUBLIC} = 1
              AND d.${DbContract.D_STATUS} = ?
              AND d.${DbContract.D_OWNER_USER_ID} <> ?
            GROUP BY
                d.${DbContract.D_ID},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                d.${DbContract.D_IS_PREMIUM},
                d.${DbContract.D_CATEGORY_ID},
                cat.${DbContract.CAT_NAME},
                d.${DbContract.D_SUBJECT_ID},
                subj.${DbContract.SUBJ_NAME},
                d.${DbContract.D_LANGUAGE_ID},
                lang.${DbContract.LANG_NAME}
            ORDER BY d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(DbContract.DECK_ACTIVE, userId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val deckIsPremium = c.getInt(5) == 1
                out += PublicDeckCatalogRow(
                    deckId = c.getLong(0),
                    title = c.getString(1),
                    description = c.getString(2),
                    ownerName = c.getString(3),
                    ownerEmail = c.getString(4),
                    isPremium = deckIsPremium,
                    cardCount = c.getInt(6),
                    isLocked = deckIsPremium && !isPremiumUser,
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

    fun getPublicDeckCardsForUser(deckId: Long): List<PublicCardRow> {
        val out = mutableListOf<PublicCardRow>()

        readable.rawQuery(
            """
            SELECT
                ${DbContract.C_ID},
                ${DbContract.C_FRONT},
                ${DbContract.C_BACK}
            FROM ${DbContract.T_CARDS}
            WHERE ${DbContract.C_DECK_ID} = ?
            ORDER BY ${DbContract.C_ID} ASC
            """.trimIndent(),
            arrayOf(deckId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += PublicCardRow(
                    cardId = c.getLong(0),
                    question = c.getString(1),
                    answer = c.getString(2)
                )
            }
        }

        return out
    }

    fun copyPublicDeckToUser(sourceDeckId: Long, targetUserId: Long): Long {
        val db = writable
        db.beginTransaction()
        return try {
            val deckCv = ContentValues()

            db.rawQuery(
                """
                SELECT
                    ${DbContract.D_TITLE},
                    ${DbContract.D_DESCRIPTION},
                    ${DbContract.D_IS_PREMIUM},
                    ${DbContract.D_CATEGORY_ID},
                    ${DbContract.D_SUBJECT_ID},
                    ${DbContract.D_LANGUAGE_ID}
                FROM ${DbContract.T_DECKS}
                WHERE ${DbContract.D_ID} = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(sourceDeckId.toString())
            ).use { c ->
                if (c.moveToFirst()) {
                    deckCv.put(DbContract.D_OWNER_USER_ID, targetUserId)
                    deckCv.put(DbContract.D_TITLE, c.getString(0) + " (copy)")
                    deckCv.put(DbContract.D_DESCRIPTION, c.getString(1))
                    deckCv.put(DbContract.D_IS_PREMIUM, c.getInt(2))
                    deckCv.put(DbContract.D_IS_PUBLIC, 0)
                    deckCv.put(DbContract.D_STATUS, DbContract.DECK_ACTIVE)
                    deckCv.put(DbContract.D_CREATED_AT, System.currentTimeMillis())

                    if (c.isNull(3)) deckCv.putNull(DbContract.D_CATEGORY_ID)
                    else deckCv.put(DbContract.D_CATEGORY_ID, c.getLong(3))

                    if (c.isNull(4)) deckCv.putNull(DbContract.D_SUBJECT_ID)
                    else deckCv.put(DbContract.D_SUBJECT_ID, c.getLong(4))

                    if (c.isNull(5)) deckCv.putNull(DbContract.D_LANGUAGE_ID)
                    else deckCv.put(DbContract.D_LANGUAGE_ID, c.getLong(5))
                }
            }

            if (deckCv.size() == 0) return -1L

            val newDeckId = db.insert(DbContract.T_DECKS, null, deckCv)
            if (newDeckId == -1L) return -1L

            db.rawQuery(
                """
                SELECT
                    ${DbContract.C_FRONT},
                    ${DbContract.C_BACK}
                FROM ${DbContract.T_CARDS}
                WHERE ${DbContract.C_DECK_ID} = ?
                """.trimIndent(),
                arrayOf(sourceDeckId.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    val cardCv = ContentValues().apply {
                        put(DbContract.C_DECK_ID, newDeckId)
                        put(DbContract.C_FRONT, c.getString(0))
                        put(DbContract.C_BACK, c.getString(1))
                        put(DbContract.C_CREATED_AT, System.currentTimeMillis())
                    }
                    db.insert(DbContract.T_CARDS, null, cardCv)
                }
            }

            db.setTransactionSuccessful()
            newDeckId
        } finally {
            db.endTransaction()
        }
    }
}