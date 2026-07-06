/*
 * Nocky Connect handoff HTTP sender
 * Licensed under GPL-3.0 | See project license for details
 */

package com.metrolist.music.connect

import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

private const val HANDOFF_HTTP_RESPONSE_LIMIT_BYTES = 512 * 1024
private const val HANDOFF_HTTP_TIMEOUT_MS = 5_000

data class NockyConnectHandoffHttpTarget(
    val host: String,
    val port: Int,
    val path: String,
) {
    val url: String
        get() = "http://$host:$port$path"
}

object NockyConnectHandoffHttpClient {
    fun targetFromDiscoveredDevice(device: NockyConnectDiscoveredDevice): NockyConnectHandoffHttpTarget {
        val endpoint = device.descriptor.handoffEndpoint
            ?: error("${device.descriptor.deviceName} does not advertise a handoff endpoint")
        require(endpoint.transport == NockyConnectHandoffTransport.LOCAL_HTTP) {
            "Unsupported handoff transport: ${endpoint.transport}"
        }
        val host = device.address.address.hostAddress
            ?: error("Could not resolve discovered device address")
        return NockyConnectHandoffHttpTarget(
            host = host,
            port = endpoint.port,
            path = endpoint.path,
        )
    }

    fun sendOfferAndSnapshot(
        target: NockyConnectHandoffHttpTarget,
        offer: NockyConnectHandoffEnvelope,
        snapshotJson: String,
    ): NockyConnectHandoffEnvelope {
        val offerResponse = sendJson(
            target = target,
            path = target.path,
            body = NockyConnectJson.format.encodeToString(
                NockyConnectHandoffEnvelope.serializer(),
                offer,
            ),
        )
        require(offerResponse.kind == NockyConnectHandoffKind.ACCEPT) {
            "Desktop did not accept handoff: ${offerResponse.kind}"
        }
        val resultResponse = sendJson(target, NOCKY_CONNECT_SNAPSHOT_PATH, snapshotJson)
        require(resultResponse.kind == NockyConnectHandoffKind.RESULT) {
            "Desktop did not return handoff result: ${resultResponse.kind}"
        }
        return resultResponse
    }

    internal fun buildPostRequest(
        target: NockyConnectHandoffHttpTarget,
        path: String,
        body: String,
    ): ByteArray {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val headers = buildString {
            append("POST $normalizedPath HTTP/1.1\r\n")
            append("Host: ${target.host}:${target.port}\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        return headers + bodyBytes
    }

    private fun sendJson(
        target: NockyConnectHandoffHttpTarget,
        path: String,
        body: String,
    ): NockyConnectHandoffEnvelope {
        Socket().use { socket ->
            socket.soTimeout = HANDOFF_HTTP_TIMEOUT_MS
            socket.connect(InetSocketAddress(target.host, target.port), HANDOFF_HTTP_TIMEOUT_MS)
            val output = socket.getOutputStream()
            output.write(buildPostRequest(target, path, body))
            output.flush()
            val response = readHttpResponse(socket)
            return decodeResponse(response)
        }
    }

    private fun readHttpResponse(socket: Socket): String {
        val input = socket.getInputStream()
        val buffer = ByteArray(4096)
        val response = StringBuilder()
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            response.append(String(buffer, 0, read, StandardCharsets.UTF_8))
            require(response.length <= HANDOFF_HTTP_RESPONSE_LIMIT_BYTES) {
                "Handoff HTTP response too large"
            }
            if (hasCompleteBody(response.toString())) break
        }
        return response.toString()
    }

    private fun hasCompleteBody(response: String): Boolean {
        val headerEnd = response.indexOf("\r\n\r\n")
        if (headerEnd < 0) return false
        val headers = response.substring(0, headerEnd)
        val contentLength = headers
            .lineSequence()
            .firstOrNull { it.lowercase().startsWith("content-length:") }
            ?.substringAfter(':')
            ?.trim()
            ?.toIntOrNull()
            ?: return false
        return response.length - (headerEnd + 4) >= contentLength
    }

    private fun decodeResponse(response: String): NockyConnectHandoffEnvelope {
        val parts = response.split("\r\n\r\n", limit = 2)
        require(parts.size == 2) { "Invalid handoff HTTP response" }
        val statusLine = parts[0].lineSequence().firstOrNull().orEmpty()
        require(statusLine.contains(" 200 ") || statusLine.contains(" 202 ")) {
            "Handoff HTTP request failed: $statusLine"
        }
        return NockyConnectJson.format.decodeFromString(
            NockyConnectHandoffEnvelope.serializer(),
            parts[1].trimEnd('\u0000'),
        )
    }
}
