package com.example.stardeckapplication.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * StudyDao: SRS state (card_progress) + due cards + study sessions.
 *
 * Usage:
 *   val dbHelper = StarDeckDbHelper(context)
 *   val studyDao = StudyDao(dbHelper)
 *
 *   val dueCount = studyDao.getDueCountForDeck(userId, deckId)
 *   val cards = studyDao.getDueCardsForDeck(userId, deckId)
 *   val sessionId = studyDao.applySrsReview(userId, deckId, cardId, DbContract.RESULT_KNOWN)
 */
class StudyDao(private val dbHelper: StarDeckDbHelper) {

    private val readable: SQLiteDatabase
        get() = dbHelper.readableDatabase

    private val writable: SQLiteDatabase
        get() = dbHelper.writableDatabase

    // ---------- PUBLIC MODELS ----------

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

    // ---------- INTERNAL MODELS ----------

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

    // ---------- SNAPSHOT API ----------

    fun getCardProgressSnapshot(userId: Long, cardId: Long): CardProgressSnapshot? {
        readable.rawQuery(
            """
            SELECT
                ${DbContract.PDUEAT},
                ${DbContract.PLASTREVIEWEDAT},
                ${DbContract.PINTERVALDAYS},
                ${DbContract.PEASEFACTOR},
                ${DbContract.PREVIEWCOUNT},
                ${DbContract.PLAPSECOUNT},
                ${DbContract.PLASTRESULT}
            FROM ${DbContract.TCARDPROGRESS}
            WHERE ${DbContract.PUSERID} = ?
              AND ${DbContract.PCARDID} = ?
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
        val db = writable
        if (snapshot == null) {
            db.delete(
                DbContract.TCARDPROGRESS,
                "${DbContract.PUSERID} = ? AND ${DbContract.PCARDID} = ?",
                arrayOf(userId.toString(), cardId.toString())
            )
            return true
        }

        val cv = ContentValues().apply {
            put(DbContract.PUSERID, userId)
            put(DbContract.PCARDID, cardId)
            put(DbContract.PDUEAT, snapshot.dueAt)
            if (snapshot.lastReviewedAt == null) {
                putNull(DbContract.PLASTREVIEWEDAT)
            } else {
                put(DbContract.PLASTREVIEWEDAT, snapshot.lastReviewedAt)
            }
            put(DbContract.PINTERVALDAYS, snapshot.intervalDays)
            put(DbContract.PEASEFACTOR, snapshot.easeFactor)
            put(DbContract.PREVIEWCOUNT, snapshot.reviewCount)
            put(DbContract.PLAPSECOUNT, snapshot.lapseCount)
            if (snapshot.lastResult == null) {
                putNull(DbContract.PLASTRESULT)
            } else {
                put(DbContract.PLASTRESULT, snapshot.lastResult)
            }
        }

        val updated = db.update(
            DbContract.TCARDPROGRESS,
            cv,
            "${DbContract.PUSERID} = ? AND ${DbContract.PCARDID} = ?",
            arrayOf(userId.toString(), cardId.toString())
        )
        if (updated == 0) {
            db.insert(DbContract.TCARDPROGRESS, null, cv)
        }
        return true
    }

    // ---------- DUE COUNTS ----------

    fun getDueCountForDeck(
        ownerUserId: Long,
        deckId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        ensureCardProgressRowsForDeck(ownerUserId, deckId)

        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.TCARDS} c
            INNER JOIN ${DbContract.TDECKS} d
                ON d.${DbContract.DID} = c.${DbContract.CDECKID}
            INNER JOIN ${DbContract.TCARDPROGRESS} p
                ON p.${DbContract.PCARDID} = c.${DbContract.CID}
               AND p.${DbContract.PUSERID} = ?
            WHERE c.${DbContract.CDECKID} = ?
              AND d.${DbContract.DOWNERUSERID} = ?
              AND d.${DbContract.DSTATUS} = ?
              AND p.${DbContract.PDUEAT} <= ?
            """.trimIndent(),
            arrayOf(
                ownerUserId.toString(),
                deckId.toString(),
                ownerUserId.toString(),
                DbContract.DECKACTIVE,
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
        ensureCardProgressRowsForUser(userId)

        readable.rawQuery(
            """
        SELECT COUNT(*)
        FROM ${DbContract.TCARDPROGRESS} p
        INNER JOIN ${DbContract.TCARDS} c
            ON c.${DbContract.CID} = p.${DbContract.PCARDID}
        INNER JOIN ${DbContract.TDECKS} d
            ON d.${DbContract.DID} = c.${DbContract.CDECKID}
        WHERE p.${DbContract.PUSERID} = ?
          AND d.${DbContract.DOWNERUSERID} = ?
          AND d.${DbContract.DSTATUS} = ?
          AND p.${DbContract.PDUEAT} <= ?
        """.trimIndent(),
            arrayOf(
                userId.toString(),
                userId.toString(),
                DbContract.DECKACTIVE,
                nowMs.toString()
            )
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    // ---------- DUE CARDS LIST ----------

    fun getDueCardsForDeck(
        ownerUserId: Long,
        deckId: Long,
        nowMs: Long = System.currentTimeMillis(),
        limit: Int = 999
    ): List<DueCardRow> {
        ensureCardProgressRowsForDeck(ownerUserId, deckId)

        val out = mutableListOf<DueCardRow>()
        readable.rawQuery(
            """
            SELECT
                c.${DbContract.CID},
                c.${DbContract.CFRONT},
                c.${DbContract.CBACK},
                c.${DbContract.CCREATEDAT},
                p.${DbContract.PDUEAT},
                p.${DbContract.PINTERVALDAYS},
                p.${DbContract.PREVIEWCOUNT},
                p.${DbContract.PLASTRESULT}
            FROM ${DbContract.TCARDS} c
            INNER JOIN ${DbContract.TDECKS} d
                ON d.${DbContract.DID} = c.${DbContract.CDECKID}
            INNER JOIN ${DbContract.TCARDPROGRESS} p
                ON p.${DbContract.PCARDID} = c.${DbContract.CID}
               AND p.${DbContract.PUSERID} = ?
            WHERE c.${DbContract.CDECKID} = ?
              AND d.${DbContract.DOWNERUSERID} = ?
              AND d.${DbContract.DSTATUS} = ?
              AND p.${DbContract.PDUEAT} <= ?
            ORDER BY
                p.${DbContract.PDUEAT} ASC,
                c.${DbContract.CCREATEDAT} ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                ownerUserId.toString(),
                deckId.toString(),
                ownerUserId.toString(),
                DbContract.DECKACTIVE,
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

    // ---------- APPLY REVIEW (SRS) — owned deck ----------

    /**
     * Apply SRS review result for a card in a deck the user OWNS.
     * Returns new study session id, or -1L on error/invalid.
     */
    fun applySrsReview(
        ownerUserId: Long,
        deckId: Long,
        cardId: Long,
        result: String,
        reviewedAt: Long = System.currentTimeMillis()
    ): Long {
        if (result != DbContract.RESULT_KNOWN && result != DbContract.RESULT_HARD) {
            return -1L
        }
        if (!ownsCardForStudy(ownerUserId, deckId, cardId)) return -1L

        val db = writable
        db.beginTransaction()
        return try {
            ensureCardProgressRowForCard(db, ownerUserId, deckId, cardId)
            val current = readCardProgressRow(db, ownerUserId, cardId)
            val next = computeNextSrsState(current, result, reviewedAt)

            val cv = ContentValues().apply {
                put(DbContract.PDUEAT, next.dueAt)
                put(DbContract.PLASTREVIEWEDAT, next.lastReviewedAt)
                put(DbContract.PINTERVALDAYS, next.intervalDays)
                put(DbContract.PEASEFACTOR, next.easeFactor)
                put(DbContract.PREVIEWCOUNT, next.reviewCount)
                put(DbContract.PLAPSECOUNT, next.lapseCount)
                put(DbContract.PLASTRESULT, next.lastResult)
            }
            db.update(
                DbContract.TCARDPROGRESS,
                cv,
                "${DbContract.PUSERID} = ? AND ${DbContract.PCARDID} = ?",
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
            sessionId
        } finally {
            db.endTransaction()
        }
    }

    // ---------- APPLY REVIEW (SRS) — public / shared deck ----------

    /**
     * Apply SRS review for a card in ANY deck (including public decks the user does NOT own).
     * The card_progress row is keyed by (studyingUserId, cardId) so it never
     * conflicts with the deck owner's own progress.
     * Returns new study session id, or -1L on error.
     */
    fun applySrsReviewForPublicDeck(
        studyingUserId: Long,
        deckId: Long,
        cardId: Long,
        result: String,
        reviewedAt: Long = System.currentTimeMillis()
    ): Long {
        if (result != DbContract.RESULT_KNOWN && result != DbContract.RESULT_HARD) return -1L

        val db = writable
        db.beginTransaction()
        return try {
            // Ensure a progress row exists for this (user, card) pair
            db.execSQL(
                """
                INSERT OR IGNORE INTO ${DbContract.TCARDPROGRESS} (
                    ${DbContract.PUSERID},
                    ${DbContract.PCARDID},
                    ${DbContract.PDUEAT},
                    ${DbContract.PLASTREVIEWEDAT},
                    ${DbContract.PINTERVALDAYS},
                    ${DbContract.PEASEFACTOR},
                    ${DbContract.PREVIEWCOUNT},
                    ${DbContract.PLAPSECOUNT},
                    ${DbContract.PLASTRESULT}
                ) VALUES (?, ?, 0, NULL, 0, 2.5, 0, 0, NULL)
                """.trimIndent(),
                arrayOf(studyingUserId, cardId)
            )

            val current = readCardProgressRow(db, studyingUserId, cardId)
            val next    = computeNextSrsState(current, result, reviewedAt)

            val cv = ContentValues().apply {
                put(DbContract.PDUEAT,          next.dueAt)
                put(DbContract.PLASTREVIEWEDAT, next.lastReviewedAt)
                put(DbContract.PINTERVALDAYS,   next.intervalDays)
                put(DbContract.PEASEFACTOR,     next.easeFactor)
                put(DbContract.PREVIEWCOUNT,    next.reviewCount)
                put(DbContract.PLAPSECOUNT,     next.lapseCount)
                put(DbContract.PLASTRESULT,     next.lastResult)
            }
            db.update(
                DbContract.TCARDPROGRESS,
                cv,
                "${DbContract.PUSERID} = ? AND ${DbContract.PCARDID} = ?",
                arrayOf(studyingUserId.toString(), cardId.toString())
            )

            val sessionId = insertStudySessionRow(
                db        = db,
                userId    = studyingUserId,
                deckId    = deckId,
                result    = result,
                createdAt = reviewedAt
            )

            db.setTransactionSuccessful()
            sessionId
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Simple fallback logger — records a study result without touching SRS state.
     * Returns the new session row ID, or -1L on failure.
     */
    fun logStudyResult(userId: Long, deckId: Long, result: String): Long {
        val cv = ContentValues().apply {
            put(DbContract.SUSERID,    userId)
            put(DbContract.SDECKID,    deckId)
            put(DbContract.SRESULT,    result)
            put(DbContract.SCREATEDAT, System.currentTimeMillis())
        }
        return writable.insert(DbContract.TSTUDYSESSIONS, null, cv)
    }

    /**
     * Deletes a single study session row owned by userId.
     * Used for undo support.
     */
    fun deleteStudySession(userId: Long, sessionId: Long): Int {
        return writable.delete(
            DbContract.TSTUDYSESSIONS,
            "${DbContract.SID} = ? AND ${DbContract.SUSERID} = ?",
            arrayOf(sessionId.toString(), userId.toString())
        )
    }

    // ---------- INTERNAL HELPERS ----------

    private fun daysToMillis(days: Int): Long =
        days.toLong() * 24L * 60L * 60L * 1000L

    private fun ensureCardProgressRowsForUser(ownerUserId: Long) {
        writable.execSQL(
            """
        INSERT OR IGNORE INTO ${DbContract.TCARDPROGRESS} (
            ${DbContract.PUSERID},
            ${DbContract.PCARDID},
            ${DbContract.PDUEAT},
            ${DbContract.PLASTREVIEWEDAT},
            ${DbContract.PINTERVALDAYS},
            ${DbContract.PEASEFACTOR},
            ${DbContract.PREVIEWCOUNT},
            ${DbContract.PLAPSECOUNT},
            ${DbContract.PLASTRESULT}
        )
        SELECT
            ?,
            c.${DbContract.CID},
            0,
            NULL,
            0,
            2.5,
            0,
            0,
            NULL
        FROM ${DbContract.TCARDS} c
        INNER JOIN ${DbContract.TDECKS} d
            ON d.${DbContract.DID} = c.${DbContract.CDECKID}
        WHERE d.${DbContract.DOWNERUSERID} = ?
          AND d.${DbContract.DSTATUS} = ?
        """.trimIndent(),
            arrayOf(ownerUserId, ownerUserId, DbContract.DECKACTIVE)
        )
    }

    private fun ensureCardProgressRowsForDeck(ownerUserId: Long, deckId: Long) {
        writable.execSQL(
            """
            INSERT OR IGNORE INTO ${DbContract.TCARDPROGRESS} (
                ${DbContract.PUSERID},
                ${DbContract.PCARDID},
                ${DbContract.PDUEAT},
                ${DbContract.PLASTREVIEWEDAT},
                ${DbContract.PINTERVALDAYS},
                ${DbContract.PEASEFACTOR},
                ${DbContract.PREVIEWCOUNT},
                ${DbContract.PLAPSECOUNT},
                ${DbContract.PLASTRESULT}
            )
            SELECT
                ?,
                c.${DbContract.CID},
                0,
                NULL,
                0,
                2.5,
                0,
                0,
                NULL
            FROM ${DbContract.TCARDS} c
            INNER JOIN ${DbContract.TDECKS} d
                ON d.${DbContract.DID} = c.${DbContract.CDECKID}
            WHERE c.${DbContract.CDECKID} = ?
              AND d.${DbContract.DOWNERUSERID} = ?
              AND d.${DbContract.DSTATUS} = ?
            """.trimIndent(),
            arrayOf(ownerUserId, deckId, ownerUserId, DbContract.DECKACTIVE)
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
            INSERT OR IGNORE INTO ${DbContract.TCARDPROGRESS} (
                ${DbContract.PUSERID},
                ${DbContract.PCARDID},
                ${DbContract.PDUEAT},
                ${DbContract.PLASTREVIEWEDAT},
                ${DbContract.PINTERVALDAYS},
                ${DbContract.PEASEFACTOR},
                ${DbContract.PREVIEWCOUNT},
                ${DbContract.PLAPSECOUNT},
                ${DbContract.PLASTRESULT}
            )
            SELECT
                ?,
                c.${DbContract.CID},
                0,
                NULL,
                0,
                2.5,
                0,
                0,
                NULL
            FROM ${DbContract.TCARDS} c
            INNER JOIN ${DbContract.TDECKS} d
                ON d.${DbContract.DID} = c.${DbContract.CDECKID}
            WHERE c.${DbContract.CID} = ?
              AND c.${DbContract.CDECKID} = ?
              AND d.${DbContract.DOWNERUSERID} = ?
              AND d.${DbContract.DSTATUS} = ?
            """.trimIndent(),
            arrayOf(ownerUserId, cardId, deckId, ownerUserId, DbContract.DECKACTIVE)
        )
    }

    private fun ownsCardForStudy(ownerUserId: Long, deckId: Long, cardId: Long): Boolean {
        readable.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.TCARDS} c
            INNER JOIN ${DbContract.TDECKS} d
                ON d.${DbContract.DID} = c.${DbContract.CDECKID}
            WHERE c.${DbContract.CID} = ?
              AND c.${DbContract.CDECKID} = ?
              AND d.${DbContract.DOWNERUSERID} = ?
              AND d.${DbContract.DSTATUS} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                cardId.toString(),
                deckId.toString(),
                ownerUserId.toString(),
                DbContract.DECKACTIVE
            )
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
                ${DbContract.PDUEAT},
                ${DbContract.PLASTREVIEWEDAT},
                ${DbContract.PINTERVALDAYS},
                ${DbContract.PEASEFACTOR},
                ${DbContract.PREVIEWCOUNT},
                ${DbContract.PLAPSECOUNT},
                ${DbContract.PLASTRESULT}
            FROM ${DbContract.TCARDPROGRESS}
            WHERE ${DbContract.PUSERID} = ?
              AND ${DbContract.PCARDID} = ?
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
                val nextInterval = when (currentInterval) {
                    0 -> 1
                    1 -> 3
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
                // Hard = short delay review (10 hours), interval resets to 0
                NextSrsState(
                    dueAt = reviewedAt + 10L * 60L * 60L * 1000L,
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
            put(DbContract.SUSERID, userId)
            put(DbContract.SDECKID, deckId)
            put(DbContract.SRESULT, result)
            put(DbContract.SCREATEDAT, createdAt)
        }
        return db.insert(DbContract.TSTUDYSESSIONS, null, cv)
    }
}
