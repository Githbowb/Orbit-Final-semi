package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_artworks")
data class ArtworkEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val type: String = "CANVAS", // CANVAS, PHOTO_CROP, PRESET_CREATION, GENERATED
    val isFavorite: Boolean = false,
    val accentColorHex: String = "#FF6B35"
)
