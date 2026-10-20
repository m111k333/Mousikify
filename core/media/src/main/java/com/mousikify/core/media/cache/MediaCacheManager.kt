package com.mousikify.core.media.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.mousikify.core.database.preferences.PlaybackPreferences
import com.mousikify.core.database.repository.SongRepository
import com.mousikify.core.media.remote.RemoteMusicRepository
import com.mousikify.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a 256 MB SimpleCache with a strict 20-song rolling cache policy for transient streamed
 * tracks, while pinning and downloading 100% of tracks saved to playlists or favorites for offline playback.
 */
@OptIn(UnstableApi::class)
@Singleton
class MediaCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val remoteMusicRepository: RemoteMusicRepository,
    private val playbackPreferences: PlaybackPreferences,
    private val songRepository: SongRepository
) {
    companion object {
        private const val TAG = "MediaCacheManager"
        private const val LEGACY_CACHE_DIR_NAME = "mousikify_audio_cache"
        private const val CACHE_DIR_NAME = "mousikify_audio_cache_v2"
        const val OFFLINE_ARTWORK_DIR_NAME = "offline_artwork_cache"
        const val MAX_CACHE_BYTES = 256L * 1024L * 1024L // 256 MB disk ceiling
        const val MAX_RECENT_CACHED_SONGS = 20           // Rolling 20-song cache window
        const val PREFETCH_Check_BYTES = 256L * 1024L    // Consider stream cached if >= 256 KB buffered
    }

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = ConcurrentHashMap<String, Boolean>()

    private val _cacheUpdateTick = MutableStateFlow(0L)
    val cacheUpdateTick: StateFlow<Long> = _cacheUpdateTick.asStateFlow()

    private val databaseProvider = StandaloneDatabaseProvider(context)
    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
    private val appMusicDir = (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        ?: File(context.filesDir, "Music")).apply { mkdirs() }
    private val offlineArtworkDir = File(context.filesDir, "offline_artwork").apply { mkdirs() }
    private val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)

    val simpleCache: SimpleCache = SimpleCache(cacheDir, evictor, databaseProvider)

    init {
        cacheScope.launch {
            runCatching {
                val legacyDir = File(context.cacheDir, LEGACY_CACHE_DIR_NAME)
                if (legacyDir.exists()) {
                    legacyDir.deleteRecursively()
                }
                val legacyArtworkDir = File(context.cacheDir, OFFLINE_ARTWORK_DIR_NAME)
                if (legacyArtworkDir.exists()) {
                    legacyArtworkDir.listFiles()?.forEach { file ->
                        val dest = File(offlineArtworkDir, file.name)
                        if (!dest.exists() || dest.length() == 0L) {
                            file.copyTo(dest, overwrite = true)
                        }
                    }
                }
            }
            pruneOrphanedCacheEntries()
        }
    }

    private val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

    val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(simpleCache)
        .setUpstreamDataSourceFactory(okHttpDataSourceFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private val hybridUpstreamFactory = DefaultDataSource.Factory(context, cacheDataSourceFactory)

    val resolvingDataSourceFactory: DataSource.Factory = ResolvingDataSource.Factory(
        hybridUpstreamFactory,
        object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri
                if (!uri.scheme.equals("ytm", ignoreCase = true)) {
                    // Local file://, content://, or already-resolved https:// URI -> pass through
                    return dataSpec
                }

                val videoId = (uri.host ?: uri.schemeSpecificPart.removePrefix("//"))
                    .substringBefore("?")
                    .trim()

                if (videoId.isEmpty()) {
                    throw java.io.IOException("Invalid ytm:// URI: $uri")
                }

                // First check: is this track already downloaded to the phone's music folder?
                val localFile = getDownloadedMusicFile(videoId)
                if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                    Log.i(TAG, "Playing $videoId directly from local music folder: ${localFile.absolutePath}")
                    return dataSpec.buildUpon()
                        .setUri(Uri.fromFile(localFile))
                        .setKey(videoId)
                        .build()
                }

                val manifest = try {
                    runBlocking {
                        remoteMusicRepository.resolveStreamManifest(videoId).getOrThrow()
                    }
                } catch (e: Throwable) {
                    // If the device is offline, serve directly from SimpleCache if cached chunks exist!
                    if (isTrackCached(videoId)) {
                        Log.i(TAG, "Offline mode: serving $videoId directly from SimpleCache")
                        return dataSpec.buildUpon()
                            .setUri(Uri.parse("https://offline.googlevideo.com/videoplayback?id=$videoId"))
                            .setKey(videoId)
                            .build()
                    }
                    Log.e(TAG, "Failed to resolve stream for $videoId", e)
                    throw if (e is java.io.IOException) e else java.io.IOException("Failed to resolve stream for $videoId: ${e.message}", e)
                }

                val headers = HashMap<String, String>(dataSpec.httpRequestHeaders)
                headers["User-Agent"] = manifest.userAgent

                return dataSpec.buildUpon()
                    .setUri(Uri.parse(manifest.streamUrl))
                    .setKey(videoId) // Critical: binds SimpleCache chunks to the permanent videoId
                    .setHttpRequestHeaders(headers)
                    .build()
            }
        }
    )

    fun getDownloadedMusicFile(videoId: String): File? {
        val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
        if (cleanId.isEmpty()) return null
        val m4a = File(appMusicDir, "$cleanId.m4a")
        if (m4a.exists() && m4a.length() > 0L) return m4a
        val opus = File(appMusicDir, "$cleanId.opus")
        if (opus.exists() && opus.length() > 0L) return opus
        val mp3 = File(appMusicDir, "$cleanId.mp3")
        if (mp3.exists() && mp3.length() > 0L) return mp3
        val webm = File(appMusicDir, "$cleanId.webm")
        if (webm.exists() && webm.length() > 0L) return webm
        return null
    }

    fun getOfflineArtworkFile(videoId: String): File? {
        val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
        if (cleanId.isEmpty()) return null
        val primary = File(offlineArtworkDir, "$cleanId.jpg")
        if (primary.exists() && primary.length() > 0L) return primary
        val legacy = File(File(context.cacheDir, OFFLINE_ARTWORK_DIR_NAME), "$cleanId.jpg")
        if (legacy.exists() && legacy.length() > 0L) return legacy
        return null
    }

    fun resolveArtworkUri(song: Song): Uri {
        val art = song.artworkUri
        if (!art.isNullOrBlank()) {
            if (art.startsWith("http://", ignoreCase = true) ||
                art.startsWith("https://", ignoreCase = true) ||
                art.startsWith("content://", ignoreCase = true)
            ) {
                return Uri.parse(art)
            }
        }
        if (song.albumId > 0L) {
            return Uri.parse("content://media/external/audio/albumart/${song.albumId}")
        }
        return Uri.EMPTY
    }

    private val artworkBytesCache = android.util.LruCache<Long, ByteArray>(32)

    fun getArtworkBytes(song: Song): ByteArray? {
        artworkBytesCache.get(song.id)?.let { return it }
        return runCatching {
            val file = song.remoteVideoId?.let { getOfflineArtworkFile(it) }
                ?: song.artworkUri?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }

            val bitmap: Bitmap? = if (file != null) {
                decodeScaledBitmap(file.absolutePath)
            } else if (song.albumId > 0L) {
                val contentUri = Uri.parse("content://media/external/audio/albumart/${song.albumId}")
                context.contentResolver.openInputStream(contentUri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }?.let { scaleBitmap(it) }
            } else {
                null
            }

            bitmap?.let { bmp ->
                val stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val bytes = stream.toByteArray()
                artworkBytesCache.put(song.id, bytes)
                bytes
            }
        }.getOrNull()
    }

    private fun decodeScaledBitmap(filePath: String, maxDim: Int = 300): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return null

        var inSampleSize = 1
        val maxActual = maxOf(width, height)
        while ((maxActual / inSampleSize) > maxDim * 2) {
            inSampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val decoded = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null
        return scaleBitmap(decoded, maxDim)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int = 300): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun cacheArtworkBytesIfNeeded(cleanId: String, artworkUrl: String?) {
        if (cleanId.isEmpty() || artworkUrl.isNullOrBlank()) return
        if (!artworkUrl.startsWith("http://", ignoreCase = true) && !artworkUrl.startsWith("https://", ignoreCase = true)) return
        val targetFile = File(offlineArtworkDir, "$cleanId.jpg")
        if (targetFile.exists() && targetFile.length() > 0L) return

        runCatching {
            offlineArtworkDir.mkdirs()
            val request = okhttp3.Request.Builder().url(artworkUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val bytes = response.body?.bytes() ?: return
                if (bytes.isEmpty()) return
                val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                val maxDim = 384
                val w = decoded.width
                val h = decoded.height
                val toSave = if (w > maxDim || h > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(w, h).toFloat()
                    val scaled = android.graphics.Bitmap.createScaledBitmap(
                        decoded,
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    if (scaled !== decoded) decoded.recycle()
                    scaled
                } else {
                    decoded
                }
                targetFile.outputStream().use { out ->
                    toSave.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                }
                toSave.recycle()
            }
        }
    }

    /**
     * Records that a remote track was played, keeping the last 20 played songs in cache
     * and evicting any older transient or orphaned songs that are not saved in a playlist or favorited.
     */
    fun onRemoteTrackPlayed(videoId: String, artworkUrl: String? = null) {
        val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
        if (cleanId.isEmpty()) return
        cacheScope.launch {
            runCatching {
                cacheArtworkBytesIfNeeded(cleanId, artworkUrl)
                val evictedIds = playbackPreferences.recordPlayedVideoIdAndGetEvicted(
                    videoId = cleanId,
                    maxCachedSongs = MAX_RECENT_CACHED_SONGS
                )
                val pinnedIds = songRepository.getPinnedRemoteVideoIds()
                for (evictedId in evictedIds) {
                    if (evictedId !in pinnedIds) {
                        evictTrackFromCache(evictedId)
                    }
                }
                pruneOrphanedCacheEntries(keepVideoId = cleanId)
                _cacheUpdateTick.value = System.currentTimeMillis()
            }
        }
    }

    /**
     * Reconciles all keys currently stored on disk in SimpleCache against:
     * 1. The rolling 20-song played history (recentCachedVideoIds)
     * 2. Songs saved to playlists or favorites (pinnedIds)
     * 3. Any currently active download or playing/prefetched track
     * Immediately removes any orphaned or removed playlist cache entries so storage never bloats.
     */
    fun pruneOrphanedCacheEntries(keepVideoId: String? = null) {
        cacheScope.launch {
            runCatching {
                val recentIds = playbackPreferences.recentCachedVideoIds.first().take(MAX_RECENT_CACHED_SONGS).toSet()
                val pinnedIds = songRepository.getPinnedRemoteVideoIds()
                val cleanKeep = keepVideoId?.removePrefix("ytm://")?.substringBefore("?")?.trim()
                val cachedKeysSnapshot = simpleCache.keys.toSet()

                for (key in cachedKeysSnapshot) {
                    val isAllowed = key in recentIds ||
                            key in pinnedIds ||
                            activeDownloads.containsKey(key) ||
                            (cleanKeep != null && key == cleanKeep)
                    if (!isAllowed) {
                        evictTrackFromCache(key)
                    }
                }

                // Also prune any orphaned offline artwork files
                offlineArtworkDir.listFiles()?.forEach { artFile ->
                    val key = artFile.nameWithoutExtension
                    val isAllowed = key in recentIds ||
                            key in pinnedIds ||
                            activeDownloads.containsKey(key) ||
                            (cleanKeep != null && key == cleanKeep)
                    if (!isAllowed) {
                        runCatching { artFile.delete() }
                    }
                }

                // Also prune any orphaned audio files in appMusicDir
                appMusicDir.listFiles()?.forEach { musicFile ->
                    val key = musicFile.nameWithoutExtension
                    val isAllowed = key in recentIds ||
                            key in pinnedIds ||
                            activeDownloads.containsKey(key) ||
                            (cleanKeep != null && key == cleanKeep)
                    if (!isAllowed) {
                        runCatching { musicFile.delete() }
                    }
                }

                _cacheUpdateTick.value = System.currentTimeMillis()
            }
        }
    }

    /**
     * Removes a track's audio chunks from SimpleCache and clears its in-memory URL manifest.
     */
    fun evictTrackFromCache(videoId: String) {
        val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
        if (cleanId.isEmpty()) return
        runCatching {
            simpleCache.removeResource(cleanId)
            File(offlineArtworkDir, "$cleanId.jpg").delete()
            getDownloadedMusicFile(cleanId)?.delete()
            remoteMusicRepository.invalidateStreamUrl(cleanId)
            Log.d(TAG, "Evicted track $cleanId")
        }
    }

    /**
     * Downloads 100% of a remote track's audio stream and its album cover into the app's phone music
     * folder in the background with embedded metadata (Title, Artist, Album, Cover Art) so that songs
     * saved to a playlist work completely offline.
     */
    fun downloadFullTrackForOffline(song: Song) {
        val videoId = song.remoteVideoId ?: return
        downloadFullTrackForOffline(videoId, song.artworkUri, song)
    }

    fun downloadFullTrackForOffline(
        videoId: String,
        artworkUrl: String? = null,
        song: Song? = null
    ) {
        val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
        if (cleanId.isEmpty()) return
        if (activeDownloads.putIfAbsent(cleanId, true) != null) return

        cacheScope.launch {
            try {
                // 1. Download and persist high-res artwork to app's offline artwork folder
                cacheArtworkBytesIfNeeded(cleanId, artworkUrl)
                val artworkFile = getOfflineArtworkFile(cleanId)
                if (artworkFile != null && song != null) {
                    try {
                        songRepository.updateArtworkUri(song.id, artworkFile.absolutePath)
                    } catch (_: Exception) {}
                }
                _cacheUpdateTick.value = System.currentTimeMillis()

                // 2. Resolve stream manifest
                val manifest = remoteMusicRepository.resolveStreamManifest(cleanId).getOrNull()
                    ?: return@launch

                val ext = when {
                    manifest.mimeType.contains("mp4", ignoreCase = true) || manifest.mimeType.contains("m4a", ignoreCase = true) -> "m4a"
                    manifest.mimeType.contains("webm", ignoreCase = true) || manifest.mimeType.contains("opus", ignoreCase = true) -> "opus"
                    manifest.mimeType.contains("mp3", ignoreCase = true) -> "mp3"
                    else -> "m4a"
                }

                // 3. Download the actual audio stream into the app's phone music folder
                val targetFile = File(appMusicDir, "$cleanId.$ext")
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    val tempFile = File(appMusicDir, "$cleanId.tmp")
                    val request = okhttp3.Request.Builder()
                        .url(manifest.streamUrl)
                        .header("User-Agent", manifest.userAgent)
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw java.io.IOException("Stream download failed: ${response.code}")
                        response.body?.byteStream()?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    if (tempFile.length() > 0L) {
                        tempFile.renameTo(targetFile)
                    } else {
                        tempFile.delete()
                    }
                }

                // 4. Tag the downloaded audio file with metadata (Title, Artist, Album, Cover Art)
                if (targetFile.exists() && targetFile.length() > 0L && song != null) {
                    try {
                        val audioFile = AudioFileIO.read(targetFile)
                        val tag = audioFile.tagOrCreateAndSetDefault
                        tag.setField(FieldKey.TITLE, song.title)
                        tag.setField(FieldKey.ARTIST, song.artistName)
                        tag.setField(FieldKey.ALBUM, song.albumName)
                        if (artworkFile != null && artworkFile.exists()) {
                            try {
                                val artwork = ArtworkFactory.createArtworkFromFile(artworkFile)
                                tag.setField(artwork)
                            } catch (_: Exception) {}
                        }
                        audioFile.commit()
                    } catch (_: Exception) {}
                }

                // 5. Populate SimpleCache chunks
                runCatching {
                    val dataSpec = DataSpec.Builder()
                        .setUri(Uri.parse(manifest.streamUrl))
                        .setPosition(0L)
                        .setLength(C.LENGTH_UNSET.toLong())
                        .setKey(cleanId)
                        .setHttpRequestHeaders(mapOf("User-Agent" to manifest.userAgent))
                        .build()

                    val cacheDataSource = cacheDataSourceFactory.createDataSource()
                    val writer = CacheWriter(
                        cacheDataSource,
                        dataSpec,
                        null,
                        null
                    )
                    writer.cache()
                }

                Log.i(TAG, "Offline download and tagging complete for $cleanId in ${targetFile.absolutePath}")
                _cacheUpdateTick.value = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.w(TAG, "Offline download failed for $cleanId: ${e.message}")
            } finally {
                activeDownloads.remove(cleanId)
            }
        }
    }

    /**
     * Checks whether a remote YouTube videoId has been completely downloaded to the app's local music folder.
     */
    fun isDownloadedLocally(videoId: String): Boolean {
        val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
        if (cleanId.isEmpty()) return false
        val localFile = getDownloadedMusicFile(cleanId)
        return localFile != null && localFile.exists() && localFile.length() > 0L
    }

    /**
     * Checks whether a remote YouTube videoId has audio chunks or downloaded files on disk.
     */
    fun isTrackCached(videoId: String, minBytes: Long = PREFETCH_Check_BYTES): Boolean {
        val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
        if (cleanId.isEmpty()) return false
        val localFile = getDownloadedMusicFile(cleanId)
        if (localFile != null && localFile.exists() && localFile.length() > 0L) {
            return true
        }
        return try {
            simpleCache.getCachedBytes(cleanId, 0L, minBytes) >= minBytes
        } catch (_: Exception) {
            false
        }
    }

    fun getCachedBytes(videoId: String, lengthBytes: Long = 2L * 1024L * 1024L): Long {
        val cleanId = videoId.removePrefix("ytm://").substringBefore("?").trim()
        if (cleanId.isEmpty()) return 0L
        return try {
            simpleCache.getCachedBytes(cleanId, 0L, lengthBytes)
        } catch (_: Exception) {
            0L
        }
    }
}
