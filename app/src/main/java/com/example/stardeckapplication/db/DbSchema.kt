package com.example.stardeckapplication.db

import android.database.sqlite.SQLiteDatabase

/**
 * SchemaDao: all CREATE TABLE + CREATE INDEX statements for StarDeck.
 *
 * Call from StarDeckDbHelper:
 *
 * override fun onCreate(db: SQLiteDatabase) {
 *     SchemaDao.createAllTables(db)
 *     // then seeding, etc.
 * }
 */
object DbSchema {

    fun createAllTables(db: SQLiteDatabase) {
        createUsersTable(db)
        createDecksTable(db)
        createCardsTable(db)
        createCardProgressTable(db)
        createStudySessionsTable(db)
        createReportsTable(db)
    }

    // ---------- USERS ----------

    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TUSERS} (
                ${DbContract.UID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.UNAME} TEXT NOT NULL,
                ${DbContract.UEMAIL} TEXT NOT NULL UNIQUE,
                ${DbContract.UPASSWORDHASH} TEXT NOT NULL,
                ${DbContract.UROLE} TEXT NOT NULL,
                ${DbContract.USTATUS} TEXT NOT NULL,
                ${DbContract.UACCEPTEDTERMS} INTEGER NOT NULL,
                ${DbContract.UFORCEPWCHANGE} INTEGER NOT NULL,
                ${DbContract.UCREATEDAT} INTEGER NOT NULL,
                ${DbContract.UISPREMIUMUSER} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.ULASTLOGINAT} INTEGER
            )
            """.trimIndent()
        )
    }

    // ---------- DECKS ----------

    private fun createDecksTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TDECKS} (
                ${DbContract.DID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.DOWNERUSERID} INTEGER NOT NULL,
                ${DbContract.DTITLE} TEXT NOT NULL,
                ${DbContract.DDESCRIPTION} TEXT,
                ${DbContract.DCREATEDAT} INTEGER NOT NULL,
                ${DbContract.DSTATUS} TEXT NOT NULL DEFAULT '${DbContract.DECKACTIVE}',
                ${DbContract.DISPREMIUM} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.DISPUBLIC} INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(${DbContract.DOWNERUSERID})
                    REFERENCES ${DbContract.TUSERS}(${DbContract.UID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_decks_owner " +
                    "ON ${DbContract.TDECKS}(${DbContract.DOWNERUSERID})"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_decks_status " +
                    "ON ${DbContract.TDECKS}(${DbContract.DSTATUS})"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_decks_public " +
                    "ON ${DbContract.TDECKS}(${DbContract.DISPUBLIC})"
        )
    }

    // ---------- CARDS ----------

    private fun createCardsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TCARDS} (
                ${DbContract.CID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.CDECKID} INTEGER NOT NULL,
                ${DbContract.CFRONT} TEXT NOT NULL,
                ${DbContract.CBACK} TEXT NOT NULL,
                ${DbContract.CCREATEDAT} INTEGER NOT NULL,
                FOREIGN KEY(${DbContract.CDECKID})
                    REFERENCES ${DbContract.TDECKS}(${DbContract.DID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_cards_deck " +
                    "ON ${DbContract.TCARDS}(${DbContract.CDECKID})"
        )
    }

    // ---------- CARD PROGRESS (SRS) ----------

    private fun createCardProgressTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TCARDPROGRESS} (
                ${DbContract.PUSERID} INTEGER NOT NULL,
                ${DbContract.PCARDID} INTEGER NOT NULL,
                ${DbContract.PDUEAT} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.PLASTREVIEWEDAT} INTEGER,
                ${DbContract.PINTERVALDAYS} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.PEASEFACTOR} REAL NOT NULL DEFAULT 2.5,
                ${DbContract.PREVIEWCOUNT} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.PLAPSECOUNT} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.PLASTRESULT} TEXT,
                PRIMARY KEY(${DbContract.PUSERID}, ${DbContract.PCARDID}),
                FOREIGN KEY(${DbContract.PUSERID})
                    REFERENCES ${DbContract.TUSERS}(${DbContract.UID})
                    ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.PCARDID})
                    REFERENCES ${DbContract.TCARDS}(${DbContract.CID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_cardprogress_due " +
                    "ON ${DbContract.TCARDPROGRESS}(${DbContract.PUSERID}, ${DbContract.PDUEAT})"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_cardprogress_card " +
                    "ON ${DbContract.TCARDPROGRESS}(${DbContract.PCARDID})"
        )
    }

    // ---------- STUDY SESSIONS ----------

    private fun createStudySessionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TSTUDYSESSIONS} (
                ${DbContract.SID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.SUSERID} INTEGER NOT NULL,
                ${DbContract.SDECKID} INTEGER NOT NULL,
                ${DbContract.SRESULT} TEXT NOT NULL,
                ${DbContract.SCREATEDAT} INTEGER NOT NULL,
                FOREIGN KEY(${DbContract.SUSERID})
                    REFERENCES ${DbContract.TUSERS}(${DbContract.UID})
                    ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.SDECKID})
                    REFERENCES ${DbContract.TDECKS}(${DbContract.DID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_sessions_user " +
                    "ON ${DbContract.TSTUDYSESSIONS}(${DbContract.SUSERID})"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_sessions_deck " +
                    "ON ${DbContract.TSTUDYSESSIONS}(${DbContract.SDECKID})"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_sessions_created " +
                    "ON ${DbContract.TSTUDYSESSIONS}(${DbContract.SCREATEDAT})"
        )
    }

    // ---------- REPORTS ----------

    private fun createReportsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TREPORTS} (
                ${DbContract.RID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.RREPORTERUSERID} INTEGER NOT NULL,
                ${DbContract.RDECKID} INTEGER NOT NULL,
                ${DbContract.RREASON} TEXT NOT NULL,
                ${DbContract.RDETAILS} TEXT,
                ${DbContract.RSTATUS} TEXT NOT NULL DEFAULT '${DbContract.REPORTOPEN}',
                ${DbContract.RCREATEDAT} INTEGER NOT NULL,
                FOREIGN KEY(${DbContract.RREPORTERUSERID})
                    REFERENCES ${DbContract.TUSERS}(${DbContract.UID})
                    ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.RDECKID})
                    REFERENCES ${DbContract.TDECKS}(${DbContract.DID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_reports_status " +
                    "ON ${DbContract.TREPORTS}(${DbContract.RSTATUS})"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_reports_deck " +
                    "ON ${DbContract.TREPORTS}(${DbContract.RDECKID})"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_reports_created " +
                    "ON ${DbContract.TREPORTS}(${DbContract.RCREATEDAT})"
        )
    }
}