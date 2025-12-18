package top.kagg886.pmf.ui.route.main.detail.illust

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Uri
import coil3.toUri
import io.ktor.util.encodeBase64
import korlibs.time.seconds
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import top.kagg886.pixko.Tag
import top.kagg886.pixko.module.illust.BookmarkVisibility
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.IllustImagesType
import top.kagg886.pixko.module.illust.bookmarkIllust
import top.kagg886.pixko.module.illust.deleteBookmarkIllust
import top.kagg886.pixko.module.illust.get
import top.kagg886.pixko.module.illust.getIllustDetail
import top.kagg886.pixko.module.ugoira.getUgoiraMetadata
import top.kagg886.pixko.module.user.UserLikePublicity
import top.kagg886.pixko.module.user.followUser
import top.kagg886.pixko.module.user.unFollowUser
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.BlackListItem
import top.kagg886.pmf.backend.database.dao.BlackListType
import top.kagg886.pmf.backend.database.dao.IllustHistory
import top.kagg886.pmf.backend.database.dao.WatchLaterItem
import top.kagg886.pmf.backend.database.dao.WatchLaterType
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.util.container
import top.kagg886.pmf.ui.util.notifyDislike
import top.kagg886.pmf.ui.util.notifyLike
import top.kagg886.pmf.util.UGOIRA_SCHEME
import top.kagg886.pmf.util.getString

class IllustDetailViewModel(private val illust: Illust) :
    ContainerHost<IllustDetailViewState, IllustDetailSideEffect>,
    ViewModel(),
    KoinComponent {
    override val container: Container<IllustDetailViewState, IllustDetailSideEffect> =
        container(IllustDetailViewState.Loading()) { load() }

    private val client = PixivConfig.newAccountFromConfig()

    fun toggleOrigin() = intent {
        val s = state
        if (s !is IllustDetailViewState.Success) {
            postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.file_was_downloading)))
            return@intent
        }
        val origin = s.illust.contentImages[IllustImagesType.ORIGIN]

        if (origin == null) {
            postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.get_original_fail)))
            return@intent
        }

        reduce {
            s.copy(
                data = origin.map(String::toUri),
            )
        }
    }

    fun load(showLoading: Boolean = true) = intent {
        if (black.matchRules(BlackListType.AUTHOR_ID, illust.user.id.toString())) {
            postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.blocking_because_black, getString(Res.string.user))))
            postSideEffect(IllustDetailSideEffect.NavigateBack)
            return@intent
        }

        if (illust.tags.map { viewModelScope.async { black.matchRules(BlackListType.TAG_NAME, it.name) } }.awaitAll().any { it }) {
            postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.blocking_because_black, getString(Res.string.tags))))
            postSideEffect(IllustDetailSideEffect.NavigateBack)
            return@intent
        }

        val inWatchLater = database.watchLaterDAO().exists(WatchLaterType.ILLUST, illust.id.toLong())

        val loadingState = IllustDetailViewState.Loading()
        if (showLoading) {
            reduce {
                loadingState
            }
        }

        if (illust.isUgoira) {
            loadingState.data.tryEmit(getString(Res.string.getting_ugoira_metadata))
            val meta = client.getUgoiraMetadata(illust)
            val data = Json.encodeToString(meta).encodeBase64()
            val url = "$UGOIRA_SCHEME://$data".toUri()
            reduce { IllustDetailViewState.Success(illust, inWatchLater, url) }
            saveDataBase(illust)
            return@intent
        }

        val options = buildList {
            if (AppConfig.showOriginalImage) {
                add(IllustImagesType.ORIGIN)
            }
            add(IllustImagesType.LARGE)
            add(IllustImagesType.MEDIUM)
        }

        val img = illust.contentImages.get(*options.toTypedArray())!!.map(String::toUri)
        reduce {
            IllustDetailViewState.Success(illust, inWatchLater, img)
        }

        // 部分API返回信息不全，需要重新拉取
        intent a@{
            val result = runCatching {
                client.getIllustDetail(illust.id.toLong())
            }
            if (result.isFailure) {
                postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.get_illust_info_fail)))
                return@a
            }
            val i = result.getOrThrow()
            if (i.contentImages[IllustImagesType.ORIGIN] == null) {
                postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.get_original_fail)))
            }
            saveDataBase(i)

            val options = buildList {
                if (AppConfig.showOriginalImage) {
                    add(IllustImagesType.ORIGIN)
                }
                add(IllustImagesType.LARGE)
                add(IllustImagesType.MEDIUM)
            }

            val img = i.contentImages.get(*options.toTypedArray())!!.map(String::toUri)
            reduce {
                IllustDetailViewState.Success(i, inWatchLater, img)
            }
        }
    }

