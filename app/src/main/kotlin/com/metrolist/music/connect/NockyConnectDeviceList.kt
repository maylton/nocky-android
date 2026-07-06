package com.metrolist.music.connect

private const val MILLIS_PER_SECOND = 1_000L

data class NockyConnectDeviceListEntry(
    val descriptor: NockyConnectDeviceDescriptor,
    val addressHost: String,
    val addressPort: Int,
    val lastSeenMs: Long,
) {
    val secondsSinceLastSeen: Long
        get() = 0L
}

class NockyConnectDeviceList {
    private val devices = linkedMapOf<String, NockyConnectDeviceListEntry>()

    val size: Int
        get() = devices.size

    val isEmpty: Boolean
        get() = devices.isEmpty()

    fun updateWithDiscovered(
        discoveredDevices: Iterable<NockyConnectDiscoveredDevice>,
        nowMs: Long,
    ) {
        discoveredDevices.forEach { device ->
            upsert(device, nowMs)
        }
    }

    fun upsert(
        device: NockyConnectDiscoveredDevice,
        nowMs: Long,
    ) {
        devices[device.descriptor.deviceId] = NockyConnectDeviceListEntry(
            descriptor = device.descriptor,
            addressHost = device.address.address.hostAddress.orEmpty(),
            addressPort = device.address.port,
            lastSeenMs = nowMs,
        )
    }

    fun removeStale(
        nowMs: Long,
        maxAgeMs: Long,
    ) {
        val iterator = devices.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val age = nowMs - entry.lastSeenMs
            if (age > maxAgeMs.coerceAtLeast(0L)) {
                iterator.remove()
            }
        }
    }

    fun entries(): List<NockyConnectDeviceListEntry> =
        devices.values.sortedWith(
            compareByDescending<NockyConnectDeviceListEntry> { it.lastSeenMs }
                .thenBy { it.descriptor.deviceName },
        )

    operator fun get(deviceId: String): NockyConnectDeviceListEntry? = devices[deviceId]
}

fun NockyConnectDeviceListEntry.lastSeenLabel(nowMs: Long): String {
    val ageMs = (nowMs - lastSeenMs).coerceAtLeast(0L)
    if (ageMs < MILLIS_PER_SECOND) return "just now"
    val seconds = ageMs / MILLIS_PER_SECOND
    return "${seconds}s ago"
}
