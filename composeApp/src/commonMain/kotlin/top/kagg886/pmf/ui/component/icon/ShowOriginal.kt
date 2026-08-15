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
 * 查看原文图标（译文模式）：左侧"A"大、右侧"文"小，
 * 表示当前显示为译文，点击恢复原文。
 * 描边色为源色，供 [androidx.compose.material3.Icon] 的 tint 覆盖。
 */
val ShowOriginal: ImageVector = Builder(
    name = "ShowOriginal",
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
        // 大"A"（translate(-95.982,0) 后）
        moveTo(135.999f, 216.0f)
        lineTo(79.999f, 104.0f)
        lineTo(23.999f, 216.0f)
        moveTo(39.999f, 184.0f)
        horizontalLineTo(119.999f)
        // 小"文"（translate(156.009,140) scale(0.5) 后）
        moveTo(200.0f, 156.0f)
        verticalLineTo(168.0f)
        moveTo(168.0f, 168.0f)
        horizontalLineTo(232.0f)
        moveTo(216.0f, 168.0f)
        arcToRelative(48.0f, 48.0f, 0.0f, false, true, -48.0f, 48.0f)
        moveTo(188.357f, 188.0f)
        arcToRelative(48.007875f, 48.007875f, 0.0f, false, false, 43.63987f, 27.98303f)
    }
}.build()
