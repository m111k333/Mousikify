package com.mousikify.core.database.repository

import com.mousikify.core.database.dao.SongDao
import com.mousikify.core.database.entity.SongEntity
import com.mousikify.core.database.entity.toEntity
import com.mousikify.core.model.Album
import com.mousikify.core.model.Artist
import com.mousikify.core.model.Folder
import com.mousikify.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(
    private val songDao: SongDao
) {
    val allSongs: Flow<List<Song>> = songDao.getAllSongs().map { list ->
        list.map { it.toDomain() }
    }

    val favorites: Flow<List<Song>> = songDao.getFavorites().map { list ->
        list.map { it.toDomain() }
    }

    val recentlyPlayed: Flow<List<Song>> = songDao.getRecentlyPlayed(30).map { list ->
        list.map { it.toDomain() }
    }

    val forgottenFavorites: Flow<List<Song>> = songDao.getForgottenFavorites(20).map { list ->
        list.map { it.toDomain() }
    }

    val albums: Flow<List<Album>> = allSongs.map { songs ->
        songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
            val first = albumSongs.first()
            Album(
                id = albumId,
                title = first.albumName.ifBlank { "Unknown Album" },
                artist = first.artistName.ifBlank { "Unknown Artist" },
                artistId = first.artistId,
                songCount = albumSongs.size,
                year = albumSongs.maxOfOrNull { it.year } ?: 0,
                artworkUri = "content://media/external/audio/albumart/$albumId"
            )
        }.sortedBy { it.title.lowercase() }
    }

    val artists: Flow<List<Artist>> = allSongs.map { songs ->
        songs.groupBy { it.artistId }.map { (artistId, artistSongs) ->
            val first = artistSongs.first()
            val albumCount = artistSongs.map { it.albumId }.distinct().size
            Artist(
                id = artistId,
                name = first.artistName.ifBlank { "Unknown Artist" },
                songCount = artistSongs.size,
                albumCount = albumCount
            )
        }.sortedBy { it.name.lowercase() }
    }

    val folders: Flow<List<Folder>> = allSongs.map { songs ->
        songs.groupBy { it.folderPath }.map { (folderPath, folderSongs) ->
            val folderName = if (folderPath.isNotEmpty()) {
                File(folderPath).name.ifEmpty { folderPath }
            } else "Internal Storage"
            Folder(
                path = folderPath,
                name = folderName,
                songCount = folderSongs.size
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun getSongsByFolder(path: String): Flow<List<Song>> =
        songDao.getSongsByFolder(path).map { list -> list.map { it.toDomain() } }

    fun getSongsByAlbum(albumId: Long): Flow<List<Song>> =
        songDao.getSongsByAlbum(albumId).map { list -> list.map { it.toDomain() } }

    fun getSongsByArtist(artistId: Long): Flow<List<Song>> =
        songDao.getSongsByArtist(artistId).map { list -> list.map { it.toDomain() } }

    fun searchSongs(query: String): Flow<List<Song>> {
        val sanitized = query.trim().replace("\"", "").replace("'", "")
        return if (sanitized.isEmpty()) {
            allSongs
        } else {
            // Append wildcard for prefix match in FTS4
            val ftsQuery = "*$sanitized*"
            songDao.searchSongsFts(ftsQuery).map { list -> list.map { it.toDomain() } }
        }
    }

    fun getSongById(id: Long): Flow<Song?> =
        songDao.getSongById(id).map { it?.toDomain() }

    suspend fun getSongByIdSync(id: Long): Song? =
        songDao.getSongByIdSync(id)?.toDomain()

    suspend fun toggleFavorite(songId: Long, isFavorite: Boolean) {
        songDao.setFavorite(songId, isFavorite)
    }

    suspend fun recordPlay(songId: Long) {
        songDao.recordPlay(songId, System.currentTimeMillis())
    }

    suspend fun upsertSongs(songs: List<Song>) {
        songDao.upsertSongs(songs.map { it.toEntity() })
    }

    suspend fun deleteSongs(ids: List<Long>) {
        songDao.deleteSongsByIds(ids)
    }
}
