/*
 * Nocky Connect pending restore applier.
 *
 * Applies a previously received Desktop snapshot to the Android player as a
 * paused queue. This module intentionally receives PlayerConnection as a
 * dependency so network code never talks to the player directly.
 */

package com.metrolist.music.connect

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as AndroidMediaMetadata
import com.metrolist.music.models.MediaMetadata
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
        playerConnection.playQueue(pending.queue.toListQueue())
        playerConnection.player.repeatMode = pending.playerState.repeatMode
        playerConnection.player.shuffleModeEnabled = pending.playerState.shuffleModeEnabled
        playerConnection.player.volume = pending.playerState.volume.coerceIn(0f, 1f)
        playerConnection.pause()
        playerConnection.seekTo(summary.positionMs)
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

internal fun MediaMetadata.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(
        AndroidMediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artists.joinToString { artist -> artist.name })
            .setAlbumTitle(album?.title)
            .build(),
    )
    .setTag(this)
    .build()
