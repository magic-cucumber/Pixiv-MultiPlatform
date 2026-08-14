package top.kagg886.pmf.ui.util

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import top.kagg886.pmf.ui.component.adaptive.NavigationSuiteScaffoldDefaults
import top.kagg886.pmf.ui.component.adaptive.NavigationSuiteType
import top.kagg886.pmf.util.TraceEffect
import top.kagg886.pmf.util.d


@Composable
fun rememberCurrentNavigationSuiteType(): NavigationSuiteType {
    val (windowWidth,windowHeight) = with(LocalDensity.current) {
        val size = LocalWindowInfo.current.containerSize
        val width = size.width.toDp()
        val height = size.height.toDp()
        width to height
    }

    TraceEffect("adaptive.kt",windowWidth,windowHeight) {
        d("current window size: $windowWidth x $windowHeight")
    }

    val layoutType = if (windowWidth >= 1200.dp) {
        NavigationSuiteType.NavigationDrawer
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    }

    TraceEffect("adaptive.kt",layoutType) {
        d("current window size: $layoutType")
    }

    return layoutType
}
