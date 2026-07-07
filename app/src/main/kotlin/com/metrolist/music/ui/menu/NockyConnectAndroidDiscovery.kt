package com.metrolist.music.ui.menu

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.metrolist.music.connect.NockyConnectDiscoveredDevice
import com.metrolist.music.connect.NockyConnectUdpDiscovery
import com.metrolist.music.playback.PlayerConnection
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidNockyConnectPresenceSession(
    private val isRunning: AtomicBoolean,
) {
    fun stop() {
        isRunning.set(false)
    }
}

internal fun scanAndroidNockyConnectDevices(
    context: Context,
    onComplete: (List<NockyConnectDiscoveredDevice>?, Throwable?) -> Unit,
) {
    Thread {
        try {
            val descriptor = buildAndroidNockyConnectDescriptor(
                context = context.applicationContext,
                advertiseHandoffEndpoint = true,
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

internal fun startAndroidNockyConnectPresenceSession(
    context: Context,
    playerConnection: PlayerConnection?,
): AndroidNockyConnectPresenceSession? {
    val appContext = context.applicationContext
    ANDROID_NOCKY_CONNECT_PLAYER_CONNECTION.set(playerConnection)
    if (!ANDROID_NOCKY_CONNECT_PRESENCE_ACTIVE.compareAndSet(false, true)) {
        return null
    }

    val isRunning = AtomicBoolean(true)
    Thread {
        try {
            val descriptor = buildAndroidNockyConnectDescriptor(
                context = appContext,
                advertiseHandoffEndpoint = true,
            )
            while (isRunning.get()) {
                startAndroidHandoffReceiver(
                    context = appContext,
                    localDeviceId = descriptor.deviceId,
                    playerConnection = ANDROID_NOCKY_CONNECT_PLAYER_CONNECTION.get(),
                    silentTimeout = true,
                    receiveTimeoutMs = NOCKY_CONNECT_HANDOFF_RECEIVER_SLICE_TIMEOUT_MS,
                )
                val devices = runCatching {
                    NockyConnectUdpDiscovery.receiveOnce(
                        localDescriptor = descriptor,
                        timeoutMs = NOCKY_CONNECT_PRESENCE_POLL_TIMEOUT_MS,
                    )
                }.getOrDefault(emptyList())
                if (devices.isNotEmpty()) {
                    saveAndroidNockyConnectDeviceCache(devices)
                }
                Thread.sleep(NOCKY_CONNECT_PRESENCE_RESTART_DELAY_MS)
            }
        } finally {
            isRunning.set(false)
            ANDROID_NOCKY_CONNECT_PRESENCE_ACTIVE.set(false)
            ANDROID_NOCKY_CONNECT_PLAYER_CONNECTION.set(null)
        }
    }.start()

    return AndroidNockyConnectPresenceSession(isRunning)
}

internal fun startAndroidNockyConnectPresenceWindow(
    context: Context,
    playerConnection: PlayerConnection?,
) {
    val appContext = context.applicationContext
    ANDROID_NOCKY_CONNECT_PLAYER_CONNECTION.set(playerConnection)
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
                playerConnection = ANDROID_NOCKY_CONNECT_PLAYER_CONNECTION.get(),
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
