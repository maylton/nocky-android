/*
 * Nocky Connect pending restore persistence.
 *
 * The handoff receiver prepares a paused restore plan first and stores it as a
 * local pending restore. Applying it to the active player is intentionally a
 * separate step so LAN transfer can remain safe and reversible.
 */

package com.metrolist.music.connect

import android.content.Context
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

private const val PENDING_RESTORE_DIR = "nocky-connect"
private const val PENDING_SNAPSHOT_FILE = "pending-restore-snapshot.json"
private const val PENDING_QUEUE_FILE = "pending-restore-queue.bin"
private const val PENDING_PLAYER_STATE_FILE = "pending-restore-player-state.bin"

data class NockyConnectPendingRestore(
    val snapshot: PlaybackSessionSnapshot,
    val queue: PersistQueue,
    val playerState: PersistPlayerState,
)

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

    fun load(context: Context): NockyConnectPendingRestore? =
        loadFromDirectory(File(context.filesDir, PENDING_RESTORE_DIR))

    fun clear(context: Context) {
        clearDirectory(File(context.filesDir, PENDING_RESTORE_DIR))
    }

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

        return restorePlan.toSummary()
    }

    internal fun loadFromDirectory(directory: File): NockyConnectPendingRestore? {
        val snapshotFile = File(directory, PENDING_SNAPSHOT_FILE)
        val queueFile = File(directory, PENDING_QUEUE_FILE)
        val playerStateFile = File(directory, PENDING_PLAYER_STATE_FILE)
        if (!snapshotFile.isFile || !queueFile.isFile || !playerStateFile.isFile) {
            return null
        }

        val snapshot = NockyConnectJson.decodePlaybackSessionSnapshot(snapshotFile.readText())
        val queue = ObjectInputStream(queueFile.inputStream()).use { input ->
            input.readObject() as PersistQueue
        }
        val playerState = ObjectInputStream(playerStateFile.inputStream()).use { input ->
            input.readObject() as PersistPlayerState
        }

        return NockyConnectPendingRestore(
            snapshot = snapshot,
            queue = queue,
            playerState = playerState,
        )
    }

    internal fun clearDirectory(directory: File) {
        File(directory, PENDING_SNAPSHOT_FILE).delete()
        File(directory, PENDING_QUEUE_FILE).delete()
        File(directory, PENDING_PLAYER_STATE_FILE).delete()
    }
}

fun NockyConnectRestorePlan.toSummary(): NockyConnectPendingRestoreSummary =
    pendingRestoreSummary(
        title = queue.title,
        itemTitles = queue.items.map { it.title },
        itemCount = queue.items.size,
        currentIndex = queue.mediaItemIndex,
        positionMs = playerState.currentPosition,
    )

fun NockyConnectPendingRestore.toSummary(): NockyConnectPendingRestoreSummary =
    pendingRestoreSummary(
        title = queue.title,
        itemTitles = queue.items.map { it.title },
        itemCount = queue.items.size,
        currentIndex = queue.mediaItemIndex,
        positionMs = playerState.currentPosition,
    )

private fun pendingRestoreSummary(
    title: String?,
    itemTitles: List<String>,
    itemCount: Int,
    currentIndex: Int,
    positionMs: Long,
): NockyConnectPendingRestoreSummary {
    val safeIndex = currentIndex.coerceIn(
        0,
        (itemCount - 1).coerceAtLeast(0),
    )
    val summaryTitle = itemTitles.getOrNull(safeIndex)
        ?: title
        ?: "queue"

    return NockyConnectPendingRestoreSummary(
        title = summaryTitle,
        itemCount = itemCount,
        currentIndex = safeIndex,
        positionMs = positionMs.coerceAtLeast(0L),
    )
}
