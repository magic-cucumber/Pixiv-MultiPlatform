package top.kagg886.pmf.ui.screen.main.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.home_rank
import top.kagg886.pmf.i18n.home_recommend
import top.kagg886.pmf.i18n.home_space
import top.kagg886.pmf.ui.component.adaptive.NavigationSuiteScaffold
import top.kagg886.pmf.ui.component.adaptive.NavigationSuiteType
import top.kagg886.pmf.ui.screen.main.home.HomeTab.*
import top.kagg886.pmf.ui.util.applyIf
import top.kagg886.pmf.ui.util.rememberCurrentNavigationSuiteType
import top.kagg886.pmf.util.TraceEffect
import top.kagg886.pmf.util.i
import top.kagg886.pmf.util.nav3.SerializableNavKey
import top.kagg886.pmf.util.nav3.viewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.ui.draw.rotate
import top.kagg886.pmf.i18n.home_refresh
import top.kagg886.pmf.i18n.home_refreshing
import top.kagg886.pmf.i18n.home_back_to_top
import top.kagg886.pmf.util.device.Platform
import top.kagg886.pmf.util.device.Mobile
import top.kagg886.pmf.util.device.current
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview

@Serializable
data object HomeRoute : SerializableNavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(content: @Composable () -> Unit) {
    val model = viewModel<HomeViewModel> { HomeViewModel() }
    val state by model.collectAsState()
    val nav = LocalNavController.current

    model.collectSideEffect { effect ->
        when (effect) {
            is HomeViewModelEffect.NavigateTo -> nav.update {
                replaceTop(effect.destination)
            }
            HomeViewModelEffect.FABClicked, HomeViewModelEffect.RefreshRequested -> Unit
        }
    }

    TraceEffect("HomeScreen", state.selectedTab, state.isRefreshing, state.canBackToTop) {
        i("Home state changed (tab=${state.selectedTab}, refreshing=${state.isRefreshing}, canBackToTop=${state.canBackToTop})")
    }

    HomeScreenContent(
        selectedTab = state.selectedTab,
        navigationType = rememberCurrentNavigationSuiteType(),
        mobile = Platform.current is Mobile,
        isRefreshing = state.isRefreshing,
        canBackToTop = state.canBackToTop,
        onFabClicked = model::dispatchFABClicked,
        onRefresh = model::requestRefresh,
        onTabSelected = model::selectTab,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    navigationType: NavigationSuiteType,
    mobile: Boolean,
    isRefreshing: Boolean,
    canBackToTop: Boolean,
    onFabClicked: () -> Unit,
    onRefresh: () -> Unit,
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    content: @Composable () -> Unit,
) {
    val state = remember(navigationType) { HomeHeaderState() }
    val animationScope = rememberCoroutineScope()
    DisposableEffect(state) {
        onDispose { state.settling?.cancel() }
    }
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y == 0f) return Offset.Zero
                state.settling?.cancel()
                return Offset(0f, state.move(available.y))
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                state.settling?.cancel()
                // 列表完成惯性后吸附；不消费列表剩余速度，也不阻塞下一次手势。
                if (state.offset < 0f && state.offset > -state.height) {
                    state.settling = animationScope.launch {
                        val target = if (state.offset < -state.height / 2f) -state.height else 0f
                        animate(state.offset, target, animationSpec = tween(180)) { value, _ ->
                            state.offset = value.coerceIn(-state.height, 0f)
                        }
                    }
                }
                return Velocity.Zero
            }
        }
    }
    val collapsible =
        navigationType == NavigationSuiteType.NavigationBar || navigationType == NavigationSuiteType.NavigationRail

    NavigationSuiteScaffold(
        layoutType = navigationType,
        navigationSuiteItems = {
            topAppBar(
                modifier = Modifier.applyIf(
                    collapsible,
                    Modifier.clipToBounds().layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        val height = placeable.height.toFloat()
                        if (state.height != height) {
                            state.height = height
                            state.offset = state.offset.coerceIn(-height, 0f)
                        }
                        val offset = state.offset.roundToInt()
                        layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) {
                            placeable.placeRelative(0, offset)
                        }
                    }
                )
            ) {
                title {
                    Text(
                        text = stringResource(
                            resource = when (selectedTab) {
                                RECOMMEND -> Lang.string.home_recommend
                                RANK -> Lang.string.home_rank
                                SPACE -> Lang.string.home_space
                            }
                        )
                    )
                }
            }
            item(
                selected = selectedTab == RECOMMEND,
                onClick = { onTabSelected(RECOMMEND) },
                icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                label = { Text(stringResource(Lang.string.home_recommend)) },
            )
            item(
                selected = selectedTab == RANK,
                onClick = { onTabSelected(RANK) },
                icon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                label = { Text(stringResource(Lang.string.home_rank)) },
            )
            item(
                selected = selectedTab == SPACE,
                onClick = { onTabSelected(SPACE) },
                icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                label = { Text(stringResource(Lang.string.home_space)) },
            )

            fab(
                enabled = canBackToTop || (!mobile && !isRefreshing),
                wrapper = { button ->
                    AnimatedVisibility(
                        visible = !mobile || canBackToTop,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) { button() }
                },
            ) {
                onClick(onFabClicked)
                icon {
                    AnimatedContent(
                        targetState = canBackToTop,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                    ) { backToTop ->
                        HomeFabIcon(backToTop, isRefreshing)
                    }
                }
                text {
                    Text(stringResource(when {
                        canBackToTop -> Lang.string.home_back_to_top
                        isRefreshing -> Lang.string.home_refreshing
                        else -> Lang.string.home_refresh
                    }))
                }
            }
        },
        content = {
            Box(Modifier.fillMaxSize().applyIf(collapsible, Modifier.nestedScroll(connection))) {
                if (mobile) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = rememberPullToRefreshState(),
                        modifier = Modifier.fillMaxSize(),
                    ) { content() }
                } else {
                    content()
                }
            }
        },
    )
}

