package top.kagg886.pmf.ui.screen.main.home

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.SideEffectMode
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.logger.Logger
import top.kagg886.pmf.ui.screen.main.home.rank.RankRoute
import top.kagg886.pmf.ui.screen.main.home.recommend.RecommendRoute
import top.kagg886.pmf.ui.screen.main.home.space.SpaceRoute
import top.kagg886.pmf.util.nav3.SerializableNavKey

enum class HomeTab(val destination: SerializableNavKey) {
    RECOMMEND(RecommendRoute),
    RANK(RankRoute),
    SPACE(SpaceRoute),
}

@Logger
class HomeViewModel : ViewModel(),
    OrbitContainerHost<HomeViewModelState, HomeViewModelState, HomeViewModelEffect> {

    override val container: OrbitContainer<HomeViewModelState, HomeViewModelState, HomeViewModelEffect> =
        orbitContainer(
            initialState = HomeViewModelState(),
            buildSettings = {
                sideEffectMode = SideEffectMode.BROADCAST
            }
        ) {
            logger.i { "Initializing home tab navigation with Recommend selected" }
        }

    fun selectTab(tab: HomeTab) = intent {
        if (state.selectedTab == tab) {
            logger.v { "Ignoring duplicate home tab selection (tab=$tab)" }
            return@intent
        }

        logger.i { "Selecting home tab (tab=$tab)" }
        reduce { state.copy(selectedTab = tab) }
        postSideEffect(HomeViewModelEffect.NavigateTo(tab.destination))
    }

    fun startRefresh() = intent {
        reduce { state.copy(isRefreshing = true) }
    }

    fun endRefresh() = intent {
        reduce { state.copy(isRefreshing = false) }
    }

    fun dispatchFABClicked() = intent {
        postSideEffect(HomeViewModelEffect.FABClicked)
    }

    fun requestRefresh() = intent {
        postSideEffect(HomeViewModelEffect.RefreshRequested)
    }

    fun setCanBackToTop(value: Boolean) = intent {
        reduce { state.copy(canBackToTop = value) }
    }
}

data class HomeViewModelState(
    val selectedTab: HomeTab = HomeTab.RECOMMEND,
    val isRefreshing: Boolean = false,
    val canBackToTop: Boolean = false,
)

sealed interface HomeViewModelEffect {
    data class NavigateTo(val destination: SerializableNavKey) : HomeViewModelEffect
    data object FABClicked : HomeViewModelEffect
    data object RefreshRequested : HomeViewModelEffect
}
