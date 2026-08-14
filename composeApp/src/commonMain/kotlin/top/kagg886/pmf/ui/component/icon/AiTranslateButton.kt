package top.kagg886.pmf.ui.component.icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.kagg886.pmf.res.*
import top.kagg886.pmf.util.stringResource

/**
 * AI 翻译按钮：原文模式显示 [Translate]，译文模式显示 [ShowOriginal]，
 * 翻译中显示加载指示。图标以 [LocalContentColor] 着色，跟随明暗主题。
 *
 * @param translated 是否处于译文态（点击后恢复原文）
 * @param translating 是否正在翻译
 * @param iconSize 字形大小（默认 24dp，正文行内建议 18dp）
 * @param touchSize 触达区域大小（默认与字形一致）
 */
@Composable
fun AiTranslateButton(
    translated: Boolean,
    translating: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    touchSize: Dp = iconSize,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(touchSize)
            .clickable(enabled = !translating, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (translating) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (translated) ShowOriginal else Translate,
                contentDescription = stringResource(
                    if (translated) Res.string.ai_translate_show_original else Res.string.ai_translate_translate,
                ),
                modifier = Modifier.size(iconSize),
                tint = LocalContentColor.current,
            )
        }
    }
}
