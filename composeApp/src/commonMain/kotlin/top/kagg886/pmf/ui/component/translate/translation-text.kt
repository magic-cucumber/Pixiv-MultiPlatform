package top.kagg886.pmf.ui.component.translate

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import top.kagg886.pmf.translate.SentencePair
import top.kagg886.pmf.ui.util.withClickable

/** 翻译失败时展示原文使用的淡红色（深色主题用更亮的红色变体）。 */
@Composable
fun failedTranslationColor(): Color = if (isSystemInDarkTheme()) {
    Color(0xFFEF9A9A).copy(alpha = 0.9f)
} else {
    Color(0xFFC62828).copy(alpha = 0.8f)
}

/** 翻译进行中流式译文使用的灰色。 */
@Composable
fun translatingTranslationColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

/**
 * 把句对句译文写入 [AnnotatedString.Builder]。
 *
 * [showingOriginal] 中包含的下标渲染原文，其余渲染译文；点击某句时回调 [onSentenceClick]。
 */
fun AnnotatedString.Builder.appendSentencePairs(
    pairs: List<SentencePair>,
    showingOriginal: Set<Int>,
    colors: ColorScheme,
    onSentenceClick: (Int) -> Unit,
) {
    pairs.forEachIndexed { index, pair ->
        if (index > 0) append('\n')
        val text = if (index in showingOriginal) pair.original else pair.translated
        withClickable(colors, text) {
            onSentenceClick(index)
        }
    }
}

/** 独立的句对句译文组件：点击句子在译文/原文之间切换。 */
@Composable
fun SentenceTranslationText(
    pairs: List<SentencePair>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    var showingOriginal by remember(pairs) { mutableStateOf(setOf<Int>()) }
    val colors = MaterialTheme.colorScheme
    Text(
        text = buildAnnotatedString {
            appendSentencePairs(pairs, showingOriginal, colors) { index ->
                showingOriginal =
                    if (index in showingOriginal) showingOriginal - index else showingOriginal + index
            }
        },
        style = style,
        modifier = modifier,
    )
}
