package top.kagg886.pmf.ui.component.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round as RoundCap
import androidx.compose.ui.graphics.StrokeJoin.Companion.Round as RoundJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * AI 翻译图标（原文模式）：左侧"文"大、右侧"A"小，
 * 表示当前显示为原文，点击后翻译为目标语言。
 * 描边色为源色，供 [androidx.compose.material3.Icon] 的 tint 覆盖。
 */
val Translate: ImageVector = Builder(
    name = "Translate",
    defaultWidth = 24.0.dp,
    defaultHeight = 24.0.dp,
    viewportWidth = 256.0f,
    viewportHeight = 256.0f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 20.0f,
        strokeLineCap = RoundCap,
        strokeLineJoin = RoundJoin,
        strokeLineMiter = 4.0f,
    ) {
        // 大"文"（translate(0,64) 后）
        moveTo(87.982f, 96.0f)
        verticalLineTo(120.0f)
        moveTo(23.982f, 120.0f)
        horizontalLineTo(151.982f)
        moveTo(119.982f, 120.0f)
        arcToRelative(96.0f, 96.0f, 0.0f, false, true, -96.0f, 96.0f)
        moveTo(64.6968f, 160.0006f)
        arcToRelative(96.01575f, 96.01575f, 0.0f, false, false, 87.27974f, 55.96606f)
        // 小"A"（translate(120.007,108) scale(0.5) 后）
        moveTo(235.998f, 216.0f)
        lineTo(207.998f, 160.0f)
        lineTo(179.998f, 216.0f)
        moveTo(187.998f, 200.0f)
        horizontalLineTo(227.998f)
    }
}.build()
