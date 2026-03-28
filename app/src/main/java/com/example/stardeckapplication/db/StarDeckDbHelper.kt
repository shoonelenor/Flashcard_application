package com.example.stardeckapplication.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class StarDeckDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DbContract.DB_NAME,
    null,
    DbContract.DB_VERSION
) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        DbSchema.createAllTables(db)

        // Initial seed data
        DbSeeder.seedStaffAccounts(db)
        DbSeeder.seedDemoDecksAndCards(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DbMigration.migrate(db, oldVersion, newVersion)

        // Re‑seed staff + demo (all seeding functions are idempotent)
        DbSeeder.seedStaffAccounts(db)
        DbSeeder.seedDemoDecksAndCards(db)
    }
}