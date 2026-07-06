/*
 * Nocky Connect local HTTP handoff receiver.
 *
 * This receiver is intentionally small and blocking. The temporary Android
 * surface starts it from a background thread while Receive mode is active.
 */

package com.metrolist.music.connect

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

private const val HANDOFF_HTTP_HEADER_LIMIT_BYTES = 16 * 1024
private const val HANDOFF_HTTP_BODY_LIMIT_BYTES = 512 * 1024
private const val HANDOFF_HTTP_SOCKET_READ_TIMEOUT_MS = 5_000

data class NockyConnectReceivedHandoffOffer(
    val envelope: NockyConnectHandoffEnvelope,
    val remoteAddress: InetSocketAddress,
)

data class NockyConnectReceivedHandoffSnapshot(
    val offer: NockyConnectHandoffEnvelope,
    val snapshot: PlaybackSessionSnapshot,
    val restorePlan: NockyConnectRestorePlan,
    val remoteAddress: InetSocketAddress,
)

object NockyConnectHandoffHttpReceiver {
    fun receiveOne(
        localDeviceId: String,
        timeoutMs: Long,
    ): NockyConnectReceivedHandoffOffer {
        ServerSocket().use { server ->
            prepareServer(server, timeoutMs)
            val accepted = acceptRequest(server)
            val envelope = decodeOfferRequest(accepted.request)
            val accept = acceptedResponseForOffer(
                envelope = envelope,
                localDeviceId = localDeviceId,
                nowEpochMs = System.currentTimeMillis(),
            )
            writeJsonResponse(
                output = accepted.output,
                statusCode = 202,
                statusText = "Accepted",
                body = NockyConnectJson.format.encodeToString(
                    NockyConnectHandoffEnvelope.serializer(),
                    accept,
                ),
            )
            return NockyConnectReceivedHandoffOffer(
                envelope = envelope,
                remoteAddress = accepted.remoteAddress,
            )
        }
    }

    fun receiveOfferAndSnapshot(
        localDeviceId: String,
        timeoutMs: Long,
    ): NockyConnectReceivedHandoffSnapshot {
        ServerSocket().use { server ->
            prepareServer(server, timeoutMs)

            val offerRequest = acceptRequest(server)
            val offerEnvelope = decodeOfferRequest(offerRequest.request)
            val accept = acceptedResponseForOffer(
                envelope = offerEnvelope,
                localDeviceId = localDeviceId,
                nowEpochMs = System.currentTimeMillis(),
            )
            writeJsonResponse(
                output = offerRequest.output,
                statusCode = 202,
                statusText = "Accepted",
                body = NockyConnectJson.format.encodeToString(
                    NockyConnectHandoffEnvelope.serializer(),
                    accept,
                ),
            )

            val snapshotRequest = acceptRequest(server)
            val snapshot = decodeSnapshotRequest(snapshotRequest.request)
            val restorePlan = NockyConnectGateway(deviceIdProvider = { localDeviceId })
                .prepareRestore(snapshot)
            val result = resultResponseForSnapshot(
                offerEnvelope = offerEnvelope,
                nowEpochMs = System.currentTimeMillis(),
            )
            writeJsonResponse(
                output = snapshotRequest.output,
                statusCode = 202,
                statusText = "Accepted",
                body = NockyConnectJson.format.encodeToString(
                    NockyConnectHandoffEnvelope.serializer(),
                    result,
                ),
            )

            return NockyConnectReceivedHandoffSnapshot(
                offer = offerEnvelope,
                snapshot = snapshot,
                restorePlan = restorePlan,
                remoteAddress = snapshotRequest.remoteAddress,
            )
        }
    }
}

internal data class NockyConnectHttpRequest(
    val method: String,
    val path: String,
    val body: String,
)

private data class AcceptedHttpRequest(
    val request: NockyConnectHttpRequest,
    val output: OutputStream,
    val remoteAddress: InetSocketAddress,
)

internal fun decodeOfferRequest(request: NockyConnectHttpRequest): NockyConnectHandoffEnvelope {
    require(request.method == "POST") { "Unsupported handoff HTTP method: ${request.method}" }
    require(request.path == NOCKY_CONNECT_HANDOFF_PATH) { "Unsupported handoff path: ${request.path}" }

    val envelope = NockyConnectJson.format.decodeFromString(
        NockyConnectHandoffEnvelope.serializer(),
        request.body,
    )
    require(envelope.schema == HANDOFF_MESSAGE_SCHEMA) {
        "Unsupported handoff schema: ${envelope.schema}"
    }
    require(envelope.schemaVersion == NOCKY_CONNECT_PROTOCOL_VERSION) {
        "Unsupported handoff schema version: ${envelope.schemaVersion}"
    }
    require(envelope.kind == NockyConnectHandoffKind.OFFER) {
        "Unsupported handoff kind: ${envelope.kind}"
    }
    require(envelope.payload is NockyConnectHandoffPayload.Offer) {
        "Handoff offer payload expected"
    }
    return envelope
}

