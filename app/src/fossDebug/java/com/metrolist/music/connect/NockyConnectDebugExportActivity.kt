package com.metrolist.music.connect

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.PersistPlayerState
import com.metrolist.music.models.PersistQueue
import com.metrolist.music.models.QueueType
import com.metrolist.music.playback.MusicService
import timber.log.Timber
import java.io.File

class NockyConnectDebugExportActivity : Activity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runOnUiThread {
                    exportSnapshot(future)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    override fun onDestroy() {
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        controllerFuture = null
        super.onDestroy()
    }

    private fun exportSnapshot(future: ListenableFuture<MediaController>) {
        val controller = try {
            future.get()
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Failed to connect to MusicService for Nocky Connect export")
            toast("Nocky Connect export failed: service unavailable")
            finish()
            return
        }

        controllerFuture = null

        try {
            val items = (0 until controller.mediaItemCount)
                .mapNotNull { index -> controller.getMediaItemAt(index).metadata }

            if (items.isEmpty()) {
                toast("Nocky Connect export skipped: no active queue")
                return
            }

            val currentIndex = controller.currentMediaItemIndex
                .takeIf { index -> index in items.indices }
                ?: 0
            val currentPosition = controller.currentPosition.coerceAtLeast(0L)
            val snapshot = NockyConnectGateway(
                deviceIdProvider = { getOrCreateNockyConnectDeviceId() },
            ).exportSnapshot(
                queue = PersistQueue(
                    title = null,
                    items = items,
                    mediaItemIndex = currentIndex,
                    position = currentPosition,
                    queueType = QueueType.YOUTUBE,
                ),
                playerState = PersistPlayerState(
                    playWhenReady = controller.playWhenReady,
                    repeatMode = controller.repeatMode,
                    shuffleModeEnabled = controller.shuffleModeEnabled,
                    volume = controller.volume.coerceIn(0f, 1f),
                    currentPosition = currentPosition,
                    currentMediaItemIndex = currentIndex,
                    playbackState = controller.playbackState,
                ),
            )

            val file = NockyConnectFileStore(exportBaseDir()).writeSnapshot(snapshot)
            Timber.tag(TAG).i("Exported Nocky Connect snapshot to ${file.absolutePath}")
            toast("Nocky snapshot exported: ${file.name}")
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Failed to export Nocky Connect snapshot")
            toast("Nocky Connect export failed")
        } finally {
            controller.release()
            finish()
        }
    }

    private fun exportBaseDir(): File = getExternalFilesDir(null) ?: filesDir

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG = "NockyConnectDebug"
    }
}
