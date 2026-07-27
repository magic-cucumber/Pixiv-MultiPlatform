package top.kagg886.pmf.util.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** The layout family appropriate for the current application window. */
public enum class LayoutType {
    Phone,
    Tablet,
    Desktop,
}

/**
 * Resolves the layout family from the current window, updating when that window is resized.
 *
 * Desktop applications retain the [LayoutType.Desktop] family. Mobile windows narrower than
 * 600 dp use [LayoutType.Phone]; all other mobile windows use [LayoutType.Tablet].
 */
public val Platform.Companion.LayoutType: LayoutType
    @Composable
    get() = when (current) {
        is Desktop -> top.kagg886.pmf.util.device.LayoutType.Desktop
        else -> if (LocalWindowInfo.current.containerDpSize.width < 600.dp) {
            top.kagg886.pmf.util.device.LayoutType.Phone
        } else {
            top.kagg886.pmf.util.device.LayoutType.Tablet
        }
    }
