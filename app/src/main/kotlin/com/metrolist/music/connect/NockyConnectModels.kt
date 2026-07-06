/*
 * Nocky Connect integration
 * Licensed under GPL-3.0 | See project license for details
 */

package com.metrolist.music.connect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val NOCKY_CONNECT_PROTOCOL_VERSION = 1
const val PLAYBACK_SESSION_SNAPSHOT_SCHEMA = "io.github.maylton.nocky.connect.PlaybackSessionSnapshot"

/**
 * Shared JSON codec for Nocky Connect payloads.
 *
 * Keep this isolated from app persistence and player internals so protocol payloads
 * can remain stable even while the Metrolist fork is gradually adapted.
 */
object NockyConnectJson {
    val format: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(snapshot: PlaybackSessionSnapshot): String =
        format.encodeToString(PlaybackSessionSnapshot.serializer(), snapshot)

    fun decodePlaybackSessionSnapshot(payload: String): PlaybackSessionSnapshot =
        format.decodeFromString(PlaybackSessionSnapshot.serializer(), payload)
}

@Serializable
data class PlaybackSessionSnapshot(
    val schema: String = PLAYBACK_SESSION_SNAPSHOT_SCHEMA,
    @SerialName("schema_version")
    val schemaVersion: Int = NOCKY_CONNECT_PROTOCOL_VERSION,
    @SerialName("session_id")
    val sessionId: String,
    val revision: Long,
    @SerialName("origin_device_id")
    val originDeviceId: String,
    @SerialName("updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @SerialName("updated_at_monotonic_ms")
    val updatedAtMonotonicMs: Long? = null,
    val source: NockyConnectSource,
    val playback: PlaybackInfo,
    val queue: PortableQueue,
)

@Serializable
data class PlaybackInfo(
    val state: NockyPlaybackState,
    @SerialName("position_ms")
    val positionMs: Long,
    @SerialName("duration_ms")
    val durationMs: Long? = null,
    val rate: Float = 1.0f,
    val volume: Float? = null,
    val muted: Boolean = false,
)

@Serializable
data class PortableQueue(
    val title: String? = null,
    @SerialName("current_index")
    val currentIndex: Int,
    @SerialName("repeat_mode")
    val repeatMode: NockyRepeatMode,
    @SerialName("shuffle_enabled")
    val shuffleEnabled: Boolean,
    @SerialName("shuffle_seed")
    val shuffleSeed: Long? = null,
    val items: List<PortableQueueItem>,
)

@Serializable
data class PortableQueueItem(
    @SerialName("queue_item_id")
    val queueItemId: String,
    val source: NockyConnectSource,
    val provider: String,
    @SerialName("playable_id")
    val playableId: String,
    @SerialName("set_video_id")
    val setVideoId: String? = null,
    @SerialName("playlist_id")
    val playlistId: String? = null,
    @SerialName("browse_id")
    val browseId: String? = null,
    val title: String,
    val artists: List<PortableArtist> = emptyList(),
    val album: PortableAlbum? = null,
    @SerialName("duration_ms")
    val durationMs: Long? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    val explicit: Boolean = false,
    @SerialName("is_video")
    val isVideo: Boolean = false,
    @SerialName("is_episode")
    val isEpisode: Boolean = false,
    val local: LocalTrackIdentity? = null,
)

@Serializable
data class PortableArtist(
    val id: String? = null,
    val name: String,
)

@Serializable
data class PortableAlbum(
    val id: String? = null,
    val title: String,
)

@Serializable
data class LocalTrackIdentity(
    @SerialName("library_id")
    val libraryId: String? = null,
    @SerialName("content_hash")
    val contentHash: String? = null,
    @SerialName("relative_path")
    val relativePath: String? = null,
    @SerialName("file_size")
    val fileSize: Long? = null,
    @SerialName("modified_at_epoch_ms")
    val modifiedAtEpochMs: Long? = null,
)

@Serializable
enum class NockyConnectSource {
    @SerialName("youtube")
    YOUTUBE,

    @SerialName("local")
    LOCAL,

    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
enum class NockyPlaybackState {
    @SerialName("idle")
    IDLE,

    @SerialName("loading")
    LOADING,

    @SerialName("playing")
    PLAYING,

    @SerialName("paused")
    PAUSED,

    @SerialName("ended")
    ENDED,

    @SerialName("error")
    ERROR,
}

@Serializable
enum class NockyRepeatMode {
    @SerialName("off")
    OFF,

    @SerialName("one")
    ONE,

    @SerialName("all")
    ALL,
}