//    fun loadByIllustId(id: Long, silent: Boolean = true) = intent {
//        if (silent) {
//            reduce { IllustDetailViewState.Loading }
//        }
//        val illust = kotlin.runCatching {
//            client.getIllustDetail(id)
//        }
//        if (illust.isFailure) {
//            if (silent) {
//                reduce { IllustDetailViewState.Error }
//            }
//            return@intent
//        }
//        loadByIllustBean(illust.getOrThrow())
//    }
//
//    fun loadByIllustBean(illust: Illust) = intent {
//        reduce {
//            IllustDetailViewState.Loading
//        }
//        reduce { IllustDetailViewState.Success(illust) }
//        saveDataBase()
//    }

    private val database by inject<AppDatabase>()

    private fun saveDataBase(i: Illust) = intent {
        if (!AppConfig.recordIllustHistory) {
            return@intent
        }
        database.illustHistoryDAO().insert(
            IllustHistory(
                id = i.id,
                illust = i,
                createTime = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    @OptIn(OrbitExperimental::class)
    fun addViewLater() = intent {
        runOn<IllustDetailViewState.Success> {
            database.watchLaterDAO().insert(
                WatchLaterItem(
                    type = WatchLaterType.ILLUST,
                    payload = illust.id.toLong(),
                    metadata = Json.encodeToJsonElement(state.illust).jsonObject,
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
        runOn<IllustDetailViewState.Success> {
            database.watchLaterDAO().delete(
                type = WatchLaterType.ILLUST,
                payload = illust.id.toLong(),
            )

            reduce {
                state.copy(
                    itemInViewLater = false,
                )
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun likeIllust(
        visibility: BookmarkVisibility = BookmarkVisibility.PUBLIC,
        tags: List<Tag>? = null,
    ) = intent {
        runOn<IllustDetailViewState.Success> {
            val result = kotlin.runCatching {
                client.bookmarkIllust(state.illust.id.toLong()) {
                    this.visibility = visibility
                    this.tags = tags
                }
            }

            if (result.isFailure || result.getOrNull() == false) {
                postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.bookmark_failed)))
                return@runOn
            }
            postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.bookmark_success)))
            illust.notifyLike()
        }
    }

    @OptIn(OrbitExperimental::class)
    fun disLikeIllust() = intent {
        runOn<IllustDetailViewState.Success> {
            val result = runCatching {
                client.deleteBookmarkIllust(state.illust.id.toLong())
            }

            if (result.isFailure || result.getOrNull() == false) {
                postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.un_bookmark_failed)))
                return@runOn
            }
            postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.un_bookmark_success)))
            illust.notifyDislike()
        }
    }

    @OptIn(OrbitExperimental::class)
    fun followUser(private: Boolean = false) = intent {
        runOn<IllustDetailViewState.Success> {
            val result = kotlin.runCatching {
                client.followUser(
                    state.illust.user.id,
                    if (private) UserLikePublicity.PRIVATE else UserLikePublicity.PUBLIC,
                )
            }
            if (result.isFailure) {
                postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.follow_fail)))
                return@runOn
            }
            if (private) {
                postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.follow_success_private)))
            } else {
                postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.follow_success)))
            }
            reduce {
                val illust = with(state.illust) { copy(user = user.copy(isFollowed = true)) }
                state.copy(illust = illust)
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun unFollowUser() = intent {
        runOn<IllustDetailViewState.Success> {
            val result = kotlin.runCatching {
                client.unFollowUser(state.illust.user.id)
            }
            if (result.isFailure) {
                postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.unfollow_fail)))
                return@runOn
            }
            postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.unfollow_success)))
            reduce {
                val illust = with(state.illust) { copy(user = user.copy(isFollowed = true)) }
                state.copy(illust = illust)
            }
        }
    }

    private val black = database.blacklistDAO()

    @OptIn(OrbitExperimental::class)
    fun black() = intent {
        runOn<IllustDetailViewState.Success> {
            black.insert(BlackListItem(state.illust.user))
            postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.filter_add_user_tips)))
            delay(3.seconds)
            postSideEffect(IllustDetailSideEffect.NavigateBack)
        }
    }

    @OptIn(OrbitExperimental::class)
    fun blackTag(tag: Tag) = intent {
        runOn<IllustDetailViewState.Success> {
            black.insert(BlackListItem(tag.name))
            postSideEffect(IllustDetailSideEffect.Toast(getString(Res.string.filter_add_tags_tips)))
            delay(3.seconds)
            postSideEffect(IllustDetailSideEffect.NavigateBack)
        }
    }
}

sealed class IllustDetailViewState {
    data class Loading(val data: MutableStateFlow<String> = MutableStateFlow("")) :
        IllustDetailViewState()

    data object Error : IllustDetailViewState()
    data class Success(val illust: Illust, val itemInViewLater: Boolean, val data: List<Uri>) :
        IllustDetailViewState() {
        constructor(illust: Illust, itemInViewLater: Boolean, data: Uri) : this(illust, itemInViewLater, listOf(data))
    }
}

sealed class IllustDetailSideEffect {
    data class Toast(val msg: String) : IllustDetailSideEffect()
    data object NavigateBack : IllustDetailSideEffect()
}
