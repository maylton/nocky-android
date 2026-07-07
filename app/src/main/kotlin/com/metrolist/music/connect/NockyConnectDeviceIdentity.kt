package com.metrolist.music.connect

import android.content.Context
import java.util.UUID

private const val NOCKY_CONNECT_PREFS = "nocky_connect"
private const val NOCKY_CONNECT_DEVICE_ID = "device_id"

fun Context.getOrCreateNockyConnectDeviceId(): String {
    val prefs = getSharedPreferences(NOCKY_CONNECT_PREFS, Context.MODE_PRIVATE)
    val existing = prefs.getString(NOCKY_CONNECT_DEVICE_ID, null)
    if (!existing.isNullOrBlank()) return existing

    val generated = UUID.randomUUID().toString()
    prefs.edit().putString(NOCKY_CONNECT_DEVICE_ID, generated).apply()
    return generated
}
