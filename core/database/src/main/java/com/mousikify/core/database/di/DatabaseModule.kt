package com.mousikify.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mousikify.core.database.MousikifyDatabase
import com.mousikify.core.database.dao.EqPresetDao
import com.mousikify.core.database.dao.PlaylistDao
import com.mousikify.core.database.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMousikifyDatabase(
        @ApplicationContext context: Context,
        eqPresetDaoProvider: Provider<EqPresetDao>
    ): MousikifyDatabase {
        return Room.databaseBuilder(
            context,
            MousikifyDatabase::class.java,
            MousikifyDatabase.DATABASE_NAME
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-populate standard 10-band presets
                CoroutineScope(Dispatchers.IO).launch {
                    val presets = listOf(
                        "INSERT INTO eq_presets (presetId, name, bandLevelsMb, bassBoostStrength, virtualizerStrength, reverbPreset, isCustom) VALUES (1, 'Flat', '0,0,0,0,0,0,0,0,0,0', 0, 0, 0, 0)",
                        "INSERT INTO eq_presets (presetId, name, bandLevelsMb, bassBoostStrength, virtualizerStrength, reverbPreset, isCustom) VALUES (2, 'Bass Boost', '500,450,350,200,50,0,0,0,0,0', 400, 0, 0, 0)",
                        "INSERT INTO eq_presets (presetId, name, bandLevelsMb, bassBoostStrength, virtualizerStrength, reverbPreset, isCustom) VALUES (3, 'Rock', '450,300,150,0,-100,-50,150,300,400,450', 100, 150, 0, 0)",
                        "INSERT INTO eq_presets (presetId, name, bandLevelsMb, bassBoostStrength, virtualizerStrength, reverbPreset, isCustom) VALUES (4, 'Pop', '-150,-50,150,300,400,350,200,100,50,-50', 50, 100, 0, 0)",
                        "INSERT INTO eq_presets (presetId, name, bandLevelsMb, bassBoostStrength, virtualizerStrength, reverbPreset, isCustom) VALUES (5, 'Electronic', '400,350,150,0,-150,100,150,250,400,450', 250, 200, 0, 0)",
                        "INSERT INTO eq_presets (presetId, name, bandLevelsMb, bassBoostStrength, virtualizerStrength, reverbPreset, isCustom) VALUES (6, 'Vocal', '-200,-200,-100,150,350,350,300,150,0,-150', 0, 100, 1, 0)"
                    )
                    presets.forEach { db.execSQL(it) }
                }
            }
        }).build()
    }

    @Provides
    fun provideSongDao(database: MousikifyDatabase): SongDao = database.songDao()

    @Provides
    fun providePlaylistDao(database: MousikifyDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideEqPresetDao(database: MousikifyDatabase): EqPresetDao = database.eqPresetDao()
}
