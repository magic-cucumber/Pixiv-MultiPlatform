package top.kagg886.pmf.ui.route.main.series.novel

import androidx.lifecycle.ViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import top.kagg886.pixko.module.novel.SeriesDetail
import top.kagg886.pixko.module.novel.getNovelSeries
import top.kagg886.pixko.module.user.UserLikePublicity
import top.kagg886.pixko.module.user.followUser
import top.kagg886.pixko.module.user.unFollowUser
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.WatchLaterItem
import top.kagg886.pmf.backend.database.dao.WatchLaterType
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.util.container
import top.kagg886.pmf.util.getString

class NovelSeriesScreenModel(private val seriesId: Int) :
    ViewModel(),
    KoinComponent,
    ContainerHost<NovelSeriesScreenState, NovelSeriesScreenSideEffect> {
    private val client = PixivConfig.newAccountFromConfig()
    override val container: Container<NovelSeriesScreenState, NovelSeriesScreenSideEffect> =
        container(NovelSeriesScreenState.Loading) {
            reload()
        }

    fun reload() = intent {
        reduce {
            NovelSeriesScreenState.Loading
        }

        val data = kotlin.runCatching {
            client.getNovelSeries(seriesId)
        }
        if (data.isFailure) {
            val unknown = getString(Res.string.unknown_error)
            reduce {
                NovelSeriesScreenState.LoadingFailed(data.exceptionOrNull()!!.message ?: unknown)
            }
            return@intent
        }

        val itemInViewLater = database.watchLaterDAO().exists(
            type = WatchLaterType.SERIES,
            payload = seriesId.toLong(),
        )

        reduce {
            NovelSeriesScreenState.LoadingSuccess(data.getOrThrow().novelSeriesDetail, itemInViewLater)
        }
    }

    @OptIn(OrbitExperimental::class)
    fun followUser(private: Boolean = false) = intent {
        runOn<NovelSeriesScreenState.LoadingSuccess> {
            val result = kotlin.runCatching {
                client.followUser(
                    state.info.user.id,
                    if (private) UserLikePublicity.PRIVATE else UserLikePublicity.PUBLIC,
                )
            }
            if (result.isFailure) {
                postSideEffect(NovelSeriesScreenSideEffect.Toast(getString(Res.string.follow_fail)))
                return@runOn
            }
            if (private) {
                postSideEffect(NovelSeriesScreenSideEffect.Toast(getString(Res.string.follow_success_private)))
            } else {
                postSideEffect(NovelSeriesScreenSideEffect.Toast(getString(Res.string.follow_success)))
            }

            reduce {
                state.copy(
                    info = state.info.copy(
                        user = state.info.user.copy(
                            isFollowed = true,
                        ),
                    ),
                )
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun unFollowUser() = intent {
        runOn<NovelSeriesScreenState.LoadingSuccess> {
            val result = kotlin.runCatching {
                client.unFollowUser(state.info.user.id)
            }
            if (result.isFailure) {
                postSideEffect(NovelSeriesScreenSideEffect.Toast(getString(Res.string.unfollow_fail)))
                return@runOn
            }
            postSideEffect(NovelSeriesScreenSideEffect.Toast(getString(Res.string.unfollow_success)))

            reduce {
                state.copy(
                    info = state.info.copy(
                        user = state.info.user.copy(
                            isFollowed = false,
                        ),
                    ),
                )
            }
        }
    }

    private val database by inject<AppDatabase>()

    @OptIn(OrbitExperimental::class)
    fun addViewLater() = intent {
        runOn<NovelSeriesScreenState.LoadingSuccess> {
            database.watchLaterDAO().insert(
                WatchLaterItem(
                    type = WatchLaterType.SERIES,
                    payload = seriesId.toLong(),
                    metadata = Json.encodeToJsonElement(state.info).jsonObject,
                ),
            )

            reduce {
                state.copy(
                    itemInViewLater = true,
                )
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun removeViewLater() = intent {
        runOn<NovelSeriesScreenState.LoadingSuccess> {
            database.watchLaterDAO().delete(
                type = WatchLaterType.SERIES,
                payload = seriesId.toLong(),
            )

            reduce {
                state.copy(
                    itemInViewLater = false,
                )
            }
        }
    }
}

sealed interface NovelSeriesScreenState {
    data object Loading : NovelSeriesScreenState
    data class LoadingSuccess(val info: SeriesDetail, val itemInViewLater: Boolean) : NovelSeriesScreenState
    data class LoadingFailed(val msg: String) : NovelSeriesScreenState
}

interface NovelSeriesScreenSideEffect {
    data class Toast(val msg: String) : NovelSeriesScreenSideEffect
}
