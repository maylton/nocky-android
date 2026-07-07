package com.metrolist.music.connect

import androidx.media3.common.Player
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import com.metrolist.music.models.QueueData
import com.metrolist.music.models.QueueType
import java.util.UUID
import kotlin.math.max

object NockyConnectSnapshotMapper {
    fun fromPersistedState(
        queue: PersistQueue,
        playerState: PersistPlayerState,
        originDeviceId: String,
        sessionId: String = newSessionId(),
        revision: Long = 1L,
        updatedAtEpochMs: Long = System.currentTimeMillis(),
        updatedAtMonotonicMs: Long? = null,
    ): PlaybackSessionSnapshot {
        val source = queue.toConnectSource()
        val currentIndex = queue.safeCurrentIndex(playerState.currentMediaItemIndex)
        val currentItem = queue.items.getOrNull(currentIndex)
        val queueData = queue.queueData

        return PlaybackSessionSnapshot(
            sessionId = sessionId,
            revision = revision,
            originDeviceId = originDeviceId,
            updatedAtEpochMs = updatedAtEpochMs,
            updatedAtMonotonicMs = updatedAtMonotonicMs,
            source = source,
            playback = PlaybackInfo(
                state = playerState.toConnectPlaybackState(),
                positionMs = playerState.currentPosition.coerceAtLeast(0L),
                durationMs = currentItem?.durationMs(),
                volume = playerState.volume.coerceIn(0f, 1f),
            ),
            queue = PortableQueue(
                title = queue.title,
                currentIndex = currentIndex,
                repeatMode = playerState.repeatMode.toConnectRepeatMode(),
                shuffleEnabled = playerState.shuffleModeEnabled,
                items = queue.items.map { item ->
                    item.toPortableQueueItem(
                        source = source,
                        queueData = queueData,
                    )
                },
            ),
        )
    }

    fun newSessionId(): String = UUID.randomUUID().toString()
}

fun PersistQueue.toConnectSource(): NockyConnectSource =
    when (queueType) {
        QueueType.LOCAL_ALBUM_RADIO -> NockyConnectSource.LOCAL
        QueueType.YOUTUBE,
        QueueType.YOUTUBE_ALBUM_RADIO,
        QueueType.LIST,
        -> NockyConnectSource.YOUTUBE
    }

private fun PersistQueue.safeCurrentIndex(playerIndex: Int): Int {
    if (items.isEmpty()) return 0
    val preferred = when {
        playerIndex in items.indices -> playerIndex
        mediaItemIndex in items.indices -> mediaItemIndex
        else -> 0
    }
    return preferred.coerceIn(0, max(items.lastIndex, 0))
}

private fun PersistPlayerState.toConnectPlaybackState(): NockyPlaybackState =
    when (playbackState) {
        Player.STATE_IDLE -> NockyPlaybackState.IDLE
        Player.STATE_BUFFERING -> NockyPlaybackState.LOADING
        Player.STATE_ENDED -> NockyPlaybackState.ENDED
        Player.STATE_READY -> if (playWhenReady) NockyPlaybackState.PLAYING else NockyPlaybackState.PAUSED
        else -> if (playWhenReady) NockyPlaybackState.PLAYING else NockyPlaybackState.PAUSED
    }

private fun Int.toConnectRepeatMode(): NockyRepeatMode =
    when (this) {
        Player.REPEAT_MODE_ONE -> NockyRepeatMode.ONE
        Player.REPEAT_MODE_ALL -> NockyRepeatMode.ALL
        else -> NockyRepeatMode.OFF
    }

fun MediaMetadata.toPortableQueueItem(
    source: NockyConnectSource,
    queueData: QueueData? = null,
): PortableQueueItem {
    val provider = when (source) {
        NockyConnectSource.YOUTUBE -> "youtube_music"
        NockyConnectSource.LOCAL -> "nocky_local"
        NockyConnectSource.UNKNOWN -> "unknown"
    }

    return PortableQueueItem(
        queueItemId = queueItemIdFor(source, id),
        source = source,
        provider = provider,
        playableId = id,
        setVideoId = setVideoId,
        playlistId = queueData.playlistIdOrNull(),
        browseId = queueData.browseIdOrNull(),
        title = title,
        artists = artists
            .mapNotNull { artist ->
                val name = artist.name.trim()
                if (name.isNockyConnectArtistSeparator()) {
                    null
                } else {
                    PortableArtist(id = artist.id, name = name)
                }
            }
            .ifEmpty { listOf(PortableArtist(name = "Unknown artist")) },
        album = album
            ?.takeIf { it.title.isNotBlank() }
            ?.let { PortableAlbum(id = it.id.takeIf { id -> id.isNotBlank() }, title = it.title) },
        durationMs = durationMs(),
        thumbnailUrl = portableThumbnailUrl(source = source, playableId = id, thumbnailUrl = thumbnailUrl),
        explicit = explicit,
        isVideo = isVideoSong,
        isEpisode = isEpisode,
        local = if (source == NockyConnectSource.LOCAL) {
            LocalTrackIdentity(libraryId = "android-local-library")
        } else {
            null
        },
    )
}

private fun String.isNockyConnectArtistSeparator(): Boolean =
    trim().lowercase() in setOf("", ",", "&", "e", "and", "feat.", "feat", "ft.", "ft")

private fun MediaMetadata.durationMs(): Long? =
    duration.takeIf { it > 0 }?.toLong()?.times(1_000L)

private fun portableThumbnailUrl(
    source: NockyConnectSource,
    playableId: String,
    thumbnailUrl: String?,
): String? {
    val safeUrl = thumbnailUrl?.trim()?.takeIf { it.isPortableHttpUrl() }
    return when (source) {
        NockyConnectSource.YOUTUBE -> safeUrl ?: youtubeThumbnailUrl(playableId)
        NockyConnectSource.LOCAL,
        NockyConnectSource.UNKNOWN,
        -> null
    }
}

private fun String.isPortableHttpUrl(): Boolean =
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

internal fun youtubeThumbnailUrl(videoId: String): String? =
    videoId
        .takeIf { it.isNotBlank() }
        ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

private fun queueItemIdFor(source: NockyConnectSource, playableId: String): String =
    when (source) {
        NockyConnectSource.YOUTUBE -> "youtube:video:$playableId"
        NockyConnectSource.LOCAL -> "local:$playableId"
        NockyConnectSource.UNKNOWN -> "unknown:$playableId"
    }

private fun QueueData?.playlistIdOrNull(): String? =
    when (this) {
        is QueueData.YouTubeAlbumRadioData -> playlistId
        is QueueData.LocalAlbumRadioData -> playlistId
        else -> null
    }

private fun QueueData?.browseIdOrNull(): String? =
    when (this) {
        is QueueData.YouTubeData -> endpoint
        else -> null
    }
