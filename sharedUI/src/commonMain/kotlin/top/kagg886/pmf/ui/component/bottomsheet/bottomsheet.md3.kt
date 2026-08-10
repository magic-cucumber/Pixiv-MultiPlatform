package top.kagg886.pmf.ui.component.bottomsheet

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.ui.component.SnackBarHostState
import top.kagg886.pmf.ui.component.SnackBarProvider
import top.kagg886.pmf.ui.component.rememberSnackBarHostState
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun BottomSheetPageScaffold(
    modifier: Modifier = Modifier,
    maxExpandedHeight: Dp = LocalWindowInfo.current.containerDpSize.height * 0.8f,
    initialPopupType: SheetPosition = SheetPosition.PartiallyExpanded,
    popupTypeChangeRequest: (SheetPosition) -> Boolean = { true },
    snackBarHost: SnackBarHostState = rememberSnackBarHostState(),
    content: @Composable BottomSheetPageScaffoldScope.() -> Unit = {},
) {
    require(maxExpandedHeight == Dp.Unspecified || maxExpandedHeight > 0.dp) {
        "maxExpandedHeight must be positive or Dp.Unspecified."
    }
    require(initialPopupType != SheetPosition.Hidden) {
        "initialPopupType must not be SheetPosition.Hidden."
    }
    require(popupTypeChangeRequest(SheetPosition.Expanded)) {
        "popupTypeChangeRequest must allow SheetPosition.Expanded."
    }

    SnackBarProvider(Modifier.fillMaxSize(), snackBarHost) {
        val navigation = LocalNavController.current
        val coroutineScope = rememberCoroutineScope()
        val animationSpec = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
        val allowProgrammaticTransition = remember { mutableStateOf(false) }
        val popupTypeChangeRequestState = rememberUpdatedState(popupTypeChangeRequest)
        lateinit var draggableState: AnchoredDraggableState<SheetPosition>

        fun onClose(targetOverride: SheetPosition? = null): Boolean {
            val target = targetOverride ?: when (draggableState.settledValue) {
                SheetPosition.Expanded -> when {
                    draggableState.anchors.hasPositionFor(SheetPosition.PartiallyExpanded) ->
                        SheetPosition.PartiallyExpanded
                    draggableState.anchors.hasPositionFor(SheetPosition.Hidden) -> SheetPosition.Hidden
                    else -> null
                }
                SheetPosition.PartiallyExpanded ->
                    SheetPosition.Hidden.takeIf { draggableState.anchors.hasPositionFor(it) }
                SheetPosition.Hidden -> null
            }
            target ?: return false
            if (!popupTypeChangeRequestState.value(target)) return false
            if (targetOverride == null) {
                coroutineScope.launch {
                    allowProgrammaticTransition.value = true
                    try {
                        draggableState.animateTo(target, animationSpec)
                    } finally {
                        allowProgrammaticTransition.value = false
                    }
                }
            }
            return true
        }

        @Suppress("DEPRECATION")
        val state = remember {
            AnchoredDraggableState(
                initialValue = SheetPosition.Hidden,
                confirmValueChange = { target ->
                    if (allowProgrammaticTransition.value) {
                        true
                    } else {
                        val isClosing = when (draggableState.settledValue) {
                            SheetPosition.Expanded ->
                                target == SheetPosition.PartiallyExpanded || target == SheetPosition.Hidden
                            SheetPosition.PartiallyExpanded -> target == SheetPosition.Hidden
                            SheetPosition.Hidden -> false
                        }
                        if (isClosing) onClose(target) else true
                    }
                },
            )
        }
        draggableState = state
        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = draggableState,
            positionalThreshold = { distance -> distance * 0.5f },
            animationSpec = animationSpec,
        )
        var initialTarget by remember { mutableStateOf<SheetPosition?>(null) }

        LaunchedEffect(initialTarget) {
            initialTarget?.let { draggableState.animateTo(it, animationSpec) }
        }
        LaunchedEffect(draggableState) {
            var hasBeenVisible = false
            snapshotFlow { draggableState.settledValue }.collect { value ->
                if (value == SheetPosition.Hidden) {
                    if (hasBeenVisible) navigation.popBackStack()
                } else {
                    hasBeenVisible = true
                }
            }
        }
        BackHandler(enabled = draggableState.settledValue != SheetPosition.Hidden) { onClose() }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null) { onClose() },
            contentAlignment = Alignment.TopCenter,
        ) {
            val fullHeightPx = constraints.maxHeight.toFloat()
            val density = LocalDensity.current
            val sheetMaxHeight = if (maxExpandedHeight != Dp.Unspecified) {
                minOf(maxHeight, maxExpandedHeight)
            } else {
                maxHeight
            }
            val sheetMaxHeightPx = with(density) { sheetMaxHeight.toPx() }
            val dragHandleHeightPx = with(density) { DragHandleContainerHeight.toPx() }
            val bottomInsetPx = WindowInsets.safeDrawing.getBottom(density).toFloat()
            val initialContentHeight = (
                minOf(fullHeightPx / 2f, sheetMaxHeightPx) - dragHandleHeightPx - bottomInsetPx
            ).roundToInt().coerceAtLeast(0)
            val sheetScope = remember(
                draggableState,
                fullHeightPx,
                dragHandleHeightPx,
                bottomInsetPx,
                initialContentHeight,
            ) {
                BottomSheetPageScaffoldScopeImpl(
                    minimumContentHeight = initialContentHeight,
                    visibleContentHeight = {
                        val sheetOffset = draggableState.offset.takeUnless(Float::isNaN) ?: fullHeightPx
                        (fullHeightPx - sheetOffset - dragHandleHeightPx - bottomInsetPx)
                            .roundToInt()
                            .coerceAtLeast(0)
                    },
                    onClose = { onClose() },
                )
            }

            Surface(
                modifier = modifier
                    .widthIn(max = SheetMaxWidth)
                    .fillMaxWidth()
                    .height(sheetMaxHeight)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = draggableState.offset.takeUnless(Float::isNaN)?.roundToInt()
                                ?: constraints.maxHeight,
                        )
                    }
                    .onSizeChanged { sheetSize ->
                        val expandedOffset = max(0f, fullHeightPx - sheetSize.height)
                        val hasPartialAnchor = sheetSize.height > fullHeightPx / 2f
                        val anchors = DraggableAnchors {
                            if (popupTypeChangeRequest(SheetPosition.Hidden)) {
                                SheetPosition.Hidden at fullHeightPx
                            }
                            if (hasPartialAnchor && popupTypeChangeRequest(SheetPosition.PartiallyExpanded)) {
                                SheetPosition.PartiallyExpanded at fullHeightPx / 2f
                            }
                            SheetPosition.Expanded at expandedOffset
                        }
                        val target = when {
                            anchors.hasPositionFor(draggableState.targetValue) -> draggableState.targetValue
                            anchors.hasPositionFor(SheetPosition.Expanded) -> SheetPosition.Expanded
                            anchors.hasPositionFor(SheetPosition.PartiallyExpanded) -> SheetPosition.PartiallyExpanded
                            else -> SheetPosition.Hidden
                        }
                        draggableState.updateAnchors(anchors, target)
                        if (initialTarget == null) {
                            initialTarget = when (initialPopupType) {
                                SheetPosition.Hidden -> error("unreachable")
                                SheetPosition.PartiallyExpanded ->
                                    if (anchors.hasPositionFor(SheetPosition.PartiallyExpanded)) {
                                        SheetPosition.PartiallyExpanded
                                    } else {
                                        SheetPosition.Expanded
                                    }
                                SheetPosition.Expanded -> SheetPosition.Expanded
                            }
                        }
                    }
                    .imePadding()
                    .clickable(interactionSource = null, indication = null, onClick = {}),
                shape = RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .anchoredDraggable(
                                state = draggableState,
                                orientation = Orientation.Vertical,
                                flingBehavior = flingBehavior,
                            )
                            .clickable(interactionSource = null, indication = null) { onClose() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                    sheetScope.content()
                }
            }
        }
    }
}

public enum class SheetPosition { Hidden, PartiallyExpanded, Expanded }

private val SheetMaxWidth = 640.dp
private val DragHandleContainerHeight = 24.dp
private val SheetCornerRadius = 28.dp
