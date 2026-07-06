package com.metrolist.music.ui.menu

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.metrolist.music.connect.NockyConnectDiscoveredDevice
import com.metrolist.music.connect.NockyConnectUdpDiscovery
import com.metrolist.music.playback.PlayerConnection

internal fun scanAndroidNockyConnectDevices(
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

internal fun startAndroidNockyConnectPresenceWindow(
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
