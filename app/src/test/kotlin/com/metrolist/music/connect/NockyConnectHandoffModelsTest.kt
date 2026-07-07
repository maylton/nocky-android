/*
 * Nocky Connect handoff protocol contract tests
 * Licensed under GPL-3.0 | See project license for details
 */

package com.metrolist.music.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NockyConnectHandoffModelsTest {
    @Test
    fun handoffOfferRoundTripsWithoutSnapshotPayload() {
        val envelope = NockyConnectHandoffEnvelope(
            messageId = "offer-message-1",
            createdAtEpochMs = 1_789_000L,
            kind = NockyConnectHandoffKind.OFFER,
            payload = NockyConnectHandoffPayload.Offer(
                offerId = "offer-1",
                senderDeviceId = "desktop-1",
                senderDeviceName = "Nocky Desktop",
                receiverDeviceId = "android-1",
                snapshotSummary = NockyConnectSnapshotSummary(
                    source = NockyConnectSource.YOUTUBE,
                    currentTitle = "Juno",
                    currentArtist = "Sabrina Carpenter",
                    queueItems = 89,
                    positionMs = 2_267L,
                    durationMs = 223_000L,
                    wasPlaying = true,
                ),
            ),
        )

        val encoded = NockyConnectJson.format.encodeToString(
            NockyConnectHandoffEnvelope.serializer(),
            envelope,
        )

        assertFalse(encoded.contains("cookies"))
        assertFalse(encoded.contains("headers"))
        assertFalse(encoded.contains("stream_url"))
        assertTrue(encoded.contains("handoff_offer"))
        assertTrue(encoded.contains("restore_paused"))

        val decoded = NockyConnectJson.format.decodeFromString(
            NockyConnectHandoffEnvelope.serializer(),
            encoded,
        )

        assertEquals(envelope, decoded)
        assertEquals(HANDOFF_MESSAGE_SCHEMA, decoded.schema)
        assertEquals(NOCKY_CONNECT_PROTOCOL_VERSION, decoded.schemaVersion)
    }

    @Test
    fun acceptDeclineAndResultRoundTrip() {
        val accept = NockyConnectHandoffEnvelope(
            messageId = "accept-message-1",
            createdAtEpochMs = 1_789_001L,
            kind = NockyConnectHandoffKind.ACCEPT,
            payload = NockyConnectHandoffPayload.Accept(
                offerId = "offer-1",
                receiverDeviceId = "android-1",
            ),
        )
        val decline = NockyConnectHandoffEnvelope(
            messageId = "decline-message-1",
            createdAtEpochMs = 1_789_002L,
            kind = NockyConnectHandoffKind.DECLINE,
            payload = NockyConnectHandoffPayload.Decline(
                offerId = "offer-2",
                receiverDeviceId = "android-1",
                reason = NockyConnectHandoffDeclineReason.USER_DECLINED,
            ),
        )
        val result = NockyConnectHandoffEnvelope(
            messageId = "result-message-1",
            createdAtEpochMs = 1_789_003L,
            kind = NockyConnectHandoffKind.RESULT,
            payload = NockyConnectHandoffPayload.Result(
                offerId = "offer-1",
                status = NockyConnectHandoffResultStatus.RESTORED_PAUSED,
            ),
        )

        listOf(accept, decline, result).forEach { envelope ->
            val encoded = NockyConnectJson.format.encodeToString(
                NockyConnectHandoffEnvelope.serializer(),
                envelope,
            )
            val decoded = NockyConnectJson.format.decodeFromString(
                NockyConnectHandoffEnvelope.serializer(),
                encoded,
            )
            assertEquals(envelope, decoded)
        }
    }
}
