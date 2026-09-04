package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VaultEntry::class, SpeedDialEntry::class, VaultFolder::class, ArtworkEntry::class], version = 4, exportSchema = false)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun speedDialDao(): SpeedDialDao
    abstract fun studioArtworkDao(): StudioArtworkDao

    companion object {
        @Volatile
        private var INSTANCE: OrbitDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vault_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                try {
                    db.execSQL("ALTER TABLE `vault_entries` ADD COLUMN `folderId` INTEGER DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `studio_artworks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `filePath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `type` TEXT NOT NULL DEFAULT 'CANVAS', `isFavorite` INTEGER NOT NULL DEFAULT 0, `accentColorHex` TEXT NOT NULL DEFAULT '#FF6B35')")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `vault_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `studio_artworks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `filePath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `type` TEXT NOT NULL DEFAULT 'CANVAS', `isFavorite` INTEGER NOT NULL DEFAULT 0, `accentColorHex` TEXT NOT NULL DEFAULT '#FF6B35')")
                try {
                    db.execSQL("ALTER TABLE `vault_entries` ADD COLUMN `folderId` INTEGER DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        fun getDatabase(context: Context): OrbitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OrbitDatabase::class.java,
                    "orbit_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_1_4, MIGRATION_2_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
