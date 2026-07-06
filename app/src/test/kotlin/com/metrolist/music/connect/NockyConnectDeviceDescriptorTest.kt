package com.metrolist.music.connect

import org.junit.Assert.assertEquals
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
}
