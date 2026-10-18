package com.mousikify.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["uri"], unique = true),
        Index("decade"),
        Index("normalizedGenre"),
        Index("vibeCategory"),
        Index("isAnalyzed")
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val rawGenre: String? = null,
    val normalizedGenre: String? = null,
    val year: Int = 0,
    val decade: String? = null,
    val durationMs: Long = 0L,
    val rmsEnergy: Float = 0.0f,
    val bpm: Int = 0,
    val vibeCategory: String? = null,
    val isAnalyzed: Boolean = false
)
