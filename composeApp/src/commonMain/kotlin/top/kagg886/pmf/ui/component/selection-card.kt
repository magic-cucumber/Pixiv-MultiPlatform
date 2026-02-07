package top.kagg886.pmf.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SelectionCard(
    modifier: Modifier = Modifier,
    select: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedContent(
        targetState = select,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        modifier = modifier,
    ) {
        when (it) {
            true -> {
                ElevatedCard(onClick) {
                    content()
                }
            }

            false -> {
                OutlinedCard(onClick) {
                    content()
                }
            }
        }
    }
}
