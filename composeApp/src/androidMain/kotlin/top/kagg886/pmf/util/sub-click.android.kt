package top.kagg886.pmf.util

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

actual fun Modifier.onSubClick(onSubClick: () -> Unit): Modifier = combinedClickable(
    enabled = true,
    onClick = {},
    onLongClick = onSubClick
)
