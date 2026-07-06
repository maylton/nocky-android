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
import com.metrolist.music.connect.NOCKY_CONNECT_HANDOFF_PORT
import com.metrolist.music.connect.NockyConnectDeviceDescriptor
import com.metrolist.music.connect.NockyConnectDevicePlatform
import com.metrolist.music.connect.NockyConnectHandoffEndpoint
import com.metrolist.music.connect.NockyConnectHandoffHttpReceiver
import com.metrolist.music.connect.NockyConnectHandoffTransport
import com.metrolist.music.connect.NockyConnectPendingRestoreApplier
import com.metrolist.music.connect.NockyConnectPendingRestoreStore
import com.metrolist.music.connect.NockyConnectUdpDiscovery
import com.metrolist.music.connect.getOrCreateNockyConnectDeviceId
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData

private const val NOCKY_CONNECT_SEND_TIMEOUT_MS = 6_000L
private const val NOCKY_CONNECT_RECEIVE_TIMEOUT_MS = 15_000L
private const val NOCKY_CONNECT_HANDOFF_RECEIVE_TIMEOUT_MS = 45_000L

private enum class AndroidNockyConnectDiscoveryMode {
    SEND,
    RECEIVE,
}

@Composable
fun nockyConnectPlayerMenuItem(
    playerConnection: PlayerConnection,
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
                NockyConnectPlayerSurface(playerConnection = playerConnection)
            }
            onDismiss()
        },
    )
}

@Composable
private fun NockyConnectPlayerSurface(
    playerConnection: PlayerConnection,
) {
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
                Material3MenuItemData(
                    title = { Text(text = "Apply pending restore") },
                    description = { Text(text = "Restore the received desktop queue paused") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.replay),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = {
                        applyPendingNockyConnectRestore(
                            context = context,
                            playerConnection = playerConnection,
                        )
                    },
                ),
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
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
            val descriptor = buildAndroidNockyConnectDescriptor(
                context = appContext,
                advertiseHandoffEndpoint = mode == AndroidNockyConnectDiscoveryMode.RECEIVE,
            )
            if (mode == AndroidNockyConnectDiscoveryMode.RECEIVE) {
                startAndroidHandoffReceiver(appContext, descriptor.deviceId)
            }
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

        showNockyConnectToast(appContext, message)
    }.start()
}

private fun startAndroidHandoffReceiver(
    context: Context,
    localDeviceId: String,
) {
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
            "Nocky Connect: pending restore saved · ${summary.title} · ${summary.itemCount} items"
        } catch (error: Exception) {
            "Nocky Connect receiver stopped: ${error.message ?: error.javaClass.simpleName}"
        }
        showNockyConnectToast(context, message)
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
