package top.kagg886.pmf.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 13:11
 * ================================================
 */

public typealias SnackBarHostState = SnackbarHostState

public val LocalSnackBarHostState = staticCompositionLocalOf<SnackBarHostState> {
    error("LocalSnackBarHostState not provided")
}

@Composable
public fun rememberSnackBarHostState(): SnackBarHostState = remember { SnackBarHostState() }

@Composable
public fun SnackBarProvider(
    modifier: Modifier = Modifier,
    provider: SnackBarHostState = rememberSnackBarHostState(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        CompositionLocalProvider(LocalSnackBarHostState provides provider) {
            content()
            SnackbarHost(
                hostState = provider,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            ) { data ->
                val (containerColor, contentColor) = snackBarColors(
                    (data.visuals as? SnackBarVisuals)?.level ?: SnackBarLevel.INFO,
                )
                Snackbar(
                    snackbarData = data,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    actionContentColor = contentColor,
                    dismissActionContentColor = contentColor,
                )
            }
        }
    }
}

public enum class SnackBarLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

public class SnackBarScope internal constructor() {
    internal var message: String = ""
        private set
    internal var level: SnackBarLevel = SnackBarLevel.INFO
        private set
    internal var actionLabel: String? = null
        private set
    internal var onAction: (() -> Unit)? = null
        private set

    public fun message(message: String) {
        this.message = message
    }

    public fun level(level: SnackBarLevel) {
        this.level = level
    }

    public fun action(label: String, onClick: (() -> Unit)? = null) {
        actionLabel = label
        onAction = onClick
    }
}

public suspend fun SnackBarHostState.showSnackBar(content: SnackBarScope.() -> Unit) {
    val scope = SnackBarScope().apply(content)
    val result = showSnackbar(
        SnackBarVisuals(
            message = scope.message,
            actionLabel = scope.actionLabel,
            level = scope.level,
        ),
    )
    if (result == SnackbarResult.ActionPerformed) {
        scope.onAction?.invoke()
    }
}

private data class SnackBarVisuals(
    override val message: String,
    override val actionLabel: String?,
    val level: SnackBarLevel,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

@Composable
private fun snackBarColors(level: SnackBarLevel): Pair<Color, Color> = when (level) {
    SnackBarLevel.INFO -> MaterialTheme.colorScheme.inverseSurface to MaterialTheme.colorScheme.inverseOnSurface
    SnackBarLevel.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    SnackBarLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    SnackBarLevel.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}
