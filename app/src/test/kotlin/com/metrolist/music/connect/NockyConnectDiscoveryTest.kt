package com.metrolist.music.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class NockyConnectDiscoveryTest {
    private fun androidDescriptor(deviceId: String): NockyConnectDeviceDescriptor =
        NockyConnectDeviceDescriptor(
            deviceId = deviceId,
            deviceName = "Android phone",
            platform = NockyConnectDevicePlatform.ANDROID,
            appName = "Nocky Android",
            appVersion = "dev",
        )

    @Test
    fun discoveryHelloRoundTrips() {
        val descriptor = androidDescriptor("android-device")
        val envelope = NockyConnectDiscoveryJson.hello(
            messageId = "message-1",
            descriptor = descriptor,
        )

        val payload = NockyConnectDiscoveryJson.encode(envelope)
        val decoded = NockyConnectDiscoveryJson.decode(payload)

        assertEquals(NOCKY_CONNECT_DISCOVERY_SCHEMA, decoded.schema)
        assertEquals(NOCKY_CONNECT_PROTOCOL_VERSION, decoded.schemaVersion)
        assertEquals(NOCKY_CONNECT_DISCOVERY_MAGIC, decoded.magic)
        assertEquals("message-1", decoded.messageId)
        assertEquals(NockyConnectDiscoveryKind.HELLO, decoded.kind)
        assertEquals("android-device", decoded.descriptor.deviceId)
    }

    @Test
    fun repliesToRemoteHelloWithAnnounce() {
        val remoteDescriptor = androidDescriptor("remote-device")
        val localDescriptor = androidDescriptor("local-device")
        val hello = NockyConnectDiscoveryJson.hello(
            messageId = "hello-1",
            descriptor = remoteDescriptor,
        )
        val payload = NockyConnectDiscoveryJson.encode(hello)

        val responsePayload = NockyConnectDiscoveryJson.responseForPayload(
            payload = payload,
            localDescriptor = localDescriptor,
            responseMessageId = "announce-1",
        )
        assertNotNull(responsePayload)

        val response = NockyConnectDiscoveryJson.decode(responsePayload!!)
        assertEquals(NockyConnectDiscoveryKind.ANNOUNCE, response.kind)
        assertEquals("announce-1", response.messageId)
        assertEquals("local-device", response.descriptor.deviceId)
    }

    @Test
    fun ignoresOwnHello() {
        val localDescriptor = androidDescriptor("local-device")
        val hello = NockyConnectDiscoveryJson.hello(
            messageId = "hello-1",
            descriptor = localDescriptor,
        )
        val payload = NockyConnectDiscoveryJson.encode(hello)

        val response = NockyConnectDiscoveryJson.responseForPayload(
            payload = payload,
            localDescriptor = localDescriptor,
            responseMessageId = "announce-1",
        )

        assertNull(response)
    }

    @Test
    fun ignoresAnnouncePackets() {
        val localDescriptor = androidDescriptor("local-device")
        val remoteDescriptor = androidDescriptor("remote-device")
        val announce = NockyConnectDiscoveryJson.announce(
            messageId = "announce-remote",
            descriptor = remoteDescriptor,
        )
        val payload = NockyConnectDiscoveryJson.encode(announce)

        val response = NockyConnectDiscoveryJson.responseForPayload(
            payload = payload,
            localDescriptor = localDescriptor,
            responseMessageId = "announce-1",
        )

        assertNull(response)
    }

    @Test
    fun rejectsUnknownDiscoveryMagic() {
        val payload = """
            {
              "schema":"io.github.maylton.nocky.connect.LanDiscovery",
              "schema_version":1,
              "magic":"OTHER_APP",
              "message_id":"message-1",
              "kind":"hello",
              "descriptor":{
                "schema":"io.github.maylton.nocky.connect.DeviceDescriptor",
                "schema_version":1,
                "device_id":"android-device",
                "device_name":"Android phone",
                "platform":"android",
                "app_name":"Nocky Android",
                "app_version":"dev",
                "protocol_version":1,
                "features":["snapshot_export","snapshot_import_paused"]
              }
            }
        """.trimIndent()

        try {
            NockyConnectDiscoveryJson.decode(payload)
            fail("Expected discovery envelope with invalid magic to fail")
        } catch (error: IllegalArgumentException) {
            assertEquals(
                "Unsupported Nocky Connect discovery magic: OTHER_APP",
                error.message,
            )
        }
    }
}
