package top.kagg886.pmf.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

private const val DefaultCollapsedMaxLines = 3

@Composable
fun ExpandableText(
    modifier: Modifier = Modifier,
    text: String,
    expandText: String,
    collapseText: String,
    collapsedMaxLines: Int = DefaultCollapsedMaxLines,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val linkColor = MaterialTheme.colorScheme.primary
    val currentOnExpandChange by rememberUpdatedState(onExpandChange)
    var availableWidth by remember { mutableIntStateOf(0) }

    val collapsedLayout = remember(text, style, availableWidth, collapsedMaxLines) {
        if (availableWidth == 0) {
            null
        } else {
            textMeasurer.measure(
                text = text,
                style = style,
                maxLines = collapsedMaxLines,
                overflow = TextOverflow.Clip,
                constraints = Constraints(maxWidth = availableWidth),
            )
        }
    }
    val hasOverflow = collapsedLayout?.hasVisualOverflow == true
    val collapsedEnd = remember(text, expandText, style, availableWidth, collapsedMaxLines, hasOverflow) {
        if (!hasOverflow) {
            text.length
        } else {
            val layout = checkNotNull(collapsedLayout)
            val lastLine = collapsedMaxLines - 1
            var low = layout.getLineStart(lastLine)
            var high = layout.getLineEnd(lastLine, visibleEnd = true)

            while (low < high) {
                val middle = (low + high + 1) / 2
                val candidate = text.substring(0, middle).trimEnd() + "...$expandText"
                val candidateLayout = textMeasurer.measure(
                    text = candidate,
                    style = style,
                    maxLines = collapsedMaxLines,
                    overflow = TextOverflow.Clip,
                    constraints = Constraints(maxWidth = availableWidth),
                )
                if (candidateLayout.hasVisualOverflow) high = middle - 1 else low = middle
            }
            low
        }
    }
    val linkStyles = remember(linkColor) {
        TextLinkStyles(style = SpanStyle(color = linkColor))
    }
    val collapsedText = buildAnnotatedString {
        append(text.substring(0, collapsedEnd).trimEnd())
        if (hasOverflow) {
            append("...")
            withLink(
                LinkAnnotation.Clickable(tag = "expand", styles = linkStyles) {
                    currentOnExpandChange(true)
                },
            ) {
                append(expandText)
            }
        }
    }
    val expandedText = buildAnnotatedString {
        append(text)
        if (hasOverflow) {
            append(" ")
            withLink(
                LinkAnnotation.Clickable(tag = "collapse", styles = linkStyles) {
                    currentOnExpandChange(false)
                },
            ) {
                append(collapseText)
            }
        }
    }

    AnimatedContent(
        // Overflow is unknown until the text has been measured. Using it as part of the target
        // state would replay the expand animation whenever an expanded lazy item re-enters the
        // composition and finishes measuring.
        targetState = isExpanded,
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { availableWidth = it.width }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        transitionSpec = {
            (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false))
        },
        label = "expandable-text",
    ) { expanded ->
        Text(
            text = if (expanded) expandedText else collapsedText,
            style = style,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Clip,
        )
    }
}
