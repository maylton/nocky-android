/*
 * Nocky Connect UDP discovery transport.
 *
 * This module is intentionally blocking and UI-free. Callers should execute it
 * from a background dispatcher/thread and then render the returned devices in
 * the Android Nocky Connect surface.
 */

package com.metrolist.music.connect

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

private const val DISCOVERY_BUFFER_BYTES = 64 * 1024
private const val DISCOVERY_READ_TIMEOUT_MS = 120

data class NockyConnectDiscoveredDevice(
    val descriptor: NockyConnectDeviceDescriptor,
    val address: InetSocketAddress,
)

object NockyConnectUdpDiscovery {
    fun scanOnce(
        localDescriptor: NockyConnectDeviceDescriptor,
        timeoutMs: Long,
    ): List<NockyConnectDiscoveredDevice> {
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.soTimeout = DISCOVERY_READ_TIMEOUT_MS
            socket.bind(InetSocketAddress(0))

            val hello = NockyConnectDiscoveryJson.hello(
                messageId = nextDiscoveryMessageId("android-hello"),
                descriptor = localDescriptor,
            )
            val payload = NockyConnectDiscoveryJson.encode(hello).toByteArray(StandardCharsets.UTF_8)
            socket.send(
                DatagramPacket(
                    payload,
                    payload.size,
                    InetAddress.getByName("255.255.255.255"),
                    NOCKY_CONNECT_DISCOVERY_PORT,
                ),
            )

            return collectDiscoveryReplies(
                socket = socket,
                localDescriptor = localDescriptor,
                timeoutMs = timeoutMs,
            )
        }
    }

    private fun collectDiscoveryReplies(
        socket: DatagramSocket,
        localDescriptor: NockyConnectDeviceDescriptor,
        timeoutMs: Long,
    ): List<NockyConnectDiscoveredDevice> {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
        val devices = linkedMapOf<String, NockyConnectDiscoveredDevice>()
        val buffer = ByteArray(DISCOVERY_BUFFER_BYTES)

        while (System.currentTimeMillis() < deadline) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            }

            val payload = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)

            runCatching {
                NockyConnectDiscoveryJson.responseForPayload(
                    payload = payload,
                    localDescriptor = localDescriptor,
                    responseMessageId = nextDiscoveryMessageId("android-announce"),
                )
            }.getOrNull()?.let { responsePayload ->
                val responseBytes = responsePayload.toByteArray(StandardCharsets.UTF_8)
                socket.send(
                    DatagramPacket(
                        responseBytes,
                        responseBytes.size,
                        packet.address,
                        packet.port,
                    ),
                )
            }

            val envelope = runCatching { NockyConnectDiscoveryJson.decode(payload) }.getOrNull()
                ?: continue
            if (envelope.descriptor.deviceId == localDescriptor.deviceId) continue
            if (envelope.kind != NockyConnectDiscoveryKind.HELLO &&
                envelope.kind != NockyConnectDiscoveryKind.ANNOUNCE
            ) {
                continue
            }

            devices[envelope.descriptor.deviceId] = NockyConnectDiscoveredDevice(
                descriptor = envelope.descriptor,
                address = InetSocketAddress(packet.address, packet.port),
            )
        }

        return devices.values.toList()
    }

    internal fun nextDiscoveryMessageId(prefix: String): String =
        "$prefix-${System.currentTimeMillis()}"
}
