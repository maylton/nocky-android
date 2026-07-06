/*
 * Nocky Connect pending restore applier.
 *
 * Applies a previously received Desktop snapshot to the Android player as a
 * paused queue. This module intentionally receives PlayerConnection as a
 * dependency so network code never talks to the player directly.
 */

package com.metrolist.music.connect

import android.content.Context
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.PersistQueue
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue

object NockyConnectPendingRestoreApplier {
    fun applyPendingRestorePaused(
        context: Context,
        playerConnection: PlayerConnection,
    ): NockyConnectPendingRestoreSummary {
        val pending = NockyConnectPendingRestoreStore.load(context)
            ?: error("No pending Nocky Connect restore found")
        val summary = pending.toSummary()
        val safeIndex = pending.queue.mediaItemIndex.coerceIn(
            0,
            (pending.queue.items.size - 1).coerceAtLeast(0),
        )
        val safePosition = pending.playerState.currentPosition.coerceAtLeast(0L)
        val mediaItems = pending.queue.items.map { metadata -> metadata.toMediaItem() }
        require(mediaItems.isNotEmpty()) { "Pending Nocky Connect queue is empty" }

        val player = playerConnection.player
        player.stop()
        player.clearMediaItems()
        player.setMediaItems(mediaItems, safeIndex, safePosition)
        player.repeatMode = pending.playerState.repeatMode
        player.shuffleModeEnabled = pending.playerState.shuffleModeEnabled
        player.volume = pending.playerState.volume.coerceIn(0f, 1f)
        player.playWhenReady = false
        player.prepare()
        player.seekTo(safeIndex, safePosition)
        player.pause()

        NockyConnectPendingRestoreStore.clear(context)
        return summary
    }
}

internal fun PersistQueue.toListQueue(): ListQueue = ListQueue(
    title = title,
    items = items.map { metadata -> metadata.toMediaItem() },
    startIndex = mediaItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
    position = position.coerceAtLeast(0L),
)
