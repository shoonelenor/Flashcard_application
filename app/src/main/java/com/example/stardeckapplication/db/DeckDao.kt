package com.example.stardeckapplication.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

class DeckDao(private val dbHelper: StarDeckDbHelper) {

    private val readable: SQLiteDatabase get() = dbHelper.readableDatabase
    private val writable: SQLiteDatabase get() = dbHelper.writableDatabase

    // ---------- DATA CLASSES ----------

    /**
     * Used by MyDecksActivity and DeckCardsActivity for owner's own decks.
     */
    data class DeckRow(
        val id          : Long,
        val title       : String,
        val description : String?,
        val createdAt   : Long,
        val status      : String,
        val isPremium   : Boolean,
        val isPublic    : Boolean
    )

    data class AdminDeckContentRow(
        val id          : Long,
        val ownerUserId : Long,
        val ownerName   : String,
        val ownerEmail  : String,
        val title       : String,
        val description : String?,
        val createdAt   : Long,
        val status      : String,
        val isPremium   : Boolean,
        val isPublic    : Boolean,
        val cardCount   : Int
    )

    data class PublicDeckCatalogRow(
        val deckId      : Long,
        val title       : String,
        val description : String?,
        val ownerName   : String,
        val isPremium   : Boolean,
        val isLocked    : Boolean,
        val cardCount   : Int
    )

    data class CardRow(
        val id        : Long,
        val front     : String,
        val back      : String,
        val createdAt : Long
    )

    // ---------- HELPERS ----------

