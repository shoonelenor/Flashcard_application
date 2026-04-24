package com.example.stardeckapplication.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class StarDeckDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DbContract.DB_NAME, null, DbContract.DB_VERSION) {

    companion object {
        @Volatile
        private var instance: StarDeckDbHelper? = null

        fun getInstance(context: Context): StarDeckDbHelper {
            return instance ?: synchronized(this) {
                instance ?: StarDeckDbHelper(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        DbSchema.createAllTables(db)
        // ✅ FIXED: seed all initial data on fresh install
        DbSeeder.seedStaffAccounts(db)
        DbSeeder.seedReportReasons(db)
        DbSeeder.seedDemoDecksAndCards(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("PRAGMA foreign_keys = ON")
        DbMigration.migrate(db, oldVersion, newVersion)
        // ✅ FIXED: re-seed staff accounts & report reasons after any migration
        // (uses INSERT OR IGNORE so no duplicates are created)
        DbSeeder.seedStaffAccounts(db)
        DbSeeder.seedReportReasons(db)
        DbSeeder.seedDemoDecksAndCards(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}