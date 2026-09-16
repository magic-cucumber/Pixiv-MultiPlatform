package top.kagg886.pmf.ui.screen.main.home.recommend.illust

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import top.kagg886.pixko.PixivAccount
import top.kagg886.pixko.module.illust.IllustResult
import top.kagg886.pixko.module.illust.getRecommendIllust
import top.kagg886.pixko.module.illust.getRecommendIllustNext
import top.kagg886.pmf.database.account.AppAccountDatabase
import top.kagg886.pmf.ui.model.IllustLikable
import top.kagg886.pmf.ui.repository.IllustNextUrlRepo
import top.kagg886.pmf.logger.Logger

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/9/16 17:23
 * ================================================
 */

@Logger
class RecommendIllustViewModel(override val database: AppAccountDatabase, override val account: PixivAccount) :
    ViewModel(), OrbitContainerHost<RecommendIllustState, RecommendIllustState, RecommendIllustSideEffect>,
    IllustLikable<RecommendIllustState, RecommendIllustSideEffect> {
    override val container: OrbitContainer<RecommendIllustState, RecommendIllustState, RecommendIllustSideEffect> =
        orbitContainer(RecommendIllustState.Loading) {
            logger.i { "Initializing recommended illustrations repository" }
            val repo = object : IllustNextUrlRepo(database, "illust-recommend") {
                override suspend fun requestInitial(): IllustResult = account.getRecommendIllust()

                override suspend fun requestNext(nextUrl: String): IllustResult =
                    account.getRecommendIllustNext(IllustResult(nextUrl = nextUrl, illusts = emptyList())) ?: IllustResult(
                        nextUrl = null,
                        illusts = emptyList()
                    )
            }
            logger.i { "Repository ready; setting recommended illustrations state to LoadSuccess" }
            reduce { RecommendIllustState.LoadSuccess(repo) }
        }

    fun refresh() = intent {
        runOn<RecommendIllustState.LoadSuccess> {
            logger.i { "Dispatching recommended illustrations refresh request" }
            postSideEffect(RecommendIllustSideEffect.Refresh)
        }
    }

    fun reportRefreshing(value: Boolean) = intent {
        postSideEffect(RecommendIllustSideEffect.RefreshingChanged(value))
    }

    fun reportCanBackToTop(value: Boolean) = intent {
        postSideEffect(RecommendIllustSideEffect.CanBackToTopChanged(value))
    }

}

sealed interface RecommendIllustState {
    data object Loading : RecommendIllustState
    data class LoadSuccess(val repo: IllustNextUrlRepo) : RecommendIllustState
}

sealed interface RecommendIllustSideEffect {
    data object Refresh : RecommendIllustSideEffect
    data class RefreshingChanged(val value: Boolean) : RecommendIllustSideEffect
    data class CanBackToTopChanged(val value: Boolean) : RecommendIllustSideEffect
}
