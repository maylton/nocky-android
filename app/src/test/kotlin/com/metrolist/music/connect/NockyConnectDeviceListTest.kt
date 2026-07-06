package com.metrolist.music.connect

import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NockyConnectDeviceListTest {
    private fun descriptor(
        deviceId: String,
        deviceName: String,
    ): NockyConnectDeviceDescriptor = NockyConnectDeviceDescriptor(
        deviceId = deviceId,
        deviceName = deviceName,
        platform = NockyConnectDevicePlatform.ANDROID,
        appName = "Nocky Android",
    )

    private fun discovered(
        deviceId: String,
        deviceName: String,
        port: Int,
    ): NockyConnectDiscoveredDevice = NockyConnectDiscoveredDevice(
        descriptor = descriptor(deviceId, deviceName),
        address = InetSocketAddress("192.168.0.8", port),
    )

    @Test
    fun upsertsDevicesByDeviceId() {
        val list = NockyConnectDeviceList()

        list.upsert(discovered("android-1", "Samsung", 34987), nowMs = 1_000L)
        list.upsert(discovered("android-1", "Samsung Renamed", 40000), nowMs = 2_000L)

        assertEquals(1, list.size)
        val entry = list["android-1"]
        assertNotNull(entry)
        assertEquals("Samsung Renamed", entry!!.descriptor.deviceName)
        assertEquals(40000, entry.addressPort)
        assertEquals(2_000L, entry.lastSeenMs)
    }

    @Test
    fun removesStaleDevices() {
        val list = NockyConnectDeviceList()

        list.upsert(discovered("fresh", "Fresh", 34987), nowMs = 10_000L)
        list.upsert(discovered("old", "Old", 34988), nowMs = 1_000L)

        list.removeStale(nowMs = 11_000L, maxAgeMs = 5_000L)

        assertNotNull(list["fresh"])
        assertNull(list["old"])
    }

    @Test
    fun entriesAreOrderedByMostRecentFirst() {
        val list = NockyConnectDeviceList()

        list.upsert(discovered("old", "Old", 34987), nowMs = 1_000L)
        list.upsert(discovered("fresh", "Fresh", 34988), nowMs = 2_000L)

        val entries = list.entries()

        assertEquals("fresh", entries[0].descriptor.deviceId)
        assertEquals("old", entries[1].descriptor.deviceId)
    }

    @Test
    fun lastSeenLabelUsesFriendlyRelativeText() {
        val list = NockyConnectDeviceList()
        list.upsert(discovered("android", "Samsung", 34987), nowMs = 1_000L)

        val entry = list["android"]!!

        assertEquals("just now", entry.lastSeenLabel(nowMs = 1_500L))
        assertEquals("4s ago", entry.lastSeenLabel(nowMs = 5_000L))
        assertFalse(list.isEmpty)
    }
}
