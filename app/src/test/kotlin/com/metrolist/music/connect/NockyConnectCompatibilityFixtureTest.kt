package com.metrolist.music.connect

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NockyConnectCompatibilityFixtureTest {
    @Test
    fun decodesSharedV1FixtureAndPreparesPausedRestore() {
        val payload = readFixture()
        val gateway = NockyConnectGateway(deviceIdProvider = { "android-device" })

        val plan = gateway.prepareRestore(payload)

        assertEquals(PLAYBACK_SESSION_SNAPSHOT_SCHEMA, plan.snapshot.schema)
        assertEquals(NOCKY_CONNECT_PROTOCOL_VERSION, plan.snapshot.schemaVersion)
        assertEquals("compat-session-v1", plan.snapshot.sessionId)
        assertEquals(7L, plan.snapshot.revision)
        assertEquals(NockyConnectSource.YOUTUBE, plan.snapshot.source)
        assertEquals(NockyPlaybackState.PAUSED, plan.snapshot.playback.state)
        assertEquals(45_000L, plan.snapshot.playback.positionMs)
        assertEquals("Compatibility fixture", plan.queue.title)
        assertEquals(1, plan.queue.mediaItemIndex)
        assertEquals(2, plan.queue.items.size)
        assertEquals("video-2", plan.queue.items[1].id)
        assertEquals("Second fixture song", plan.queue.items[1].title)
        assertFalse(plan.playerState.playWhenReady)
        assertEquals(Player.REPEAT_MODE_ALL, plan.playerState.repeatMode)
        assertEquals(Player.STATE_READY, plan.playerState.playbackState)
    }

    private fun readFixture(): String =
        requireNotNull(
            javaClass.classLoader?.getResourceAsStream("nocky-connect-snapshot-v1.json"),
        ) { "Missing nocky-connect-snapshot-v1.json test resource" }
            .bufferedReader()
            .use { it.readText() }
}
