package top.kagg886.pmf.ui.component.bottomsheet

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

@Stable
public interface BottomSheetPageScaffoldScope {
    public fun Modifier.matchContent(): Modifier
    public fun close()
}

internal class BottomSheetPageScaffoldScopeImpl(
    private val minimumContentHeight: Int,
    private val visibleContentHeight: () -> Int,
    private val onClose: () -> Unit,
) : BottomSheetPageScaffoldScope {
    override fun Modifier.matchContent(): Modifier = layout { measurable, constraints ->
        val height = visibleContentHeight()
            .coerceAtLeast(minimumContentHeight)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }

    override fun close(): Unit = onClose()
}
