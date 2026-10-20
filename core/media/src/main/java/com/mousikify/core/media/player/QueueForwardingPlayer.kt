package com.mousikify.core.media.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.mousikify.core.media.queue.QueueManager
import com.mousikify.core.model.Song

@OptIn(UnstableApi::class)
class QueueForwardingPlayer(
    player: Player,
    private val queueManager: QueueManager,
    private val onPlaySong: (Song) -> Unit
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: @Player.Command Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() {
        val next = queueManager.getNextSong()
        if (next != null) {
            onPlaySong(next)
        }
    }

    override fun seekToNextMediaItem() {
        seekToNext()
    }

    override fun seekToPrevious() {
        if (currentPosition > 3000L) {
            seekTo(0L)
        } else {
            val prev = queueManager.getPreviousSong()
            if (prev != null) {
                onPlaySong(prev)
            } else {
                seekTo(0L)
            }
        }
    }

    override fun seekToPreviousMediaItem() {
        seekToPrevious()
    }
}
