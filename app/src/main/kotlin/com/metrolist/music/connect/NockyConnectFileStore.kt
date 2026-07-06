package com.metrolist.music.connect

import java.io.File

class NockyConnectFileStore(
    baseDir: File,
) {
    private val directory: File = baseDir.resolve(DIRECTORY_NAME)

    fun writeSnapshot(snapshot: PlaybackSessionSnapshot): File =
        writeSnapshotJson(
            sessionId = snapshot.sessionId,
            revision = snapshot.revision,
            payload = NockyConnectJson.encode(snapshot),
        )

    fun writeSnapshotJson(
        sessionId: String,
        revision: Long,
        payload: String,
    ): File {
        directory.mkdirs()
        val file = directory.resolve(fileNameFor(sessionId, revision))
        file.writeText(payload)
        return file
    }

    fun readSnapshot(file: File): PlaybackSessionSnapshot =
        NockyConnectJson.decodePlaybackSessionSnapshot(file.readText())

    fun latestSnapshotFile(): File? =
        directory
            .listFiles { file -> file.isFile && file.extension == FILE_EXTENSION }
            ?.maxByOrNull { it.lastModified() }

    fun listSnapshotFiles(): List<File> =
        directory
            .listFiles { file -> file.isFile && file.extension == FILE_EXTENSION }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    companion object {
        private const val DIRECTORY_NAME = "nocky-connect"
        private const val FILE_EXTENSION = "json"

        private fun fileNameFor(
            sessionId: String,
            revision: Long,
        ): String {
            val safeSessionId = sessionId
                .ifBlank { "session" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(80)
            return "snapshot_${safeSessionId}_r${revision.coerceAtLeast(0L)}.$FILE_EXTENSION"
        }
    }
}
