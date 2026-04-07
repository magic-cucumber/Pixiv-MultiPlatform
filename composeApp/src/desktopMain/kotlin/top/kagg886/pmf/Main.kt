package top.kagg886.pmf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    setupEnv()

    val windowState = rememberWindowState(size = DpSize(1380.dp, 860.dp))

    Window(
        onCloseRequest = ::exitApplication,
        title = BuildConfig.APP_NAME,
        state = windowState,
        undecorated = true,
        transparent = true,
        resizable = true,
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1120, 720)
            window.background = java.awt.Color(0, 0, 0, 0)
        }

        DesktopShell(
            isMaximized = windowState.placement == WindowPlacement.Maximized,
            onMinimize = { window.isMinimized = true },
            onToggleMaximize = {
                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Maximized
                }
            },
            onClose = ::exitApplication,
        ) {
            App()
        }
    }
}

@Composable
private fun DesktopShell(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val outerShape = if (isMaximized) RoundedCornerShape(0.dp) else RoundedCornerShape(28.dp)
    val innerShape = if (isMaximized) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1016),
                        Color(0xFF121723),
                        Color(0xFF171D2B),
                    ),
                ),
            )
            .padding(if (isMaximized) 0.dp else 12.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .shadow(if (isMaximized) 0.dp else 26.dp, outerShape)
                .clip(outerShape)
                .border(1.dp, Color.White.copy(alpha = 0.10f), outerShape),
            shape = outerShape,
            color = Color(0xFF0B0E14),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DesktopTitleBar(
                    isMaximized = isMaximized,
                    onMinimize = onMinimize,
                    onToggleMaximize = onToggleMaximize,
                    onClose = onClose,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isMaximized) 0.dp else 8.dp, vertical = if (isMaximized) 0.dp else 8.dp)
                        .clip(innerShape)
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DesktopTitleBar(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    var hoveringClose by remember { mutableStateOf(false) }

    WindowDraggableArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF151925),
                            Color(0xFF10141E),
                            Color(0xFF0C1018),
                        ),
                    ),
                )
                .padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF52D273)),
                )
                Text(
                    text = BuildConfig.APP_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.95f),
                )
                Text(
                    text = "Windows Desktop",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.52f),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WindowActionButton(
                    onClick = onMinimize,
                    icon = { Icon(Icons.Rounded.Minimize, contentDescription = "Minimize") },
                )
                WindowActionButton(
                    onClick = onToggleMaximize,
                    icon = { Icon(Icons.Rounded.CropSquare, contentDescription = if (isMaximized) "Restore" else "Maximize") },
                )
                WindowActionButton(
                    onClick = onClose,
                    containerColor = if (hoveringClose) Color(0xFFE5484D) else Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White,
                    onHoverChanged = { hoveringClose = it },
                    icon = { Icon(Icons.Rounded.Close, contentDescription = "Close") },
                )
            }
        }
    }
}

@Composable
private fun WindowActionButton(
    onClick: () -> Unit,
    containerColor: Color = Color.White.copy(alpha = 0.08f),
    contentColor: Color = Color.White.copy(alpha = 0.90f),
    onHoverChanged: ((Boolean) -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        icon()
    }
}
