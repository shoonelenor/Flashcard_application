package com.example.stardeckapplication.db

import android.database.sqlite.SQLiteDatabase

object DbMigration {

    fun migrate(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DbSchema.createAllTables(db)

        if (oldVersion < 13) addCategorySupport(db)
        if (oldVersion < 14) addSubjectSupport(db)
        if (oldVersion < 15) addLanguageSupport(db)
        if (oldVersion < 16) addAchievementSupport(db)
        if (oldVersion < 17) addSubscriptionPlanSupport(db)
        if (oldVersion < 18) fixReportsTableForDualUse(db)
        if (oldVersion < 19) addReasonTypeToReportReasons(db)
        if (oldVersion < 20) addFriendshipsSupport(db)
        if (oldVersion < 21) addCardImageColumns(db)  // NEW: image support
    }

    // ── NEW: v21 – image columns on cards ────────────────────────────────────
    private fun addCardImageColumns(db: SQLiteDatabase) {
        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.T_CARDS} ADD COLUMN ${DbContract.C_FRONT_IMAGE_PATH} TEXT"
            )
        } catch (_: Exception) { }

        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.T_CARDS} ADD COLUMN ${DbContract.C_BACK_IMAGE_PATH} TEXT"
            )
        } catch (_: Exception) { }
    }

    private fun addReasonTypeToReportReasons(db: SQLiteDatabase) {
        try {
            db.execSQL(
                "ALTER TABLE ${DbContract.TREPORTREASONS} ADD COLUMN ${DbContract.RRTYPE} TEXT NOT NULL DEFAULT '${DbContract.RR_TYPE_HELP}'"
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_report_reasons_type_active_sort ON ${DbContract.TREPORTREASONS}(${DbContract.RRTYPE}, ${DbContract.RRISACTIVE}, ${DbContract.RRSORTORDER})"
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL("ALTER TABLE ${DbContract.TREPORTREASONS} RENAME TO report_reasons_v18")
            DbSchema.recreateReportReasonsTable(db)
            db.execSQL(
                """
                INSERT OR IGNORE INTO ${DbContract.TREPORTREASONS}
                    (${DbContract.RRID}, ${DbContract.RRTYPE}, ${DbContract.RRNAME}, ${DbContract.RRDESCRIPTION},
                     ${DbContract.RRISACTIVE}, ${DbContract.RRSORTORDER}, ${DbContract.RRCREATEDAT})
                SELECT
                    id,
                    COALESCE(reason_type, '${DbContract.RR_TYPE_HELP}'),
                    name,
                    description,
                    is_active,
                    sort_order,
                    created_at
                FROM report_reasons_v18
                """.trimIndent()
            )
            db.execSQL("DROP TABLE IF EXISTS report_reasons_v18")
        } catch (_: Exception) {
            try {
                DbSchema.recreateReportReasonsTable(db)
            } catch (_: Exception) {
            }
        }
    }

    private fun fixReportsTableForDualUse(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE ${DbContract.TREPORTS} RENAME TO reports_old")
        } catch (_: Exception) {
            try {
                DbSchema.recreateReportsTable(db)
                return
            } catch (_: Exception) {
                return
            }
        }

        try {
            DbSchema.recreateReportsTable(db)

            db.execSQL(
                """
                INSERT INTO ${DbContract.TREPORTS} (
                    ${DbContract.RID},
                    ${DbContract.RREPORTERUSERID},
                    ${DbContract.RDECKID},
                    ${DbContract.RREASONID},
                    ${DbContract.RREASON},
                    ${DbContract.RDETAILS},
                    ${DbContract.RSTATUS},
                    ${DbContract.RCREATEDAT}
                )
                SELECT
                    ${DbContract.RID},
                    ${DbContract.RREPORTERUSERID},
                    ${DbContract.RDECKID},
                    ${DbContract.RREASONID},
                    ${DbContract.RREASON},
                    ${DbContract.RDETAILS},
                    ${DbContract.RSTATUS},
                    ${DbContract.RCREATEDAT}
                FROM reports_old
                """.trimIndent()
            )
        } catch (_: Exception) {
        } finally {
            try {
                db.execSQL("DROP TABLE IF EXISTS reports_old")
            } catch (_: Exception) {
            }
        }
    }

    private fun addCategorySupport(db: SQLiteDatabase) {
        try {
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
        } catch (_: Exception) {
        }

        try {
            db.execSQL("ALTER TABLE ${DbContract.TDECKS} ADD COLUMN ${DbContract.DCATEGORYID} INTEGER")
        } catch (_: Exception) {
        }

        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_category ON ${DbContract.TDECKS}(${DbContract.DCATEGORYID})")
        } catch (_: Exception) {
        }
    }

    private fun addSubjectSupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.TSUBJECTS} (
                    ${DbContract.SUBJID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.SUBJCATEGORYID} INTEGER NOT NULL,
                    ${DbContract.SUBJNAME} TEXT NOT NULL COLLATE NOCASE,
                    ${DbContract.SUBJDESCRIPTION} TEXT,
                    ${DbContract.SUBJISACTIVE} INTEGER NOT NULL DEFAULT 1,
                    ${DbContract.SUBJSORTORDER} INTEGER NOT NULL DEFAULT 0,
                    ${DbContract.SUBJCREATEDAT} INTEGER NOT NULL,
                    UNIQUE(${DbContract.SUBJCATEGORYID}, ${DbContract.SUBJNAME})
                )
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL("ALTER TABLE ${DbContract.TDECKS} ADD COLUMN ${DbContract.DSUBJECTID} INTEGER")
        } catch (_: Exception) {
        }

        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_subject ON ${DbContract.TDECKS}(${DbContract.DSUBJECTID})")
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_subjects_category_active_sort ON ${DbContract.TSUBJECTS}(${DbContract.SUBJCATEGORYID}, ${DbContract.SUBJISACTIVE}, ${DbContract.SUBJSORTORDER})"
            )
        } catch (_: Exception) {
        }
    }

    private fun addLanguageSupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.TLANGUAGES} (
                    ${DbContract.LANGID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.LANGNAME} TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    ${DbContract.LANGDESCRIPTION} TEXT,
                    ${DbContract.LANGISACTIVE} INTEGER NOT NULL DEFAULT 1,
                    ${DbContract.LANGSORTORDER} INTEGER NOT NULL DEFAULT 0,
                    ${DbContract.LANGCREATEDAT} INTEGER NOT NULL
                )
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL("ALTER TABLE ${DbContract.TDECKS} ADD COLUMN ${DbContract.DLANGUAGEID} INTEGER")
        } catch (_: Exception) {
        }

        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_language ON ${DbContract.TDECKS}(${DbContract.DLANGUAGEID})")
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_languages_active_sort ON ${DbContract.TLANGUAGES}(${DbContract.LANGISACTIVE}, ${DbContract.LANGSORTORDER})"
            )
        } catch (_: Exception) {
        }
    }

    private fun addAchievementSupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.TACHIEVEMENTS} (
                    ${DbContract.AID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.ATITLE} TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    ${DbContract.ADESCRIPTION} TEXT,
                    ${DbContract.AMETRICKEY} TEXT NOT NULL,
                    ${DbContract.ATARGETVALUE} INTEGER NOT NULL,
                    ${DbContract.AISACTIVE} INTEGER NOT NULL DEFAULT 1,
                    ${DbContract.ASORTORDER} INTEGER NOT NULL DEFAULT 0,
                    ${DbContract.ACREATEDAT} INTEGER NOT NULL
                )
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_achievements_active_sort ON ${DbContract.TACHIEVEMENTS}(${DbContract.AISACTIVE}, ${DbContract.ASORTORDER})"
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.TUSERACHIEVEMENTS} (
                    ${DbContract.UAUSERID} INTEGER NOT NULL,
                    ${DbContract.UAACHIEVEMENTID} INTEGER NOT NULL,
                    ${DbContract.UAUNLOCKEDAT} INTEGER NOT NULL,
                    PRIMARY KEY(${DbContract.UAUSERID}, ${DbContract.UAACHIEVEMENTID})
                )
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_user_achievements_user ON ${DbContract.TUSERACHIEVEMENTS}(${DbContract.UAUSERID}, ${DbContract.UAUNLOCKEDAT})"
            )
        } catch (_: Exception) {
        }
    }

    private fun addSubscriptionPlanSupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.TSUBSCRIPTIONPLANS} (
                    ${DbContract.SPID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.SPNAME} TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    ${DbContract.SPBILLINGCYCLE} TEXT NOT NULL,
                    ${DbContract.SPPRICETEXT} TEXT NOT NULL,
                    ${DbContract.SPDURATIONDAYS} INTEGER NOT NULL,
                    ${DbContract.SPDESCRIPTION} TEXT,
                    ${DbContract.SPISACTIVE} INTEGER NOT NULL DEFAULT 1,
                    ${DbContract.SPSORTORDER} INTEGER NOT NULL DEFAULT 0,
                    ${DbContract.SPCREATEDAT} INTEGER NOT NULL
                )
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_subscription_plans_active_sort ON ${DbContract.TSUBSCRIPTIONPLANS}(${DbContract.SPISACTIVE}, ${DbContract.SPSORTORDER})"
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.TUSERSUBSCRIPTIONS} (
                    ${DbContract.USID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.USUSERID} INTEGER NOT NULL,
                    ${DbContract.USPLANID} INTEGER NOT NULL,
                    ${DbContract.USPURCHASEDAT} INTEGER NOT NULL,
                    ${DbContract.USEXPIRESAT} INTEGER,
                    ${DbContract.USISACTIVE} INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_active ON ${DbContract.TUSERSUBSCRIPTIONS}(${DbContract.USUSERID}, ${DbContract.USISACTIVE})"
            )
        } catch (_: Exception) {
        }
    }

    private fun addFriendshipsSupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
            CREATE TABLE IF NOT EXISTS ${DbContract.TFRIENDSHIPS} (
                ${DbContract.FID}              INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbContract.FREQUESTERUSERID} INTEGER NOT NULL,
                ${DbContract.FADDRESSEEUSERID} INTEGER NOT NULL,
                ${DbContract.FSTATUS}          TEXT NOT NULL DEFAULT '${DbContract.FRIEND_PENDING}',
                ${DbContract.FCREATEDAT}       INTEGER NOT NULL,
                ${DbContract.FRESPONDEDAT}     INTEGER,
                FOREIGN KEY(${DbContract.FREQUESTERUSERID}) REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.FADDRESSEEUSERID}) REFERENCES ${DbContract.TUSERS}(${DbContract.UID}) ON DELETE CASCADE,
                CHECK(${DbContract.FREQUESTERUSERID} <> ${DbContract.FADDRESSEEUSERID})
            )
            """.trimIndent()
            )
        } catch (_: Exception) { }

        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_friendships_requester ON ${DbContract.TFRIENDSHIPS}(${DbContract.FREQUESTERUSERID}, ${DbContract.FSTATUS})")
        } catch (_: Exception) { }

        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_friendships_addressee ON ${DbContract.TFRIENDSHIPS}(${DbContract.FADDRESSEEUSERID}, ${DbContract.FSTATUS})")
        } catch (_: Exception) { }
    }
}
