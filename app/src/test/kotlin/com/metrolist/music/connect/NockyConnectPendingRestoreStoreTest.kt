/*
 * Nocky Connect pending restore store tests
 * Licensed under GPL-3.0 | See project license for details
 */

package com.metrolist.music.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NockyConnectPendingRestoreStoreTest {
    @Test
    fun savesSnapshotQueueAndPausedPlayerState() {
        val directory = createTempDir(prefix = "nocky-connect-restore-test")
        try {
            val snapshot = sampleSnapshot()
            val restorePlan = NockyConnectGateway(deviceIdProvider = { "android-1" })
                .prepareRestore(snapshot)

            val summary = NockyConnectPendingRestoreStore.saveToDirectory(
                directory = directory,
                snapshot = snapshot,
                restorePlan = restorePlan,
            )

            assertEquals("Juno", summary.title)
            assertEquals(1, summary.itemCount)
            assertEquals(0, summary.currentIndex)
            assertEquals(2_267L, summary.positionMs)
            assertTrue(File(directory, "pending-restore-snapshot.json").isFile)
            assertTrue(File(directory, "pending-restore-queue.bin").isFile)
            assertTrue(File(directory, "pending-restore-player-state.bin").isFile)
            assertTrue(
                File(directory, "pending-restore-snapshot.json")
                    .readText()
                    .contains("PlaybackSessionSnapshot"),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun sampleSnapshot(): PlaybackSessionSnapshot = PlaybackSessionSnapshot(
        sessionId = "snapshot-session-1",
        revision = 1L,
        originDeviceId = "desktop-1",
        updatedAtEpochMs = 1_789_000L,
        source = NockyConnectSource.YOUTUBE,
        playback = PlaybackInfo(
            state = NockyPlaybackState.PAUSED,
            positionMs = 2_267L,
            durationMs = 223_000L,
        ),
        queue = PortableQueue(
            title = "Desktop queue",
            currentIndex = 0,
            repeatMode = NockyRepeatMode.OFF,
            shuffleEnabled = false,
            items = listOf(
                PortableQueueItem(
                    queueItemId = "youtube:video:video-1",
                    source = NockyConnectSource.YOUTUBE,
                    provider = "youtube_music",
                    playableId = "video-1",
                    title = "Juno",
                    artists = listOf(PortableArtist(name = "Sabrina Carpenter")),
                    durationMs = 223_000L,
                ),
            ),
        ),
    )
}
