/*
 * Nocky Connect pending restore store tests
 * Licensed under GPL-3.0 | See project license for details
 */

package com.metrolist.music.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class NockyConnectPendingRestoreStoreTest {
    @Test
    fun savesSnapshotQueueAndPausedPlayerState() {
        val directory = createTempDirectory(prefix = "nocky-connect-restore-test").toFile()
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

    @Test
    fun loadsAndClearsPendingRestore() {
        val directory = createTempDirectory(prefix = "nocky-connect-restore-test").toFile()
        try {
            val snapshot = sampleSnapshot()
            val restorePlan = NockyConnectGateway(deviceIdProvider = { "android-1" })
                .prepareRestore(snapshot)
            NockyConnectPendingRestoreStore.saveToDirectory(directory, snapshot, restorePlan)

            val pending = NockyConnectPendingRestoreStore.loadFromDirectory(directory)

            requireNotNull(pending)
            assertEquals(snapshot, pending.snapshot)
            assertEquals("Juno", pending.queue.items.first().title)
            assertEquals(2_267L, pending.playerState.currentPosition)
            assertEquals("Juno", pending.toSummary().title)

            NockyConnectPendingRestoreStore.clearDirectory(directory)
            assertNull(NockyConnectPendingRestoreStore.loadFromDirectory(directory))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun summarizesPendingRestoreQueueWithoutAndroidMediaItemConversion() {
        val restorePlan = NockyConnectGateway(deviceIdProvider = { "android-1" })
            .prepareRestore(sampleSnapshot())

        val summary = restorePlan.toSummary()

        assertEquals("Juno", summary.title)
        assertEquals(1, summary.itemCount)
        assertEquals(0, summary.currentIndex)
        assertEquals(2_267L, summary.positionMs)
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
