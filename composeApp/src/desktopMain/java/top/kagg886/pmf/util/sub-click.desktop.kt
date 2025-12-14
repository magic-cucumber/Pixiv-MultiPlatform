package top.kagg886.pmf.util

import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton

actual fun Modifier.onSubClick(onSubClick: () -> Unit): Modifier = onClick(
    matcher = PointerMatcher.mouse(PointerButton.Secondary),
    onClick = onSubClick
)
