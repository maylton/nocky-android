package com.metrolist.music.ui.menu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.metrolist.music.R
import com.metrolist.music.connect.NockyConnectDeviceDescriptor
import com.metrolist.music.connect.NockyConnectDevicePlatform
import com.metrolist.music.connect.NockyConnectDiscoveredDevice
import com.metrolist.music.connect.NockyConnectHandoffEnvelope
import com.metrolist.music.connect.NockyConnectHandoffHttpClient
import com.metrolist.music.connect.NockyConnectHandoffHttpReceiver
import com.metrolist.music.connect.NockyConnectHandoffKind
import com.metrolist.music.connect.NockyConnectHandoffPayload
import com.metrolist.music.connect.NockyConnectHandoffRestoreResult
import com.metrolist.music.connect.NockyConnectHandoffResultStatus
import com.metrolist.music.connect.NockyConnectPendingRestoreApplier
import com.metrolist.music.connect.NockyConnectPendingRestoreStore
import com.metrolist.music.connect.NockyConnectRestorePolicy
import com.metrolist.music.connect.NockyConnectSnapshotSummary
import com.metrolist.music.connect.PlaybackSessionSnapshot
import com.metrolist.music.connect.exportNockyConnectSnapshotForCurrentDevice
import com.metrolist.music.playback.PlayerConnection
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal fun sendAndroidSnapshotToSelectedDesktop(
    context: Context,
    playerConnection: PlayerConnection?,
    device: NockyConnectDiscoveredDevice,
    onComplete: (AndroidNockyConnectSendResult) -> Unit,
) {
    val appContext = context.applicationContext
    Toast.makeText(
        appContext,
        appContext.getString(R.string.nocky_connect_toast_sending_to_device, device.descriptor.deviceName),
        Toast.LENGTH_SHORT,
    ).show()
    Thread {
        val result = try {
            val descriptor = buildAndroidNockyConnectDescriptor(
                context = appContext,
                advertiseHandoffEndpoint = false,
            )
            AndroidNockyConnectSendResult(
                success = true,
                message = sendAndroidSnapshotToDesktop(
                    context = appContext,
                    localDescriptor = descriptor,
                    playerConnection = playerConnection,
                    devices = listOf(device),
                ),
            )
        } catch (error: Exception) {
            AndroidNockyConnectSendResult(
                success = false,
                message = appContext.getString(
                    R.string.nocky_connect_toast_failed,
                    error.message ?: error.javaClass.simpleName,
                ),
            )
        }
        showNockyConnectToast(appContext, result.message)
        Handler(Looper.getMainLooper()).post {
            onComplete(result)
        }
    }.start()
}

internal fun sendAndroidSnapshotToDesktop(
    context: Context,
    localDescriptor: NockyConnectDeviceDescriptor,
    playerConnection: PlayerConnection?,
    devices: List<NockyConnectDiscoveredDevice>,
): String {
    val appContext = context.applicationContext
    val connection = playerConnection ?: error(appContext.getString(R.string.nocky_connect_error_android_player_not_connected))
    val desktop = devices.firstOrNull { device ->
        device.descriptor.platform == NockyConnectDevicePlatform.LINUX_DESKTOP &&
            device.descriptor.handoffEndpoint != null
    } ?: error(appContext.getString(R.string.nocky_connect_error_open_desktop))
    val snapshot = exportCurrentAndroidSnapshotOnMainThread(appContext, connection)
    val snapshotJson = com.metrolist.music.connect.NockyConnectJson.encode(snapshot)
    val target = NockyConnectHandoffHttpClient.targetFromDiscoveredDevice(desktop)
    val offer = buildAndroidHandoffOffer(
        localDescriptor = localDescriptor,
        receiver = desktop.descriptor,
        snapshot = snapshot,
    )
    val result = NockyConnectHandoffHttpClient.sendOfferAndSnapshot(
        target = target,
        offer = offer,
        snapshotJson = snapshotJson,
    )
    val resultPayload = result.payload as? NockyConnectHandoffPayload.Result
    require(result.kind == NockyConnectHandoffKind.RESULT) {
        appContext.getString(R.string.nocky_connect_error_unexpected_desktop_response, result.kind)
    }
    require(resultPayload?.status == NockyConnectHandoffResultStatus.RESTORED_PAUSED) {
        resultPayload?.errorMessage
            ?: appContext.getString(R.string.nocky_connect_error_desktop_restore_status, resultPayload?.status)
    }
    val currentTitle = snapshot.queue.items
        .getOrNull(snapshot.queue.currentIndex.coerceIn(0, (snapshot.queue.items.size - 1).coerceAtLeast(0)))
        ?.title
        ?: appContext.getString(R.string.nocky_connect_queue_fallback)
    return appContext.getString(
        R.string.nocky_connect_toast_sent_to_desktop,
        desktop.descriptor.deviceName,
        currentTitle,
        snapshot.queue.items.size,
    )
}

