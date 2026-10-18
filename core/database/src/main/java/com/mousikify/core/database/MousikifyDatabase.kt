package com.mousikify.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mousikify.core.database.converter.Converters
import com.mousikify.core.database.dao.EqPresetDao
import com.mousikify.core.database.dao.PlaylistDao
import com.mousikify.core.database.dao.SongDao
import com.mousikify.core.database.entity.EqPresetEntity
import com.mousikify.core.database.entity.PlaylistEntity
import com.mousikify.core.database.entity.PlaylistSongCrossRef
import com.mousikify.core.database.entity.SongEntity
import com.mousikify.core.database.entity.SongFtsEntity

@Database(
    entities = [
        SongEntity::class,
        SongFtsEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        EqPresetEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MousikifyDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun eqPresetDao(): EqPresetDao

    companion object {
        const val DATABASE_NAME = "mousikify.db"
    }
}
