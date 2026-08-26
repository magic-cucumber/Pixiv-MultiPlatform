package top.kagg886.pmf.ui.screen.main.home.rank

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import top.kagg886.pmf.i18n.home_rank_day
import top.kagg886.pmf.i18n.home_rank_day_female
import top.kagg886.pmf.i18n.home_rank_day_male
import top.kagg886.pmf.i18n.home_rank_month
import top.kagg886.pmf.i18n.home_rank_week
import top.kagg886.pmf.i18n.home_rank_week_original
import top.kagg886.pmf.i18n.home_rank_week_rookie
import top.kagg886.pmf.util.TraceEffect
import top.kagg886.pmf.util.i
import top.kagg886.pmf.util.nav3.SerializableNavKey
import top.kagg886.pmf.util.nav3.viewModel

@Serializable
data object RankRoute : SerializableNavKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankScreen(content: @Composable () -> Unit) {
    val model = viewModel<RankModel> { RankModel() }
    val state by model.collectAsState()
    val nav = LocalNavController.current

    model.collectSideEffect { effect ->
        when (effect) {
            is RankModelEffect.NavigateTo -> nav.update {
                replaceTop(effect.destination)
            }
        }
    }

    TraceEffect("RankScreen", state.selectedTab) {
        i("Rank sub-tab changed (tab=${state.selectedTab})")
    }

    RankScreenContent(
        selectedTab = state.selectedTab,
        onTabSelected = model::selectTab,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankScreenContent(
    selectedTab: RankTab,
    onTabSelected: (RankTab) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab.ordinal) {
            RankTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Text(
                            text = when (tab) {
                                RankTab.DAY -> stringResource(Lang.string.home_rank_day)
                                RankTab.WEEK -> stringResource(Lang.string.home_rank_week)
                                RankTab.MONTH -> stringResource(Lang.string.home_rank_month)
                                RankTab.DAY_MALE -> stringResource(Lang.string.home_rank_day_male)
                                RankTab.DAY_FEMALE -> stringResource(Lang.string.home_rank_day_female)
                                RankTab.WEEK_ORIGINAL -> stringResource(Lang.string.home_rank_week_original)
                                RankTab.WEEK_ROOKIE -> stringResource(Lang.string.home_rank_week_rookie)
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
