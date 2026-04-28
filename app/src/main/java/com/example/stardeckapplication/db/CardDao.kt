package com.example.stardeckapplication.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class CardDao(context: Context) {

    private val dbHelper = StarDeckDbHelper(context)

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun getReadableDb(): SQLiteDatabase = dbHelper.readableDatabase
    private fun getWritableDb(): SQLiteDatabase = dbHelper.writableDatabase

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /**
     * Insert a new card. Returns the new row id, or -1 on failure.
     * @param deckId      parent deck
     * @param front       front text
     * @param back        back text
     * @param frontImagePath  optional absolute path saved in internal storage
     * @param backImagePath   optional absolute path saved in internal storage
     */
    fun insertCard(
        deckId: Long,
        front: String,
        back: String,
        frontImagePath: String? = null,
        backImagePath: String? = null
    ): Long {
        val db = getWritableDb()
        val cv = ContentValues().apply {
            put(DbContract.CDECKID, deckId)
            put(DbContract.CFRONT, front.trim())
            put(DbContract.CBACK, back.trim())
            put(DbContract.CFRONTIMAGEPATH, frontImagePath)
            put(DbContract.CBACKIMAGEPATH, backImagePath)
            put(DbContract.CCREATEDAT, System.currentTimeMillis())
        }
        return db.insert(DbContract.TCARDS, null, cv)
    }

    /**
     * Update an existing card by its id.
     * @return number of rows affected (1 on success, 0 if not found)
     */
    fun updateCard(
        cardId: Long,
        front: String,
        back: String,
        frontImagePath: String? = null,
        backImagePath: String? = null
    ): Int {
        val db = getWritableDb()
        val cv = ContentValues().apply {
            put(DbContract.CFRONT, front.trim())
            put(DbContract.CBACK, back.trim())
            put(DbContract.CFRONTIMAGEPATH, frontImagePath)
            put(DbContract.CBACKIMAGEPATH, backImagePath)
        }
        return db.update(
            DbContract.TCARDS,
            cv,
            "${DbContract.CID} = ?",
            arrayOf(cardId.toString())
        )
    }

    /** Delete a single card by id. Returns rows affected. */
    fun deleteCard(cardId: Long): Int {
        val db = getWritableDb()
        return db.delete(
            DbContract.TCARDS,
            "${DbContract.CID} = ?",
            arrayOf(cardId.toString())
        )
    }

    /** Delete all cards belonging to a deck. Returns rows affected. */
    fun deleteCardsByDeck(deckId: Long): Int {
        val db = getWritableDb()
        return db.delete(
            DbContract.TCARDS,
            "${DbContract.CDECKID} = ?",
            arrayOf(deckId.toString())
        )
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Return a Cursor of all cards for [deckId], ordered by creation time.
     * Caller is responsible for closing the cursor.
     */
    fun getCardsByDeck(deckId: Long): Cursor {
        val db = getReadableDb()
        return db.query(
            DbContract.TCARDS,
            arrayOf(
                DbContract.CID,
                DbContract.CDECKID,
                DbContract.CFRONT,
                DbContract.CBACK,
                DbContract.CFRONTIMAGEPATH,
                DbContract.CBACKIMAGEPATH,
                DbContract.CCREATEDAT
            ),
            "${DbContract.CDECKID} = ?",
            arrayOf(deckId.toString()),
            null, null,
            "${DbContract.CCREATEDAT} ASC"
        )
    }

    /**
     * Return a Cursor for a single card by [cardId].
     * Caller is responsible for closing the cursor.
     */
    fun getCardById(cardId: Long): Cursor {
        val db = getReadableDb()
        return db.query(
            DbContract.TCARDS,
            arrayOf(
                DbContract.CID,
                DbContract.CDECKID,
                DbContract.CFRONT,
                DbContract.CBACK,
                DbContract.CFRONTIMAGEPATH,
                DbContract.CBACKIMAGEPATH,
                DbContract.CCREATEDAT
            ),
            "${DbContract.CID} = ?",
            arrayOf(cardId.toString()),
            null, null, null
        )
    }

    /** Return the total number of cards in a deck. */
    fun getCardCountByDeck(deckId: Long): Int {
        val db = getReadableDb()
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${DbContract.TCARDS} WHERE ${DbContract.CDECKID} = ?",
            arrayOf(deckId.toString())
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    // ── Convenience data-class mapper ─────────────────────────────────────────

    /** Convenience: return all cards for [deckId] as a list of [Card] objects. */
    fun getCardListByDeck(deckId: Long): List<Card> {
        val result = mutableListOf<Card>()
        getCardsByDeck(deckId).use { cursor ->
            val idIdx            = cursor.getColumnIndexOrThrow(DbContract.CID)
            val deckIdIdx        = cursor.getColumnIndexOrThrow(DbContract.CDECKID)
            val frontIdx         = cursor.getColumnIndexOrThrow(DbContract.CFRONT)
            val backIdx          = cursor.getColumnIndexOrThrow(DbContract.CBACK)
            val frontImageIdx    = cursor.getColumnIndexOrThrow(DbContract.CFRONTIMAGEPATH)
            val backImageIdx     = cursor.getColumnIndexOrThrow(DbContract.CBACKIMAGEPATH)
            val createdAtIdx     = cursor.getColumnIndexOrThrow(DbContract.CCREATEDAT)
            while (cursor.moveToNext()) {
                result += Card(
                    id             = cursor.getLong(idIdx),
                    deckId         = cursor.getLong(deckIdIdx),
                    front          = cursor.getString(frontIdx),
                    back           = cursor.getString(backIdx),
                    frontImagePath = cursor.getString(frontImageIdx),
                    backImagePath  = cursor.getString(backImageIdx),
                    createdAt      = cursor.getLong(createdAtIdx)
                )
            }
        }
        return result
    }

    /** Convenience: fetch a single [Card] or null if not found. */
    fun getCardOrNull(cardId: Long): Card? {
        getCardById(cardId).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return Card(
                id             = cursor.getLong(cursor.getColumnIndexOrThrow(DbContract.CID)),
                deckId         = cursor.getLong(cursor.getColumnIndexOrThrow(DbContract.CDECKID)),
                front          = cursor.getString(cursor.getColumnIndexOrThrow(DbContract.CFRONT)),
                back           = cursor.getString(cursor.getColumnIndexOrThrow(DbContract.CBACK)),
                frontImagePath = cursor.getString(cursor.getColumnIndexOrThrow(DbContract.CFRONTIMAGEPATH)),
                backImagePath  = cursor.getString(cursor.getColumnIndexOrThrow(DbContract.CBACKIMAGEPATH)),
                createdAt      = cursor.getLong(cursor.getColumnIndexOrThrow(DbContract.CCREATEDAT))
            )
        }
    }

    // ── Data class ────────────────────────────────────────────────────────────

    data class Card(
        val id: Long = 0L,
        val deckId: Long,
        val front: String,
        val back: String,
        val frontImagePath: String? = null,
        val backImagePath: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    )
}
