package top.kagg886.pmf.ui.route.main.detail.author

import androidx.lifecycle.ViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import top.kagg886.pixko.module.user.UserInfo
import top.kagg886.pixko.module.user.UserLikePublicity
import top.kagg886.pixko.module.user.followUser
import top.kagg886.pixko.module.user.getUserInfo
import top.kagg886.pixko.module.user.unFollowUser
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.WatchLaterItem
import top.kagg886.pmf.backend.database.dao.WatchLaterType
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.util.container
import top.kagg886.pmf.util.getString
import top.kagg886.pmf.util.logger

class AuthorScreenModel(val id: Int) :
    ContainerHost<AuthorScreenState, AuthorScreenSideEffect>,
    ViewModel(),
    KoinComponent {
    override val container: Container<AuthorScreenState, AuthorScreenSideEffect> =
        container(AuthorScreenState.Loading) { loadUserById(id) }
    private val client = PixivConfig.newAccountFromConfig()

    fun loadUserById(id: Int, silent: Boolean = true) = intent {
        if (silent) {
            reduce { AuthorScreenState.Loading }
        }
        val info = kotlin.runCatching {
            client.getUserInfo(id)
        }
        if (info.isFailure) {
            logger.w("failed to get author info", info.exceptionOrNull())
            if (silent) {
                reduce { AuthorScreenState.Error }
            }
            return@intent
        }

        val itemInViewLater = database.watchLaterDAO().exists(
            type = WatchLaterType.AUTHOR,
            payload = id.toLong(),
        )

        reduce { AuthorScreenState.Success(info.getOrThrow(), itemInViewLater) }
    }

    @OptIn(OrbitExperimental::class)
    fun followUser(private: Boolean = false) = intent {
        runOn<AuthorScreenState.Success> {
            val result = kotlin.runCatching {
                client.followUser(
                    state.user.user.id,
                    if (private) UserLikePublicity.PRIVATE else UserLikePublicity.PUBLIC,
                )
            }
            if (result.isFailure) {
                postSideEffect(AuthorScreenSideEffect.Toast(getString(Res.string.follow_fail)))
                return@runOn
            }
            if (private) {
                postSideEffect(AuthorScreenSideEffect.Toast(getString(Res.string.follow_success_private)))
            } else {
                postSideEffect(AuthorScreenSideEffect.Toast(getString(Res.string.follow_success)))
            }
            reduce {
                state.copy(
                    user = state.user.copy(
                        user = state.user.user.copy(
                            isFollowed = true,
                        ),
                        profile = state.user.profile.copy(
                            totalFollowUsers = state.user.profile.totalFollowUsers + 1,
                        ),
                    ),
                )
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun unFollowUser() = intent {
        runOn<AuthorScreenState.Success> {
            val result = kotlin.runCatching {
                client.unFollowUser(state.user.user.id)
            }
            if (result.isFailure) {
                postSideEffect(AuthorScreenSideEffect.Toast(getString(Res.string.unfollow_fail)))
                return@runOn
            }
            postSideEffect(AuthorScreenSideEffect.Toast(getString(Res.string.unfollow_success)))
            reduce {
                state.copy(
                    user = state.user.copy(
                        user = state.user.user.copy(
                            isFollowed = false,
                        ),
                        profile = state.user.profile.copy(
                            totalFollowUsers = state.user.profile.totalFollowUsers - 1,
                        ),
                    ),
                )
            }
        }
    }

    private val database by inject<AppDatabase>()

    @OptIn(OrbitExperimental::class)
    fun addViewLater() = intent {
        runOn<AuthorScreenState.Success> {
            database.watchLaterDAO().insert(
                WatchLaterItem(
                    type = WatchLaterType.AUTHOR,
                    payload = state.user.user.id.toLong(),
                    metadata = Json.encodeToJsonElement(state.user).jsonObject,
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
        runOn<AuthorScreenState.Success> {
            database.watchLaterDAO().delete(
                type = WatchLaterType.AUTHOR,
                payload = state.user.user.id.toLong(),
            )

            reduce {
                state.copy(
                    itemInViewLater = false,
                )
            }
        }
    }
}

sealed class AuthorScreenState {
    data object Loading : AuthorScreenState()
    data object Error : AuthorScreenState()
    data class Success(val user: UserInfo, val itemInViewLater: Boolean, val initPage: Int = 0) : AuthorScreenState()
}

sealed class AuthorScreenSideEffect {
    data class Toast(val msg: String) : AuthorScreenSideEffect()
}
