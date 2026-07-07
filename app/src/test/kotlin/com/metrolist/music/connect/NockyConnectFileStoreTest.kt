package com.metrolist.music.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class NockyConnectFileStoreTest {
    @Test
    fun writesReadsAndListsSnapshots() {
        val root = createTempDirectory(prefix = "nocky-connect-test").toFile()
        try {
            val store = NockyConnectFileStore(root)
            val snapshot = snapshot(sessionId = "session/with spaces", revision = 5L)

            val file = store.writeSnapshot(snapshot)
            val decoded = store.readSnapshot(file)
            val files = store.listSnapshotFiles()

            assertTrue(file.exists())
            assertTrue(file.name.startsWith("snapshot_session_with_spaces_r5"))
            assertEquals(snapshot, decoded)
            assertEquals(file, files.single())
            assertEquals(file, store.latestSnapshotFile())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun returnsNullWhenNoSnapshotsExist() {
        val root = createTempDirectory(prefix = "nocky-connect-empty").toFile()
        try {
            val store = NockyConnectFileStore(root)

            assertEquals(emptyList<File>(), store.listSnapshotFiles())
            assertEquals(null, store.latestSnapshotFile())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun snapshot(
        sessionId: String = "file-session",
        revision: Long = 1L,
    ) = PlaybackSessionSnapshot(
        sessionId = sessionId,
        revision = revision,
        originDeviceId = "android-device",
        updatedAtEpochMs = 1_700_000_000_000L,
        source = NockyConnectSource.YOUTUBE,
        playback = PlaybackInfo(
            state = NockyPlaybackState.PAUSED,
            positionMs = 1_000L,
        ),
        queue = PortableQueue(
            currentIndex = 0,
            repeatMode = NockyRepeatMode.OFF,
            shuffleEnabled = false,
            items = emptyList(),
        ),
    )
}
