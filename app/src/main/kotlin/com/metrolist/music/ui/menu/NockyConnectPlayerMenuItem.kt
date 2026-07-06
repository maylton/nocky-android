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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.connect.NockyConnectDeviceDescriptor
import com.metrolist.music.connect.NockyConnectDevicePlatform
import com.metrolist.music.connect.NockyConnectUdpDiscovery
import com.metrolist.music.connect.getOrCreateNockyConnectDeviceId
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData

private const val NOCKY_CONNECT_SEND_TIMEOUT_MS = 6_000L
private const val NOCKY_CONNECT_RECEIVE_TIMEOUT_MS = 15_000L

private enum class AndroidNockyConnectDiscoveryMode {
    SEND,
    RECEIVE,
}

@Composable
fun nockyConnectPlayerMenuItem(
    onDismiss: () -> Unit,
): Material3MenuItemData {
    val bottomSheetPageState = LocalBottomSheetPageState.current

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
                NockyConnectPlayerSurface()
            }
            onDismiss()
        },
    )
}

@Composable
private fun NockyConnectPlayerSurface() {
    val context = LocalContext.current

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
        Spacer(modifier = Modifier.height(24.dp))
        Material3MenuGroup(
            items = listOf(
                Material3MenuItemData(
                    title = { Text(text = stringResource(R.string.nocky_connect_send_to_desktop)) },
                    description = { Text(text = stringResource(R.string.nocky_connect_send_to_desktop_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.cast),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = {
                        runAndroidNockyConnectDiscovery(
                            context = context,
                            mode = AndroidNockyConnectDiscoveryMode.SEND,
                        )
                    },
                ),
                Material3MenuItemData(
                    title = { Text(text = stringResource(R.string.nocky_connect_receive_from_desktop)) },
                    description = { Text(text = stringResource(R.string.nocky_connect_receive_from_desktop_desc)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = {
                        runAndroidNockyConnectDiscovery(
                            context = context,
                            mode = AndroidNockyConnectDiscoveryMode.RECEIVE,
                        )
                    },
                ),
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun runAndroidNockyConnectDiscovery(
    context: Context,
    mode: AndroidNockyConnectDiscoveryMode,
) {
    val appContext = context.applicationContext
    val startingMessage = when (mode) {
        AndroidNockyConnectDiscoveryMode.SEND -> "Nocky Connect: scanning for up to 6 seconds…"
        AndroidNockyConnectDiscoveryMode.RECEIVE -> "Nocky Connect: waiting up to 15 seconds…"
    }
    Toast.makeText(appContext, startingMessage, Toast.LENGTH_SHORT).show()

    Thread {
        val message = try {
            val descriptor = NockyConnectDeviceDescriptor(
                deviceId = appContext.getOrCreateNockyConnectDeviceId(),
                deviceName = androidDeviceName(),
                platform = NockyConnectDevicePlatform.ANDROID,
                appName = "Nocky Android",
                appVersion = null,
            )
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
            if (devices.isEmpty()) {
                when (mode) {
                    AndroidNockyConnectDiscoveryMode.SEND -> "Nocky Connect: no devices found"
                    AndroidNockyConnectDiscoveryMode.RECEIVE -> "Nocky Connect: no desktop tried to connect"
                }
            } else {
                val names = devices
                    .take(3)
                    .joinToString { device -> device.descriptor.deviceName }
                "Nocky Connect: found ${devices.size} device(s): $names"
            }
        } catch (error: Exception) {
            "Nocky Connect failed: ${error.message ?: error.javaClass.simpleName}"
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }.start()
}

private fun androidDeviceName(): String =
    listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { value -> value.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" }