internal fun decodeSnapshotRequest(request: NockyConnectHttpRequest): PlaybackSessionSnapshot {
    require(request.method == "POST") { "Unsupported snapshot HTTP method: ${request.method}" }
    require(request.path == NOCKY_CONNECT_SNAPSHOT_PATH) { "Unsupported snapshot path: ${request.path}" }

    val snapshot = NockyConnectJson.decodePlaybackSessionSnapshot(request.body)
    require(snapshot.schema == PLAYBACK_SESSION_SNAPSHOT_SCHEMA) {
        "Unsupported snapshot schema: ${snapshot.schema}"
    }
    require(snapshot.schemaVersion == NOCKY_CONNECT_PROTOCOL_VERSION) {
        "Unsupported snapshot schema version: ${snapshot.schemaVersion}"
    }
    return snapshot
}

internal fun acceptedResponseForOffer(
    envelope: NockyConnectHandoffEnvelope,
    localDeviceId: String,
    nowEpochMs: Long,
): NockyConnectHandoffEnvelope {
    val offer = envelope.payload as? NockyConnectHandoffPayload.Offer
    requireNotNull(offer) { "Handoff offer payload expected" }

    return NockyConnectHandoffEnvelope(
        messageId = "android-accept-$nowEpochMs",
        createdAtEpochMs = nowEpochMs,
        kind = NockyConnectHandoffKind.ACCEPT,
        payload = NockyConnectHandoffPayload.Accept(
            offerId = offer.offerId,
            receiverDeviceId = localDeviceId,
        ),
    )
}

internal fun resultResponseForSnapshot(
    offerEnvelope: NockyConnectHandoffEnvelope,
    nowEpochMs: Long,
): NockyConnectHandoffEnvelope {
    val offer = offerEnvelope.payload as? NockyConnectHandoffPayload.Offer
    requireNotNull(offer) { "Handoff offer payload expected" }

    return NockyConnectHandoffEnvelope(
        messageId = "android-result-$nowEpochMs",
        createdAtEpochMs = nowEpochMs,
        kind = NockyConnectHandoffKind.RESULT,
        payload = NockyConnectHandoffPayload.Result(
            offerId = offer.offerId,
            status = NockyConnectHandoffResultStatus.RESTORED_PAUSED,
        ),
    )
}

private fun prepareServer(server: ServerSocket, timeoutMs: Long) {
    server.reuseAddress = true
    server.soTimeout = timeoutMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    server.bind(InetSocketAddress("0.0.0.0", NOCKY_CONNECT_HANDOFF_PORT))
}

private fun acceptRequest(server: ServerSocket): AcceptedHttpRequest {
    val socket = server.accept()
    socket.soTimeout = HANDOFF_HTTP_SOCKET_READ_TIMEOUT_MS
    val request = readHttpRequest(socket.getInputStream())
    return AcceptedHttpRequest(
        request = request,
        output = socket.getOutputStream(),
        remoteAddress = socket.remoteSocketAddress as InetSocketAddress,
    )
}

private fun readHttpRequest(input: InputStream): NockyConnectHttpRequest {
    val headerBytes = ByteArrayOutputStream()
    var previous = 0
    var current: Int
    var matched = 0
    val delimiter = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())

    while (true) {
        current = input.read()
        if (current == -1) break
        headerBytes.write(current)
        if (current.toByte() == delimiter[matched]) {
            matched += 1
            if (matched == delimiter.size) break
        } else {
            matched = if (current.toByte() == delimiter[0]) 1 else 0
        }
        require(headerBytes.size() <= HANDOFF_HTTP_HEADER_LIMIT_BYTES) {
            "Handoff HTTP headers are too large"
        }
        previous = current
    }
    require(headerBytes.size() > 0 && previous != -1) { "Empty handoff HTTP request" }

    val headerText = headerBytes.toString(StandardCharsets.UTF_8.name())
    val headerLines = headerText.split("\r\n").filter { it.isNotEmpty() }
    val requestLine = headerLines.firstOrNull().orEmpty()
    val requestParts = requestLine.split(" ")
    require(requestParts.size >= 2) { "Invalid handoff HTTP request line" }

    val contentLength = headerLines
        .drop(1)
        .firstOrNull { line -> line.startsWith("Content-Length:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.toIntOrNull()
        ?: 0
    require(contentLength in 0..HANDOFF_HTTP_BODY_LIMIT_BYTES) {
        "Invalid handoff HTTP body length: $contentLength"
    }

    val bodyBytes = ByteArray(contentLength)
    var offset = 0
    while (offset < contentLength) {
        val read = input.read(bodyBytes, offset, contentLength - offset)
        if (read == -1) throw SocketTimeoutException("Handoff HTTP body ended early")
        offset += read
    }

    return NockyConnectHttpRequest(
        method = requestParts[0],
        path = requestParts[1],
        body = String(bodyBytes, StandardCharsets.UTF_8),
    )
}

private fun writeJsonResponse(
    output: OutputStream,
    statusCode: Int,
    statusText: String,
    body: String,
) {
    val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
    val headers = buildString {
        append("HTTP/1.1 $statusCode $statusText\r\n")
        append("Content-Type: application/json; charset=utf-8\r\n")
        append("Content-Length: ${bodyBytes.size}\r\n")
        append("Connection: close\r\n")
        append("\r\n")
    }.toByteArray(StandardCharsets.UTF_8)

    output.write(headers)
    output.write(bodyBytes)
    output.flush()
}
