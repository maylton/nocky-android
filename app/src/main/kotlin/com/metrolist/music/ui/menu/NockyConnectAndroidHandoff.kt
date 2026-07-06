package com.metrolist.music.ui.menu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.metrolist.music.connect.NockyConnectDeviceDescriptor
import com.metrolist.music.connect.NockyConnectDevicePlatform
import com.metrolist.music.connect.NockyConnectDiscoveredDevice
import com.metrolist.music.connect.NockyConnectHandoffEnvelope
import com.metrolist.music.connect.NockyConnectHandoffHttpClient
import com.metrolist.music.connect.NockyConnectHandoffHttpReceiver
import com.metrolist.music.connect.NockyConnectHandoffKind
import com.metrolist.music.connect.NockyConnectHandoffPayload
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
    Toast.makeText(
        context.applicationContext,
        "Nocky Connect: sending to ${device.descriptor.deviceName}…",
        Toast.LENGTH_SHORT,
    ).show()
    Thread {
        val result = try {
            val descriptor = buildAndroidNockyConnectDescriptor(
                context = context.applicationContext,
                advertiseHandoffEndpoint = false,
            )
            AndroidNockyConnectSendResult(
                success = true,
                message = sendAndroidSnapshotToDesktop(
                    localDescriptor = descriptor,
                    playerConnection = playerConnection,
                    devices = listOf(device),
                ),
            )
        } catch (error: Exception) {
            AndroidNockyConnectSendResult(
                success = false,
                message = "Nocky Connect failed: ${error.message ?: error.javaClass.simpleName}",
            )
        }
        showNockyConnectToast(context.applicationContext, result.message)
        Handler(Looper.getMainLooper()).post {
            onComplete(result)
        }
    }.start()
}

internal fun sendAndroidSnapshotToDesktop(
    localDescriptor: NockyConnectDeviceDescriptor,
    playerConnection: PlayerConnection?,
    devices: List<NockyConnectDiscoveredDevice>,
): String {
    val connection = playerConnection ?: error("Android player is not connected")
    val desktop = devices.firstOrNull { device ->
        device.descriptor.platform == NockyConnectDevicePlatform.LINUX_DESKTOP &&
            device.descriptor.handoffEndpoint != null
    } ?: error("Open Nocky Connect on Desktop and try again")
    val snapshot = exportCurrentAndroidSnapshotOnMainThread(connection)
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
        "Unexpected desktop handoff response: ${result.kind}"
    }
    require(resultPayload?.status == NockyConnectHandoffResultStatus.RESTORED_PAUSED) {
        "Desktop did not restore paused: ${resultPayload?.status}"
    }
    val currentTitle = snapshot.queue.items
        .getOrNull(snapshot.queue.currentIndex.coerceIn(0, (snapshot.queue.items.size - 1).coerceAtLeast(0)))
        ?.title
        ?: "queue"
    return "Nocky Connect: sent to ${desktop.descriptor.deviceName} · $currentTitle · ${snapshot.queue.items.size} items"
}

internal fun exportCurrentAndroidSnapshotOnMainThread(
    playerConnection: PlayerConnection,
): PlaybackSessionSnapshot {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return playerConnection.service.exportNockyConnectSnapshotForCurrentDevice()
            ?: error("Current Android queue is empty")
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
        "Timed out while reading Android player snapshot"
    }
    failure.get()?.let { error ->
        throw IllegalStateException(error.message ?: error.javaClass.simpleName, error)
    }
    return snapshot.get() ?: error("Current Android queue is empty")
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
) {
    if (!ANDROID_NOCKY_CONNECT_HANDOFF_RECEIVER_ACTIVE.compareAndSet(false, true)) {
        if (!silentTimeout) {
            showNockyConnectToast(context, "Nocky Connect: this device is already available for Desktop")
        }
        return
    }

    Thread {
        val message = try {
            val received = NockyConnectHandoffHttpReceiver.receiveOfferAndSnapshot(
                localDeviceId = localDeviceId,
                timeoutMs = NOCKY_CONNECT_HANDOFF_RECEIVE_TIMEOUT_MS,
            )
            val summary = NockyConnectPendingRestoreStore.save(
                context = context,
                snapshot = received.snapshot,
                restorePlan = received.restorePlan,
            )
            if (playerConnection != null) {
                Handler(Looper.getMainLooper()).post {
                    applyPendingNockyConnectRestore(
                        context = context,
                        playerConnection = playerConnection,
                    )
                }
                "Nocky Connect: desktop snapshot received · applying paused restore…"
            } else {
                "Nocky Connect: pending restore saved · ${summary.title} · ${summary.itemCount} items"
            }
        } catch (error: Exception) {
            if (silentTimeout && error is SocketTimeoutException) {
                null
            } else {
                "Nocky Connect receiver stopped: ${error.message ?: error.javaClass.simpleName}"
            }
        } finally {
            ANDROID_NOCKY_CONNECT_HANDOFF_RECEIVER_ACTIVE.set(false)
        }
        if (message != null) {
            showNockyConnectToast(context, message)
        }
    }.start()
}

private fun applyPendingNockyConnectRestore(
    context: Context,
    playerConnection: PlayerConnection,
) {
    val message = try {
        val summary = NockyConnectPendingRestoreApplier.applyPendingRestorePaused(
            context = context.applicationContext,
            playerConnection = playerConnection,
        )
        "Nocky Connect: restored paused · ${summary.title} · ${summary.itemCount} items"
    } catch (error: Exception) {
        "Nocky Connect restore failed: ${error.message ?: error.javaClass.simpleName}"
    }
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
}
