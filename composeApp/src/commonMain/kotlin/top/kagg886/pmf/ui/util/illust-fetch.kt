package top.kagg886.pmf.ui.util

import androidx.collection.MutableIntSet
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import korlibs.io.async.async
import kotlin.getValue
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
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.bookmarkIllust
import top.kagg886.pixko.module.illust.deleteBookmarkIllust
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.BlackListType
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.res.*
import top.kagg886.pmf.util.logger

abstract class IllustFetchViewModel :
    ContainerHost<IllustFetchViewState, IllustFetchSideEffect>,
    ViewModel(),
    KoinComponent {
    protected val client = PixivConfig.newAccountFromConfig()
    private val signal = MutableSharedFlow<Unit>()
    override val container: Container<IllustFetchViewState, IllustFetchSideEffect> = container(IllustFetchViewState())
    abstract fun source(): Flow<PagingData<Illust>>

    // 为true则不显示
    fun Illust.block() = with(AppConfig) {
        val base = isLimited || (filterAi && isAI) || (filterR18G && isR18G) || (filterR18 && isR18)

        val aspectRatio = when (filterAspectRatioType) {
            AppConfig.AspectRatioFilterType.NONE -> false
            AppConfig.AspectRatioFilterType.PHONE -> width >= height
            AppConfig.AspectRatioFilterType.PC -> width <= height
        }

        base || aspectRatio
    }

    private val database by inject<AppDatabase>()
    private val blackListDao = database.blacklistDAO()

    suspend fun Illust.blockSuspend(): Boolean = blackListDao.matchRules(BlackListType.AUTHOR_ID, user.id.toString()).apply {
        if (this) {
            logger.d("successfully to filter illust. cause author id(${user.id}) is in black list")
        }
    } || tags.map {
        viewModelScope.async {
            blackListDao.matchRules(BlackListType.TAG_NAME, it.name)
        }
    }.awaitAll().contains(true).apply {
        if (this) {
            logger.d("successfully to filter illust. cause tag names(${tags.joinToString { it.name }}) is in black list")
        }
    }

    val data = merge(flowOf(Unit), signal).flatMapLatestScoped { scope, _ ->
        illustRouter.intercept(
            source().cachedIn(scope).map { data -> data.filterNot { i -> i.blockSuspend() } },
        ).map { data -> data.filterNot { i -> i.block() } }
    }.map { data -> MutableIntSet().let { s -> data.filter { s.add(it.id) } } }.cachedIn(viewModelScope)

    fun refresh() = intent { signal.emit(Unit) }

    @OptIn(OrbitExperimental::class)
    fun likeIllust(
        illust: Illust,
        visibility: BookmarkVisibility = BookmarkVisibility.PUBLIC,
        tags: List<Tag>? = null,
    ) = intent {
        runOn<IllustFetchViewState> {
            val result = runCatching {
                client.bookmarkIllust(illust.id.toLong()) {
                    this.visibility = visibility
                    this.tags = tags
                }
            }

            if (result.isFailure || result.getOrNull() == false) {
                postSideEffect(IllustFetchSideEffect.Toast(getString(Res.string.bookmark_failed)))
                return@runOn
            }
            postSideEffect(IllustFetchSideEffect.Toast(getString(Res.string.bookmark_success)))
            illust.notifyLike()
        }
    }

    @OptIn(OrbitExperimental::class)
    fun disLikeIllust(illust: Illust) = intent {
        runOn<IllustFetchViewState> {
            val result = runCatching { client.deleteBookmarkIllust(illust.id.toLong()) }

            if (result.isFailure || result.getOrNull() == false) {
                postSideEffect(IllustFetchSideEffect.Toast(getString(Res.string.un_bookmark_failed)))
                return@runOn
            }
            postSideEffect(IllustFetchSideEffect.Toast(getString(Res.string.un_bookmark_success)))
            illust.notifyDislike()
        }
    }
}

data class IllustFetchViewState(val scrollerState: LazyStaggeredGridState = LazyStaggeredGridState())

sealed class IllustFetchSideEffect {
    data class Toast(val msg: String) : IllustFetchSideEffect()
}

inline fun <T : Any> PagingData<T>.filterNot(crossinline f: suspend (T) -> Boolean) = filter { v -> !f(v) }
