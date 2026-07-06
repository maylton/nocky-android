package com.metrolist.music.connect

import androidx.media3.common.Player
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NockyConnectGatewayTest {
    @Test
    fun exportsAndPreparesRestorePlanFromJson() {
        val gateway = NockyConnectGateway(
            deviceIdProvider = { "android-device" },
            clock = { 1_700_000_000_000L },
        )
        val queue = PersistQueue(
            title = "Gateway queue",
            items = listOf(song()),
            mediaItemIndex = 0,
            position = 10_000L,
        )
        val playerState = PersistPlayerState(
            playWhenReady = true,
            repeatMode = Player.REPEAT_MODE_ALL,
            shuffleModeEnabled = true,
            volume = 0.5f,
            currentPosition = 10_000L,
            currentMediaItemIndex = 0,
            playbackState = Player.STATE_READY,
        )

        val payload = gateway.exportSnapshotJson(
            queue = queue,
            playerState = playerState,
            sessionId = "gateway-session",
            revision = 2L,
        )
        val plan = gateway.prepareRestore(payload)

        assertEquals("gateway-session", plan.snapshot.sessionId)
        assertEquals("android-device", plan.snapshot.originDeviceId)
        assertEquals(2L, plan.snapshot.revision)
        assertEquals("Gateway queue", plan.queue.title)
        assertEquals("video-1", plan.queue.items.single().id)
        assertEquals(10_000L, plan.playerState.currentPosition)
        assertFalse(plan.playerState.playWhenReady)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSchemaVersion() {
        val gateway = NockyConnectGateway(deviceIdProvider = { "android-device" })
        val snapshot = PlaybackSessionSnapshot(
            schemaVersion = NOCKY_CONNECT_PROTOCOL_VERSION + 1,
            sessionId = "future-session",
            revision = 1L,
            originDeviceId = "desktop-device",
            updatedAtEpochMs = 1_700_000_000_000L,
            source = NockyConnectSource.YOUTUBE,
            playback = PlaybackInfo(
                state = NockyPlaybackState.PAUSED,
                positionMs = 0L,
            ),
            queue = PortableQueue(
                currentIndex = 0,
                repeatMode = NockyRepeatMode.OFF,
                shuffleEnabled = false,
                items = emptyList(),
            ),
        )

        gateway.prepareRestore(snapshot)
    }

    private fun song() = MediaMetadata(
        id = "video-1",
        title = "First song",
        artists = listOf(MediaMetadata.Artist(id = "artist-1", name = "Artist One")),
        duration = 180,
        thumbnailUrl = "https://example.com/first.jpg",
        album = MediaMetadata.Album(id = "album-1", title = "Album One"),
        setVideoId = "set-video-1",
    )
}
