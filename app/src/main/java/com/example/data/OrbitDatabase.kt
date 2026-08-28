package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VaultEntry::class, SpeedDialEntry::class, VaultFolder::class, ArtworkEntry::class], version = 4, exportSchema = false)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun speedDialDao(): SpeedDialDao
    abstract fun studioArtworkDao(): StudioArtworkDao

    companion object {
        @Volatile
        private var INSTANCE: OrbitDatabase? = null

        fun getDatabase(context: Context): OrbitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OrbitDatabase::class.java,
                    "orbit_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
