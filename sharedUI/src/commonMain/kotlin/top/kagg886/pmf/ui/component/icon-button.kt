@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.material3.internal.childSemantics
import androidx.compose.material3.tokens.MotionSchemeKeyTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 15:35
 * ================================================
 */

@Composable
fun LoadingIconButton(
    modifier: Modifier = Modifier,
    state: LoadingIconButtonState,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = IconButtonDefaults.standardShape,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    content: @Composable (LoadingIconButtonState) -> Unit,
) {
    val buttonEnabled = enabled && state !is LoadingIconButtonState.Loading
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(IconButtonDefaults.smallContainerSize())
            .clip(shape)
            .background(color = colors.containerColor(enabled), shape = shape)
            .combinedClickable(
                enabled = buttonEnabled,
                onClick = onClick,
                role = Role.Button,
                onLongClick = onLongClick,
                interactionSource = interactionSource,
                indication = ripple(),
            )
            .childSemantics(),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = colors.contentColor(enabled)
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
            ) { state ->
                when (state) {
                    LoadingIconButtonState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )

                    else -> content(state)
                }
            }
        }
    }
}


sealed interface LoadingIconButtonState {
    data object Loading : LoadingIconButtonState
    data class NotLoading<T>(val state: T) : LoadingIconButtonState
}

@Preview
@Composable
private fun LoadingIconButtonPreview() {
    MaterialTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoadingIconButton(
                state = LoadingIconButtonState.NotLoading(Unit),
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                )
            }
            LoadingIconButton(state = LoadingIconButtonState.Loading) {}
        }
    }
}
