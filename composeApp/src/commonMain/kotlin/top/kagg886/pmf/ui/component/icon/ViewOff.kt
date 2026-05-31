package top.kagg886.pmf.ui.component.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ViewOff: ImageVector by lazy {
    Builder(
        name = "ViewOff",
        defaultWidth = 24.0.dp,
        defaultHeight = 24.0.dp,
        viewportWidth = 24.0f,
        viewportHeight = 24.0f,
    ).apply {
        path(
            fill = SolidColor(Color(0xFF5f6368)),
            stroke = null,
            strokeLineWidth = 0.0f,
            strokeLineCap = Butt,
            strokeLineJoin = Miter,
            strokeLineMiter = 4.0f,
            pathFillType = NonZero,
        ) {
            moveTo(12.0f, 6.5f)
            curveToRelative(3.79f, 0.0f, 7.17f, 2.13f, 8.82f, 5.5f)
            curveToRelative(-0.7f, 1.43f, -1.79f, 2.61f, -3.07f, 3.46f)
            lineToRelative(1.43f, 1.43f)
            curveTo(20.72f, 15.8f, 21.95f, 14.13f, 23.0f, 12.0f)
            curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
            curveToRelative(-1.4f, 0.0f, -2.74f, 0.25f, -3.98f, 0.7f)
            lineToRelative(1.68f, 1.68f)
            curveToRelative(0.73f, -0.24f, 1.5f, -0.38f, 2.3f, -0.38f)
            close()
            moveTo(2.01f, 4.87f)
            lineToRelative(2.68f, 2.68f)
            curveTo(3.06f, 8.83f, 1.77f, 10.36f, 1.0f, 12.0f)
            curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
            curveToRelative(1.55f, 0.0f, 3.03f, -0.3f, 4.38f, -0.84f)
            lineToRelative(3.42f, 3.42f)
            lineToRelative(1.27f, -1.27f)
            lineTo(3.28f, 3.6f)
            lineTo(2.01f, 4.87f)
            close()
            moveTo(7.53f, 10.39f)
            lineToRelative(1.55f, 1.55f)
            curveToRelative(-0.05f, 0.28f, -0.08f, 0.56f, -0.08f, 0.86f)
            curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
            curveToRelative(0.3f, 0.0f, 0.58f, -0.03f, 0.86f, -0.08f)
            lineToRelative(1.55f, 1.55f)
            curveToRelative(-0.74f, 0.46f, -1.6f, 0.73f, -2.41f, 0.73f)
            curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
            curveToRelative(0.0f, -0.81f, 0.27f, -1.67f, 0.73f, -2.41f)
            close()
            moveTo(11.84f, 9.02f)
            lineToRelative(3.15f, 3.15f)
            lineToRelative(0.02f, -0.16f)
            curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
            lineToRelative(-0.17f, 0.01f)
            close()
        }
    }.build()
}
