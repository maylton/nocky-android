/*
 * Nocky Connect player menu entry point.
 *
 * This file intentionally owns only the menu item UI and its temporary surface.
 * Device discovery, send/receive actions and confirmation flows will be wired
 * separately.
 */

package com.metrolist.music.ui.menu

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
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData

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
    val comingSoon = stringResource(R.string.nocky_connect_discovery_coming_soon)

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
                        Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show()
                    },
                ),
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}
