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
    }

    private fun addCategorySupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.T_CATEGORIES} (
                    ${DbContract.CAT_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.CAT_NAME} TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    ${DbContract.CAT_DESCRIPTION} TEXT,
                    ${DbContract.CAT_IS_ACTIVE} INTEGER NOT NULL DEFAULT 1,
                    ${DbContract.CAT_SORT_ORDER} INTEGER NOT NULL DEFAULT 0,
                    ${DbContract.CAT_CREATED_AT} INTEGER NOT NULL
                )
                """.trimIndent()
            )
        } catch (_: Exception) { }
        try {
            db.execSQL("""ALTER TABLE ${DbContract.T_DECKS} ADD COLUMN ${DbContract.D_CATEGORY_ID} INTEGER""")
        } catch (_: Exception) { }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_category ON ${DbContract.T_DECKS}(${DbContract.D_CATEGORY_ID})")
        } catch (_: Exception) { }
    }

    private fun addSubjectSupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.T_SUBJECTS} (
                    ${DbContract.SUBJ_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.SUBJ_CATEGORY_ID} INTEGER NOT NULL,
                    ${DbContract.SUBJ_NAME} TEXT NOT NULL COLLATE NOCASE,
                    ${DbContract.SUBJ_DESCRIPTION} TEXT,
                    ${DbContract.SUBJ_IS_ACTIVE} INTEGER NOT NULL DEFAULT 1,
                    ${DbContract.SUBJ_SORT_ORDER} INTEGER NOT NULL DEFAULT 0,
                    ${DbContract.SUBJ_CREATED_AT} INTEGER NOT NULL,
                    UNIQUE(${DbContract.SUBJ_CATEGORY_ID}, ${DbContract.SUBJ_NAME})
                )
                """.trimIndent()
            )
        } catch (_: Exception) { }
        try {
            db.execSQL("""ALTER TABLE ${DbContract.T_DECKS} ADD COLUMN ${DbContract.D_SUBJECT_ID} INTEGER""")
        } catch (_: Exception) { }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_subject ON ${DbContract.T_DECKS}(${DbContract.D_SUBJECT_ID})")
        } catch (_: Exception) { }
        try {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_subjects_category_active_sort
                ON ${DbContract.T_SUBJECTS}(${DbContract.SUBJ_CATEGORY_ID}, ${DbContract.SUBJ_IS_ACTIVE}, ${DbContract.SUBJ_SORT_ORDER})
                """.trimIndent()
            )
        } catch (_: Exception) { }
    }

    private fun addLanguageSupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.T_LANGUAGES} (
                    ${DbContract.LANG_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.LANG_NAME} TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    ${DbContract.LANG_DESCRIPTION} TEXT,
                    ${DbContract.LANG_IS_ACTIVE} INTEGER NOT NULL DEFAULT 1,
                    ${DbContract.LANG_SORT_ORDER} INTEGER NOT NULL DEFAULT 0,
                    ${DbContract.LANG_CREATED_AT} INTEGER NOT NULL
                )
                """.trimIndent()
            )
        } catch (_: Exception) { }
        try {
            db.execSQL("""ALTER TABLE ${DbContract.T_DECKS} ADD COLUMN ${DbContract.D_LANGUAGE_ID} INTEGER""")
        } catch (_: Exception) { }
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_decks_language ON ${DbContract.T_DECKS}(${DbContract.D_LANGUAGE_ID})")
        } catch (_: Exception) { }
        try {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_languages_active_sort
                ON ${DbContract.T_LANGUAGES}(${DbContract.LANG_IS_ACTIVE}, ${DbContract.LANG_SORT_ORDER})
                """.trimIndent()
            )
        } catch (_: Exception) { }
    }

    private fun addAchievementSupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.T_ACHIEVEMENTS} (
                    ${DbContract.A_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.A_TITLE} TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    ${DbContract.A_DESCRIPTION} TEXT,
                    ${DbContract.A_METRIC_KEY} TEXT NOT NULL,
                    ${DbContract.A_TARGET_VALUE} INTEGER NOT NULL,
                    ${DbContract.A_IS_ACTIVE} INTEGER NOT NULL DEFAULT 1,
                    ${DbContract.A_SORT_ORDER} INTEGER NOT NULL DEFAULT 0,
                    ${DbContract.A_CREATED_AT} INTEGER NOT NULL
                )
                """.trimIndent()
            )
        } catch (_: Exception) { }
        try {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_achievements_active_sort
                ON ${DbContract.T_ACHIEVEMENTS}(${DbContract.A_IS_ACTIVE}, ${DbContract.A_SORT_ORDER})
                """.trimIndent()
            )
        } catch (_: Exception) { }
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.T_USER_ACHIEVEMENTS} (
                    ${DbContract.UA_USER_ID} INTEGER NOT NULL,
                    ${DbContract.UA_ACHIEVEMENT_ID} INTEGER NOT NULL,
                    ${DbContract.UA_UNLOCKED_AT} INTEGER NOT NULL,
                    PRIMARY KEY(${DbContract.UA_USER_ID}, ${DbContract.UA_ACHIEVEMENT_ID})
                )
                """.trimIndent()
            )
        } catch (_: Exception) { }
        try {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_user_achievements_user
                ON ${DbContract.T_USER_ACHIEVEMENTS}(${DbContract.UA_USER_ID}, ${DbContract.UA_UNLOCKED_AT})
                """.trimIndent()
            )
        } catch (_: Exception) { }
    }

    private fun addSubscriptionPlanSupport(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.T_SUBSCRIPTION_PLANS} (
                    ${DbContract.SP_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.SP_NAME} TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    ${DbContract.SP_BILLING_CYCLE} TEXT NOT NULL,
                    ${DbContract.SP_PRICE_TEXT} TEXT NOT NULL,
                    ${DbContract.SP_DURATION_DAYS} INTEGER NOT NULL,
                    ${DbContract.SP_DESCRIPTION} TEXT,
                    ${DbContract.SP_IS_ACTIVE} INTEGER NOT NULL DEFAULT 1,
                    ${DbContract.SP_SORT_ORDER} INTEGER NOT NULL DEFAULT 0,
                    ${DbContract.SP_CREATED_AT} INTEGER NOT NULL
                )
                """.trimIndent()
            )
        } catch (_: Exception) { }
        try {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_subscription_plans_active_sort
                ON ${DbContract.T_SUBSCRIPTION_PLANS}(${DbContract.SP_IS_ACTIVE}, ${DbContract.SP_SORT_ORDER})
                """.trimIndent()
            )
        } catch (_: Exception) { }
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${DbContract.T_USER_SUBSCRIPTIONS} (
                    ${DbContract.US_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.US_USER_ID} INTEGER NOT NULL,
                    ${DbContract.US_PLAN_ID} INTEGER NOT NULL,
                    ${DbContract.US_PURCHASED_AT} INTEGER NOT NULL,
                    ${DbContract.US_EXPIRES_AT} INTEGER,
                    ${DbContract.US_IS_ACTIVE} INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        } catch (_: Exception) { }
        try {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_active
                ON ${DbContract.T_USER_SUBSCRIPTIONS}(${DbContract.US_USER_ID}, ${DbContract.US_IS_ACTIVE})
                """.trimIndent()
            )
        } catch (_: Exception) { }
    }
}