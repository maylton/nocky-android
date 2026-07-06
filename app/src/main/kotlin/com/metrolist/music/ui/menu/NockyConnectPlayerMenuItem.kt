/*
 * Nocky Connect player menu entry point.
 *
 * This file intentionally owns only the menu item UI. Device discovery,
 * send/receive actions and confirmation surfaces will be wired separately.
 */

package com.metrolist.music.ui.menu

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3MenuItemData

@Composable
fun nockyConnectPlayerMenuItem(
    onDismiss: () -> Unit,
): Material3MenuItemData {
    val context = LocalContext.current
    val comingSoon = stringResource(R.string.nocky_connect_discovery_coming_soon)

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
            Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show()
            onDismiss()
        },
    )
}
