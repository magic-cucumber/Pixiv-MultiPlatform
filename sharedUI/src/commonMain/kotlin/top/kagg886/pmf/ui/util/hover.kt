package top.kagg886.pmf.ui.util

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/13 16:11
 * ================================================
 */

fun Modifier.hoverable(onHoverExit: () -> Unit = {}, onHoverEnter: () -> Unit): Modifier = composed {
    val currentHoverExit by rememberUpdatedState(onHoverExit)
    val currentHoverEnter by rememberUpdatedState(onHoverEnter)

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is HoverInteraction.Enter) {
                currentHoverEnter()
            }

            if (it is HoverInteraction.Exit) {
                currentHoverExit()
            }
        }
    }

    hoverable(interactionSource)
}
