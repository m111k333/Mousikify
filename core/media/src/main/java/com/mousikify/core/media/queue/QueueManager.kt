package com.mousikify.core.media.queue

import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.model.RepeatMode
import com.mousikify.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class QueueManager @Inject constructor(
    private val songRepository: SongRepository,
    private val preferences: PlaybackPreferences
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _originalQueue = mutableListOf<Song>()
    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    init {
        scope.launch {
            _isShuffleEnabled.value = preferences.shuffleEnabled.first()
            _repeatMode.value = preferences.repeatMode.first()
            restoreLastSession()
        }
    }

    private suspend fun restoreLastSession() {
        val lastSongId = preferences.lastPlayedSongId.first() ?: return
        val savedQueueIds = preferences.currentQueueIds.first()
        val savedIndex = preferences.currentQueueIndex.first()

        val allSongs = songRepository.allSongs.first().associateBy { it.id }
        if (savedQueueIds.isNotEmpty()) {
            val restoredList = savedQueueIds.mapNotNull { allSongs[it] }
            if (restoredList.isNotEmpty()) {
                _originalQueue.clear()
                _originalQueue.addAll(restoredList)
                _currentQueue.value = restoredList
                val index = savedIndex.coerceIn(0, restoredList.size - 1)
                _currentIndex.value = index
                _currentSong.value = restoredList[index]
                return
            }
        }

        // Fallback: restore just the single song if queue was not saved
        allSongs[lastSongId]?.let { song ->
            _originalQueue.clear()
            _originalQueue.add(song)
            _currentQueue.value = listOf(song)
            _currentIndex.value = 0
            _currentSong.value = song
        }
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0, autoPlay: Boolean = true): Song? {
        if (songs.isEmpty()) {
            _originalQueue.clear()
            _currentQueue.value = emptyList()
            _currentIndex.value = -1
            _currentSong.value = null
            return null
        }

        _originalQueue.clear()
        _originalQueue.addAll(songs)

        val targetSong = songs.getOrNull(startIndex) ?: songs.first()

        if (_isShuffleEnabled.value) {
            val shuffled = songs.toMutableList()
            // Keep targetSong at the first position in shuffled queue
            val targetIdx = shuffled.indexOfFirst { it.id == targetSong.id }
            if (targetIdx >= 0) {
                shuffled.removeAt(targetIdx)
            }
            fisherYatesShuffle(shuffled)
            shuffled.add(0, targetSong)
            _currentQueue.value = shuffled
            _currentIndex.value = 0
        } else {
            _currentQueue.value = songs
            _currentIndex.value = startIndex.coerceIn(0, songs.size - 1)
        }

        _currentSong.value = targetSong
        persistQueueState()
        return targetSong
    }

    fun playSongAt(index: Int): Song? {
        val queue = _currentQueue.value
        if (index !in queue.indices) return null
        _currentIndex.value = index
        val song = queue[index]
        _currentSong.value = song
        persistQueueState()
        return song
    }

    fun getNextSong(): Song? {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return null

        if (_repeatMode.value == RepeatMode.ONE) {
            return _currentSong.value
        }

        val nextIndex = _currentIndex.value + 1
        return if (nextIndex < queue.size) {
            _currentIndex.value = nextIndex
            val song = queue[nextIndex]
            _currentSong.value = song
            persistQueueState()
            song
        } else if (_repeatMode.value == RepeatMode.ALL) {
            _currentIndex.value = 0
            val song = queue[0]
            _currentSong.value = song
            persistQueueState()
            song
        } else {
            null
        }
    }

    fun peekNextSong(): Song? {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return null
        if (_repeatMode.value == RepeatMode.ONE) return _currentSong.value
        val nextIndex = _currentIndex.value + 1
        return if (nextIndex < queue.size) {
            queue[nextIndex]
        } else if (_repeatMode.value == RepeatMode.ALL) {
            queue.firstOrNull()
        } else {
            null
        }
    }

    fun getPreviousSong(): Song? {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return null

        val prevIndex = _currentIndex.value - 1
        return if (prevIndex >= 0) {
            _currentIndex.value = prevIndex
            val song = queue[prevIndex]
            _currentSong.value = song
            persistQueueState()
            song
        } else if (_repeatMode.value == RepeatMode.ALL) {
            val lastIdx = queue.size - 1
            _currentIndex.value = lastIdx
            val song = queue[lastIdx]
            _currentSong.value = song
            persistQueueState()
            song
        } else {
            null
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        if (_isShuffleEnabled.value == enabled) return
        toggleShuffle()
    }

    fun toggleShuffle(): Boolean {
        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle
        val current = _currentSong.value

        if (newShuffle) {
            val list = _originalQueue.toMutableList()
            if (current != null) {
                val idx = list.indexOfFirst { it.id == current.id }
                if (idx >= 0) {
                    list.removeAt(idx)
                }
                fisherYatesShuffle(list)
                list.add(0, current)
                _currentIndex.value = 0
            } else {
                fisherYatesShuffle(list)
                _currentIndex.value = 0
            }
            _currentQueue.value = list
        } else {
            // Restore original queue order
            _currentQueue.value = ArrayList(_originalQueue)
            if (current != null) {
                val index = _originalQueue.indexOfFirst { it.id == current.id }
                _currentIndex.value = if (index >= 0) index else 0
            }
        }

        scope.launch { preferences.setShuffleEnabled(newShuffle) }
        persistQueueState()
        return newShuffle
    }

    fun cycleRepeatMode(): RepeatMode {
        val nextMode = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = nextMode
        scope.launch { preferences.setRepeatMode(nextMode) }
        return nextMode
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val list = _currentQueue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _currentQueue.value = list

            val current = _currentSong.value
            if (current != null) {
                _currentIndex.value = list.indexOfFirst { it.id == current.id }
            }
            persistQueueState()
        }
    }

    fun updateFavorite(songId: Long, isFavorite: Boolean) {
        _currentSong.value?.let { current ->
            if (current.id == songId) {
                _currentSong.value = current.copy(isFavorite = isFavorite)
            }
        }
        val updatedList = _currentQueue.value.map { song ->
            if (song.id == songId) song.copy(isFavorite = isFavorite) else song
        }
        _currentQueue.value = updatedList
        for (i in _originalQueue.indices) {
            if (_originalQueue[i].id == songId) {
                _originalQueue[i] = _originalQueue[i].copy(isFavorite = isFavorite)
            }
        }
    }

    private fun persistQueueState() {
        val ids = _currentQueue.value.map { it.id }
        val idx = _currentIndex.value
        val songId = _currentSong.value?.id
        scope.launch {
            preferences.saveQueue(ids, idx)
            if (songId != null) {
                preferences.saveLastPlaybackState(songId, 0L)
            }
        }
    }

    private fun <T> fisherYatesShuffle(list: MutableList<T>) {
        for (i in list.indices.reversed()) {
            val j = Random.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }
    }
}
