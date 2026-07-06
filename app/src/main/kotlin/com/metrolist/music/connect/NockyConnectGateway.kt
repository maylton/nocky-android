package com.metrolist.music.connect

import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue

class NockyConnectGateway(
    private val deviceIdProvider: () -> String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun exportSnapshot(
        queue: PersistQueue,
        playerState: PersistPlayerState,
        sessionId: String = NockyConnectSnapshotMapper.newSessionId(),
        revision: Long = 1L,
    ): PlaybackSessionSnapshot =
        NockyConnectSnapshotMapper.fromPersistedState(
            queue = queue,
            playerState = playerState,
            originDeviceId = deviceIdProvider(),
            sessionId = sessionId,
            revision = revision,
            updatedAtEpochMs = clock(),
        )

    fun exportSnapshotJson(
        queue: PersistQueue,
        playerState: PersistPlayerState,
        sessionId: String = NockyConnectSnapshotMapper.newSessionId(),
        revision: Long = 1L,
    ): String =
        NockyConnectJson.encode(
            exportSnapshot(
                queue = queue,
                playerState = playerState,
                sessionId = sessionId,
                revision = revision,
            ),
        )

    fun decodeSnapshot(payload: String): PlaybackSessionSnapshot =
        NockyConnectJson.decodePlaybackSessionSnapshot(payload).also(::requireSupported)

    fun prepareRestore(payload: String): NockyConnectRestorePlan =
        prepareRestore(decodeSnapshot(payload))

    fun prepareRestore(snapshot: PlaybackSessionSnapshot): NockyConnectRestorePlan {
        requireSupported(snapshot)
        return NockyConnectRestorePlan(
            snapshot = snapshot,
            queue = NockyConnectSnapshotRestorer.toPersistQueue(snapshot),
            playerState = NockyConnectSnapshotRestorer.toPausedPlayerState(snapshot),
        )
    }

    private fun requireSupported(snapshot: PlaybackSessionSnapshot) {
        require(snapshot.schema == PLAYBACK_SESSION_SNAPSHOT_SCHEMA) {
            "Unsupported Nocky Connect schema: ${snapshot.schema}"
        }
        require(snapshot.schemaVersion == NOCKY_CONNECT_PROTOCOL_VERSION) {
            "Unsupported Nocky Connect schema version: ${snapshot.schemaVersion}"
        }
    }
}

data class NockyConnectRestorePlan(
    val snapshot: PlaybackSessionSnapshot,
    val queue: PersistQueue,
    val playerState: PersistPlayerState,
)
