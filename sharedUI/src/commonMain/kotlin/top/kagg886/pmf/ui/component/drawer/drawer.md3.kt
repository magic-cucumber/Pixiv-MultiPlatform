@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.ui.component.drawer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.internal.Strings
import androidx.compose.material3.internal.getString
import androidx.compose.material3.tokens.NavigationDrawerTokens
import androidx.compose.material3.value
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.ui.component.SnackBarHostState
import top.kagg886.pmf.ui.component.SnackBarProvider
import top.kagg886.pmf.ui.component.rememberSnackBarHostState
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun DrawerSheetPageScaffold(
    modifier: Modifier = Modifier,
    direction: DrawerSheetPopupDirection = DrawerSheetPopupDirection.LEFT,
    snackBarHost: SnackBarHostState = rememberSnackBarHostState(),
    content: @Composable DrawerSheetPageScaffoldScope.() -> Unit = {},
) {
    SnackBarProvider(Modifier.fillMaxSize(), snackBarHost) {
        val navigation = LocalNavController.current
        val coroutineScope = rememberCoroutineScope()
        val draggableState = remember { AnchoredDraggableState(DrawerPosition.Closed) }
        val animationSpec = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = draggableState,
            positionalThreshold = { distance -> distance * 0.5f },
            animationSpec = animationSpec,
        )
        var initialTarget by remember { mutableStateOf<DrawerPosition?>(null) }
        var dismissingFromScrim by remember { mutableStateOf(false) }

        LaunchedEffect(initialTarget) {
            initialTarget?.let { draggableState.animateTo(it, animationSpec) }
        }
        LaunchedEffect(draggableState) {
            var hasBeenVisible = false
            snapshotFlow { draggableState.settledValue }.collect { value ->
                if (value == DrawerPosition.Closed) {
                    if (hasBeenVisible && !dismissingFromScrim) navigation.popBackStack()
                } else {
                    hasBeenVisible = true
                }
            }
        }

        val onClose: () -> Unit = {
            if (draggableState.settledValue != DrawerPosition.Closed) {
                coroutineScope.launch { draggableState.animateTo(DrawerPosition.Closed, animationSpec) }
            }
        }
        BackHandler(enabled = draggableState.settledValue != DrawerPosition.Closed, onBack = onClose)

        val sheetShape = when (direction) {
            DrawerSheetPopupDirection.LEFT -> RoundedCornerShape(
                topEnd = DrawerCornerRadius,
                bottomEnd = DrawerCornerRadius,
            )
            DrawerSheetPopupDirection.RIGHT -> RoundedCornerShape(
                topStart = DrawerCornerRadius,
                bottomStart = DrawerCornerRadius,
            )
        }
        val sheetAlignment = if (direction == DrawerSheetPopupDirection.LEFT) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        }
        val sheetInsetSide = if (direction == DrawerSheetPopupDirection.LEFT) {
            WindowInsetsSides.Start
        } else {
            WindowInsetsSides.End
        }
        val navigationMenu = getString(Strings.NavigationMenu)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .anchoredDraggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    flingBehavior = flingBehavior,
                )
                .clickable(interactionSource = null, indication = null) {
                    if (!dismissingFromScrim) {
                        dismissingFromScrim = true
                        coroutineScope.launch {
                            try {
                                draggableState.animateTo(DrawerPosition.Closed, animationSpec)
                            } finally {
                                navigation.popBackStack()
                            }
                        }
                    }
                },
            contentAlignment = sheetAlignment,
        ) {
            val drawerScope = remember(onClose) { DrawerSheetPageScaffoldScopeImpl(onClose) }
            Surface(
                modifier = modifier
                    .widthIn(min = MinimumDrawerWidth, max = MaximumDrawerWidth)
                    .fillMaxHeight()
                    .offset {
                        IntOffset(
                            x = draggableState.offset.takeUnless(Float::isNaN)?.roundToInt()
                                ?: if (direction == DrawerSheetPopupDirection.LEFT) {
                                    -constraints.maxWidth
                                } else {
                                    constraints.maxWidth
                                },
                            y = 0,
                        )
                    }
                    .onSizeChanged { sheetSize ->
                        val closedAnchor = if (direction == DrawerSheetPopupDirection.LEFT) {
                            -sheetSize.width.toFloat()
                        } else {
                            sheetSize.width.toFloat()
                        }
                        draggableState.updateAnchors(
                            DraggableAnchors {
                                DrawerPosition.Closed at closedAnchor
                                DrawerPosition.Open at 0f
                            },
                            draggableState.targetValue,
                        )
                        if (initialTarget == null) initialTarget = DrawerPosition.Open
                    }
                    .imePadding()
                    .clickable(interactionSource = null, indication = null, onClick = {})
                    .semantics { paneTitle = navigationMenu },
                shape = sheetShape,
                color = NavigationDrawerTokens.ModalContainerColor.value,
                tonalElevation = ModalDrawerElevation,
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + sheetInsetSide),
                        ),
                ) {
                    drawerScope.content()
                }
            }
        }
    }
}

public enum class DrawerSheetPopupDirection { LEFT, RIGHT }

private enum class DrawerPosition { Closed, Open }

private val MinimumDrawerWidth = 240.dp
private val MaximumDrawerWidth = 360.dp
private val DrawerCornerRadius = 16.dp
private val ModalDrawerElevation = 0.dp
