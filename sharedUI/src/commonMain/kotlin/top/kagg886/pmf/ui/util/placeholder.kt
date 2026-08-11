package top.kagg886.pmf.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.fade
import com.eygraber.compose.placeholder.placeholder as skeleton

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/11 09:34
 * ================================================
 */

@Composable
fun Modifier.placeholder(
    visible: Boolean = true,
    shape: Shape = RectangleShape,

    highlight: Color = MaterialTheme.colorScheme.surface,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
) = skeleton(
    visible = visible,
    color = background,
    shape = shape,
    highlight = PlaceholderHighlight.fade(highlight)
)


@Preview(showBackground = true)
@Composable
private fun PlaceholderPreviewPlaceHolder() {
    MaterialTheme {
        Card(Modifier.placeholder(true, shape = CardDefaults.shape, background = CardDefaults.cardColors().containerColor)) {
            Box(Modifier.size(200.dp,120.dp), contentAlignment = Alignment.Center) {
                Text("Hello, World!")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderPreview() {
    MaterialTheme {
        Card(Modifier.placeholder(false, shape = CardDefaults.shape, background = CardDefaults.cardColors().containerColor)) {
            Box(Modifier.size(200.dp,120.dp), contentAlignment = Alignment.Center) {
                Text("Hello, World!")
            }
        }
    }
}

