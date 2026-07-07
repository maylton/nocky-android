package com.metrolist.music.connect

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NockyConnectSnapshotRestorerTest {
    @Test
    fun restoresYoutubeSnapshotToPausedQueueAndPlayerState() {
        val snapshot = playbackSnapshot(
            state = NockyPlaybackState.PLAYING,
            repeatMode = NockyRepeatMode.ONE,
            shuffleEnabled = true,
        )

        val queue = NockyConnectSnapshotRestorer.toPersistQueue(snapshot)
        val playerState = NockyConnectSnapshotRestorer.toPausedPlayerState(snapshot)

        assertEquals("Remote queue", queue.title)
        assertEquals(1, queue.mediaItemIndex)
        assertEquals(42_000L, queue.position)
        assertEquals(2, queue.items.size)
        assertEquals("video-2", queue.items[1].id)
        assertEquals("Second song", queue.items[1].title)
        assertEquals("set-video-2", queue.items[1].setVideoId)
        assertEquals(181, queue.items[1].duration)
        assertEquals("https://example.com/second.jpg", queue.items[1].thumbnailUrl)

        assertFalse(playerState.playWhenReady)
        assertEquals(Player.REPEAT_MODE_ONE, playerState.repeatMode)
        assertEquals(true, playerState.shuffleModeEnabled)
        assertEquals(42_000L, playerState.currentPosition)
        assertEquals(1, playerState.currentMediaItemIndex)
        assertEquals(Player.STATE_READY, playerState.playbackState)
    }

    @Test
    fun usesYoutubeThumbnailFallbackWhenSnapshotContainsLocalDesktopPath() {
        val snapshot = playbackSnapshot(firstThumbnailUrl = "/home/user/.cache/nocky/cover.jpg")

        val queue = NockyConnectSnapshotRestorer.toPersistQueue(snapshot)

        assertEquals("https://i.ytimg.com/vi/video-1/hqdefault.jpg", queue.items[0].thumbnailUrl)
    }

    @Test
    fun promotesDefaultYoutubeVideoThumbnailsToHighResolutionArtworkCandidates() {
        val snapshot = playbackSnapshot(firstThumbnailUrl = "https://i.ytimg.com/vi/video-1/hqdefault.jpg")

        val queue = NockyConnectSnapshotRestorer.toPersistQueue(snapshot)

        assertEquals("https://i.ytimg.com/vi/video-1/maxresdefault.jpg", queue.items[0].thumbnailUrl)
    }

    @Test
    fun clampsInvalidCurrentIndexDuringRestore() {
        val snapshot = playbackSnapshot(currentIndex = 99)

        val queue = NockyConnectSnapshotRestorer.toPersistQueue(snapshot)
        val playerState = NockyConnectSnapshotRestorer.toPausedPlayerState(snapshot)

        assertEquals(1, queue.mediaItemIndex)
        assertEquals(1, playerState.currentMediaItemIndex)
    }

    private fun playbackSnapshot(
        currentIndex: Int = 1,
        state: NockyPlaybackState = NockyPlaybackState.PAUSED,
        repeatMode: NockyRepeatMode = NockyRepeatMode.ALL,
        shuffleEnabled: Boolean = false,
        firstThumbnailUrl: String? = "https://example.com/first.jpg",
    ) = PlaybackSessionSnapshot(
        sessionId = "restore-session",
        revision = 3L,
        originDeviceId = "desktop-device",
        updatedAtEpochMs = 1_700_000_000_000L,
        source = NockyConnectSource.YOUTUBE,
        playback = PlaybackInfo(
            state = state,
            positionMs = 42_000L,
            durationMs = 181_000L,
            volume = 0.5f,
        ),
        queue = PortableQueue(
            title = "Remote queue",
            currentIndex = currentIndex,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            items = listOf(
                PortableQueueItem(
                    queueItemId = "youtube:video:video-1",
                    source = NockyConnectSource.YOUTUBE,
                    provider = "youtube_music",
                    playableId = "video-1",
                    setVideoId = "set-video-1",
                    title = "First song",
                    artists = listOf(PortableArtist(id = "artist-1", name = "Artist One")),
                    album = PortableAlbum(id = "album-1", title = "Album One"),
                    durationMs = 180_000L,
                    thumbnailUrl = firstThumbnailUrl,
                ),
                PortableQueueItem(
                    queueItemId = "youtube:video:video-2",
                    source = NockyConnectSource.YOUTUBE,
                    provider = "youtube_music",
                    playableId = "video-2",
                    setVideoId = "set-video-2",
                    title = "Second song",
                    artists = listOf(PortableArtist(id = "artist-2", name = "Artist Two")),
                    album = PortableAlbum(id = "album-2", title = "Album Two"),
                    durationMs = 181_000L,
                    thumbnailUrl = "https://example.com/second.jpg",
                ),
            ),
        ),
    )
}
