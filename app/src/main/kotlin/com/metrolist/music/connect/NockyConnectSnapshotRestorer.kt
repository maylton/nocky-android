package com.metrolist.music.connect

import androidx.media3.common.Player
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import com.metrolist.music.models.QueueData
import com.metrolist.music.models.QueueType

object NockyConnectSnapshotRestorer {
    fun toPersistQueue(snapshot: PlaybackSessionSnapshot): PersistQueue {
        val queue = snapshot.queue
        val source = snapshot.source
        val items = queue.items.map { it.toMediaMetadata() }
        val safeIndex = queue.currentIndex.coerceIn(
            0,
            (items.size - 1).coerceAtLeast(0),
        )

        return PersistQueue(
            title = queue.title,
            items = items,
            mediaItemIndex = safeIndex,
            position = snapshot.playback.positionMs.coerceAtLeast(0L),
            queueType = source.toQueueType(),
            queueData = queue.items.firstOrNull()?.toQueueData(source),
        )
    }

    fun toPausedPlayerState(snapshot: PlaybackSessionSnapshot): PersistPlayerState {
        val itemCount = snapshot.queue.items.size
        val safeIndex = snapshot.queue.currentIndex.coerceIn(
            0,
            (itemCount - 1).coerceAtLeast(0),
        )

        return PersistPlayerState(
            playWhenReady = false,
            repeatMode = snapshot.queue.repeatMode.toPlayerRepeatMode(),
            shuffleModeEnabled = snapshot.queue.shuffleEnabled,
            volume = snapshot.playback.volume?.coerceIn(0f, 1f) ?: 1f,
            currentPosition = snapshot.playback.positionMs.coerceAtLeast(0L),
            currentMediaItemIndex = safeIndex,
            playbackState = if (itemCount == 0) Player.STATE_IDLE else Player.STATE_READY,
            timestamp = snapshot.updatedAtEpochMs,
        )
    }
}

fun PortableQueueItem.toMediaMetadata(): MediaMetadata =
    MediaMetadata(
        id = playableId,
        title = title,
        artists = artists.map { artist ->
            MediaMetadata.Artist(
                id = artist.id,
                name = artist.name,
            )
        },
        duration = durationMs?.let { (it / 1_000L).toInt() } ?: -1,
        thumbnailUrl = thumbnailUrl,
        album = album?.let { album ->
            MediaMetadata.Album(
                id = album.id ?: "",
                title = album.title,
            )
        },
        setVideoId = setVideoId,
        musicVideoType = if (isVideo) "MUSIC_VIDEO_TYPE_OMV" else null,
        explicit = explicit,
        isEpisode = isEpisode,
    )

private fun NockyConnectSource.toQueueType(): QueueType =
    when (this) {
        NockyConnectSource.LOCAL -> QueueType.LOCAL_ALBUM_RADIO
        NockyConnectSource.YOUTUBE,
        NockyConnectSource.UNKNOWN,
        -> QueueType.YOUTUBE
    }

private fun PortableQueueItem.toQueueData(source: NockyConnectSource): QueueData? =
    when (source) {
        NockyConnectSource.YOUTUBE ->
            playlistId?.let { QueueData.YouTubeAlbumRadioData(playlistId = it) }
                ?: browseId?.let { QueueData.YouTubeData(endpoint = it) }
        NockyConnectSource.LOCAL ->
            album?.id?.takeIf { it.isNotBlank() }?.let {
                QueueData.LocalAlbumRadioData(albumId = it)
            }
        NockyConnectSource.UNKNOWN -> null
    }

private fun NockyRepeatMode.toPlayerRepeatMode(): Int =
    when (this) {
        NockyRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        NockyRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        NockyRepeatMode.OFF -> Player.REPEAT_MODE_OFF
    }
