package com.example.stardeckapplication.db

import android.database.sqlite.SQLiteDatabase

/**
 * Handles all database migrations (onUpgrade) for StarDeck.
 *
 * Use from StarDeckDbHelper:
 *
 * override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
 *     MigrationDao.migrate(db, oldVersion, newVersion)
 *     DataSeeder.seedAll(db) // later, when you create DataSeeder
 * }
 */
object DbMigration {

    fun migrate(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Safety: ensure all base tables exist before running migrations
        DbSchema.createAllTables(db)

        if (oldVersion < 5) {
            addDeckStatusColumn(db)
        }
        if (oldVersion < 7) {
            addPremiumColumns(db)
        }
        if (oldVersion < 8) {
            addLastLoginColumn(db)
        }
        if (oldVersion < 9) {
            migrateToVersion9(db)
        }
        if (oldVersion < 10) {
            addDeckPublicColumn(db)
        }
    }

    // ---------- v5: deck status column ----------

    private fun addDeckStatusColumn(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                ALTER TABLE ${DbContract.T_DECKS}
                ADD COLUMN ${DbContract.D_STATUS} TEXT NOT NULL DEFAULT '${DbContract.DECK_ACTIVE}'
                """.trimIndent()
            )
        } catch (e: Exception) {
            // If column already exists, just try to fill nulls
            try {
                db.execSQL(
                    """
                    UPDATE ${DbContract.T_DECKS}
                    SET ${DbContract.D_STATUS}='${DbContract.DECK_ACTIVE}'
                    WHERE ${DbContract.D_STATUS} IS NULL
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }
    }

    // ---------- v7: premium related columns ----------

    private fun addPremiumColumns(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                ALTER TABLE ${DbContract.T_USERS}
                ADD COLUMN ${DbContract.U_IS_PREMIUM_USER} INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                """
                ALTER TABLE ${DbContract.T_DECKS}
                ADD COLUMN ${DbContract.D_IS_PREMIUM} INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                """
                UPDATE ${DbContract.T_USERS}
                SET ${DbContract.U_IS_PREMIUM_USER}=0
                WHERE ${DbContract.U_IS_PREMIUM_USER} IS NULL
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                """
                UPDATE ${DbContract.T_DECKS}
                SET ${DbContract.D_IS_PREMIUM}=0
                WHERE ${DbContract.D_IS_PREMIUM} IS NULL
                """.trimIndent()
            )
        } catch (_: Exception) {
        }
    }

    // ---------- v8: last login column ----------

    private fun addLastLoginColumn(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                ALTER TABLE ${DbContract.T_USERS}
                ADD COLUMN ${DbContract.U_LAST_LOGIN_AT} INTEGER
                """.trimIndent()
            )
        } catch (_: Exception) {
        }
    }

    // ---------- v10: deck public column ----------

    private fun addDeckPublicColumn(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                ALTER TABLE ${DbContract.T_DECKS}
                ADD COLUMN ${DbContract.D_IS_PUBLIC} INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                """
                UPDATE ${DbContract.T_DECKS}
                SET ${DbContract.D_IS_PUBLIC}=0
                WHERE ${DbContract.D_IS_PUBLIC} IS NULL
                """.trimIndent()
            )
        } catch (_: Exception) {
        }

        try {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_decks_public
                ON ${DbContract.T_DECKS}(${DbContract.D_IS_PUBLIC})
                """.trimIndent()
            )
        } catch (_: Exception) {
        }
    }

    // ---------- v9: rebuild decks table + card_progress + cleanup ----------

    private fun migrateToVersion9(db: SQLiteDatabase) {
        // Temporarily disable FK checks while we rebuild decks table
        db.execSQL("PRAGMA foreign_keys=OFF")
        db.beginTransaction()
        try {
            // New decks table with clean FK + default values
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS decks_new (
                    ${DbContract.D_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    ${DbContract.D_OWNER_USER_ID} INTEGER NOT NULL,
                    ${DbContract.D_TITLE} TEXT NOT NULL,
                    ${DbContract.D_DESCRIPTION} TEXT,
                    ${DbContract.D_CREATED_AT} INTEGER NOT NULL,
                    ${DbContract.D_STATUS} TEXT NOT NULL DEFAULT '${DbContract.DECK_ACTIVE}',
                    ${DbContract.D_IS_PREMIUM} INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(${DbContract.D_OWNER_USER_ID})
                        REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID})
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Copy valid rows from old decks, fixing null status/premium
            db.execSQL(
                """
                INSERT INTO decks_new (
                    ${DbContract.D_ID},
                    ${DbContract.D_OWNER_USER_ID},
                    ${DbContract.D_TITLE},
                    ${DbContract.D_DESCRIPTION},
                    ${DbContract.D_CREATED_AT},
                    ${DbContract.D_STATUS},
                    ${DbContract.D_IS_PREMIUM}
                )
                SELECT
                    d.${DbContract.D_ID},
                    d.${DbContract.D_OWNER_USER_ID},
                    d.${DbContract.D_TITLE},
                    d.${DbContract.D_DESCRIPTION},
                    d.${DbContract.D_CREATED_AT},
                    COALESCE(d.${DbContract.D_STATUS}, '${DbContract.DECK_ACTIVE}'),
                    COALESCE(d.${DbContract.D_IS_PREMIUM}, 0)
                FROM ${DbContract.T_DECKS} d
                INNER JOIN ${DbContract.T_USERS} u
                    ON u.${DbContract.U_ID} = d.${DbContract.D_OWNER_USER_ID}
                """.trimIndent()
            )

            // Swap tables
            db.execSQL("DROP TABLE ${DbContract.T_DECKS}")
            db.execSQL("ALTER TABLE decks_new RENAME TO ${DbContract.T_DECKS}")

            // Re-create indexes for decks
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_decks_owner
                ON ${DbContract.T_DECKS}(${DbContract.D_OWNER_USER_ID})
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_decks_status
                ON ${DbContract.T_DECKS}(${DbContract.D_STATUS})
                """.trimIndent()
            )

            // Ensure card_progress table exists in v9+
            createCardProgressTableIfMissing(db)

            // Remove rows pointing to deleted users/decks/cards
            cleanupOrphanRowsAfterVersion9(db)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    private fun createCardProgressTableIfMissing(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${DbContract.T_CARD_PROGRESS} (
                ${DbContract.P_USER_ID} INTEGER NOT NULL,
                ${DbContract.P_CARD_ID} INTEGER NOT NULL,
                ${DbContract.P_DUE_AT} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.P_LAST_REVIEWED_AT} INTEGER,
                ${DbContract.P_INTERVAL_DAYS} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.P_EASE_FACTOR} REAL NOT NULL DEFAULT 2.5,
                ${DbContract.P_REVIEW_COUNT} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.P_LAPSE_COUNT} INTEGER NOT NULL DEFAULT 0,
                ${DbContract.P_LAST_RESULT} TEXT,
                PRIMARY KEY(${DbContract.P_USER_ID}, ${DbContract.P_CARD_ID}),
                FOREIGN KEY(${DbContract.P_USER_ID})
                    REFERENCES ${DbContract.T_USERS}(${DbContract.U_ID})
                    ON DELETE CASCADE,
                FOREIGN KEY(${DbContract.P_CARD_ID})
                    REFERENCES ${DbContract.T_CARDS}(${DbContract.C_ID})
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_card_progress_due
            ON ${DbContract.T_CARD_PROGRESS}(${DbContract.P_USER_ID}, ${DbContract.P_DUE_AT})
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_card_progress_card
            ON ${DbContract.T_CARD_PROGRESS}(${DbContract.P_CARD_ID})
            """.trimIndent()
        )
    }

    private fun cleanupOrphanRowsAfterVersion9(db: SQLiteDatabase) {
        // Cards whose deck no longer exists
        db.execSQL(
            """
            DELETE FROM ${DbContract.T_CARDS}
            WHERE ${DbContract.C_DECK_ID} NOT IN (
                SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS}
            )
            """.trimIndent()
        )

        // Study sessions whose deck no longer exists
        db.execSQL(
            """
            DELETE FROM ${DbContract.T_STUDY_SESSIONS}
            WHERE ${DbContract.S_DECK_ID} NOT IN (
                SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS}
            )
            """.trimIndent()
        )

        // Reports whose deck no longer exists
        db.execSQL(
            """
            DELETE FROM ${DbContract.T_REPORTS}
            WHERE ${DbContract.R_DECK_ID} NOT IN (
                SELECT ${DbContract.D_ID} FROM ${DbContract.T_DECKS}
            )
            """.trimIndent()
        )

        // Card progress pointing to deleted users
        db.execSQL(
            """
            DELETE FROM ${DbContract.T_CARD_PROGRESS}
            WHERE ${DbContract.P_USER_ID} NOT IN (
                SELECT ${DbContract.U_ID} FROM ${DbContract.T_USERS}
            )
            """.trimIndent()
        )

        // Card progress pointing to deleted cards
        db.execSQL(
            """
            DELETE FROM ${DbContract.T_CARD_PROGRESS}
            WHERE ${DbContract.P_CARD_ID} NOT IN (
                SELECT ${DbContract.C_ID} FROM ${DbContract.T_CARDS}
            )
            """.trimIndent()
        )
    }
}