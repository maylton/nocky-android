package com.metrolist.music.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NockyConnectDiscoveryTest {
    @Test
    fun discoveryHelloRoundTrips() {
        val descriptor = NockyConnectDeviceDescriptor(
            deviceId = "android-device",
            deviceName = "Android phone",
            platform = NockyConnectDevicePlatform.ANDROID,
            appName = "Nocky Android",
            appVersion = "dev",
        )
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

        val error = assertFailsWith<IllegalArgumentException> {
            NockyConnectDiscoveryJson.decode(payload)
        }
        assertEquals(
            "Unsupported Nocky Connect discovery magic: OTHER_APP",
            error.message,
        )
    }
}
