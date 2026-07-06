/*
 * Nocky Connect player menu entry point.
 *
 * This file intentionally owns only the menu item UI and its temporary surface.
 * Device discovery, send/receive actions and confirmation flows will be wired
 * separately.
 */

package com.metrolist.music.ui.menu

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.connect.NOCKY_CONNECT_HANDOFF_PORT
import com.metrolist.music.connect.NockyConnectDeviceDescriptor
import com.metrolist.music.connect.NockyConnectDevicePlatform
import com.metrolist.music.connect.NockyConnectDiscoveredDevice
import com.metrolist.music.connect.NockyConnectHandoffEndpoint
import com.metrolist.music.connect.NockyConnectHandoffEnvelope
import com.metrolist.music.connect.NockyConnectHandoffHttpClient
import com.metrolist.music.connect.NockyConnectHandoffHttpReceiver
import com.metrolist.music.connect.NockyConnectHandoffKind
import com.metrolist.music.connect.NockyConnectHandoffPayload
import com.metrolist.music.connect.NockyConnectHandoffResultStatus
import com.metrolist.music.connect.NockyConnectHandoffTransport
import com.metrolist.music.connect.NockyConnectPendingRestoreApplier
import com.metrolist.music.connect.NockyConnectPendingRestoreStore
import com.metrolist.music.connect.NockyConnectRestorePolicy
import com.metrolist.music.connect.NockyConnectSnapshotSummary
import com.metrolist.music.connect.NockyConnectUdpDiscovery
import com.metrolist.music.connect.PlaybackSessionSnapshot
import com.metrolist.music.connect.exportNockyConnectSnapshotForCurrentDevice
import com.metrolist.music.connect.getOrCreateNockyConnectDeviceId
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val NOCKY_CONNECT_SEND_TIMEOUT_MS = 6_000L
private const val NOCKY_CONNECT_RECEIVE_TIMEOUT_MS = 15_000L
private const val NOCKY_CONNECT_ANDROID_PRESENCE_WINDOW_MS = 60_000L
private const val NOCKY_CONNECT_DEVICE_STALE_AFTER_MS = 300_000L
private const val NOCKY_CONNECT_HANDOFF_RECEIVE_TIMEOUT_MS = 45_000L
private const val NOCKY_CONNECT_MAIN_THREAD_EXPORT_TIMEOUT_MS = 2_000L

private val ANDROID_NOCKY_CONNECT_PRESENCE_ACTIVE = AtomicBoolean(false)
private val ANDROID_NOCKY_CONNECT_HANDOFF_RECEIVER_ACTIVE = AtomicBoolean(false)
private val ANDROID_NOCKY_CONNECT_DEVICE_CACHE = AtomicReference<List<AndroidNockyConnectCachedDevice>>(emptyList())

private data class AndroidNockyConnectCachedDevice(
    val device: NockyConnectDiscoveredDevice,
    val lastSeenEpochMs: Long,
)

private enum class AndroidNockyConnectDiscoveryMode {
    SEND,
    RECEIVE,
}

