package com.metrolist.music.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NockyConnectDeviceDescriptorTest {
    @Test
    fun encodesAndDecodesAndroidDescriptor() {
        val descriptor = NockyConnectDeviceDescriptor(
            deviceId = "android-device",
            deviceName = "Android phone",
            platform = NockyConnectDevicePlatform.ANDROID,
            appName = "Nocky Android",
            appVersion = "dev",
        )

        val payload = NockyConnectDeviceDescriptorJson.encode(descriptor)
        val decoded = NockyConnectDeviceDescriptorJson.decode(payload)

        assertEquals(DEVICE_DESCRIPTOR_SCHEMA, decoded.schema)
        assertEquals(NOCKY_CONNECT_PROTOCOL_VERSION, decoded.schemaVersion)
        assertEquals("android-device", decoded.deviceId)
        assertEquals(NockyConnectDevicePlatform.ANDROID, decoded.platform)
        assertTrue(decoded.features.contains(NockyConnectFeature.SNAPSHOT_EXPORT))
        assertTrue(decoded.features.contains(NockyConnectFeature.SNAPSHOT_IMPORT_PAUSED))
        assertTrue(decoded.features.contains(NockyConnectFeature.HANDOFF_OFFER))
        assertNull(decoded.handoffEndpoint)
    }

    @Test
    fun encodesAndDecodesAndroidDescriptorWithHandoffEndpoint() {
        val descriptor = NockyConnectDeviceDescriptor(
            deviceId = "android-device",
            deviceName = "Android phone",
            platform = NockyConnectDevicePlatform.ANDROID,
            appName = "Nocky Android",
            appVersion = "dev",
            handoffEndpoint = NockyConnectHandoffEndpoint(
                transport = NockyConnectHandoffTransport.LOCAL_HTTP,
                port = NOCKY_CONNECT_HANDOFF_PORT,
            ),
        )

        val payload = NockyConnectDeviceDescriptorJson.encode(descriptor)
        val decoded = NockyConnectDeviceDescriptorJson.decode(payload)

        assertEquals(
            NockyConnectHandoffEndpoint(
                transport = NockyConnectHandoffTransport.LOCAL_HTTP,
                port = NOCKY_CONNECT_HANDOFF_PORT,
            ),
            decoded.handoffEndpoint,
        )
        assertTrue(payload.contains("handoff_endpoint"))
        assertTrue(payload.contains("local_http"))
        assertTrue(payload.contains(NOCKY_CONNECT_HANDOFF_PATH))
    }

    @Test
    fun decodesSharedV1DescriptorFixture() {
        val payload = readFixture()

        val descriptor = NockyConnectDeviceDescriptorJson.decode(payload)

        assertEquals(DEVICE_DESCRIPTOR_SCHEMA, descriptor.schema)
        assertEquals(NOCKY_CONNECT_PROTOCOL_VERSION, descriptor.schemaVersion)
        assertEquals("fixture-android-device", descriptor.deviceId)
        assertEquals("Fixture Android phone", descriptor.deviceName)
        assertEquals(NockyConnectDevicePlatform.ANDROID, descriptor.platform)
        assertEquals(NOCKY_CONNECT_PROTOCOL_VERSION, descriptor.protocolVersion)
        assertTrue(descriptor.features.contains(NockyConnectFeature.SNAPSHOT_EXPORT))
        assertTrue(descriptor.features.contains(NockyConnectFeature.SNAPSHOT_IMPORT_PAUSED))
        assertTrue(descriptor.features.contains(NockyConnectFeature.FILE_ROUND_TRIP))
        assertNull(descriptor.handoffEndpoint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedProtocolVersion() {
        val payload = """
            {
              "schema": "$DEVICE_DESCRIPTOR_SCHEMA",
              "schema_version": $NOCKY_CONNECT_PROTOCOL_VERSION,
              "device_id": "future-device",
              "device_name": "Future device",
              "platform": "android",
              "app_name": "Nocky Android",
              "app_version": "future",
              "protocol_version": ${NOCKY_CONNECT_PROTOCOL_VERSION + 1},
              "features": ["snapshot_export"]
            }
        """.trimIndent()

        NockyConnectDeviceDescriptorJson.decode(payload)
    }

    private fun readFixture(): String =
        requireNotNull(
            javaClass.classLoader?.getResourceAsStream("nocky-connect-device-descriptor-v1.json"),
        ) { "Missing nocky-connect-device-descriptor-v1.json test resource" }
            .bufferedReader()
            .use { it.readText() }
}
