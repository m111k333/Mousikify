package com.mousikify.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.mousikify.core.media.controller.MusicController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpotifyWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun musicController(): MusicController
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.mousikify.widget.ACTION_PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.mousikify.widget.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.mousikify.widget.ACTION_SKIP_PREV"
        const val ACTION_TOGGLE_FAVORITE = "com.mousikify.widget.ACTION_TOGGLE_FAVORITE"
    }

    private val providerScope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action ?: return

        when (action) {
            ACTION_PLAY_PAUSE, ACTION_SKIP_NEXT, ACTION_SKIP_PREV, ACTION_TOGGLE_FAVORITE -> {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java
                    )
                    val musicController = entryPoint.musicController()

                    when (action) {
                        ACTION_PLAY_PAUSE -> musicController.togglePlayPause()
                        ACTION_SKIP_NEXT -> musicController.skipToNext()
                        ACTION_SKIP_PREV -> musicController.skipToPrevious()
                        ACTION_TOGGLE_FAVORITE -> {
                            val song = musicController.currentSong.value
                            if (song != null) {
                                musicController.toggleFavorite(song)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
            val musicController = entryPoint.musicController()
            SpotifyWidgetUpdater.updateWidget(
                context,
                musicController.currentSong.value,
                musicController.isPlaying.value
            )
        } catch (_: Exception) {
            SpotifyWidgetUpdater.updateWidget(context, null, false)
        }
    }
}
