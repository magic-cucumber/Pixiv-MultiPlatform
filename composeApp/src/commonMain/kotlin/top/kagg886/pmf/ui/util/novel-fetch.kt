package top.kagg886.pmf.ui.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import korlibs.io.async.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import top.kagg886.pixko.Tag
import top.kagg886.pixko.module.illust.BookmarkVisibility
import top.kagg886.pixko.module.novel.Novel
import top.kagg886.pixko.module.novel.bookmarkNovel
import top.kagg886.pixko.module.novel.deleteBookmarkNovel
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.BlackListType
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.res.*
import top.kagg886.pmf.util.logger

abstract class NovelFetchViewModel :
    ContainerHost<NovelFetchViewState, NovelFetchSideEffect>,
    ViewModel(),
    KoinComponent {
    protected val client = PixivConfig.newAccountFromConfig()
    private val signal = MutableSharedFlow<Unit>()

    override val container: Container<NovelFetchViewState, NovelFetchSideEffect> = container(NovelFetchViewState())
    abstract fun source(): Flow<PagingData<Novel>>

    // 返回true代表拦截
    fun Novel.block() = with(AppConfig) {
        val isUserDisAllow = run {
            val a = filterShortNovel && textLength <= filterShortNovelMaxLength
            val b = filterLongTag && tags.any { it.name.length > filterLongTagMinLength }
            val c = filterAiNovel && isAI
            val d = filterR18GNovel && isR18G
            val e = filterR18Novel && (isR18 || isR18G)
            a || b || c || d || e
        }

        // FIXME: 需要移植到Pixko中。
        val isCoverIllegal = run {
            val a = imageUrls.content == "https://s.pximg.net/common/images/limit_r18_100.png"

            a
        }

        isUserDisAllow || isCoverIllegal
    }

    private val database by inject<AppDatabase>()
    private val blackListDao = database.blacklistDAO()

    suspend fun Novel.blockSuspend(): Boolean = blackListDao.matchRules(BlackListType.AUTHOR_ID, user.id.toString()).apply {
        if (this) {
            logger.d("successfully to filter novel. cause author id(${user.id}) is in black list")
        }
    } || tags.map {
        viewModelScope.async {
            blackListDao.matchRules(BlackListType.TAG_NAME, it.name)
        }
    }.awaitAll().contains(true).apply {
        if (this) {
            logger.d("successfully to filter novel. cause tag names(${tags.joinToString { it.name }}) is in black list")
        }
    }

    val data = merge(flowOf(Unit), signal).flatMapLatestScoped { scope, _ ->
        novelRouter.intercept(
            source().cachedIn(scope).map { data -> data.filterNot { i -> i.blockSuspend() } },
        ).map { data -> data.filterNot { i -> i.block() } }
    }.cachedIn(viewModelScope)

    fun refresh() = intent { signal.emit(Unit) }

    @OptIn(OrbitExperimental::class)
    fun likeNovel(
        novel: Novel,
        visibility: BookmarkVisibility = BookmarkVisibility.PUBLIC,
        tags: List<Tag>? = null,
    ) = intent {
        runOn<NovelFetchViewState> {
            val result = kotlin.runCatching {
                client.bookmarkNovel(novel.id.toLong()) {
                    this.visibility = visibility
                    this.tags = tags
                }
            }

            if (result.isFailure || result.getOrNull() == false) {
                postSideEffect(NovelFetchSideEffect.Toast(getString(Res.string.bookmark_failed)))
                return@runOn
            }
            postSideEffect(NovelFetchSideEffect.Toast(getString(Res.string.bookmark_success)))
            novelRouter.push { n -> if (n.id == novel.id) n.copy(isBookmarked = true) else n }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun disLikeNovel(novel: Novel) = intent {
        runOn<NovelFetchViewState> {
            val result = runCatching { client.deleteBookmarkNovel(novel.id.toLong()) }

            if (result.isFailure || result.getOrNull() == false) {
                postSideEffect(NovelFetchSideEffect.Toast(getString(Res.string.un_bookmark_failed)))
                return@runOn
            }
            postSideEffect(NovelFetchSideEffect.Toast(getString(Res.string.un_bookmark_success)))
            novelRouter.push { n -> if (n.id == novel.id) n.copy(isBookmarked = false) else n }
        }
    }
}

data class NovelFetchViewState(val scrollerState: LazyListState = LazyListState())

sealed class NovelFetchSideEffect {
    data class Toast(val msg: String) : NovelFetchSideEffect()
}
