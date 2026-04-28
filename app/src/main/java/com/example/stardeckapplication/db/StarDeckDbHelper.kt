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
        DbSeeder.seedReportReasons(db)
        DbSeeder.seedMasterData(db)
        DbSeeder.seedAchievements(db)
        DbSeeder.seedSubscriptionPlans(db)
        DbSeeder.seedStaffAccounts(db)
        DbSeeder.seedDemoDecksAndCards(db)
        DbSeeder.seedFriendships(db)
        DbSeeder.seedStudySessions(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("PRAGMA foreign_keys = ON")
        DbMigration.migrate(db, oldVersion, newVersion)
        DbSeeder.seedReportReasons(db)
        DbSeeder.seedMasterData(db)
        DbSeeder.seedAchievements(db)
        DbSeeder.seedSubscriptionPlans(db)
        DbSeeder.seedStaffAccounts(db)
        DbSeeder.seedDemoDecksAndCards(db)
        DbSeeder.seedFriendships(db)
        DbSeeder.seedStudySessions(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}
