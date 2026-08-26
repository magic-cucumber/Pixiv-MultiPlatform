package top.kagg886.pmf.ui.screen.main.home.recommend

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pmf.logger.Logger
import top.kagg886.pmf.ui.screen.main.home.recommend.illust.RecommendIllustRoute
import top.kagg886.pmf.ui.screen.main.home.recommend.novel.RecommendNovelRoute
import top.kagg886.pmf.util.nav3.SerializableNavKey

enum class RecommendTab(val destination: SerializableNavKey) {
    ILLUST(RecommendIllustRoute),
    NOVEL(RecommendNovelRoute),
}

@Logger
class RecommendModel : ViewModel(),
    OrbitContainerHost<RecommendModelState, RecommendModelState, RecommendModelEffect> {

    override val container: OrbitContainer<RecommendModelState, RecommendModelState, RecommendModelEffect> =
        orbitContainer(RecommendModelState()) {
            logger.i { "Initializing recommend sub-tab navigation with Illustrations selected" }
        }

    fun selectTab(tab: RecommendTab) = intent {
        if (state.selectedTab == tab) {
            logger.v { "Ignoring duplicate recommend sub-tab selection (tab=$tab)" }
            return@intent
        }

        logger.i { "Selecting recommend sub-tab (tab=$tab)" }
        reduce { state.copy(selectedTab = tab) }
        postSideEffect(RecommendModelEffect.NavigateTo(tab.destination))
    }
}

data class RecommendModelState(
    val selectedTab: RecommendTab = RecommendTab.ILLUST,
)

sealed interface RecommendModelEffect {
    data class NavigateTo(val destination: SerializableNavKey) : RecommendModelEffect
}
