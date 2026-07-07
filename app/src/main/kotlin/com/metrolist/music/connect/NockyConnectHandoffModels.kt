/*
 * Nocky Connect handoff protocol contracts
 * Licensed under GPL-3.0 | See project license for details
 */

package com.metrolist.music.connect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val HANDOFF_MESSAGE_SCHEMA = "io.github.maylton.nocky.connect.HandoffMessage"

@Serializable
data class NockyConnectHandoffEnvelope(
    val schema: String = HANDOFF_MESSAGE_SCHEMA,
    @SerialName("schema_version")
    val schemaVersion: Int = NOCKY_CONNECT_PROTOCOL_VERSION,
    @SerialName("message_id")
    val messageId: String,
    @SerialName("created_at_epoch_ms")
    val createdAtEpochMs: Long,
    val kind: NockyConnectHandoffKind,
    val payload: NockyConnectHandoffPayload,
)

@Serializable
enum class NockyConnectHandoffKind {
    @SerialName("handoff_offer")
    OFFER,

    @SerialName("handoff_accept")
    ACCEPT,

    @SerialName("handoff_decline")
    DECLINE,

    @SerialName("handoff_result")
    RESULT,
}

@Serializable
sealed class NockyConnectHandoffPayload {
    @Serializable
    @SerialName("offer")
    data class Offer(
        @SerialName("offer_id")
        val offerId: String,
        @SerialName("sender_device_id")
        val senderDeviceId: String,
        @SerialName("sender_device_name")
        val senderDeviceName: String,
        @SerialName("receiver_device_id")
        val receiverDeviceId: String,
        @SerialName("snapshot_summary")
        val snapshotSummary: NockyConnectSnapshotSummary,
        @SerialName("restore_policy")
        val restorePolicy: NockyConnectRestorePolicy = NockyConnectRestorePolicy.RESTORE_PAUSED,
    ) : NockyConnectHandoffPayload()

    @Serializable
    @SerialName("accept")
    data class Accept(
        @SerialName("offer_id")
        val offerId: String,
        @SerialName("receiver_device_id")
        val receiverDeviceId: String,
    ) : NockyConnectHandoffPayload()

    @Serializable
    @SerialName("decline")
    data class Decline(
        @SerialName("offer_id")
        val offerId: String,
        @SerialName("receiver_device_id")
        val receiverDeviceId: String,
        val reason: NockyConnectHandoffDeclineReason,
    ) : NockyConnectHandoffPayload()

    @Serializable
    @SerialName("result")
    data class Result(
        @SerialName("offer_id")
        val offerId: String,
        val status: NockyConnectHandoffResultStatus,
        @SerialName("error_message")
        val errorMessage: String? = null,
    ) : NockyConnectHandoffPayload()
}

@Serializable
data class NockyConnectSnapshotSummary(
    val source: NockyConnectSource,
    @SerialName("current_title")
    val currentTitle: String? = null,
    @SerialName("current_artist")
    val currentArtist: String? = null,
    @SerialName("queue_items")
    val queueItems: Int,
    @SerialName("position_ms")
    val positionMs: Long,
    @SerialName("duration_ms")
    val durationMs: Long? = null,
    @SerialName("was_playing")
    val wasPlaying: Boolean,
)

@Serializable
enum class NockyConnectRestorePolicy {
    @SerialName("restore_paused")
    RESTORE_PAUSED,
}

@Serializable
enum class NockyConnectHandoffDeclineReason {
    @SerialName("user_declined")
    USER_DECLINED,

    @SerialName("busy")
    BUSY,

    @SerialName("unsupported")
    UNSUPPORTED,
}

@Serializable
enum class NockyConnectHandoffResultStatus {
    @SerialName("restored_paused")
    RESTORED_PAUSED,

    @SerialName("failed")
    FAILED,
}
