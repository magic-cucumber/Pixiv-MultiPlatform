package top.kagg886.pmf.ui.screen.main.home.space

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import top.kagg886.pmf.LocalNavController
import top.kagg886.pmf.i18n.Lang
import top.kagg886.pmf.i18n.home_space_follow
import top.kagg886.pmf.i18n.home_space_latest
import top.kagg886.pmf.util.TraceEffect
import top.kagg886.pmf.util.i
import top.kagg886.pmf.util.nav3.SerializableNavKey
import top.kagg886.pmf.util.nav3.viewModel

@Serializable
data object SpaceRoute : SerializableNavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreen(content: @Composable () -> Unit) {
    val model = viewModel<SpaceModel> { SpaceModel() }
    val state by model.collectAsState()
    val nav = LocalNavController.current

    model.collectSideEffect { effect ->
        when (effect) {
            is SpaceModelEffect.NavigateTo -> nav.update {
                replaceTop(effect.destination)
            }
        }
    }

    TraceEffect("SpaceScreen", state.selectedTab) {
        i("Space sub-tab changed (tab=${state.selectedTab})")
    }

    SpaceScreenContent(
        selectedTab = state.selectedTab,
        onTabSelected = model::selectTab,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpaceScreenContent(
    selectedTab: SpaceTab,
    onTabSelected: (SpaceTab) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            SpaceTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Text(
                            text = when (tab) {
                                SpaceTab.FOLLOW -> stringResource(Lang.string.home_space_follow)
                                SpaceTab.LATEST -> stringResource(Lang.string.home_space_latest)
                            },
                        )
                    },
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            content()
        }
    }
}
