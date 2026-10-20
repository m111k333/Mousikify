package com.mousikify.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.mousikify.MainActivity
import com.mousikify.R
import com.mousikify.core.designsystem.palette.PaletteExtractor
import com.mousikify.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object SpotifyWidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null
    private const val DEFAULT_WIDGET_BG_COLOR = 0xFF242424.toInt()
    private const val MAX_WIDGET_ART_DIMENSION = 256
    private const val OFFLINE_ARTWORK_DIR_NAME = "offline_artwork_cache"

    @Volatile
    private var cachedSongId: Long? = null
    @Volatile
    private var cachedBitmap: Bitmap? = null
    @Volatile
    private var cachedBgColor: Int = DEFAULT_WIDGET_BG_COLOR

    fun updateWidget(
        context: Context,
        song: Song?,
        isPlaying: Boolean
    ) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return
        val componentName = ComponentName(appContext, SpotifyWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds == null || appWidgetIds.isEmpty()) return

        updateJob?.cancel()
        updateJob = scope.launch {
            val views = RemoteViews(appContext.packageName, R.layout.widget_spotify_player)
            var widgetBgColor = DEFAULT_WIDGET_BG_COLOR

            if (song != null) {
                views.setTextViewText(R.id.widget_song_title, song.title)
                views.setTextViewText(R.id.widget_artist_name, song.artistName)

                // Favorite button icon
                val favRes = if (song.isFavorite) R.drawable.widget_ic_favorite_filled else R.drawable.widget_ic_favorite_border
                views.setImageViewResource(R.id.widget_btn_favorite, favRes)

                // Play / Pause button icon
                val playPauseRes = if (isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play
                views.setImageViewResource(R.id.widget_btn_play_pause, playPauseRes)

                setupPendingIntents(appContext, views)

                // Reuse cached bitmap & color if the same song is playing (e.g. play/pause or favorite toggle)
                val existingBitmap = cachedBitmap
                if (cachedSongId == song.id && existingBitmap != null && !existingBitmap.isRecycled) {
                    widgetBgColor = cachedBgColor
                    views.setImageViewBitmap(R.id.widget_album_art, existingBitmap)
                    views.setInt(R.id.widget_background, "setColorFilter", widgetBgColor)
                    pushWidgetUpdate(appWidgetManager, appWidgetIds, views)
                    return@launch
                }

                // Immediately push text & transport controls so widget feels instant while artwork loads
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
                views.setInt(R.id.widget_background, "setColorFilter", widgetBgColor)
                pushWidgetUpdate(appWidgetManager, appWidgetIds, views)

                val bitmap = loadArtworkBitmap(appContext, song)
                coroutineContext.ensureActive()
                if (bitmap != null && !bitmap.isRecycled) {
                    widgetBgColor = getContrastingWidgetBgColor(bitmap)
                    cachedSongId = song.id
                    cachedBitmap = bitmap
                    cachedBgColor = widgetBgColor
                    views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                } else {
                    cachedSongId = song.id
                    cachedBitmap = null
                    cachedBgColor = DEFAULT_WIDGET_BG_COLOR
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
                }
            } else {
                cachedSongId = null
                cachedBitmap = null
                cachedBgColor = DEFAULT_WIDGET_BG_COLOR
                views.setTextViewText(R.id.widget_song_title, "Mousikify")
                views.setTextViewText(R.id.widget_artist_name, "Local Music")
                views.setImageViewResource(R.id.widget_btn_favorite, R.drawable.widget_ic_favorite_border)
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
            }

            // Tint the widget background with dynamic seed color while keeping 16dp rounded corners
            views.setInt(R.id.widget_background, "setColorFilter", widgetBgColor)

            // Play / Pause button icon
            val playPauseRes = if (isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseRes)

            // Setup pending intents for buttons and background
            setupPendingIntents(appContext, views)

            pushWidgetUpdate(appWidgetManager, appWidgetIds, views)
        }
    }

    private fun pushWidgetUpdate(
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        views: RemoteViews
    ) {
        try {
            appWidgetManager.updateAppWidget(appWidgetIds, views)
        } catch (_: Exception) {
            // Fallback if bitmap IPC payload was still too large
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
            try { appWidgetManager.updateAppWidget(appWidgetIds, views) } catch (_: Exception) {}
        }
    }

    private suspend fun getContrastingWidgetBgColor(bitmap: Bitmap): Int {
        return try {
            val seedColor = PaletteExtractor.extractGradientSeedColor(bitmap)
            val lum = seedColor.luminance()

            // Clamp max luminance to 0.12f to guarantee high WCAG contrast (> 7:1) with white/light text
            val targetMaxLum = 0.12f
            if (lum > targetMaxLum) {
                val factor = targetMaxLum / lum
                val r = (seedColor.red * factor).coerceIn(0f, 1f)
                val g = (seedColor.green * factor).coerceIn(0f, 1f)
                val b = (seedColor.blue * factor).coerceIn(0f, 1f)
                Color(r, g, b, 1f).toArgb()
            } else {
                seedColor.toArgb()
            }
        } catch (_: Exception) {
            DEFAULT_WIDGET_BG_COLOR
        }
    }

    private fun setupPendingIntents(context: Context, views: RemoteViews) {
        // Open App on widget click (resumes existing activity/backstack instead of restarting from scratch)
        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_background, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_content, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_song_title, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_artist_name, openAppPendingIntent)

        // Play/Pause intent
        val playPauseIntent = Intent(context, SpotifyWidgetProvider::class.java).apply {
            action = SpotifyWidgetProvider.ACTION_PLAY_PAUSE
        }
        val playPausePending = PendingIntent.getBroadcast(
            context, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePending)

        // Previous track intent
        val prevIntent = Intent(context, SpotifyWidgetProvider::class.java).apply {
            action = SpotifyWidgetProvider.ACTION_SKIP_PREV
        }
        val prevPending = PendingIntent.getBroadcast(
            context, 2, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPending)

        // Next track intent
        val nextIntent = Intent(context, SpotifyWidgetProvider::class.java).apply {
            action = SpotifyWidgetProvider.ACTION_SKIP_NEXT
        }
        val nextPending = PendingIntent.getBroadcast(
            context, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_next, nextPending)

        // Favorite intent
        val favIntent = Intent(context, SpotifyWidgetProvider::class.java).apply {
            action = SpotifyWidgetProvider.ACTION_TOGGLE_FAVORITE
        }
        val favPending = PendingIntent.getBroadcast(
            context, 4, favIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_favorite, favPending)
    }

    private suspend fun loadArtworkBitmap(context: Context, song: Song): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cleanVideoId = song.remoteVideoId?.removePrefix("ytm://")?.substringBefore("?")?.trim()
            val offlineArtDir = File(context.cacheDir, OFFLINE_ARTWORK_DIR_NAME)
            val offlineArtFile = if (!cleanVideoId.isNullOrEmpty()) File(offlineArtDir, "$cleanVideoId.jpg") else null

            val artworkData: Any? = when {
                offlineArtFile != null && offlineArtFile.exists() && offlineArtFile.length() > 0L -> offlineArtFile
                !song.artworkUri.isNullOrBlank() -> song.artworkUri
                song.albumId > 0L -> "content://media/external/audio/albumart/${song.albumId}"
                else -> null
            }

            var bitmap: Bitmap? = if (artworkData != null) {
                val request = ImageRequest.Builder(context)
                    .data(artworkData)
                    .size(MAX_WIDGET_ART_DIMENSION, MAX_WIDGET_ART_DIMENSION)
                    .allowHardware(false)
                    .build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            } else null

            // Fallback for local files: extract embedded picture if Coil didn't resolve artwork
            if (bitmap == null && song.isLocal) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(song.contentUri))
                    retriever.embeddedPicture?.let { bytes ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    }
                } catch (_: Exception) {
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }

            val finalBitmap = bitmap ?: return@withContext null

            // Persist a copy in offline_artwork_cache if this is an in-app remote/downloaded song
            if (offlineArtFile != null && (!offlineArtFile.exists() || offlineArtFile.length() == 0L)) {
                runCatching {
                    offlineArtDir.mkdirs()
                    offlineArtFile.outputStream().use { out ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                }
            }

            finalBitmap
        } catch (_: Exception) {
            null
        }
    }
}
