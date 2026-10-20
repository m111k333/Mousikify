package com.mousikify.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN artwork_uri TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS tracks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    uri TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    rawGenre TEXT,
                    normalizedGenre TEXT,
                    year INTEGER NOT NULL,
                    decade TEXT,
                    durationMs INTEGER NOT NULL,
                    rmsEnergy REAL NOT NULL,
                    bpm INTEGER NOT NULL,
                    vibeCategory TEXT,
                    isAnalyzed INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tracks_uri ON tracks(uri)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_decade ON tracks(decade)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_normalizedGenre ON tracks(normalizedGenre)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_vibeCategory ON tracks(vibeCategory)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_isAnalyzed ON tracks(isAnalyzed)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS tracks")
        }
    }

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
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .fallbackToDestructiveMigration()
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-populate standard 10-band presets synchronously within database creation
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
        }).build()
    }

    @Provides
    fun provideSongDao(database: MousikifyDatabase): SongDao = database.songDao()

    @Provides
    fun providePlaylistDao(database: MousikifyDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideEqPresetDao(database: MousikifyDatabase): EqPresetDao = database.eqPresetDao()
}