    private fun getDeckOwnerUserIdAny(deckId: Long): Long {
        readable.rawQuery(
            """
            SELECT ${DbContract.D_OWNER_USER_ID}
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_ID} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return -1L
    }

    private fun deckIsReportablePublic(deckId: Long): Boolean {
        readable.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_ID} = ?
              AND ${DbContract.D_STATUS} = ?
              AND COALESCE(${DbContract.D_IS_PUBLIC}, 0) = 1
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            return c.moveToFirst()
        }
    }

    private fun hasOpenReportByUserForDeck(reporterUserId: Long, deckId: Long): Boolean {
        readable.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_REPORTS}
            WHERE ${DbContract.R_REPORTER_USER_ID} = ?
              AND ${DbContract.R_DECK_ID} = ?
              AND ${DbContract.R_STATUS} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                reporterUserId.toString(),
                deckId.toString(),
                DbContract.REPORT_OPEN
            )
        ).use { c ->
            return c.moveToFirst()
        }
    }

    private fun deckExistsAny(deckId: Long): Boolean {
        readable.rawQuery(
            "SELECT 1 FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_ID} = ? LIMIT 1",
            arrayOf(deckId.toString())
        ).use { return it.moveToFirst() }
    }

    private fun activeAdminExists(userId: Long): Boolean {
        readable.rawQuery(
            """
            SELECT 1 FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ID} = ? AND ${DbContract.U_ROLE} = ? AND ${DbContract.U_STATUS} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), DbContract.ROLE_ADMIN, DbContract.STATUS_ACTIVE)
        ).use { return it.moveToFirst() }
    }

    private fun activeUserExists(userId: Long): Boolean {
        readable.rawQuery(
            """
            SELECT 1 FROM ${DbContract.T_USERS}
            WHERE ${DbContract.U_ID} = ? AND ${DbContract.U_ROLE} = ? AND ${DbContract.U_STATUS} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), DbContract.ROLE_USER, DbContract.STATUS_ACTIVE)
        ).use { return it.moveToFirst() }
    }

    private fun adminOwnsDeck(adminUserId: Long, deckId: Long): Boolean {
        readable.rawQuery(
            """
            SELECT 1 FROM ${DbContract.T_DECKS} d
            INNER JOIN ${DbContract.T_USERS} u ON u.${DbContract.U_ID} = d.${DbContract.D_OWNER_USER_ID}
            WHERE d.${DbContract.D_ID} = ?
              AND d.${DbContract.D_OWNER_USER_ID} = ?
              AND u.${DbContract.U_ROLE} = ?
              AND u.${DbContract.U_STATUS} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), adminUserId.toString(), DbContract.ROLE_ADMIN, DbContract.STATUS_ACTIVE)
        ).use { return it.moveToFirst() }
    }

    private fun ownerHasDeckTitle(ownerUserId: Long, title: String): Boolean {
        readable.rawQuery(
            """
            SELECT 1 FROM ${DbContract.T_DECKS}
            WHERE ${DbContract.D_OWNER_USER_ID} = ? AND ${DbContract.D_TITLE} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(ownerUserId.toString(), title)
        ).use { return it.moveToFirst() }
    }

    // ---------- USER DECK LIST ----------

    fun getDecksForOwner(ownerUserId: Long): List<DeckRow> {
        val out = mutableListOf<DeckRow>()
        readable.rawQuery(
            """
            SELECT ${DbContract.D_ID}, ${DbContract.D_TITLE}, ${DbContract.D_DESCRIPTION},
                   ${DbContract.D_CREATED_AT}, ${DbContract.D_STATUS},
                   COALESCE(${DbContract.D_IS_PREMIUM}, 0),
                   COALESCE(${DbContract.D_IS_PUBLIC},  0)
            FROM   ${DbContract.T_DECKS}
            WHERE  ${DbContract.D_OWNER_USER_ID} = ?
              AND  ${DbContract.D_STATUS} = ?
            ORDER  BY ${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(ownerUserId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            while (c.moveToNext()) {
                out += DeckRow(
                    id          = c.getLong(0),
                    title       = c.getString(1),
                    description = c.getString(2),
                    createdAt   = c.getLong(3),
                    status      = c.getString(4),
                    isPremium   = c.getInt(5) == 1,
                    isPublic    = c.getInt(6) == 1
                )
            }
        }
        return out
    }

    fun getDeckTitleForOwner(ownerUserId: Long, deckId: Long): String? {
        readable.rawQuery(
            """
            SELECT ${DbContract.D_TITLE} FROM ${DbContract.T_DECKS}
            WHERE  ${DbContract.D_ID} = ?
              AND  ${DbContract.D_OWNER_USER_ID} = ?
              AND  ${DbContract.D_STATUS} = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), ownerUserId.toString(), DbContract.DECK_ACTIVE)
        ).use { return if (it.moveToFirst()) it.getString(0) else null }
    }

    fun getCardsForDeck(ownerUserId: Long, deckId: Long): List<CardRow> {
        val out = mutableListOf<CardRow>()
        readable.rawQuery(
            """
        SELECT c.${DbContract.C_ID}, c.${DbContract.C_FRONT},
               c.${DbContract.C_BACK}, c.${DbContract.C_CREATED_AT}
        FROM   ${DbContract.T_CARDS} c
        INNER JOIN ${DbContract.T_DECKS} d ON d.${DbContract.D_ID} = c.${DbContract.C_DECK_ID}
        WHERE  c.${DbContract.C_DECK_ID}       = ?
          AND  d.${DbContract.D_OWNER_USER_ID}  = ?
          AND  d.${DbContract.D_STATUS}         = ?
        ORDER  BY c.${DbContract.C_CREATED_AT} DESC
        """.trimIndent(),
            arrayOf(deckId.toString(), ownerUserId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            while (c.moveToNext())
                out += CardRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
        }
        return out
    }

    // ---------- USER DECK CRUD ----------

    fun createDeck(ownerUserId: Long, title: String, description: String?): Long {
        val cv = ContentValues().apply {
            put(DbContract.D_OWNER_USER_ID, ownerUserId)
            put(DbContract.D_TITLE,         title.trim())
            put(DbContract.D_DESCRIPTION,   description?.trim()?.ifBlank { null })
            put(DbContract.D_CREATED_AT,    System.currentTimeMillis())
            put(DbContract.D_STATUS,        DbContract.DECK_ACTIVE)
            put(DbContract.D_IS_PREMIUM,    0)
            put(DbContract.D_IS_PUBLIC,     0)
        }
        return writable.insertOrThrow(DbContract.T_DECKS, null, cv)
    }

    fun updateDeck(ownerUserId: Long, deckId: Long, title: String, description: String?): Int {
        val cv = ContentValues().apply {
            put(DbContract.D_TITLE,       title.trim())
            put(DbContract.D_DESCRIPTION, description?.trim()?.ifBlank { null })
        }
        return writable.update(
            DbContract.T_DECKS, cv,
            "${DbContract.D_ID} = ? AND ${DbContract.D_OWNER_USER_ID} = ?",
            arrayOf(deckId.toString(), ownerUserId.toString())
        )
    }

    fun deleteDeck(ownerUserId: Long, deckId: Long): Int {
        return writable.delete(
            DbContract.T_DECKS,
            "${DbContract.D_ID} = ? AND ${DbContract.D_OWNER_USER_ID} = ?",
            arrayOf(deckId.toString(), ownerUserId.toString())
        )
    }

    // ---------- REPORTS ----------

    fun createDeckReport(
        reporterUserId: Long,
        deckId: Long,
        reason: String,
        details: String?
    ): Long {
        return createDeckReport(
            reporterUserId = reporterUserId,
            deckId = deckId,
            reasonId = null,
            reason = reason,
            details = details
        )
    }

    fun createDeckReport(
        reporterUserId: Long,
        deckId: Long,
        reasonId: Long?,
        reason: String,
        details: String?
    ): Long {
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) return -1L
        if (!deckIsReportablePublic(deckId)) return -5L

        val ownerUserId = getDeckOwnerUserIdAny(deckId)
        if (ownerUserId <= 0L) return -5L
        if (ownerUserId == reporterUserId) return -2L
        if (hasOpenReportByUserForDeck(reporterUserId, deckId)) return -3L

        val cv = ContentValues().apply {
            put(DbContract.R_REPORTER_USER_ID, reporterUserId)
            put(DbContract.R_DECK_ID, deckId)

            if (reasonId != null && reasonId > 0L) {
                put(DbContract.R_REASON_ID, reasonId)
            } else {
                putNull(DbContract.R_REASON_ID)
            }

            put(DbContract.R_REASON, cleanReason)
            put(DbContract.R_DETAILS, details?.trim()?.ifBlank { null })
            put(DbContract.R_STATUS, DbContract.REPORT_OPEN)
            put(DbContract.R_CREATED_AT, System.currentTimeMillis())
        }

        return try {
            writable.insertOrThrow(DbContract.T_REPORTS, null, cv)
        } catch (e: Exception) {
            -1L
        }
    }

    // ---------- PUBLIC LIBRARY ----------

    fun getPublicDeckCatalogForUser(userId: Long): List<PublicDeckCatalogRow> {
        val out = mutableListOf<PublicDeckCatalogRow>()
        readable.rawQuery(
            """
            SELECT
                d.${DbContract.D_ID},
                d.${DbContract.D_TITLE},
                d.${DbContract.D_DESCRIPTION},
                owner.${DbContract.U_NAME},
                COALESCE(d.${DbContract.D_IS_PREMIUM}, 0),
                CASE WHEN COALESCE(d.${DbContract.D_IS_PREMIUM}, 0) = 1
                          AND COALESCE(viewer.${DbContract.U_IS_PREMIUM_USER}, 0) = 0
                     THEN 1 ELSE 0 END AS is_locked,
                COUNT(c.${DbContract.C_ID}) AS card_count
            FROM   ${DbContract.T_DECKS} d
            INNER  JOIN ${DbContract.T_USERS} owner  ON owner.${DbContract.U_ID}  = d.${DbContract.D_OWNER_USER_ID}
            LEFT   JOIN ${DbContract.T_USERS} viewer ON viewer.${DbContract.U_ID} = ?
            LEFT   JOIN ${DbContract.T_CARDS} c      ON c.${DbContract.C_DECK_ID} = d.${DbContract.D_ID}
            WHERE  d.${DbContract.D_STATUS} = ?
              AND  COALESCE(d.${DbContract.D_IS_PUBLIC}, 0) = 1
            GROUP  BY d.${DbContract.D_ID}, d.${DbContract.D_TITLE}, d.${DbContract.D_DESCRIPTION},
                      owner.${DbContract.U_NAME}, d.${DbContract.D_IS_PREMIUM},
                      viewer.${DbContract.U_IS_PREMIUM_USER}
            ORDER  BY d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(userId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            while (c.moveToNext()) {
                out += PublicDeckCatalogRow(
                    deckId      = c.getLong(0),
                    title       = c.getString(1),
                    description = c.getString(2),
                    ownerName   = c.getString(3),
                    isPremium   = c.getInt(4) == 1,
                    isLocked    = c.getInt(5) == 1,
                    cardCount   = c.getInt(6)
                )
            }
        }
        return out
    }

    fun getPublicDeckTitleForUser(userId: Long, deckId: Long): String? {
        readable.rawQuery(
            """
            SELECT ${DbContract.D_TITLE} FROM ${DbContract.T_DECKS}
            WHERE  ${DbContract.D_ID} = ?
              AND  ${DbContract.D_STATUS} = ?
              AND  COALESCE(${DbContract.D_IS_PUBLIC}, 0) = 1
            LIMIT 1
            """.trimIndent(),
            arrayOf(deckId.toString(), DbContract.DECK_ACTIVE)
        ).use { return if (it.moveToFirst()) it.getString(0) else null }
    }

    fun isDeckLockedForUser(userId: Long, deckId: Long): Boolean {
        readable.rawQuery(
            """
            SELECT CASE WHEN COALESCE(d.${DbContract.D_IS_PREMIUM}, 0) = 1
                             AND COALESCE(u.${DbContract.U_IS_PREMIUM_USER}, 0) = 0
                        THEN 1 ELSE 0 END
            FROM   ${DbContract.T_DECKS} d
            LEFT   JOIN ${DbContract.T_USERS} u ON u.${DbContract.U_ID} = ?
            WHERE  d.${DbContract.D_ID} = ?
              AND  d.${DbContract.D_STATUS} = ?
              AND  COALESCE(d.${DbContract.D_IS_PUBLIC}, 0) = 1
            LIMIT 1
            """.trimIndent(),
            arrayOf(userId.toString(), deckId.toString(), DbContract.DECK_ACTIVE)
        ).use { c -> return c.moveToFirst() && c.getInt(0) == 1 }
    }

    fun getPublicDeckCardsForUser(userId: Long, deckId: Long): List<CardRow> {
        if (isDeckLockedForUser(userId, deckId)) return emptyList()
        val out = mutableListOf<CardRow>()
        readable.rawQuery(
            """
            SELECT c.${DbContract.C_ID}, c.${DbContract.C_FRONT},
                   c.${DbContract.C_BACK}, c.${DbContract.C_CREATED_AT}
            FROM   ${DbContract.T_CARDS} c
            INNER  JOIN ${DbContract.T_DECKS} d ON d.${DbContract.D_ID} = c.${DbContract.C_DECK_ID}
            WHERE  d.${DbContract.D_ID} = ?
              AND  d.${DbContract.D_STATUS} = ?
              AND  COALESCE(d.${DbContract.D_IS_PUBLIC}, 0) = 1
            ORDER  BY c.${DbContract.C_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(deckId.toString(), DbContract.DECK_ACTIVE)
        ).use { c ->
            while (c.moveToNext()) {
                out += CardRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            }
        }
        return out
    }

    // ---------- COPY PUBLIC DECK ----------

    fun copyPublicDeckToUser(viewerUserId: Long, sourceDeckId: Long): Long {
        val db = writable
        val isSelf = db.rawQuery(
            "SELECT 1 FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_ID} = ? AND ${DbContract.D_OWNER_USER_ID} = ? LIMIT 1",
            arrayOf(sourceDeckId.toString(), viewerUserId.toString())
        ).use { it.moveToFirst() }
        if (isSelf) return -2L
        if (isDeckLockedForUser(viewerUserId, sourceDeckId)) return -1L

        val cards = getPublicDeckCardsForUser(viewerUserId, sourceDeckId)
        if (cards.isEmpty()) return -1L
        val sourceTitle = getPublicDeckTitleForUser(viewerUserId, sourceDeckId) ?: return -1L

        db.beginTransaction()
        return try {
            var finalTitle = sourceTitle
            var suffix = 1
            while (ownerHasDeckTitle(viewerUserId, finalTitle)) {
                finalTitle = "$sourceTitle ($suffix)"; suffix++
            }
            val cvDeck = ContentValues().apply {
                put(DbContract.D_OWNER_USER_ID, viewerUserId)
                put(DbContract.D_TITLE,         finalTitle)
                put(DbContract.D_DESCRIPTION,   "Copied from public library.")
                put(DbContract.D_CREATED_AT,    System.currentTimeMillis())
                put(DbContract.D_STATUS,        DbContract.DECK_ACTIVE)
                put(DbContract.D_IS_PREMIUM,    0)
                put(DbContract.D_IS_PUBLIC,     0)
            }
            val newDeckId = db.insertOrThrow(DbContract.T_DECKS, null, cvDeck)
            for (card in cards) {
                val cvCard = ContentValues().apply {
                    put(DbContract.C_DECK_ID,    newDeckId)
                    put(DbContract.C_FRONT,      card.front)
                    put(DbContract.C_BACK,       card.back)
                    put(DbContract.C_CREATED_AT, System.currentTimeMillis())
                }
                db.insertOrThrow(DbContract.T_CARDS, null, cvCard)
            }
            db.setTransactionSuccessful()
            newDeckId
        } finally {
            db.endTransaction()
        }
    }

    // ---------- ADMIN DECK CONTENT ----------

    fun adminGetOwnDeckContent(adminUserId: Long): List<AdminDeckContentRow> {
        if (!activeAdminExists(adminUserId)) return emptyList()
        val out = mutableListOf<AdminDeckContentRow>()
        readable.rawQuery(
            """
            SELECT d.${DbContract.D_ID}, d.${DbContract.D_OWNER_USER_ID},
                   u.${DbContract.U_NAME}, u.${DbContract.U_EMAIL},
                   d.${DbContract.D_TITLE}, d.${DbContract.D_DESCRIPTION},
                   d.${DbContract.D_CREATED_AT}, d.${DbContract.D_STATUS},
                   COALESCE(d.${DbContract.D_IS_PREMIUM}, 0),
                   COALESCE(d.${DbContract.D_IS_PUBLIC},  0),
                   COUNT(c.${DbContract.C_ID}) AS card_count
            FROM   ${DbContract.T_DECKS} d
            INNER  JOIN ${DbContract.T_USERS} u ON u.${DbContract.U_ID}  = d.${DbContract.D_OWNER_USER_ID}
            LEFT   JOIN ${DbContract.T_CARDS} c ON c.${DbContract.C_DECK_ID} = d.${DbContract.D_ID}
            WHERE  d.${DbContract.D_OWNER_USER_ID} = ?
            GROUP  BY d.${DbContract.D_ID}, d.${DbContract.D_OWNER_USER_ID},
                      u.${DbContract.U_NAME}, u.${DbContract.U_EMAIL},
                      d.${DbContract.D_TITLE}, d.${DbContract.D_DESCRIPTION},
                      d.${DbContract.D_CREATED_AT}, d.${DbContract.D_STATUS},
                      d.${DbContract.D_IS_PREMIUM}, d.${DbContract.D_IS_PUBLIC}
            ORDER  BY d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(adminUserId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += AdminDeckContentRow(c.getLong(0), c.getLong(1), c.getString(2),
                    c.getString(3), c.getString(4), c.getString(5), c.getLong(6),
                    c.getString(7), c.getInt(8) == 1, c.getInt(9) == 1, c.getInt(10))
            }
        }
        return out
    }

    fun adminGetAllDeckContent(): List<AdminDeckContentRow> {
        val out = mutableListOf<AdminDeckContentRow>()
        readable.rawQuery(
            """
            SELECT d.${DbContract.D_ID}, d.${DbContract.D_OWNER_USER_ID},
                   u.${DbContract.U_NAME}, u.${DbContract.U_EMAIL},
                   d.${DbContract.D_TITLE}, d.${DbContract.D_DESCRIPTION},
                   d.${DbContract.D_CREATED_AT}, d.${DbContract.D_STATUS},
                   COALESCE(d.${DbContract.D_IS_PREMIUM}, 0),
                   COALESCE(d.${DbContract.D_IS_PUBLIC},  0),
                   COUNT(c.${DbContract.C_ID}) AS card_count
            FROM   ${DbContract.T_DECKS} d
            INNER  JOIN ${DbContract.T_USERS} u ON u.${DbContract.U_ID}  = d.${DbContract.D_OWNER_USER_ID}
            LEFT   JOIN ${DbContract.T_CARDS} c ON c.${DbContract.C_DECK_ID} = d.${DbContract.D_ID}
            GROUP  BY d.${DbContract.D_ID}, d.${DbContract.D_OWNER_USER_ID},
                      u.${DbContract.U_NAME}, u.${DbContract.U_EMAIL},
                      d.${DbContract.D_TITLE}, d.${DbContract.D_DESCRIPTION},
                      d.${DbContract.D_CREATED_AT}, d.${DbContract.D_STATUS},
                      d.${DbContract.D_IS_PREMIUM}, d.${DbContract.D_IS_PUBLIC}
            ORDER  BY d.${DbContract.D_IS_PREMIUM} DESC, d.${DbContract.D_CREATED_AT} DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += AdminDeckContentRow(c.getLong(0), c.getLong(1), c.getString(2),
                    c.getString(3), c.getString(4), c.getString(5), c.getLong(6),
                    c.getString(7), c.getInt(8) == 1, c.getInt(9) == 1, c.getInt(10))
            }
        }
        return out
    }

    fun adminCreateDeckContentForAdmin(
        adminUserId: Long, title: String, description: String?,
        isPremium: Boolean, isPublic: Boolean, isHidden: Boolean
    ): Long {
        if (!activeAdminExists(adminUserId)) return -1L
        return adminCreateDeckContent(adminUserId, title, description, isPremium, isHidden, isPublic)
    }

    fun adminCreateDeckContent(
        ownerUserId: Long, title: String, description: String?,
        isPremium: Boolean, isHidden: Boolean, isPublic: Boolean
    ): Long {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return -1L
        if (!activeAdminExists(ownerUserId) && !activeUserExists(ownerUserId)) return -1L

        val cv = ContentValues().apply {
            put(DbContract.D_OWNER_USER_ID, ownerUserId)
            put(DbContract.D_TITLE,         cleanTitle)
            put(DbContract.D_DESCRIPTION,   description?.trim()?.ifBlank { null })
            put(DbContract.D_CREATED_AT,    System.currentTimeMillis())
            put(DbContract.D_STATUS,        if (isHidden) DbContract.DECK_HIDDEN else DbContract.DECK_ACTIVE)
            put(DbContract.D_IS_PREMIUM,    if (isPremium) 1 else 0)
            put(DbContract.D_IS_PUBLIC,     if (isPublic)  1 else 0)
        }
        return writable.insertOrThrow(DbContract.T_DECKS, null, cv)
    }

    fun adminUpdateDeckContentForAdmin(
        adminUserId: Long, deckId: Long, title: String, description: String?,
        isPremium: Boolean, isPublic: Boolean, isHidden: Boolean
    ): Int {
        if (!adminOwnsDeck(adminUserId, deckId)) return 0
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return 0
        val cv = ContentValues().apply {
            put(DbContract.D_TITLE,       cleanTitle)
            put(DbContract.D_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.D_IS_PREMIUM,  if (isPremium) 1 else 0)
            put(DbContract.D_IS_PUBLIC,   if (isPublic)  1 else 0)
            put(DbContract.D_STATUS,      if (isHidden) DbContract.DECK_HIDDEN else DbContract.DECK_ACTIVE)
        }
        return writable.update(
            DbContract.T_DECKS, cv,
            "${DbContract.D_ID} = ? AND ${DbContract.D_OWNER_USER_ID} = ?",
            arrayOf(deckId.toString(), adminUserId.toString())
        )
    }

    fun adminUpdateDeckContent(
        deckId: Long, title: String, description: String?,
        isPremium: Boolean, isHidden: Boolean, isPublic: Boolean
    ): Int {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return 0
        val cv = ContentValues().apply {
            put(DbContract.D_TITLE,       cleanTitle)
            put(DbContract.D_DESCRIPTION, description?.trim()?.ifBlank { null })
            put(DbContract.D_IS_PREMIUM,  if (isPremium) 1 else 0)
            put(DbContract.D_STATUS,      if (isHidden) DbContract.DECK_HIDDEN else DbContract.DECK_ACTIVE)
            put(DbContract.D_IS_PUBLIC,   if (isPublic)  1 else 0)
        }
        return writable.update(DbContract.T_DECKS, cv, "${DbContract.D_ID} = ?", arrayOf(deckId.toString()))
    }

    fun adminDeleteDeckContentForAdmin(adminUserId: Long, deckId: Long): Int {
        if (!adminOwnsDeck(adminUserId, deckId)) return 0
        val db = writable
        db.beginTransaction()
        return try {
            db.delete(DbContract.T_CARDS, "${DbContract.C_DECK_ID} = ?", arrayOf(deckId.toString()))
            val rows = db.delete(
                DbContract.T_DECKS,
                "${DbContract.D_ID} = ? AND ${DbContract.D_OWNER_USER_ID} = ?",
                arrayOf(deckId.toString(), adminUserId.toString())
            )
            if (rows >= 1) db.setTransactionSuccessful()
            rows
        } finally {
            db.endTransaction()
        }
    }

    fun adminDeleteDeckContent(deckId: Long): Int =
        writable.delete(DbContract.T_DECKS, "${DbContract.D_ID} = ?", arrayOf(deckId.toString()))

    fun getDeckTitleAny(deckId: Long): String? {
        readable.rawQuery(
            "SELECT ${DbContract.D_TITLE} FROM ${DbContract.T_DECKS} WHERE ${DbContract.D_ID} = ? LIMIT 1",
            arrayOf(deckId.toString())
        ).use { return if (it.moveToFirst()) it.getString(0) else null }
    }

    fun adminGetCardsForDeck(adminUserId: Long, deckId: Long): List<CardRow> {
        if (!adminOwnsDeck(adminUserId, deckId)) return emptyList()
        val out = mutableListOf<CardRow>()
        readable.rawQuery(
            """
            SELECT ${DbContract.C_ID}, ${DbContract.C_FRONT},
                   ${DbContract.C_BACK}, ${DbContract.C_CREATED_AT}
            FROM   ${DbContract.T_CARDS}
            WHERE  ${DbContract.C_DECK_ID} = ?
            ORDER  BY ${DbContract.C_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(deckId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += CardRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            }
        }
        return out
    }

    fun adminCreateCard(adminUserId: Long, deckId: Long, front: String, back: String): Long {
        if (!adminOwnsDeck(adminUserId, deckId)) return -1L
        val f = front.trim(); val b = back.trim()
        if (f.isBlank() && b.isBlank()) return -1L
        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID, deckId); put(DbContract.C_FRONT, f)
            put(DbContract.C_BACK, b);         put(DbContract.C_CREATED_AT, System.currentTimeMillis())
        }
        return writable.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    fun adminUpdateCard(adminUserId: Long, deckId: Long, cardId: Long, front: String, back: String): Int {
        if (!adminOwnsDeck(adminUserId, deckId)) return 0
        val f = front.trim(); val b = back.trim()
        if (f.isBlank() && b.isBlank()) return 0
        val cv = ContentValues().apply { put(DbContract.C_FRONT, f); put(DbContract.C_BACK, b) }
        return writable.update(DbContract.T_CARDS, cv,
            "${DbContract.C_ID} = ? AND ${DbContract.C_DECK_ID} = ?",
            arrayOf(cardId.toString(), deckId.toString()))
    }

    fun adminDeleteCard(adminUserId: Long, deckId: Long, cardId: Long): Int {
        if (!adminOwnsDeck(adminUserId, deckId)) return 0
        return writable.delete(DbContract.T_CARDS,
            "${DbContract.C_ID} = ? AND ${DbContract.C_DECK_ID} = ?",
            arrayOf(cardId.toString(), deckId.toString()))
    }

    fun createCardAny(deckId: Long, front: String, back: String): Long {
        if (!deckExistsAny(deckId)) return -1L
        val cv = ContentValues().apply {
            put(DbContract.C_DECK_ID, deckId); put(DbContract.C_FRONT, front.trim())
            put(DbContract.C_BACK, back.trim()); put(DbContract.C_CREATED_AT, System.currentTimeMillis())
        }
        return writable.insertOrThrow(DbContract.T_CARDS, null, cv)
    }

    fun updateCardAny(deckId: Long, cardId: Long, front: String, back: String): Int {
        val cv = ContentValues().apply { put(DbContract.C_FRONT, front.trim()); put(DbContract.C_BACK, back.trim()) }
        return writable.update(DbContract.T_CARDS, cv,
            "${DbContract.C_ID} = ? AND ${DbContract.C_DECK_ID} = ?",
            arrayOf(cardId.toString(), deckId.toString()))
    }

    fun deleteCardAny(deckId: Long, cardId: Long): Int =
        writable.delete(DbContract.T_CARDS,
            "${DbContract.C_ID} = ? AND ${DbContract.C_DECK_ID} = ?",
            arrayOf(cardId.toString(), deckId.toString()))

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
}