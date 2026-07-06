/*
 * Nocky Connect LAN discovery contract.
 *
 * This module intentionally defines only the shared discovery envelope. The
 * socket transport and explicit accept/deny confirmation flow will be wired in
 * separate Android components.
 */

package com.metrolist.music.connect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val NOCKY_CONNECT_DISCOVERY_SCHEMA = "io.github.maylton.nocky.connect.LanDiscovery"
const val NOCKY_CONNECT_DISCOVERY_PORT = 34987
const val NOCKY_CONNECT_DISCOVERY_MAGIC = "NOCKY_CONNECT_DISCOVERY_V1"

@Serializable
data class NockyConnectDiscoveryEnvelope(
    val schema: String = NOCKY_CONNECT_DISCOVERY_SCHEMA,
    @SerialName("schema_version")
    val schemaVersion: Int = NOCKY_CONNECT_PROTOCOL_VERSION,
    val magic: String = NOCKY_CONNECT_DISCOVERY_MAGIC,
    @SerialName("message_id")
    val messageId: String,
    val kind: NockyConnectDiscoveryKind,
    val descriptor: NockyConnectDeviceDescriptor,
)

@Serializable
enum class NockyConnectDiscoveryKind {
    @SerialName("hello")
    HELLO,

    @SerialName("announce")
    ANNOUNCE,
}

object NockyConnectDiscoveryJson {
    fun hello(
        messageId: String,
        descriptor: NockyConnectDeviceDescriptor,
    ): NockyConnectDiscoveryEnvelope = NockyConnectDiscoveryEnvelope(
        messageId = messageId,
        kind = NockyConnectDiscoveryKind.HELLO,
        descriptor = descriptor,
    )

    fun announce(
        messageId: String,
        descriptor: NockyConnectDeviceDescriptor,
    ): NockyConnectDiscoveryEnvelope = NockyConnectDiscoveryEnvelope(
        messageId = messageId,
        kind = NockyConnectDiscoveryKind.ANNOUNCE,
        descriptor = descriptor,
    )

    fun encode(envelope: NockyConnectDiscoveryEnvelope): String =
        NockyConnectJson.format.encodeToString(NockyConnectDiscoveryEnvelope.serializer(), envelope)

    fun decode(payload: String): NockyConnectDiscoveryEnvelope =
        NockyConnectJson.format
            .decodeFromString(NockyConnectDiscoveryEnvelope.serializer(), payload)
            .also(::requireSupported)

    fun requireSupported(envelope: NockyConnectDiscoveryEnvelope) {
        require(envelope.schema == NOCKY_CONNECT_DISCOVERY_SCHEMA) {
            "Unsupported Nocky Connect discovery schema: ${envelope.schema}"
        }
        require(envelope.schemaVersion == NOCKY_CONNECT_PROTOCOL_VERSION) {
            "Unsupported Nocky Connect discovery schema version: ${envelope.schemaVersion}"
        }
        require(envelope.magic == NOCKY_CONNECT_DISCOVERY_MAGIC) {
            "Unsupported Nocky Connect discovery magic: ${envelope.magic}"
        }
        NockyConnectDeviceDescriptorJson.requireSupported(envelope.descriptor)
    }
}