@Composable
private fun HomeFabIcon(backToTop: Boolean, isRefreshing: Boolean) {
    val rotation = if (!backToTop && isRefreshing) {
        val transition = rememberInfiniteTransition(label = "Home refresh")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "Refresh rotation",
        )
        angle
    } else 0f
    Icon(
        imageVector = if (backToTop) Icons.Outlined.VerticalAlignTop else Icons.Outlined.Refresh,
        contentDescription = stringResource(when {
            backToTop -> Lang.string.home_back_to_top
            isRefreshing -> Lang.string.home_refreshing
            else -> Lang.string.home_refresh
        }),
        modifier = Modifier.rotate(rotation),
    )
}

@Preview(name = "Mobile hidden FAB", widthDp = 400, heightDp = 800)
@Composable
private fun HomeMobilePreview() = HomePreview(mobile = true)

@Preview(name = "Mobile drawer back to top", widthDp = 1200, heightDp = 800)
@Composable
private fun HomeMobileDrawerPreview() = HomePreview(
    mobile = true,
    navigationType = NavigationSuiteType.NavigationDrawer,
    canBackToTop = true,
)

@Preview(name = "Desktop refresh", widthDp = 800, heightDp = 800)
@Composable
private fun HomeDesktopPreview() = HomePreview(mobile = false)

@Preview(name = "Desktop refreshing", widthDp = 1200, heightDp = 800)
@Composable
private fun HomeRefreshingPreview() = HomePreview(
    mobile = false,
    navigationType = NavigationSuiteType.NavigationDrawer,
    isRefreshing = true,
)

@Composable
private fun HomePreview(
    mobile: Boolean,
    navigationType: NavigationSuiteType = NavigationSuiteType.NavigationBar,
    isRefreshing: Boolean = false,
    canBackToTop: Boolean = false,
) {
    MaterialTheme {
        HomeScreenContent(
            navigationType = navigationType,
            mobile = mobile,
            isRefreshing = isRefreshing,
            canBackToTop = canBackToTop,
            selectedTab = RECOMMEND,
            onFabClicked = {},
            onRefresh = {},
            onTabSelected = {},
            content = {},
        )
    }
}

/** 只管理标题偏移，不读取或替换子页面的滚动状态。 */
private class HomeHeaderState {
    var height by mutableFloatStateOf(0f)
    var offset by mutableFloatStateOf(0f)
    var settling: Job? = null

    fun move(delta: Float): Float {
        val previous = offset
        offset = (previous + delta).coerceIn(-height, 0f)
        return offset - previous
    }
}
