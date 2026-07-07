package com.metrolist.music.connect

import androidx.media3.common.Player
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import com.metrolist.music.models.QueueData
import com.metrolist.music.models.QueueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NockyConnectSnapshotMapperTest {
    @Test
    fun exportsYoutubeQueueAsPortableSnapshot() {
        val queue = PersistQueue(
            title = "Liked songs",
            items = listOf(firstSong(), secondSong()),
            mediaItemIndex = 0,
            position = 12_000L,
            queueType = QueueType.YOUTUBE_ALBUM_RADIO,
            queueData = QueueData.YouTubeAlbumRadioData(playlistId = "PL123"),
        )
        val playerState = PersistPlayerState(
            playWhenReady = false,
            repeatMode = Player.REPEAT_MODE_ALL,
            shuffleModeEnabled = true,
            volume = 0.75f,
            currentPosition = 98_765L,
            currentMediaItemIndex = 1,
            playbackState = Player.STATE_READY,
            timestamp = 1_700_000_000_000L,
        )

        val snapshot = NockyConnectSnapshotMapper.fromPersistedState(
            queue = queue,
            playerState = playerState,
            originDeviceId = "android-test-device",
            sessionId = "test-session",
            revision = 7L,
            updatedAtEpochMs = 1_700_000_000_123L,
        )

        assertEquals(PLAYBACK_SESSION_SNAPSHOT_SCHEMA, snapshot.schema)
        assertEquals(NOCKY_CONNECT_PROTOCOL_VERSION, snapshot.schemaVersion)
        assertEquals("test-session", snapshot.sessionId)
        assertEquals(7L, snapshot.revision)
        assertEquals("android-test-device", snapshot.originDeviceId)
        assertEquals(NockyConnectSource.YOUTUBE, snapshot.source)
        assertEquals(NockyPlaybackState.PAUSED, snapshot.playback.state)
        assertEquals(98_765L, snapshot.playback.positionMs)
        assertEquals(181_000L, snapshot.playback.durationMs)
        assertEquals(NockyRepeatMode.ALL, snapshot.queue.repeatMode)
        assertEquals(true, snapshot.queue.shuffleEnabled)
        assertEquals(1, snapshot.queue.currentIndex)
        assertEquals(2, snapshot.queue.items.size)

        val currentItem = snapshot.queue.items[1]
        assertEquals("youtube:video:video-2", currentItem.queueItemId)
        assertEquals("youtube_music", currentItem.provider)
        assertEquals("video-2", currentItem.playableId)
        assertEquals("set-video-2", currentItem.setVideoId)
        assertEquals("PL123", currentItem.playlistId)
        assertEquals("https://example.com/second.jpg", currentItem.thumbnailUrl)
        assertNull(currentItem.local)
    }

    @Test
    fun usesYoutubeThumbnailFallbackWhenArtworkIsMissingOrLocalOnly() {
        val snapshot = NockyConnectSnapshotMapper.fromPersistedState(
            queue = PersistQueue(
                title = "Queue",
                items = listOf(firstSong(thumbnailUrl = null), secondSong(thumbnailUrl = "/tmp/local-only.jpg")),
                mediaItemIndex = 0,
                position = 0L,
                queueType = QueueType.YOUTUBE,
            ),
            playerState = PersistPlayerState(
                playWhenReady = false,
                repeatMode = Player.REPEAT_MODE_OFF,
                shuffleModeEnabled = false,
                volume = 1f,
                currentPosition = 0L,
                currentMediaItemIndex = 0,
                playbackState = Player.STATE_READY,
            ),
            originDeviceId = "android-test-device",
            sessionId = "artwork-session",
            revision = 1L,
            updatedAtEpochMs = 1_700_000_000_000L,
        )

        assertEquals("https://i.ytimg.com/vi/video-1/hqdefault.jpg", snapshot.queue.items[0].thumbnailUrl)
        assertEquals("https://i.ytimg.com/vi/video-2/hqdefault.jpg", snapshot.queue.items[1].thumbnailUrl)
    }

    @Test
    fun codecRoundTripsSnapshotJson() {
        val snapshot = NockyConnectSnapshotMapper.fromPersistedState(
            queue = PersistQueue(
                title = "Queue",
                items = listOf(firstSong()),
                mediaItemIndex = 0,
                position = 0L,
            ),
            playerState = PersistPlayerState(
                playWhenReady = true,
                repeatMode = Player.REPEAT_MODE_ONE,
                shuffleModeEnabled = false,
                volume = 1f,
                currentPosition = 1_234L,
                currentMediaItemIndex = 0,
                playbackState = Player.STATE_READY,
            ),
            originDeviceId = "android-test-device",
            sessionId = "round-trip-session",
            revision = 1L,
            updatedAtEpochMs = 1_700_000_000_000L,
        )

        val payload = NockyConnectJson.encode(snapshot)
        val decoded = NockyConnectJson.decodePlaybackSessionSnapshot(payload)

        assertEquals(snapshot, decoded)
    }

    @Test
    fun marksLocalQueueAsBestEffortLocalIdentity() {
        val snapshot = NockyConnectSnapshotMapper.fromPersistedState(
            queue = PersistQueue(
                title = "Local radio",
                items = listOf(firstSong()),
                mediaItemIndex = 0,
                position = 0L,
                queueType = QueueType.LOCAL_ALBUM_RADIO,
            ),
            playerState = PersistPlayerState(
                playWhenReady = false,
                repeatMode = Player.REPEAT_MODE_OFF,
                shuffleModeEnabled = false,
                volume = 1f,
                currentPosition = 0L,
                currentMediaItemIndex = 0,
                playbackState = Player.STATE_READY,
            ),
            originDeviceId = "android-test-device",
            sessionId = "local-session",
            revision = 1L,
            updatedAtEpochMs = 1_700_000_000_000L,
        )

        assertEquals(NockyConnectSource.LOCAL, snapshot.source)
        assertEquals(NockyConnectSource.LOCAL, snapshot.queue.items.single().source)
        assertNotNull(snapshot.queue.items.single().local)
        assertEquals("android-local-library", snapshot.queue.items.single().local?.libraryId)
        assertNull(snapshot.queue.items.single().thumbnailUrl)
    }

    private fun firstSong(thumbnailUrl: String? = "https://example.com/first.jpg") = MediaMetadata(
        id = "video-1",
        title = "First song",
        artists = listOf(MediaMetadata.Artist(id = "artist-1", name = "Artist One")),
        duration = 180,
        thumbnailUrl = thumbnailUrl,
        album = MediaMetadata.Album(id = "album-1", title = "Album One"),
        setVideoId = "set-video-1",
    )

    private fun secondSong(thumbnailUrl: String? = "https://example.com/second.jpg") = MediaMetadata(
        id = "video-2",
        title = "Second song",
        artists = listOf(MediaMetadata.Artist(id = "artist-2", name = "Artist Two")),
        duration = 181,
        thumbnailUrl = thumbnailUrl,
        album = MediaMetadata.Album(id = "album-2", title = "Album Two"),
        setVideoId = "set-video-2",
    )
}