@Composable
fun nockyConnectPlayerMenuItem(
    onDismiss: () -> Unit,
): Material3MenuItemData {
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current

    return Material3MenuItemData(
        title = { Text(text = stringResource(R.string.nocky_connect)) },
        description = { Text(text = stringResource(R.string.nocky_connect_desc)) },
        icon = {
            Icon(
                painter = painterResource(R.drawable.cast),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        onClick = {
            bottomSheetPageState.show {
                NockyConnectPlayerSurface(playerConnection = playerConnection)
            }
            onDismiss()
        },
    )
}

@Composable
private fun NockyConnectPlayerSurface(
    playerConnection: PlayerConnection?,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val localDeviceName = remember { androidDeviceName() }
    var devices by remember { mutableStateOf(emptyList<NockyConnectDiscoveredDevice>()) }
    var isScanning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Scanning for nearby devices…") }

    fun refreshDevices() {
        startAndroidNockyConnectPresenceWindow(appContext, playerConnection)
        val cached = loadAndroidNockyConnectDeviceCache()
        if (cached.isNotEmpty()) {
            devices = cached
            statusText = when (cached.count { it.descriptor.platform == NockyConnectDevicePlatform.LINUX_DESKTOP }) {
                0 -> "Scanning for nearby devices… Android is visible to Desktop for 60 seconds."
                1 -> "1 cached desktop available · refreshing…"
                else -> "Cached desktops available · refreshing…"
            }
        } else {
            statusText = "Scanning for nearby devices… Android is visible to Desktop for 60 seconds."
        }
        isScanning = true
        scanAndroidNockyConnectDevices(appContext) { result, error ->
            isScanning = false
            if (error != null) {
                val cachedDevices = loadAndroidNockyConnectDeviceCache()
                devices = cachedDevices
                statusText = if (cachedDevices.isEmpty()) {
                    "Discovery failed: ${error.message ?: error.javaClass.simpleName}"
                } else {
                    "Discovery failed. Showing recently seen devices."
                }
            } else {
                val found = result.orEmpty()
                if (found.isNotEmpty()) {
                    saveAndroidNockyConnectDeviceCache(found)
                }
                val merged = loadAndroidNockyConnectDeviceCache()
                devices = merged
                val foundDesktopCount = found.count { it.descriptor.platform == NockyConnectDevicePlatform.LINUX_DESKTOP }
                val desktopCount = merged.count { it.descriptor.platform == NockyConnectDevicePlatform.LINUX_DESKTOP }
                statusText = when {
                    desktopCount == 0 -> "No desktop found yet. Android stays visible for Desktop for 60 seconds."
                    foundDesktopCount == 0 && desktopCount == 1 -> "1 cached desktop available"
                    foundDesktopCount == 0 -> "Cached desktops available"
                    desktopCount == 1 -> "1 desktop available"
                    else -> "Multiple desktops available"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    val desktopDevices = devices.filter { device ->
        device.descriptor.platform == NockyConnectDevicePlatform.LINUX_DESKTOP &&
            device.descriptor.handoffEndpoint != null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.nocky_connect),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.nocky_connect_surface_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("This device")
        Material3MenuGroup(
            items = listOf(
                Material3MenuItemData(
                    title = {
                        Text(
                            text = "✓ $localDeviceName",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    description = { Text(text = "Android · playing on this device") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.cast),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                ),
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Available devices")
        Material3MenuGroup(
            items = buildList {
                if (desktopDevices.isEmpty()) {
                    add(
                        Material3MenuItemData(
                            title = { Text(text = if (isScanning) "Scanning…" else "No desktop found") },
                            description = { Text(text = statusText) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.cast),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                        ),
                    )
                } else {
                    desktopDevices.forEach { device ->
                        add(
                            Material3MenuItemData(
                                title = {
                                    Text(
                                        text = device.descriptor.deviceName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                description = { Text(text = "Linux desktop · tap to move playback") },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.cast),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                onClick = {
                                    sendAndroidSnapshotToSelectedDesktop(
                                        context = appContext,
                                        playerConnection = playerConnection,
                                        device = device,
                                    )
                                },
                            ),
                        )
                    }
                }
                add(
                    Material3MenuItemData(
                        title = { Text(text = "Scan again") },
                        description = { Text(text = statusText) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.replay),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        onClick = { refreshDevices() },
                    ),
                )
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Actions")
        Material3MenuGroup(
            items = listOf(
                Material3MenuItemData(
                    title = { Text(text = "Make this device available for Desktop") },
                    description = { Text(text = "Wait for Desktop to send playback here") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = {
                        runAndroidNockyConnectDiscovery(
                            context = appContext,
                            mode = AndroidNockyConnectDiscoveryMode.RECEIVE,
                            playerConnection = playerConnection,
                        )
                    },
                ),
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
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

private fun scanAndroidNockyConnectDevices(
    context: Context,
    onComplete: (List<NockyConnectDiscoveredDevice>?, Throwable?) -> Unit,
) {
    Thread {
        try {
            val descriptor = buildAndroidNockyConnectDescriptor(
                context = context.applicationContext,
                advertiseHandoffEndpoint = false,
            )
            val devices = NockyConnectUdpDiscovery.scanOnce(
                localDescriptor = descriptor,
                timeoutMs = NOCKY_CONNECT_SEND_TIMEOUT_MS,
            )
            Handler(Looper.getMainLooper()).post {
                onComplete(devices, null)
            }
        } catch (error: Throwable) {
            Handler(Looper.getMainLooper()).post {
                onComplete(null, error)
            }
        }
    }.start()
}

private fun loadAndroidNockyConnectDeviceCache(): List<NockyConnectDiscoveredDevice> =
    pruneAndroidNockyConnectDeviceCache().map { cached -> cached.device }

private fun saveAndroidNockyConnectDeviceCache(devices: List<NockyConnectDiscoveredDevice>) {
    if (devices.isEmpty()) return
    val now = System.currentTimeMillis()
    val merged = linkedMapOf<String, AndroidNockyConnectCachedDevice>()
    pruneAndroidNockyConnectDeviceCache().forEach { cached ->
        merged[cached.device.descriptor.deviceId] = cached
    }
    devices.forEach { device ->
        merged[device.descriptor.deviceId] = AndroidNockyConnectCachedDevice(
            device = device,
            lastSeenEpochMs = now,
        )
    }
    ANDROID_NOCKY_CONNECT_DEVICE_CACHE.set(merged.values.toList())
}

private fun pruneAndroidNockyConnectDeviceCache(): List<AndroidNockyConnectCachedDevice> {
    val now = System.currentTimeMillis()
    val fresh = ANDROID_NOCKY_CONNECT_DEVICE_CACHE.get().filter { cached ->
        now - cached.lastSeenEpochMs <= NOCKY_CONNECT_DEVICE_STALE_AFTER_MS
    }
    ANDROID_NOCKY_CONNECT_DEVICE_CACHE.set(fresh)
    return fresh
}

private fun startAndroidNockyConnectPresenceWindow(
    context: Context,
    playerConnection: PlayerConnection?,
) {
    val appContext = context.applicationContext
    if (!ANDROID_NOCKY_CONNECT_PRESENCE_ACTIVE.compareAndSet(false, true)) {
        return
    }

    Thread {
        try {
            val descriptor = buildAndroidNockyConnectDescriptor(
                context = appContext,
                advertiseHandoffEndpoint = true,
            )
            startAndroidHandoffReceiver(
                context = appContext,
                localDeviceId = descriptor.deviceId,
                playerConnection = playerConnection,
                silentTimeout = true,
            )
            val devices = NockyConnectUdpDiscovery.receiveOnce(
                localDescriptor = descriptor,
                timeoutMs = NOCKY_CONNECT_ANDROID_PRESENCE_WINDOW_MS,
            )
            if (devices.isNotEmpty()) {
                saveAndroidNockyConnectDeviceCache(devices)
            }
        } catch (_: Exception) {
            // Presence is opportunistic: regular foreground scans still report
            // actionable discovery errors to the surface.
        } finally {
            ANDROID_NOCKY_CONNECT_PRESENCE_ACTIVE.set(false)
        }
    }.start()
}

private fun sendAndroidSnapshotToSelectedDesktop(
    context: Context,
    playerConnection: PlayerConnection?,
    device: NockyConnectDiscoveredDevice,
) {
    Toast.makeText(context.applicationContext, "Nocky Connect: sending to ${device.descriptor.deviceName}…", Toast.LENGTH_SHORT).show()
    Thread {
        val message = try {
            val descriptor = buildAndroidNockyConnectDescriptor(
                context = context.applicationContext,
                advertiseHandoffEndpoint = false,
            )
            sendAndroidSnapshotToDesktop(
                localDescriptor = descriptor,
                playerConnection = playerConnection,
                devices = listOf(device),
            )
        } catch (error: Exception) {
            "Nocky Connect failed: ${error.message ?: error.javaClass.simpleName}"
        }
        showNockyConnectToast(context.applicationContext, message)
    }.start()
}

private fun runAndroidNockyConnectDiscovery(
    context: Context,
    mode: AndroidNockyConnectDiscoveryMode,
    playerConnection: PlayerConnection?,
) {
    val appContext = context.applicationContext
    val startingMessage = when (mode) {
        AndroidNockyConnectDiscoveryMode.SEND -> "Nocky Connect: scanning for up to 6 seconds…"
        AndroidNockyConnectDiscoveryMode.RECEIVE -> "Nocky Connect: waiting up to 15 seconds…"
    }
    Toast.makeText(appContext, startingMessage, Toast.LENGTH_SHORT).show()

    Thread {
        val message = try {
            val descriptor = buildAndroidNockyConnectDescriptor(
                context = appContext,
                advertiseHandoffEndpoint = mode == AndroidNockyConnectDiscoveryMode.RECEIVE,
            )
            if (mode == AndroidNockyConnectDiscoveryMode.RECEIVE) {
                startAndroidHandoffReceiver(appContext, descriptor.deviceId, playerConnection)
            }
            if (mode == AndroidNockyConnectDiscoveryMode.RECEIVE && ANDROID_NOCKY_CONNECT_PRESENCE_ACTIVE.get()) {
                "Nocky Connect: this device is already available for Desktop"
            } else {
                val devices = when (mode) {
                    AndroidNockyConnectDiscoveryMode.SEND -> NockyConnectUdpDiscovery.scanOnce(
                        localDescriptor = descriptor,
                        timeoutMs = NOCKY_CONNECT_SEND_TIMEOUT_MS,
                    )
                    AndroidNockyConnectDiscoveryMode.RECEIVE -> NockyConnectUdpDiscovery.receiveOnce(
                        localDescriptor = descriptor,
                        timeoutMs = NOCKY_CONNECT_RECEIVE_TIMEOUT_MS,
                    )
                }
                if (devices.isNotEmpty()) {
                    saveAndroidNockyConnectDeviceCache(devices)
                }
                if (devices.isEmpty()) {
                    when (mode) {
                        AndroidNockyConnectDiscoveryMode.SEND -> "Nocky Connect: no devices found"
                        AndroidNockyConnectDiscoveryMode.RECEIVE -> "Nocky Connect: no desktop tried to connect"
                    }
                } else if (mode == AndroidNockyConnectDiscoveryMode.SEND) {
                    sendAndroidSnapshotToDesktop(
                        localDescriptor = descriptor,
                        playerConnection = playerConnection,
                        devices = devices,
                    )
                } else {
                    val names = devices
                        .take(3)
                        .joinToString { device -> device.descriptor.deviceName }
                    "Nocky Connect: found ${devices.size} device(s): $names"
                }
            }
        } catch (error: Exception) {
            "Nocky Connect failed: ${error.message ?: error.javaClass.simpleName}"
        }

        showNockyConnectToast(appContext, message)
    }.start()
}

private fun sendAndroidSnapshotToDesktop(
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

private fun exportCurrentAndroidSnapshotOnMainThread(
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

private fun buildAndroidHandoffOffer(
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

private fun startAndroidHandoffReceiver(
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

private fun showNockyConnectToast(
    context: Context,
    message: String,
) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

private fun buildAndroidNockyConnectDescriptor(
    context: Context,
    advertiseHandoffEndpoint: Boolean,
): NockyConnectDeviceDescriptor = NockyConnectDeviceDescriptor(
    deviceId = context.getOrCreateNockyConnectDeviceId(),
    deviceName = androidDeviceName(),
    platform = NockyConnectDevicePlatform.ANDROID,
    appName = "Nocky Android",
    appVersion = null,
    handoffEndpoint = if (advertiseHandoffEndpoint) {
        NockyConnectHandoffEndpoint(
            transport = NockyConnectHandoffTransport.LOCAL_HTTP,
            port = NOCKY_CONNECT_HANDOFF_PORT,
        )
    } else {
        null
    },
)

private fun androidDeviceName(): String =
    listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { value -> value.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" }
