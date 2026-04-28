package com.example.stardeckapplication.db

import android.database.sqlite.SQLiteDatabase
import android.util.Log

object DbMigration {

    private const val TAG = "DbMigration"

    /**
     * Called from [StarDeckDbHelper.onUpgrade].
     * Applies every migration from [oldVersion] up to [newVersion] in order.
     */
    fun migrate(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var v = oldVersion
        while (v < newVersion) {
            Log.d(TAG, "Migrating DB $v -> ${v + 1}")
            when (v) {
                1  -> migrate1to2(db)
                2  -> migrate2to3(db)
                3  -> migrate3to4(db)
                4  -> migrate4to5(db)
                5  -> migrate5to6(db)
                6  -> migrate6to7(db)
                7  -> migrate7to8(db)
                8  -> migrate8to9(db)
                9  -> migrate9to10(db)
                10 -> migrate10to11(db)
                11 -> migrate11to12(db)
                12 -> migrate12to13(db)
                13 -> migrate13to14(db)
                14 -> migrate14to15(db)
                15 -> migrate15to16(db)
                16 -> migrate16to17(db)
                17 -> migrate17to18(db)
                18 -> migrate18to19(db)
                19 -> migrate19to20(db)
                20 -> migrate20to21(db)   // ← image columns
                else -> Log.w(TAG, "No migration defined for version $v")
            }
            v++
        }
    }

    // ── Individual migrations ─────────────────────────────────────────────────

    private fun migrate1to2(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TUSERS} ADD COLUMN ${DbContract.UISPREMIUMUSER} INTEGER NOT NULL DEFAULT 0")
    }

    private fun migrate2to3(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TUSERS} ADD COLUMN ${DbContract.ULASTLOGINAT} INTEGER")
    }

    private fun migrate3to4(db: SQLiteDatabase) {
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

    private fun migrate4to5(db: SQLiteDatabase) {
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

    private fun migrate5to6(db: SQLiteDatabase) {
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_achievements_active_sort ON ${DbContract.TACHIEVEMENTS}(${DbContract.AISACTIVE}, ${DbContract.ASORTORDER})")
    }

    private fun migrate6to7(db: SQLiteDatabase) {
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

    private fun migrate7to8(db: SQLiteDatabase) {
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_report_reasons_type_active_sort ON ${DbContract.TREPORTREASONS}(${DbContract.RRTYPE}, ${DbContract.RRISACTIVE}, ${DbContract.RRSORTORDER})")
    }

    private fun migrate8to9(db: SQLiteDatabase) {
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

    private fun migrate9to10(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TREPORTREASONS} ADD COLUMN IF NOT EXISTS ${DbContract.RRTYPE} TEXT NOT NULL DEFAULT '${DbContract.RR_TYPE_HELP}'")
    }

    private fun migrate10to11(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TDECKS} ADD COLUMN ${DbContract.DISPUBLIC} INTEGER NOT NULL DEFAULT 0")
    }

    private fun migrate11to12(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TDECKS} ADD COLUMN ${DbContract.DISPREMIUM} INTEGER NOT NULL DEFAULT 0")
    }

    private fun migrate12to13(db: SQLiteDatabase) {
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

    private fun migrate13to14(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TUSERS} ADD COLUMN ${DbContract.UACCEPTEDTERMS} INTEGER NOT NULL DEFAULT 0")
    }

    private fun migrate14to15(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TUSERS} ADD COLUMN ${DbContract.UFORCEPWCHANGE} INTEGER NOT NULL DEFAULT 0")
    }

    private fun migrate15to16(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TDECKS} ADD COLUMN ${DbContract.DCATEGORYID}  INTEGER")
        db.execSQL("ALTER TABLE ${DbContract.TDECKS} ADD COLUMN ${DbContract.DSUBJECTID}   INTEGER")
        db.execSQL("ALTER TABLE ${DbContract.TDECKS} ADD COLUMN ${DbContract.DLANGUAGEID}  INTEGER")
    }

    private fun migrate16to17(db: SQLiteDatabase) {
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_languages_active_sort ON ${DbContract.TLANGUAGES}(${DbContract.LANGISACTIVE}, ${DbContract.LANGSORTORDER})")
    }

    private fun migrate17to18(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TDECKS} ADD COLUMN ${DbContract.DSTATUS} TEXT NOT NULL DEFAULT '${DbContract.DECKACTIVE}'")
    }

    private fun migrate18to19(db: SQLiteDatabase) {
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_categories_active_sort ON ${DbContract.TCATEGORIES}(${DbContract.CATISACTIVE}, ${DbContract.CATSORTORDER})")
    }

    private fun migrate19to20(db: SQLiteDatabase) {
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_subjects_category_active_sort ON ${DbContract.TSUBJECTS}(${DbContract.SUBJCATEGORYID}, ${DbContract.SUBJISACTIVE}, ${DbContract.SUBJSORTORDER})")
    }

    /**
     * Migration 20 → 21: add front_image_path and back_image_path columns to
     * the existing cards table for existing installs.
     * New installs get these columns via DbSchema.createCardsTable().
     */
    private fun migrate20to21(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE ${DbContract.TCARDS} ADD COLUMN ${DbContract.CFRONTIMAGEPATH} TEXT")
        db.execSQL("ALTER TABLE ${DbContract.TCARDS} ADD COLUMN ${DbContract.CBACKIMAGEPATH}  TEXT")
        Log.d(TAG, "Migration 20→21: image columns added to cards table")
    }
}
