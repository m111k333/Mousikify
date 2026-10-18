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
import com.mousikify.MainActivity
import com.mousikify.R
import com.mousikify.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object SpotifyWidgetUpdater {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun updateWidget(
        context: Context,
        song: Song?,
        isPlaying: Boolean
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val componentName = ComponentName(context, SpotifyWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds == null || appWidgetIds.isEmpty()) return

        scope.launch {
            val views = RemoteViews(context.packageName, R.layout.widget_spotify_player)

            if (song != null) {
                views.setTextViewText(R.id.widget_song_title, song.title)
                views.setTextViewText(R.id.widget_artist_name, song.artistName)

                // Favorite button icon
                val favRes = if (song.isFavorite) R.drawable.widget_ic_favorite_filled else R.drawable.widget_ic_favorite_border
                views.setImageViewResource(R.id.widget_btn_favorite, favRes)

                // Try loading artwork bitmap
                val bitmap = loadArtworkBitmap(context, song)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
                }
            } else {
                views.setTextViewText(R.id.widget_song_title, "Mousikify")
                views.setTextViewText(R.id.widget_artist_name, "Local Music")
                views.setImageViewResource(R.id.widget_btn_favorite, R.drawable.widget_ic_favorite_border)
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_launcher_foreground)
            }

            // Play / Pause button icon
            val playPauseRes = if (isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseRes)

            // Setup pending intents for buttons
            setupPendingIntents(context, views)

            // Apply updates to all widget instances
            appWidgetManager.updateAppWidget(appWidgetIds, views)
        }
    }

    private fun setupPendingIntents(context: Context, views: RemoteViews) {
        // Open App on widget click
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)

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
            // First check cached art file
            if (!song.artworkUri.isNullOrBlank()) {
                val file = File(song.artworkUri!!)
                if (file.exists() && file.length() > 0) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    return@withContext BitmapFactory.decodeFile(file.absolutePath, opts)
                }
            }
            // Fallback: extract embedded picture from audio file
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(song.contentUri))
                val bytes = retriever.embeddedPicture
                if (bytes != null) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        null
    }
}
