package com.metrolist.music.connect

import androidx.media3.common.Player
import com.metrolist.music.extensions.mediaItems
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.queues.ListQueue

fun MusicService.exportNockyConnectSnapshot(
    deviceId: String,
    sessionId: String = NockyConnectSnapshotMapper.newSessionId(),
    revision: Long = 1L,
): PlaybackSessionSnapshot? {
    if (!isPlayerReady.value) return null
    if (player.mediaItemCount == 0) return null

    return NockyConnectGateway(deviceIdProvider = { deviceId })
        .exportSnapshot(
            queue = currentPersistQueueForNockyConnect(),
            playerState = currentPersistPlayerStateForNockyConnect(),
            sessionId = sessionId,
            revision = revision,
        )
}

fun MusicService.exportNockyConnectSnapshotJson(
    deviceId: String,
    sessionId: String = NockyConnectSnapshotMapper.newSessionId(),
    revision: Long = 1L,
): String? =
    exportNockyConnectSnapshot(
        deviceId = deviceId,
        sessionId = sessionId,
        revision = revision,
    )?.let(NockyConnectJson::encode)

fun MusicService.prepareNockyConnectRestore(
    payload: String,
    deviceId: String,
): NockyConnectRestorePlan =
    NockyConnectGateway(deviceIdProvider = { deviceId }).prepareRestore(payload)

fun MusicService.restoreNockyConnectSnapshotPaused(
    plan: NockyConnectRestorePlan,
) {
    val restoreQueue = ListQueue(
        title = plan.queue.title,
        items = plan.queue.items.map { it.toMediaItem() },
        startIndex = plan.playerState.currentMediaItemIndex,
        position = plan.playerState.currentPosition,
    )

    player.repeatMode = plan.playerState.repeatMode
    player.shuffleModeEnabled = plan.playerState.shuffleModeEnabled
    playerVolume.value = plan.playerState.volume
    playQueue(
        queue = restoreQueue,
        playWhenReady = false,
    )
}

fun MusicService.restoreNockyConnectSnapshotJsonPaused(
    payload: String,
    deviceId: String,
): NockyConnectRestorePlan {
    val plan = prepareNockyConnectRestore(
        payload = payload,
        deviceId = deviceId,
    )
    restoreNockyConnectSnapshotPaused(plan)
    return plan
}

private fun MusicService.currentPersistQueueForNockyConnect(): PersistQueue =
    PersistQueue(
        title = queueTitle,
        items = player.mediaItems.mapNotNull { it.metadata },
        mediaItemIndex = player.currentMediaItemIndex,
        position = player.currentPosition,
    )

private fun MusicService.currentPersistPlayerStateForNockyConnect(): PersistPlayerState =
    PersistPlayerState(
        playWhenReady = player.playWhenReady,
        repeatMode = player.repeatMode,
        shuffleModeEnabled = player.shuffleModeEnabled,
        volume = playerVolume.value,
        currentPosition = player.currentPosition,
        currentMediaItemIndex = player.currentMediaItemIndex.coerceAtLeast(0),
        playbackState = player.playbackState,
    )
