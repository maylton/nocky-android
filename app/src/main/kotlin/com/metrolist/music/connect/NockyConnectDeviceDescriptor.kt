package com.metrolist.music.connect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val DEVICE_DESCRIPTOR_SCHEMA = "io.github.maylton.nocky.connect.DeviceDescriptor"

@Serializable
data class NockyConnectDeviceDescriptor(
    val schema: String = DEVICE_DESCRIPTOR_SCHEMA,
    @SerialName("schema_version")
    val schemaVersion: Int = NOCKY_CONNECT_PROTOCOL_VERSION,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("device_name")
    val deviceName: String,
    val platform: NockyConnectDevicePlatform,
    @SerialName("app_name")
    val appName: String,
    @SerialName("app_version")
    val appVersion: String? = null,
    @SerialName("protocol_version")
    val protocolVersion: Int = NOCKY_CONNECT_PROTOCOL_VERSION,
    val features: List<NockyConnectFeature> = listOf(
        NockyConnectFeature.SNAPSHOT_EXPORT,
        NockyConnectFeature.SNAPSHOT_IMPORT_PAUSED,
        NockyConnectFeature.FILE_ROUND_TRIP,
        NockyConnectFeature.LAN_PAIRING,
        NockyConnectFeature.HANDOFF_ACK,
        NockyConnectFeature.HANDOFF_OFFER,
    ),
    @SerialName("handoff_endpoint")
    val handoffEndpoint: NockyConnectHandoffEndpoint? = null,
)

@Serializable
enum class NockyConnectDevicePlatform {
    @SerialName("android")
    ANDROID,

    @SerialName("linux_desktop")
    LINUX_DESKTOP,

    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
enum class NockyConnectFeature {
    @SerialName("snapshot_export")
    SNAPSHOT_EXPORT,

    @SerialName("snapshot_import_paused")
    SNAPSHOT_IMPORT_PAUSED,

    @SerialName("file_round_trip")
    FILE_ROUND_TRIP,

    @SerialName("lan_pairing")
    LAN_PAIRING,

    @SerialName("handoff_ack")
    HANDOFF_ACK,

    @SerialName("handoff_offer")
    HANDOFF_OFFER,
}

@Serializable
data class NockyConnectHandoffEndpoint(
    val transport: NockyConnectHandoffTransport,
    val port: Int,
    val path: String = "/nocky-connect/handoff",
) {
    init {
        require(port in 1..65_535) { "Invalid Nocky Connect handoff port: $port" }
    }
}

@Serializable
enum class NockyConnectHandoffTransport {
    @SerialName("local_http")
    LOCAL_HTTP,
}

object NockyConnectDeviceDescriptorJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(descriptor: NockyConnectDeviceDescriptor): String =
        json.encodeToString(NockyConnectDeviceDescriptor.serializer(), descriptor)

    fun decode(payload: String): NockyConnectDeviceDescriptor =
        json.decodeFromString(NockyConnectDeviceDescriptor.serializer(), payload).also(::requireSupported)

    fun requireSupported(descriptor: NockyConnectDeviceDescriptor) {
        require(descriptor.schema == DEVICE_DESCRIPTOR_SCHEMA) {
            "Unsupported Nocky Connect device descriptor schema: ${descriptor.schema}"
        }
        require(descriptor.schemaVersion == NOCKY_CONNECT_PROTOCOL_VERSION) {
            "Unsupported Nocky Connect device descriptor schema version: ${descriptor.schemaVersion}"
        }
        require(descriptor.protocolVersion == NOCKY_CONNECT_PROTOCOL_VERSION) {
            "Unsupported Nocky Connect protocol version: ${descriptor.protocolVersion}"
        }
    }
}
