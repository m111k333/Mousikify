package com.mousikify.core.media.remote

import android.net.Uri
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.mousikify.core.model.Song
import com.mousikify.core.model.toRemoteSongId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Keyless Paging 3 PagingSource that queries YouTube Music Songs (and fallback Videos)
 * directly on-device via NewPipeExtractor with infinite scroll support.
 */
class YouTubeMusicSearchPagingSource(
    private val query: String,
    private val contentFilter: String = YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
) : PagingSource<Page, Song>() {

    private var currentExtractor: SearchExtractor? = null

    override fun getRefreshKey(state: PagingState<Page, Song>): Page? = null

    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, Song> =
        withContext(Dispatchers.IO) {
            try {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) {
                    return@withContext LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
                }

                val ytService = ServiceList.YouTube
                val pageToLoad = params.key

                val infoPage = if (pageToLoad == null) {
                    val queryHandler = ytService.searchQHFactory.fromQuery(
                        trimmed,
                        listOf(contentFilter),
                        ""
                    )
                    val extractor = ytService.getSearchExtractor(queryHandler).also {
                        it.fetchPage()
                        currentExtractor = it
                    }
                    extractor.initialPage
                } else {
                    val extractor = currentExtractor
                        ?: return@withContext LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
                    extractor.getPage(pageToLoad)
                }

                val tracks = infoPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { item -> item.toSong() }

                val nextPage = if (infoPage.hasNextPage()) infoPage.nextPage else null

                LoadResult.Page(
                    data = tracks,
                    prevKey = null,
                    nextKey = nextPage
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

    companion object {
        fun StreamInfoItem.toSong(): Song? {
            val videoId = extractVideoId(url) ?: return null
            val rawThumb = thumbnails.maxByOrNull { it.height * it.width }?.url
            val highResThumb = upgradeThumbnailUrl(rawThumb, videoId)

            val rawArtist = uploaderName?.trim().orEmpty()
            val cleanArtist = rawArtist
                .removeSuffix(" - Topic")
                .ifBlank { "YouTube Music" }

            return Song(
                id = videoId.toRemoteSongId(),
                contentUri = "ytm://$videoId",
                title = name?.trim()?.ifBlank { "Unknown Track" } ?: "Unknown Track",
                artistId = cleanArtist.lowercase().hashCode().toLong(),
                artistName = cleanArtist,
                albumId = -1L,
                albumName = "YouTube Music",
                durationMs = if (duration > 0) duration * 1000L else 0L,
                trackNumber = 0,
                year = 0,
                folderPath = "YouTube Music Stream",
                dateAdded = System.currentTimeMillis() / 1000L,
                dateModified = System.currentTimeMillis() / 1000L,
                isFavorite = false,
                artworkUri = highResThumb
            )
        }

        fun extractVideoId(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val parsed = runCatching { Uri.parse(url) }.getOrNull()
            val vParam = parsed?.getQueryParameter("v")
            if (!vParam.isNullOrBlank()) return vParam

            val host = parsed?.host?.lowercase().orEmpty()
            if (host.contains("youtu.be")) {
                return parsed?.lastPathSegment?.takeIf { it.isNotBlank() }
            }
            val afterWatch = url.substringAfter("watch?v=", "").substringBefore("&")
            if (afterWatch.isNotBlank()) return afterWatch
            return null
        }

        fun upgradeThumbnailUrl(rawUrl: String?, videoId: String): String {
            if (rawUrl.isNullOrBlank()) {
                return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
            return rawUrl
                .replace("w60-h60", "w544-h544")
                .replace("w120-h120", "w544-h544")
                .replace("hqdefault.jpg", "maxresdefault.jpg")
        }
    }
}
