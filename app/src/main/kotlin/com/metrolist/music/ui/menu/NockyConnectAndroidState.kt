package com.metrolist.music.ui.menu

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.metrolist.music.connect.NOCKY_CONNECT_HANDOFF_PORT
import com.metrolist.music.connect.NockyConnectDeviceDescriptor
import com.metrolist.music.connect.NockyConnectDevicePlatform
import com.metrolist.music.connect.NockyConnectDiscoveredDevice
import com.metrolist.music.connect.NockyConnectHandoffEndpoint
import com.metrolist.music.connect.NockyConnectHandoffTransport
import com.metrolist.music.connect.getOrCreateNockyConnectDeviceId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal const val NOCKY_CONNECT_SEND_TIMEOUT_MS = 6_000L
internal const val NOCKY_CONNECT_ANDROID_PRESENCE_WINDOW_MS = 60_000L
internal const val NOCKY_CONNECT_DEVICE_AVAILABLE_NOW_MS = 30_000L
internal const val NOCKY_CONNECT_DEVICE_STALE_AFTER_MS = 300_000L
internal const val NOCKY_CONNECT_HANDOFF_RECEIVE_TIMEOUT_MS = NOCKY_CONNECT_ANDROID_PRESENCE_WINDOW_MS + 5_000L
internal const val NOCKY_CONNECT_MAIN_THREAD_EXPORT_TIMEOUT_MS = 2_000L

internal val ANDROID_NOCKY_CONNECT_PRESENCE_ACTIVE = AtomicBoolean(false)
internal val ANDROID_NOCKY_CONNECT_HANDOFF_RECEIVER_ACTIVE = AtomicBoolean(false)
internal val ANDROID_NOCKY_CONNECT_DEVICE_CACHE = AtomicReference<List<AndroidNockyConnectCachedDevice>>(emptyList())

internal data class AndroidNockyConnectCachedDevice(
    val device: NockyConnectDiscoveredDevice,
    val lastSeenEpochMs: Long,
)

internal data class AndroidNockyConnectSendResult(
    val success: Boolean,
    val message: String,
)

internal fun loadAndroidNockyConnectDeviceCache(): List<AndroidNockyConnectCachedDevice> =
    pruneAndroidNockyConnectDeviceCache()

internal fun saveAndroidNockyConnectDeviceCache(devices: List<NockyConnectDiscoveredDevice>) {
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

internal fun pruneAndroidNockyConnectDeviceCache(): List<AndroidNockyConnectCachedDevice> {
    val now = System.currentTimeMillis()
    val fresh = ANDROID_NOCKY_CONNECT_DEVICE_CACHE.get().filter { cached ->
        now - cached.lastSeenEpochMs <= NOCKY_CONNECT_DEVICE_STALE_AFTER_MS
    }
    ANDROID_NOCKY_CONNECT_DEVICE_CACHE.set(fresh)
    return fresh
}

internal fun androidNockyConnectDeviceSubtitle(
    cached: AndroidNockyConnectCachedDevice,
    isConnecting: Boolean = false,
    hasFailed: Boolean = false,
): String {
    val platform = androidNockyConnectPlatformLabel(cached.device.descriptor.platform)
    if (isConnecting) {
        return "$platform · connecting…"
    }
    if (hasFailed) {
        return "$platform · failed to connect · scan again or check Desktop"
    }

    val ageMs = (System.currentTimeMillis() - cached.lastSeenEpochMs).coerceAtLeast(0L)
    return if (ageMs <= NOCKY_CONNECT_DEVICE_AVAILABLE_NOW_MS) {
        "$platform · available now · tap to move playback"
    } else {
        "$platform · recently seen · last seen ${androidNockyConnectRelativeAge(ageMs)} ago · tap to try moving playback"
    }
}

internal fun androidNockyConnectPlatformLabel(platform: NockyConnectDevicePlatform): String = when (platform) {
    NockyConnectDevicePlatform.ANDROID -> "Android"
    NockyConnectDevicePlatform.LINUX_DESKTOP -> "Linux desktop"
    NockyConnectDevicePlatform.UNKNOWN -> "Unknown device"
}

internal fun androidNockyConnectRelativeAge(ageMs: Long): String {
    val seconds = ageMs / 1_000L
    if (seconds < 60L) return "${seconds}s"

    val minutes = seconds / 60L
    if (minutes < 60L) return "${minutes}m"

    val hours = minutes / 60L
    return "${hours}h"
}

internal fun buildAndroidNockyConnectDescriptor(
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

internal fun showNockyConnectToast(
    context: Context,
    message: String,
) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

internal fun androidDeviceName(): String =
    listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { value -> value.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" }
