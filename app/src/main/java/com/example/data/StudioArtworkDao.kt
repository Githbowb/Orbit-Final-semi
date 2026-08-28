package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioArtworkDao {
    @Query("SELECT * FROM studio_artworks ORDER BY createdAt DESC")
    fun getAllArtworks(): Flow<List<ArtworkEntry>>

    @Query("SELECT * FROM studio_artworks WHERE id = :id")
    suspend fun getArtworkById(id: Long): ArtworkEntry?

    @Query("SELECT COUNT(*) FROM studio_artworks")
    suspend fun getArtworkCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artwork: ArtworkEntry): Long

    @Update
    suspend fun update(artwork: ArtworkEntry)

    @Delete
    suspend fun delete(artwork: ArtworkEntry)

    @Query("DELETE FROM studio_artworks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
