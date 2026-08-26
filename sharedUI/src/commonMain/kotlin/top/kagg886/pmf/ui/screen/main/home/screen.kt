package top.kagg886.pmf.ui.screen.main.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import top.kagg886.pmf.ui.screen.main.home.HomeTab.*
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
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        layoutType = rememberCurrentNavigationSuiteType(),
        navigationSuiteItems = {
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
            item(
                selected = selectedTab == HomeTab.RECOMMEND,
                onClick = { onTabSelected(HomeTab.RECOMMEND) },
                icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                label = { Text(stringResource(Lang.string.home_recommend)) },
            )
            item(
                selected = selectedTab == HomeTab.RANK,
                onClick = { onTabSelected(HomeTab.RANK) },
                icon = { Icon(Icons.Outlined.Star, contentDescription = null) },
                label = { Text(stringResource(Lang.string.home_rank)) },
            )
            item(
                selected = selectedTab == HomeTab.SPACE,
                onClick = { onTabSelected(HomeTab.SPACE) },
                icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                label = { Text(stringResource(Lang.string.home_space)) },
            )
        },
        content = content,
    )
}
