package top.kagg886.pmf.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
                val outlineBorder = CardDefaults.outlinedCardBorder()
                Card(
                    onClick = onClick,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    border = BorderStroke(
                        width = outlineBorder.width * 1.5f,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                ) {
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