internal fun exportCurrentAndroidSnapshotOnMainThread(
    context: Context,
    playerConnection: PlayerConnection,
): PlaybackSessionSnapshot {
    val appContext = context.applicationContext
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return playerConnection.service.exportNockyConnectSnapshotForCurrentDevice()
            ?: error(appContext.getString(R.string.nocky_connect_error_current_queue_empty))
    }

    val latch = CountDownLatch(1)
    val snapshot = AtomicReference<PlaybackSessionSnapshot?>()
    val failure = AtomicReference<Throwable?>()
    Handler(Looper.getMainLooper()).post {
        try {
            snapshot.set(playerConnection.service.exportNockyConnectSnapshotForCurrentDevice())
        } catch (error: Throwable) {
            failure.set(error)
        } finally {
            latch.countDown()
        }
    }

    check(latch.await(NOCKY_CONNECT_MAIN_THREAD_EXPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        appContext.getString(R.string.nocky_connect_error_snapshot_read_timeout)
    }
    failure.get()?.let { error ->
        throw IllegalStateException(error.message ?: error.javaClass.simpleName, error)
    }
    return snapshot.get() ?: error(appContext.getString(R.string.nocky_connect_error_current_queue_empty))
}

internal fun buildAndroidHandoffOffer(
    localDescriptor: NockyConnectDeviceDescriptor,
    receiver: NockyConnectDeviceDescriptor,
    snapshot: PlaybackSessionSnapshot,
): NockyConnectHandoffEnvelope {
    val now = System.currentTimeMillis()
    val offerId = "android-offer-$now"
    val safeIndex = snapshot.queue.currentIndex.coerceIn(
        0,
        (snapshot.queue.items.size - 1).coerceAtLeast(0),
    )
    val current = snapshot.queue.items.getOrNull(safeIndex)
    return NockyConnectHandoffEnvelope(
        messageId = "android-offer-message-$now",
        createdAtEpochMs = now,
        kind = NockyConnectHandoffKind.OFFER,
        payload = NockyConnectHandoffPayload.Offer(
            offerId = offerId,
            senderDeviceId = localDescriptor.deviceId,
            senderDeviceName = localDescriptor.deviceName,
            receiverDeviceId = receiver.deviceId,
            snapshotSummary = NockyConnectSnapshotSummary(
                source = snapshot.source,
                currentTitle = current?.title,
                currentArtist = current?.artists?.firstOrNull()?.name,
                queueItems = snapshot.queue.items.size,
                positionMs = snapshot.playback.positionMs,
                durationMs = snapshot.playback.durationMs,
                wasPlaying = snapshot.playback.state == com.metrolist.music.connect.NockyPlaybackState.PLAYING,
            ),
            restorePolicy = NockyConnectRestorePolicy.RESTORE_PAUSED,
        ),
    )
}

