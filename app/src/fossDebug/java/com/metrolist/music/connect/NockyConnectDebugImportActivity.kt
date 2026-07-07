package com.metrolist.music.connect

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import com.metrolist.music.playback.MusicService
import timber.log.Timber
import java.io.File

class NockyConnectDebugImportActivity : Activity() {
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, serviceBinder: IBinder) {
            bound = true
            val service = (serviceBinder as MusicService.MusicBinder).service
            importSnapshot(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, MusicService::class.java)
        if (!bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            toast("Nocky Connect import failed: service unavailable")
            finish()
        }
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }

    private fun importSnapshot(service: MusicService) {
        try {
            if (!service.isPlayerReady.value) {
                toast("Nocky Connect import failed: player is not ready")
                return
            }

            val file = requestedSnapshotFile()
                ?: latestSnapshotFile()
                ?: run {
                    toast("Nocky Connect import skipped: no snapshot file")
                    return
                }

            val payload = file.readText()
            val plan = service.restoreNockyConnectSnapshotJsonPausedForCurrentDevice(payload)
            Timber.tag(TAG).i(
                "Imported Nocky Connect snapshot from ${file.absolutePath} with ${plan.queue.items.size} items",
            )
            toast("Nocky snapshot imported: ${plan.queue.items.size} tracks")
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Failed to import Nocky Connect snapshot")
            toast("Nocky Connect import failed")
        } finally {
            finish()
        }
    }

    private fun requestedSnapshotFile(): File? {
        val raw = intent.getStringExtra(EXTRA_SNAPSHOT_PATH)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return File(raw).takeIf { it.isFile }
    }

    private fun latestSnapshotFile(): File? =
        NockyConnectFileStore(importBaseDir()).latestSnapshotFile()

    private fun importBaseDir(): File = getExternalFilesDir(null) ?: filesDir

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG = "NockyConnectDebug"
        const val EXTRA_SNAPSHOT_PATH = "snapshot_path"
    }
}
