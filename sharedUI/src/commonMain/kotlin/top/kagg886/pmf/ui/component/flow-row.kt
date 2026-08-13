package top.kagg886.pmf.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A Layout-based flow row with a measured overflow indicator.
 *
 * The measurement and last-line backtracking approach is adapted from
 * https://github.com/astamato/ContextualFlowRowSample.
 */
@Composable
fun <T> ContextualFlowRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    onMoreClick: (List<T>) -> Unit = {},
    overflowContent: @Composable (remainingCount: Int, onClick: () -> Unit) -> Unit = { count, onClick ->
        Text(
            text = "+$count",
            modifier = Modifier.clickable(onClick = onClick),
        )
    },
    itemContent: @Composable (T) -> Unit,
) {
    require(maxLines > 0) { "maxLines must be greater than zero" }

    val visibleItemsCount = remember(items) { mutableIntStateOf(items.size) }
    val remainingCount = (items.size - visibleItemsCount.intValue).coerceAtLeast(0)

    Layout(
        modifier = modifier,
        content = {
            items.forEach { item -> itemContent(item) }

            // The largest possible counter reserves enough space for the real +N item.
            overflowContent(items.size) {}

            overflowContent(remainingCount) {
                val firstHiddenIndex = visibleItemsCount.intValue.coerceIn(0, items.size)
                onMoreClick(items.drop(firstHiddenIndex))
            }
        },
    ) { measurables, constraints ->
        if (items.isEmpty()) {
            visibleItemsCount.intValue = 0
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }

        val itemMeasurables = measurables.take(items.size)
        val templateMeasurable = measurables[items.size]
        val actualOverflowMeasurable = measurables[items.size + 1]
        val itemPlaceables = itemMeasurables.map { it.measure(Constraints()) }
        val templatePlaceable = templateMeasurable.measure(Constraints())
        val actualOverflowPlaceable = actualOverflowMeasurable.measure(Constraints())

        val result = calculateContextualFlowLayout(
            placeables = itemPlaceables,
            overflowWidth = templatePlaceable.width,
            overflowHeight = actualOverflowPlaceable.height,
            maxWidth = constraints.maxWidth,
            maxLines = maxLines,
            horizontalSpacing = horizontalSpacing.roundToPx(),
            verticalSpacing = verticalSpacing.roundToPx(),
        )
        visibleItemsCount.intValue = result.visibleItemCount

        val placedItems = buildList {
            addAll(result.itemPositions)
            result.overflowPosition?.let { position ->
                add(PlacedFlowItem(actualOverflowPlaceable, position))
            }
        }
        val width = result.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = result.height.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width, height) {
            placedItems.forEach { item ->
                item.placeable.placeRelative(item.position.first, item.position.second)
            }
        }
    }
}

private data class PlacedFlowItem(
    val placeable: Placeable,
    val position: Pair<Int, Int>,
)

private data class ContextualFlowLayoutResult(
    val itemPositions: List<PlacedFlowItem>,
    val overflowPosition: Pair<Int, Int>?,
    val visibleItemCount: Int,
    val width: Int,
    val height: Int,
)

private fun calculateContextualFlowLayout(
    placeables: List<Placeable>,
    overflowWidth: Int,
    overflowHeight: Int,
    maxWidth: Int,
    maxLines: Int,
    horizontalSpacing: Int,
    verticalSpacing: Int,
): ContextualFlowLayoutResult {
    if (placeables.isEmpty()) {
        return ContextualFlowLayoutResult(
            itemPositions = emptyList(),
            overflowPosition = null,
            visibleItemCount = 0,
            width = 0,
            height = 0,
        )
    }

    val itemPositions = mutableListOf<PlacedFlowItem>()
    var currentX = 0
    var currentY = 0
    var currentLine = 1
    var lineHeight = 0
    var maxWidthUsed = 0

    fun resultWithOverflow(): ContextualFlowLayoutResult {
        val overflowPosition = currentX to currentY
        return ContextualFlowLayoutResult(
            itemPositions = itemPositions,
            overflowPosition = overflowPosition,
            visibleItemCount = itemPositions.size,
            width = maxOf(maxWidthUsed, overflowPosition.first + overflowWidth),
            height = maxOf(currentY + lineHeight, currentY + overflowHeight),
        )
    }

    for (index in placeables.indices) {
        val placeable = placeables[index]
        val remainingAfterThis = placeables.size - index - 1

        if (currentX > 0 && currentX + placeable.width > maxWidth) {
            if (currentLine >= maxLines) {
                return resultWithOverflow()
            }

            currentLine++
            currentX = 0
            currentY += lineHeight + verticalSpacing
            lineHeight = 0
        }

        if (
            currentLine == maxLines &&
            remainingAfterThis > 0 &&
            currentX + placeable.width + horizontalSpacing + overflowWidth > maxWidth
        ) {
            return resultWithOverflow()
        }

        itemPositions += PlacedFlowItem(placeable, currentX to currentY)
        lineHeight = maxOf(lineHeight, placeable.height)
        currentX += placeable.width + horizontalSpacing
        maxWidthUsed = maxOf(maxWidthUsed, currentX - horizontalSpacing)
    }

    return ContextualFlowLayoutResult(
        itemPositions = itemPositions,
        overflowPosition = null,
        visibleItemCount = itemPositions.size,
        width = maxWidthUsed,
        height = currentY + lineHeight,
    )
}

@Preview(name = "Contextual flow row", showBackground = true, widthDp = 320)
@Composable
private fun ContextualFlowRowPreview() {
    val items = listOf(
        "Compose",
        "Kotlin",
        "Multiplatform",
        "Android",
        "iOS",
        "Desktop",
    )

    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ContextualFlowRow(
                items = items,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                horizontalSpacing = 4.dp,
                onMoreClick = {},
                overflowContent = { count, onClick ->
                    Text(
                        text = "+$count",
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = onClick)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
            ) { item ->
                Text(
                    text = item,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