internal fun startAndroidHandoffReceiver(
    context: Context,
    localDeviceId: String,
    playerConnection: PlayerConnection?,
    silentTimeout: Boolean = false,
    receiveTimeoutMs: Long = NOCKY_CONNECT_HANDOFF_RECEIVE_TIMEOUT_MS,
) {
    val appContext = context.applicationContext
    if (!ANDROID_NOCKY_CONNECT_HANDOFF_RECEIVER_ACTIVE.compareAndSet(false, true)) {
        if (!silentTimeout) {
            showNockyConnectToast(appContext, appContext.getString(R.string.nocky_connect_toast_already_available))
        }
        return
    }

    Thread {
        val toastMessage = AtomicReference<String?>()
        val message = try {
            NockyConnectHandoffHttpReceiver.receiveOfferAndSnapshot(
                localDeviceId = localDeviceId,
                timeoutMs = receiveTimeoutMs,
                restoreBeforeResult = { snapshot, restorePlan ->
                    val summary = NockyConnectPendingRestoreStore.save(
                        context = appContext,
                        snapshot = snapshot,
                        restorePlan = restorePlan,
                    )
                    val currentPlayerConnection = playerConnection ?: ANDROID_NOCKY_CONNECT_PLAYER_CONNECTION.get()
                    if (currentPlayerConnection == null) {
                        val pendingMessage = appContext.getString(
                            R.string.nocky_connect_toast_pending_restore_saved,
                            summary.title,
                            summary.itemCount,
                        )
                        toastMessage.set(pendingMessage)
                        NockyConnectHandoffRestoreResult.failed(pendingMessage)
                    } else {
                        val applied = applyPendingNockyConnectRestoreBlocking(
                            context = appContext,
                            playerConnection = currentPlayerConnection,
                        )
                        toastMessage.set(applied.second)
                        if (applied.first) {
                            NockyConnectHandoffRestoreResult.restored()
                        } else {
                            NockyConnectHandoffRestoreResult.failed(applied.second)
                        }
                    }
                },
            )
            toastMessage.get() ?: appContext.getString(R.string.nocky_connect_toast_desktop_snapshot_received)
        } catch (error: Exception) {
            if (silentTimeout && error is SocketTimeoutException) {
                null
            } else {
                appContext.getString(
                    R.string.nocky_connect_toast_receiver_stopped,
                    error.message ?: error.javaClass.simpleName,
                )
            }
        } finally {
            ANDROID_NOCKY_CONNECT_HANDOFF_RECEIVER_ACTIVE.set(false)
        }
        if (message != null) {
            showNockyConnectToast(appContext, message)
        }
    }.start()
}

private fun applyPendingNockyConnectRestoreBlocking(
    context: Context,
    playerConnection: PlayerConnection,
): Pair<Boolean, String> {
    val appContext = context.applicationContext

    fun applyNow(): Pair<Boolean, String> =
        try {
            val summary = NockyConnectPendingRestoreApplier.applyPendingRestorePaused(
                context = appContext,
                playerConnection = playerConnection,
            )
            true to appContext.getString(
                R.string.nocky_connect_toast_restored_paused,
                summary.title,
                summary.itemCount,
            )
        } catch (error: Exception) {
            false to appContext.getString(
                R.string.nocky_connect_toast_restore_failed,
                error.message ?: error.javaClass.simpleName,
            )
        }

    if (Looper.myLooper() == Looper.getMainLooper()) {
        return applyNow()
    }

    val latch = CountDownLatch(1)
    val result = AtomicReference<Pair<Boolean, String>>()
    Handler(Looper.getMainLooper()).post {
        try {
            result.set(applyNow())
        } finally {
            latch.countDown()
        }
    }

    return if (latch.await(NOCKY_CONNECT_MAIN_THREAD_EXPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        result.get() ?: (false to appContext.getString(R.string.nocky_connect_toast_restore_failed, "empty restore result"))
    } else {
        false to appContext.getString(
            R.string.nocky_connect_toast_restore_failed,
            "Android player did not apply the handoff in time",
        )
    }
}

private fun applyPendingNockyConnectRestore(
    context: Context,
    playerConnection: PlayerConnection,
) {
    val message = applyPendingNockyConnectRestoreBlocking(
        context = context,
        playerConnection = playerConnection,
    ).second
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
}
