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
        }
    }

    TraceEffect("HomeScreen", state.selectedTab) {
        i("Home tab changed (tab=${state.selectedTab})")
    }

    HomeScreenContent(
        selectedTab = state.selectedTab,
        onTabSelected = model::selectTab,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    navigationType: NavigationSuiteType = rememberCurrentNavigationSuiteType(),
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
        },
        content = {
            Box(Modifier.fillMaxSize().applyIf(collapsible, Modifier.nestedScroll(connection))) {
                content()
            }
        },
    )
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
