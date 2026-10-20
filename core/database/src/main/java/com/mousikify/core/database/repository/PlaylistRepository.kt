package com.mousikify.core.database.repository

import com.mousikify.core.database.dao.PlaylistDao
import com.mousikify.core.database.dao.SongDao
import com.mousikify.core.database.entity.PlaylistEntity
import com.mousikify.core.database.entity.SongEntity
import com.mousikify.core.database.entity.toEntity
import com.mousikify.core.model.Playlist
import com.mousikify.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) {
    val playlists: Flow<List<Playlist>> = playlistDao.getAllPlaylistsWithCount().map { list ->
        list.map { entity ->
            Playlist(
                id = entity.playlistId,
                name = entity.name,
                createdAt = entity.createdAt,
                customCoverUri = entity.customCoverUri,
                songCount = entity.songCount
            )
        }
    }

    fun getPlaylistById(id: Long): Flow<Playlist?> =
        playlistDao.getPlaylistById(id).map { entity ->
            entity?.let {
                Playlist(
                    id = it.playlistId,
                    name = it.name,
                    createdAt = it.createdAt,
                    customCoverUri = it.customCoverUri,
                    songCount = 0
                )
            }
        }

    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.getSongsForPlaylist(playlistId).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun createPlaylist(name: String, coverUri: String? = null): Long {
        val entity = PlaylistEntity(
            name = name,
            createdAt = System.currentTimeMillis(),
            customCoverUri = coverUri
        )
        return playlistDao.insertPlaylist(entity)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val existing = playlistDao.getPlaylistByIdSync(playlistId) ?: return
        playlistDao.updatePlaylist(existing.copy(name = trimmed))
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        playlistDao.addSongToEndOfPlaylist(playlistId, songId)
        val playlist = playlistDao.getPlaylistByIdSync(playlistId)
        if (playlist != null && playlist.customCoverUri.isNullOrBlank()) {
            val song = songDao.getSongByIdSync(songId)
            if (song != null && !song.artworkUri.isNullOrBlank()) {
                playlistDao.updatePlaylist(playlist.copy(customCoverUri = song.artworkUri))
            }
        }
    }

    suspend fun addSongObjectToPlaylist(playlistId: Long, song: Song) {
        if (song.isRemoteStream) {
            val existing = songDao.getSongByIdSync(song.id)
            val entity = song.toEntity().copy(
                isFavorite = existing?.isFavorite ?: song.isFavorite,
                playCount = existing?.playCount ?: song.playCount,
                lastPlayedAt = existing?.lastPlayedAt ?: song.lastPlayedAt
            )
            songDao.upsertSongs(listOf(entity))
        }
        addSongToPlaylist(playlistId, song.id)
    }

    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean {
        return playlistDao.isSongInPlaylist(playlistId, songId) > 0
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun reorderPlaylist(playlistId: Long, orderedSongIds: List<Long>) {
        playlistDao.reorderPlaylist(playlistId, orderedSongIds)
    }
}
