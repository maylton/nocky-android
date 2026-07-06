/*
 * Nocky Connect pending restore applier.
 *
 * Applies a previously received Desktop snapshot to the Android player as a
 * paused queue. This module intentionally receives PlayerConnection as a
 * dependency so network code never talks to the player directly.
 */

package com.metrolist.music.connect

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as AndroidMediaMetadata
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue

private const val RESTORE_QUEUE_READY_MAX_ATTEMPTS = 20
private const val RESTORE_QUEUE_READY_POLL_MS = 150L

object NockyConnectPendingRestoreApplier {
    fun applyPendingRestorePaused(
        context: Context,
        playerConnection: PlayerConnection,
    ): NockyConnectPendingRestoreSummary {
        val pending = NockyConnectPendingRestoreStore.load(context)
            ?: error("No pending Nocky Connect restore found")
        val summary = pending.toSummary()
        val listQueue = pending.queue.toListQueue()

        playerConnection.service.playQueue(listQueue, playWhenReady = false)
        applyPausedStateWhenQueueReady(
            context = context,
            playerConnection = playerConnection,
            pendingQueue = pending.queue,
            playerState = pending.playerState,
            attempt = 0,
        )
        return summary
    }
}

private fun applyPausedStateWhenQueueReady(
    context: Context,
    playerConnection: PlayerConnection,
    pendingQueue: PersistQueue,
    playerState: PersistPlayerState,
    attempt: Int,
) {
    val player = playerConnection.player
    val expectedFirstId = pendingQueue.items.firstOrNull()?.id
    val hasExpectedQueue = player.mediaItemCount == pendingQueue.items.size &&
        expectedFirstId != null &&
        player.getMediaItemAt(0).mediaId == expectedFirstId

    if (hasExpectedQueue || attempt >= RESTORE_QUEUE_READY_MAX_ATTEMPTS) {
        val safeIndex = pendingQueue.mediaItemIndex.coerceIn(
            0,
            (player.mediaItemCount - 1).coerceAtLeast(0),
        )
        player.repeatMode = playerState.repeatMode
        player.shuffleModeEnabled = playerState.shuffleModeEnabled
        player.volume = playerState.volume.coerceIn(0f, 1f)
        player.playWhenReady = false
        if (player.mediaItemCount > 0) {
            player.seekTo(safeIndex, playerState.currentPosition.coerceAtLeast(0L))
        }
        player.pause()
        NockyConnectPendingRestoreStore.clear(context)
        return
    }

    Handler(Looper.getMainLooper()).postDelayed(
        {
            applyPausedStateWhenQueueReady(
                context = context,
                playerConnection = playerConnection,
                pendingQueue = pendingQueue,
                playerState = playerState,
                attempt = attempt + 1,
            )
        },
        RESTORE_QUEUE_READY_POLL_MS,
    )
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
