package com.mousikify.core.media.recommendation

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight, 100% open-source on-device similar artist discovery service.
 *
 * Primary provider: Deezer Public Related Artists API (Keyless open REST, fast & accurate)
 * Supports multi-degree similarity expansion to discover artists outside the user's immediate circle.
 */
@Singleton
class SimilarArtistDiscoveryService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "SimilarArtistDiscovery"
        private const val DEEZER_SEARCH_BASE = "https://api.deezer.com/search/artist"
    }

    private val fastHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Resolves 3 to 10 similar artists for a given seed artist using Deezer open data.
     * Guaranteed non-blocking and safe against network drops.
     */
    suspend fun findSimilarArtists(artistName: String, limit: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val trimmed = artistName.trim()
        if (trimmed.isEmpty() || trimmed.equals("<unknown>", ignoreCase = true) || trimmed.equals("YouTube Music", ignoreCase = true)) {
            return@withContext emptyList()
        }

        fetchFromDeezer(trimmed, limit)
    }

    /**
     * Resolves similar artists with avatar pictures and fan count for rich UI carousels.
     */
    suspend fun findSimilarArtistsDetailed(artistName: String, limit: Int = 10): List<com.mousikify.core.model.Artist> = withContext(Dispatchers.IO) {
        val trimmed = artistName.trim()
        if (trimmed.isEmpty() || trimmed.equals("<unknown>", ignoreCase = true) || trimmed.equals("YouTube Music", ignoreCase = true)) {
            return@withContext emptyList()
        }
        fetchDetailedFromDeezer(trimmed, limit)
    }

    /**
     * Expands discovery to 2nd-degree similar artists (related artists of candidate artists)
     * to ensure a wide and diverse pool of artists the user does not yet know.
     */
    suspend fun expandSimilarArtists(
        candidateArtists: List<String>,
        limitPerArtist: Int = 5,
        maxTotal: Int = 20
    ): List<String> = withContext(Dispatchers.IO) {
        if (candidateArtists.isEmpty()) return@withContext emptyList()

        coroutineScope {
            val deferredList = candidateArtists.take(4).map { artist ->
                async {
                    fetchFromDeezer(artist, limitPerArtist)
                }
            }
            deferredList.flatMap { it.await() }
                .distinctBy { it.lowercase() }
                .take(maxTotal)
        }
    }

    private fun fetchFromDeezer(artistName: String, limit: Int): List<String> {
        return runCatching {
            val searchUrl = "$DEEZER_SEARCH_BASE?q=${Uri.encode(artistName)}"
            val searchRequest = Request.Builder().url(searchUrl).build()

            val artistId = fastHttpClient.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data == null || data.length() == 0) return emptyList()

                // Find exact match if possible, otherwise use top result
                var bestId = data.getJSONObject(0).optLong("id", -1L)
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "")
                    if (name.equals(artistName, ignoreCase = true)) {
                        bestId = obj.optLong("id", -1L)
                        break
                    }
                }
                bestId
            }

            if (artistId <= 0L) return emptyList()

            val relatedUrl = "https://api.deezer.com/artist/$artistId/related"
            val relatedRequest = Request.Builder().url(relatedUrl).build()

            fastHttpClient.newCall(relatedRequest).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return emptyList()
                val results = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    if (name.isNotEmpty() && !name.equals(artistName, ignoreCase = true) && !name.equals("<unknown>", ignoreCase = true)) {
                        results.add(name)
                        if (results.size >= limit) break
                    }
                }
                results
            }
        }.getOrElse { e ->
            Log.d(TAG, "Deezer related artist lookup failed for '$artistName': ${e.message}")
            emptyList()
        }
    }

    private fun fetchDetailedFromDeezer(artistName: String, limit: Int): List<com.mousikify.core.model.Artist> {
        return runCatching {
            val searchUrl = "$DEEZER_SEARCH_BASE?q=${Uri.encode(artistName)}"
            val searchRequest = Request.Builder().url(searchUrl).build()

            val artistId = fastHttpClient.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                if (data == null || data.length() == 0) return emptyList()

                var bestId = data.getJSONObject(0).optLong("id", -1L)
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "")
                    if (name.equals(artistName, ignoreCase = true)) {
                        bestId = obj.optLong("id", -1L)
                        break
                    }
                }
                bestId
            }

            if (artistId <= 0L) return emptyList()

            val relatedUrl = "https://api.deezer.com/artist/$artistId/related"
            val relatedRequest = Request.Builder().url(relatedUrl).build()

            fastHttpClient.newCall(relatedRequest).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return emptyList()
                val results = mutableListOf<com.mousikify.core.model.Artist>()
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val name = obj.optString("name", "").trim()
                    val pic = obj.optString("picture_medium", "").ifBlank {
                        obj.optString("picture_big", "")
                    }
                    val nbFan = obj.optInt("nb_fan", 0)
                    val fansText = if (nbFan > 0) {
                        if (nbFan >= 1_000_000) String.format("%.1fM fans", nbFan / 1_000_000.0)
                        else if (nbFan >= 1_000) String.format("%.1fK fans", nbFan / 1000.0)
                        else "$nbFan fans"
                    } else "Artist"
                    if (name.isNotEmpty() && !name.equals(artistName, ignoreCase = true) && !name.equals("<unknown>", ignoreCase = true)) {
                        results.add(
                            com.mousikify.core.model.Artist(
                                id = -id,
                                name = name,
                                avatarUrl = pic.ifBlank { null },
                                monthlyListeners = fansText
                            )
                        )
                        if (results.size >= limit) break
                    }
                }
                results
            }
        }.getOrElse { e ->
            Log.d(TAG, "Deezer detailed related artist lookup failed for '$artistName': ${e.message}")
            emptyList()
        }
    }
}
