package top.kagg886.pmf.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/31 16:00
 * ================================================
 */

@Composable
fun EmptyScreen(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit = {},
    title: @Composable () -> Unit = {},
    summary: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ProvideTextStyle(
            MaterialTheme.typography.displaySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            icon()
        }

        Spacer(Modifier.height(24.dp))

        ProvideTextStyle(
            MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        ) {
            title()
        }

        Spacer(Modifier.height(8.dp))

        ProvideTextStyle(
            MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        ) {
            summary()
        }

        Spacer(Modifier.height(24.dp))

        actions()
    }
}

@Preview
@Composable
private fun EmptyScreenErrorPreview() {
    MaterialTheme {
        EmptyScreen(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            },
            title = { Text("Something went wrong") },
            summary = { Text("An unknown error occurred while loading. Please try again later.") },
            actions = {
                Button(onClick = {}) {
                    Text("Retry")
                }
            }
        )
    }
}

@Preview
@Composable
private fun EmptyScreenNetworkPreview() {
    MaterialTheme {
        EmptyScreen(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            },
            title = { Text("Network connection failed") },
            summary = { Text("Check your network settings and try again.") },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = {}) {
                        Text("Open settings")
                    }
                    Button(onClick = {}) {
                        Text("Retry")
                    }
                }
            }
        )
    }
}

@Preview
@Composable
private fun EmptyScreenEmptyPreview() {
    MaterialTheme {
        EmptyScreen(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            },
            title = { Text("Nothing here") },
            summary = { Text("There's nothing here yet.") }
        )
    }
}

@Preview
@Composable
private fun EmptyScreenDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface {
            EmptyScreen(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                },
                title = { Text("Something went wrong") },
                summary = { Text("An unknown error occurred while loading. Please try again later.") },
                actions = {
                    Button(onClick = {}) {
                        Text("Retry")
                    }
                }
            )
        }
    }
}
