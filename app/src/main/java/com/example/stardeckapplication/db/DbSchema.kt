package com.example.stardeckapplication.db

import android.database.sqlite.SQLiteDatabase

object DbSchema {

    fun createAllTables(db: SQLiteDatabase) {
        createUsersTable(db)
        createCategoriesTable(db)
        createSubjectsTable(db)
        createLanguagesTable(db)
        createSubscriptionPlansTable(db)
        createAchievementsTable(db)
        createDecksTable(db)
        createCardsTable(db)
        createCardProgressTable(db)
        createStudySessionsTable(db)
        createReportReasonsTable(db)
        createReportsTable(db)
        createUserAchievementsTable(db)
        createUserSubscriptionsTable(db)
        createFriendshipsTable(db)          // ← NEW
    }

    fun recreateReportReasonsTable(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS ${DbContract.TREPORTREASONS}")
        createReportReasonsTable(db)
    }

    fun recreateReportsTable(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS ${DbContract.TREPORTS}")
        createReportsTable(db)
    }

    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TUSERS} (
                ${DbContract.UID}             INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.UNAME}           TEXT NOT NULL,
                ${DbContract.UEMAIL}          TEXT NOT NULL UNIQUE,
                ${DbContract.UPASSWORDHASH}   TEXT NOT NULL,
                ${DbContract.UROLE}           TEXT NOT NULL,
                ${DbContract.USTATUS}         TEXT NOT NULL,
                ${DbContract.UACCEPTEDTERMS}  INTEGER NOT NULL,
                ${DbContract.UFORCEPWCHANGE}  INTEGER NOT NULL,
                ${DbContract.UCREATEDAT}      INTEGER NOT NULL,
                ${DbContract.UISPREMIUMUSER}  INTEGER NOT NULL DEFAULT 0,
                ${DbContract.ULASTLOGINAT}    INTEGER
            )
            """.trimIndent()
        )
    }

    private fun createCategoriesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TCATEGORIES} (
                ${DbContract.CATID}          INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.CATNAME}        TEXT NOT NULL COLLATE NOCASE UNIQUE,
                ${DbContract.CATDESCRIPTION} TEXT,
                ${DbContract.CATISACTIVE}    INTEGER NOT NULL DEFAULT 1,
                ${DbContract.CATSORTORDER}   INTEGER NOT NULL DEFAULT 0,
                ${DbContract.CATCREATEDAT}   INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_categories_active_sort ON ${DbContract.TCATEGORIES}(${DbContract.CATISACTIVE}, ${DbContract.CATSORTORDER})"
        )
    }

    private fun createSubjectsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TSUBJECTS} (
                ${DbContract.SUBJID}          INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.SUBJCATEGORYID}  INTEGER NOT NULL,
                ${DbContract.SUBJNAME}        TEXT NOT NULL COLLATE NOCASE,
                ${DbContract.SUBJDESCRIPTION} TEXT,
                ${DbContract.SUBJISACTIVE}    INTEGER NOT NULL DEFAULT 1,
                ${DbContract.SUBJSORTORDER}   INTEGER NOT NULL DEFAULT 0,
                ${DbContract.SUBJCREATEDAT}   INTEGER NOT NULL,
                UNIQUE(${DbContract.SUBJCATEGORYID}, ${DbContract.SUBJNAME}),
                FOREIGN KEY(${DbContract.SUBJCATEGORYID})
                    REFERENCES ${DbContract.TCATEGORIES}(${DbContract.CATID}) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_subjects_category_active_sort ON ${DbContract.TSUBJECTS}(${DbContract.SUBJCATEGORYID}, ${DbContract.SUBJISACTIVE}, ${DbContract.SUBJSORTORDER})"
        )
    }

    private fun createLanguagesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TLANGUAGES} (
                ${DbContract.LANGID}          INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.LANGNAME}        TEXT NOT NULL COLLATE NOCASE UNIQUE,
                ${DbContract.LANGDESCRIPTION} TEXT,
                ${DbContract.LANGISACTIVE}    INTEGER NOT NULL DEFAULT 1,
                ${DbContract.LANGSORTORDER}   INTEGER NOT NULL DEFAULT 0,
                ${DbContract.LANGCREATEDAT}   INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_languages_active_sort ON ${DbContract.TLANGUAGES}(${DbContract.LANGISACTIVE}, ${DbContract.LANGSORTORDER})"
        )
    }

    private fun createSubscriptionPlansTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TSUBSCRIPTIONPLANS} (
                ${DbContract.SPID}           INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.SPNAME}         TEXT NOT NULL COLLATE NOCASE UNIQUE,
                ${DbContract.SPBILLINGCYCLE} TEXT NOT NULL,
                ${DbContract.SPPRICETEXT}    TEXT NOT NULL,
                ${DbContract.SPDURATIONDAYS} INTEGER NOT NULL,
                ${DbContract.SPDESCRIPTION}  TEXT,
                ${DbContract.SPISACTIVE}     INTEGER NOT NULL DEFAULT 1,
                ${DbContract.SPSORTORDER}    INTEGER NOT NULL DEFAULT 0,
                ${DbContract.SPCREATEDAT}    INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_subscription_plans_active_sort ON ${DbContract.TSUBSCRIPTIONPLANS}(${DbContract.SPISACTIVE}, ${DbContract.SPSORTORDER})"
        )
    }

    private fun createAchievementsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TACHIEVEMENTS} (
                ${DbContract.AID}          INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.ATITLE}       TEXT NOT NULL COLLATE NOCASE UNIQUE,
                ${DbContract.ADESCRIPTION} TEXT,
                ${DbContract.AMETRICKEY}   TEXT NOT NULL,
                ${DbContract.ATARGETVALUE} INTEGER NOT NULL,
                ${DbContract.AISACTIVE}    INTEGER NOT NULL DEFAULT 1,
                ${DbContract.ASORTORDER}   INTEGER NOT NULL DEFAULT 0,
                ${DbContract.ACREATEDAT}   INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_achievements_active_sort ON ${DbContract.TACHIEVEMENTS}(${DbContract.AISACTIVE}, ${DbContract.ASORTORDER})"
        )
    }

    private fun createDecksTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TDECKS} (
                ${DbContract.DID}          INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.DOWNERUSERID} INTEGER NOT NULL,
                ${DbContract.DCATEGORYID}  INTEGER,
                ${DbContract.DSUBJECTID}   INTEGER,
                ${DbContract.DLANGUAGEID}  INTEGER,
                ${DbContract.DTITLE}       TEXT NOT NULL,
                ${DbContract.DDESCRIPTION} TEXT,
                ${DbContract.DCREATEDAT}   INTEGER NOT NULL,
                ${DbContract.DSTATUS}      TEXT NOT NULL DEFAULT '${DbContract.DECKACTIVE}',
                ${DbContract.DISPREMIUM}   INTEGER NOT NULL DEFAULT 0,
                ${DbContract.DISPUBLIC}    INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(${DbContract.DOWNERUSERID}) REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.DCATEGORYID})  REFERENCES ${DbContract.TCATEGORIES}(${DbContract.CATID}) ON DELETE SET NULL,
                FOREIGN KEY(${DbContract.DSUBJECTID})   REFERENCES ${DbContract.TSUBJECTS}(${DbContract.SUBJID}) ON DELETE SET NULL,
                FOREIGN KEY(${DbContract.DLANGUAGEID})  REFERENCES ${DbContract.TLANGUAGES}(${DbContract.LANGID}) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_owner    ON ${DbContract.TDECKS}(${DbContract.DOWNERUSERID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_status   ON ${DbContract.TDECKS}(${DbContract.DSTATUS})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_public   ON ${DbContract.TDECKS}(${DbContract.DISPUBLIC})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_category ON ${DbContract.TDECKS}(${DbContract.DCATEGORYID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_subject  ON ${DbContract.TDECKS}(${DbContract.DSUBJECTID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_language ON ${DbContract.TDECKS}(${DbContract.DLANGUAGEID})")
    }

    private fun createCardsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TCARDS} (
                ${DbContract.CID}        INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.CDECKID}    INTEGER NOT NULL,
                ${DbContract.CFRONT}     TEXT NOT NULL,
                ${DbContract.CBACK}      TEXT NOT NULL,
                ${DbContract.CCREATEDAT} INTEGER NOT NULL,
                FOREIGN KEY(${DbContract.CDECKID}) REFERENCES ${DbContract.TDECKS}(${DbContract.DID}) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cards_deck ON ${DbContract.TCARDS}(${DbContract.CDECKID})")
    }

    private fun createCardProgressTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TCARDPROGRESS} (
                ${DbContract.PUSERID}         INTEGER NOT NULL,
                ${DbContract.PCARDID}         INTEGER NOT NULL,
                ${DbContract.PDUEAT}          INTEGER NOT NULL DEFAULT 0,
                ${DbContract.PLASTREVIEWEDAT} INTEGER,
                ${DbContract.PINTERVALDAYS}   INTEGER NOT NULL DEFAULT 0,
                ${DbContract.PEASEFACTOR}     REAL NOT NULL DEFAULT 2.5,
                ${DbContract.PREVIEWCOUNT}    INTEGER NOT NULL DEFAULT 0,
                ${DbContract.PLAPSECOUNT}     INTEGER NOT NULL DEFAULT 0,
                ${DbContract.PLASTRESULT}     TEXT,
                PRIMARY KEY(${DbContract.PUSERID}, ${DbContract.PCARDID}),
                FOREIGN KEY(${DbContract.PUSERID}) REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.PCARDID}) REFERENCES ${DbContract.TCARDS}(${DbContract.CID}) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cardprogress_due  ON ${DbContract.TCARDPROGRESS}(${DbContract.PUSERID}, ${DbContract.PDUEAT})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cardprogress_card ON ${DbContract.TCARDPROGRESS}(${DbContract.PCARDID})")
    }

    private fun createStudySessionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TSTUDYSESSIONS} (
                ${DbContract.SID}        INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.SUSERID}    INTEGER NOT NULL,
                ${DbContract.SDECKID}    INTEGER NOT NULL,
                ${DbContract.SRESULT}    TEXT NOT NULL,
                ${DbContract.SCREATEDAT} INTEGER NOT NULL,
                FOREIGN KEY(${DbContract.SUSERID}) REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.SDECKID}) REFERENCES ${DbContract.TDECKS}(${DbContract.DID}) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun createReportReasonsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TREPORTREASONS} (
                ${DbContract.RRID}          INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.RRTYPE}        TEXT NOT NULL DEFAULT '${DbContract.RR_TYPE_HELP}',
                ${DbContract.RRNAME}        TEXT NOT NULL COLLATE NOCASE,
                ${DbContract.RRDESCRIPTION} TEXT,
                ${DbContract.RRISACTIVE}    INTEGER NOT NULL DEFAULT 1,
                ${DbContract.RRSORTORDER}   INTEGER NOT NULL DEFAULT 0,
                ${DbContract.RRCREATEDAT}   INTEGER NOT NULL,
                UNIQUE(${DbContract.RRNAME}, ${DbContract.RRTYPE})
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_report_reasons_type_active_sort ON ${DbContract.TREPORTREASONS}(${DbContract.RRTYPE}, ${DbContract.RRISACTIVE}, ${DbContract.RRSORTORDER})"
        )
    }

    private fun createReportsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TREPORTS} (
                ${DbContract.RID}             INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.RREPORTERUSERID} INTEGER NOT NULL,
                ${DbContract.RDECKID}         INTEGER DEFAULT NULL,
                ${DbContract.RREASONID}       INTEGER DEFAULT NULL,
                ${DbContract.RREASON}         TEXT,
                ${DbContract.RDETAILS}        TEXT,
                ${DbContract.RSTATUS}         TEXT NOT NULL DEFAULT '${DbContract.REPORTOPEN}',
                ${DbContract.RCREATEDAT}      INTEGER NOT NULL,
                FOREIGN KEY(${DbContract.RREPORTERUSERID}) REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.RDECKID})         REFERENCES ${DbContract.TDECKS}(${DbContract.DID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.RREASONID})       REFERENCES ${DbContract.TREPORTREASONS}(${DbContract.RRID}) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_deck      ON ${DbContract.TREPORTS}(${DbContract.RDECKID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_status    ON ${DbContract.TREPORTS}(${DbContract.RSTATUS})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_created   ON ${DbContract.TREPORTS}(${DbContract.RCREATEDAT})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_reason_id ON ${DbContract.TREPORTS}(${DbContract.RREASONID})")
    }

    private fun createUserAchievementsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TUSERACHIEVEMENTS} (
                ${DbContract.UAUSERID}        INTEGER NOT NULL,
                ${DbContract.UAACHIEVEMENTID} INTEGER NOT NULL,
                ${DbContract.UAUNLOCKEDAT}    INTEGER NOT NULL,
                PRIMARY KEY(${DbContract.UAUSERID}, ${DbContract.UAACHIEVEMENTID}),
                FOREIGN KEY(${DbContract.UAUSERID})        REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.UAACHIEVEMENTID}) REFERENCES ${DbContract.TACHIEVEMENTS}(${DbContract.AID}) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_user_achievements_user ON ${DbContract.TUSERACHIEVEMENTS}(${DbContract.UAUSERID}, ${DbContract.UAUNLOCKEDAT})")
    }

    private fun createUserSubscriptionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TUSERSUBSCRIPTIONS} (
                ${DbContract.USID}          INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.USUSERID}      INTEGER NOT NULL,
                ${DbContract.USPLANID}      INTEGER NOT NULL,
                ${DbContract.USPURCHASEDAT} INTEGER NOT NULL,
                ${DbContract.USEXPIRESAT}   INTEGER,
                ${DbContract.USISACTIVE}    INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(${DbContract.USUSERID}) REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.USPLANID}) REFERENCES ${DbContract.TSUBSCRIPTIONPLANS}(${DbContract.SPID}) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_active ON ${DbContract.TUSERSUBSCRIPTIONS}(${DbContract.USUSERID}, ${DbContract.USISACTIVE})")
    }

    // ── NEW: Friendships ─────────────────────────────────────────────────────
    private fun createFriendshipsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.TFRIENDSHIPS} (
                ${DbContract.FID}                 INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.FREQUESTERUSERID}    INTEGER NOT NULL,
                ${DbContract.FADDRESSEEUSERID}    INTEGER NOT NULL,
                ${DbContract.FSTATUS}             TEXT NOT NULL DEFAULT '${DbContract.FRIEND_PENDING}',
                ${DbContract.FCREATEDAT}          INTEGER NOT NULL,
                ${DbContract.FRESPONDEDAT}        INTEGER,
                FOREIGN KEY(${DbContract.FREQUESTERUSERID}) REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.FADDRESSEEUSERID}) REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                CHECK(${DbContract.FREQUESTERUSERID} <> ${DbContract.FADDRESSEEUSERID})
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_friendships_requester ON ${DbContract.TFRIENDSHIPS}(${DbContract.FREQUESTERUSERID}, ${DbContract.FSTATUS})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_friendships_addressee ON ${DbContract.TFRIENDSHIPS}(${DbContract.FADDRESSEEUSERID}, ${DbContract.FSTATUS})")
    }
}