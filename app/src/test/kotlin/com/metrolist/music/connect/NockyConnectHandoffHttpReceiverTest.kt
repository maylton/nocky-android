/*
 * Nocky Connect local HTTP handoff receiver tests
 * Licensed under GPL-3.0 | See project license for details
 */

package com.metrolist.music.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NockyConnectHandoffHttpReceiverTest {
    @Test
    fun decodesPostOfferRequest() {
        val envelope = sampleOfferEnvelope()
        val request = NockyConnectHttpRequest(
            method = "POST",
            path = NOCKY_CONNECT_HANDOFF_PATH,
            body = NockyConnectJson.format.encodeToString(
                NockyConnectHandoffEnvelope.serializer(),
                envelope,
            ),
        )

        val decoded = decodeOfferRequest(request)

        assertEquals(envelope, decoded)
    }

    @Test
    fun buildsAcceptedResponseForOffer() {
        val accept = acceptedResponseForOffer(
            envelope = sampleOfferEnvelope(),
            localDeviceId = "android-1",
            nowEpochMs = 1_789_100L,
        )

        assertEquals(NockyConnectHandoffKind.ACCEPT, accept.kind)
        val payload = accept.payload as NockyConnectHandoffPayload.Accept
        assertEquals("offer-1", payload.offerId)
        assertEquals("android-1", payload.receiverDeviceId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongPath() {
        decodeOfferRequest(
            NockyConnectHttpRequest(
                method = "POST",
                path = "/wrong",
                body = NockyConnectJson.format.encodeToString(
                    NockyConnectHandoffEnvelope.serializer(),
                    sampleOfferEnvelope(),
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonOfferKind() {
        val accept = NockyConnectHandoffEnvelope(
            messageId = "accept-message-1",
            createdAtEpochMs = 1_789_001L,
            kind = NockyConnectHandoffKind.ACCEPT,
            payload = NockyConnectHandoffPayload.Accept(
                offerId = "offer-1",
                receiverDeviceId = "android-1",
            ),
        )

        decodeOfferRequest(
            NockyConnectHttpRequest(
                method = "POST",
                path = NOCKY_CONNECT_HANDOFF_PATH,
                body = NockyConnectJson.format.encodeToString(
                    NockyConnectHandoffEnvelope.serializer(),
                    accept,
                ),
            ),
        )
    }

    private fun sampleOfferEnvelope(): NockyConnectHandoffEnvelope = NockyConnectHandoffEnvelope(
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
    ).also { envelope ->
        val encoded = NockyConnectJson.format.encodeToString(
            NockyConnectHandoffEnvelope.serializer(),
            envelope,
        )
        assertTrue(encoded.contains("handoff_offer"))
    }
}
