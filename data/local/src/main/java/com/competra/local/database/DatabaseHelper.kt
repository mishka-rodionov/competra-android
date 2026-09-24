package com.competra.local.database

import android.content.Context
import androidx.room.Room

private const val DATABASE_NAME = "competra-db"

class DatabaseHelper(private val context: Context) {

    val db: CompetraDatabase by lazy { createDatabase() }

    fun createDatabase() =
        Room
            .databaseBuilder(context = context, CompetraDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_27_28, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_42_43, MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

}