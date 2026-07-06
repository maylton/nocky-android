/*
 * Nocky Connect pending restore persistence.
 *
 * The handoff receiver prepares a paused restore plan first and stores it as a
 * local pending restore. Applying it to the active player is intentionally a
 * separate step so LAN transfer can remain safe and reversible.
 */

package com.metrolist.music.connect

import android.content.Context
import java.io.File
import java.io.ObjectOutputStream

private const val PENDING_RESTORE_DIR = "nocky-connect"
private const val PENDING_SNAPSHOT_FILE = "pending-restore-snapshot.json"
private const val PENDING_QUEUE_FILE = "pending-restore-queue.bin"
private const val PENDING_PLAYER_STATE_FILE = "pending-restore-player-state.bin"

data class NockyConnectPendingRestoreSummary(
    val title: String,
    val itemCount: Int,
    val currentIndex: Int,
    val positionMs: Long,
)

object NockyConnectPendingRestoreStore {
    fun save(
        context: Context,
        snapshot: PlaybackSessionSnapshot,
        restorePlan: NockyConnectRestorePlan,
    ): NockyConnectPendingRestoreSummary = saveToDirectory(
        directory = File(context.filesDir, PENDING_RESTORE_DIR),
        snapshot = snapshot,
        restorePlan = restorePlan,
    )

    internal fun saveToDirectory(
        directory: File,
        snapshot: PlaybackSessionSnapshot,
        restorePlan: NockyConnectRestorePlan,
    ): NockyConnectPendingRestoreSummary {
        directory.mkdirs()
        require(directory.isDirectory) { "Could not create Nocky Connect restore directory" }

        File(directory, PENDING_SNAPSHOT_FILE).writeText(NockyConnectJson.encode(snapshot))
        ObjectOutputStream(File(directory, PENDING_QUEUE_FILE).outputStream()).use { output ->
            output.writeObject(restorePlan.queue)
        }
        ObjectOutputStream(File(directory, PENDING_PLAYER_STATE_FILE).outputStream()).use { output ->
            output.writeObject(restorePlan.playerState)
        }

        val safeIndex = restorePlan.queue.mediaItemIndex.coerceIn(
            0,
            (restorePlan.queue.items.size - 1).coerceAtLeast(0),
        )
        val title = restorePlan.queue.items
            .getOrNull(safeIndex)
            ?.title
            ?: restorePlan.queue.title
            ?: "queue"

        return NockyConnectPendingRestoreSummary(
            title = title,
            itemCount = restorePlan.queue.items.size,
            currentIndex = safeIndex,
            positionMs = restorePlan.playerState.currentPosition.coerceAtLeast(0L),
        )
    }
}
