package com.metrolist.music.connect

import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.mediaItems
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.queues.ListQueue
import java.io.File

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

fun MusicService.exportNockyConnectSnapshotForCurrentDevice(
    sessionId: String = NockyConnectSnapshotMapper.newSessionId(),
    revision: Long = 1L,
): PlaybackSessionSnapshot? =
    exportNockyConnectSnapshot(
        deviceId = getOrCreateNockyConnectDeviceId(),
        sessionId = sessionId,
        revision = revision,
    )

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

fun MusicService.exportNockyConnectSnapshotJsonForCurrentDevice(
    sessionId: String = NockyConnectSnapshotMapper.newSessionId(),
    revision: Long = 1L,
): String? =
    exportNockyConnectSnapshotJson(
        deviceId = getOrCreateNockyConnectDeviceId(),
        sessionId = sessionId,
        revision = revision,
    )

fun MusicService.writeNockyConnectSnapshotFile(
    sessionId: String = NockyConnectSnapshotMapper.newSessionId(),
    revision: Long = 1L,
): File? =
    exportNockyConnectSnapshotForCurrentDevice(
        sessionId = sessionId,
        revision = revision,
    )?.let { snapshot ->
        NockyConnectFileStore(filesDir).writeSnapshot(snapshot)
    }

fun MusicService.prepareNockyConnectRestore(
    payload: String,
    deviceId: String,
): NockyConnectRestorePlan =
    NockyConnectGateway(deviceIdProvider = { deviceId }).prepareRestore(payload)

fun MusicService.prepareNockyConnectRestoreForCurrentDevice(
    payload: String,
): NockyConnectRestorePlan =
    prepareNockyConnectRestore(
        payload = payload,
        deviceId = getOrCreateNockyConnectDeviceId(),
    )

fun MusicService.restoreNockyConnectSnapshotPaused(
    plan: NockyConnectRestorePlan,
) {
    if (!isPlayerReady.value) return
    if (plan.queue.items.isEmpty()) return

    val restoreQueue = ListQueue(
        title = plan.queue.title,
        items = plan.queue.items.map { it.toNockyConnectMediaItem() },
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

fun MusicService.restoreNockyConnectSnapshotJsonPausedForCurrentDevice(
    payload: String,
): NockyConnectRestorePlan {
    val plan = prepareNockyConnectRestoreForCurrentDevice(payload)
    restoreNockyConnectSnapshotPaused(plan)
    return plan
}

fun MusicService.restoreLatestNockyConnectSnapshotFilePaused(): NockyConnectRestorePlan? {
    val store = NockyConnectFileStore(filesDir)
    val latest = store.latestSnapshotFile() ?: return null
    val payload = latest.readText()
    return restoreNockyConnectSnapshotJsonPausedForCurrentDevice(payload)
}

private fun MusicService.currentPersistQueueForNockyConnect(): PersistQueue =
    PersistQueue(
        title = queueTitle,
        items = player.mediaItems.mapNotNull { item -> item.metadata ?: item.mediaMetadataFallback() },
        mediaItemIndex = player.currentMediaItemIndex.coerceAtLeast(0),
        position = player.currentPosition.coerceAtLeast(0L),
    )

private fun MediaItem.mediaMetadataFallback(): MediaMetadata? {
    val metadata = mediaMetadata
    val title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
        ?: metadata.subtitle?.toString()?.takeIf { it.isNotBlank() }
        ?: "Unknown artist"
    val artworkUri = metadata.artworkUri?.toString()
        ?: metadata.extras?.getString("artwork_uri")
    return MediaMetadata(
        id = mediaId,
        title = title,
        artists = artist.split(',')
            .map { name -> name.trim() }
            .filter { name -> name.isNotEmpty() }
            .map { name -> MediaMetadata.Artist(id = null, name = name) }
            .ifEmpty { listOf(MediaMetadata.Artist(id = null, name = "Unknown artist")) },
        duration = -1,
        thumbnailUrl = artworkUri,
        album = metadata.albumTitle
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { albumTitle -> MediaMetadata.Album(id = "", title = albumTitle) },
    )
}

private fun MusicService.currentPersistPlayerStateForNockyConnect(): PersistPlayerState =
    PersistPlayerState(
        playWhenReady = player.playWhenReady,
        repeatMode = player.repeatMode,
        shuffleModeEnabled = player.shuffleModeEnabled,
        volume = playerVolume.value,
        currentPosition = player.currentPosition.coerceAtLeast(0),
        currentMediaItemIndex = player.currentMediaItemIndex.coerceAtLeast(0),
        playbackState = player.playbackState,
    )
