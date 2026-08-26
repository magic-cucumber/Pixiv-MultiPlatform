package top.kagg886.pmf.ui.screen.main.home.rank

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.logger.Logger
import top.kagg886.pmf.ui.screen.main.home.rank.day.RankDayRoute
import top.kagg886.pmf.ui.screen.main.home.rank.dayfemale.RankDayFemaleRoute
import top.kagg886.pmf.ui.screen.main.home.rank.daymale.RankDayMaleRoute
import top.kagg886.pmf.ui.screen.main.home.rank.month.RankMonthRoute
import top.kagg886.pmf.ui.screen.main.home.rank.week.RankWeekRoute
import top.kagg886.pmf.ui.screen.main.home.rank.weekoriginal.RankWeekOriginalRoute
import top.kagg886.pmf.ui.screen.main.home.rank.weekrookie.RankWeekRookieRoute
import top.kagg886.pmf.util.nav3.SerializableNavKey

enum class RankTab(val destination: SerializableNavKey) {
    DAY(RankDayRoute),
    WEEK(RankWeekRoute),
    MONTH(RankMonthRoute),
    DAY_MALE(RankDayMaleRoute),
    DAY_FEMALE(RankDayFemaleRoute),
    WEEK_ORIGINAL(RankWeekOriginalRoute),
    WEEK_ROOKIE(RankWeekRookieRoute),
}

@Logger
class RankModel : ViewModel(),
    OrbitContainerHost<RankModelState, RankModelState, RankModelEffect> {

    override val container: OrbitContainer<RankModelState, RankModelState, RankModelEffect> =
        orbitContainer(RankModelState()) {
            logger.i { "Initializing rank sub-tab navigation with Daily selected" }
        }

    fun selectTab(tab: RankTab) = intent {
        if (state.selectedTab == tab) {
            logger.v { "Ignoring duplicate rank sub-tab selection (tab=$tab)" }
            return@intent
        }

        logger.i { "Selecting rank sub-tab (tab=$tab)" }
        reduce { state.copy(selectedTab = tab) }
        postSideEffect(RankModelEffect.NavigateTo(tab.destination))
    }
}

data class RankModelState(
    val selectedTab: RankTab = RankTab.DAY,
)

sealed interface RankModelEffect {
    data class NavigateTo(val destination: SerializableNavKey) : RankModelEffect
}
