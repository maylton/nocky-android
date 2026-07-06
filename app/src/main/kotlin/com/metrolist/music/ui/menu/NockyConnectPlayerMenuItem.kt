/*
 * Nocky Connect player menu entry point.
 *
 * This file intentionally owns only the menu item UI and its temporary surface.
 * Device discovery, send/receive actions and confirmation flows are delegated
 * to small helpers in this package.
 */

package com.metrolist.music.ui.menu

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
import com.metrolist.music.connect.NockyConnectDevicePlatform
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData

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
    var devices by remember { mutableStateOf(emptyList<AndroidNockyConnectCachedDevice>()) }
    var isScanning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Scanning for nearby devices…") }
    var connectingDeviceId by remember { mutableStateOf<String?>(null) }
    var failedDeviceId by remember { mutableStateOf<String?>(null) }

    fun refreshDevices() {
        connectingDeviceId = null
        failedDeviceId = null
        startAndroidNockyConnectPresenceWindow(appContext, playerConnection)
        val cached = loadAndroidNockyConnectDeviceCache()
        if (cached.isNotEmpty()) {
            devices = cached
            statusText = when (cached.count { it.device.descriptor.platform == NockyConnectDevicePlatform.LINUX_DESKTOP }) {
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
                val desktopCount = merged.count { it.device.descriptor.platform == NockyConnectDevicePlatform.LINUX_DESKTOP }
                statusText = when {
                    desktopCount == 0 -> "No desktop found yet. Android stays visible for Desktop for 60 seconds."
                    foundDesktopCount == 0 && desktopCount == 1 -> "1 recently seen desktop available"
                    foundDesktopCount == 0 -> "Recently seen desktops available"
                    desktopCount == 1 -> "1 desktop available"
                    else -> "Multiple desktops available"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    val desktopDevices = devices.filter { cached ->
        cached.device.descriptor.platform == NockyConnectDevicePlatform.LINUX_DESKTOP &&
            cached.device.descriptor.handoffEndpoint != null
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
                    desktopDevices.forEach { cached ->
                        val device = cached.device
                        val deviceId = device.descriptor.deviceId
                        add(
                            Material3MenuItemData(
                                title = {
                                    Text(
                                        text = device.descriptor.deviceName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                description = {
                                    Text(
                                        text = androidNockyConnectDeviceSubtitle(
                                            cached = cached,
                                            isConnecting = connectingDeviceId == deviceId,
                                            hasFailed = failedDeviceId == deviceId,
                                        ),
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.cast),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                onClick = {
                                    connectingDeviceId = deviceId
                                    failedDeviceId = null
                                    sendAndroidSnapshotToSelectedDesktop(
                                        context = appContext,
                                        playerConnection = playerConnection,
                                        device = device,
                                    ) { result ->
                                        connectingDeviceId = null
                                        failedDeviceId = if (result.success) null else deviceId
                                    }
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
