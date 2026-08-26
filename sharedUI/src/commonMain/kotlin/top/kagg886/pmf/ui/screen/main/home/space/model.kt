package top.kagg886.pmf.ui.screen.main.home.space

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.logger.Logger
import top.kagg886.pmf.ui.screen.main.home.space.follow.SpaceFollowRoute
import top.kagg886.pmf.ui.screen.main.home.space.latest.SpaceLatestRoute
import top.kagg886.pmf.util.nav3.SerializableNavKey

enum class SpaceTab(val destination: SerializableNavKey) {
    FOLLOW(SpaceFollowRoute),
    LATEST(SpaceLatestRoute),
}

@Logger
class SpaceModel : ViewModel(),
    OrbitContainerHost<SpaceModelState, SpaceModelState, SpaceModelEffect> {

    override val container: OrbitContainer<SpaceModelState, SpaceModelState, SpaceModelEffect> =
        orbitContainer(SpaceModelState()) {
            logger.i { "Initializing space sub-tab navigation with Following selected" }
        }

    fun selectTab(tab: SpaceTab) = intent {
        if (state.selectedTab == tab) {
            logger.v { "Ignoring duplicate space sub-tab selection (tab=$tab)" }
            return@intent
        }

        logger.i { "Selecting space sub-tab (tab=$tab)" }
        reduce { state.copy(selectedTab = tab) }
        postSideEffect(SpaceModelEffect.NavigateTo(tab.destination))
    }
}

data class SpaceModelState(
    val selectedTab: SpaceTab = SpaceTab.FOLLOW,
)

sealed interface SpaceModelEffect {
    data class NavigateTo(val destination: SerializableNavKey) : SpaceModelEffect
}
