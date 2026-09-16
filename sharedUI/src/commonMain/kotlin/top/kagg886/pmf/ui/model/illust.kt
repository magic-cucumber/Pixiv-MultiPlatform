package top.kagg886.pmf.ui.model

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import top.kagg886.pixko.PixivAccount
import top.kagg886.pixko.module.illust.BookmarkOptions
import top.kagg886.pixko.module.illust.bookmarkIllust
import top.kagg886.pixko.module.illust.deleteBookmarkIllust
import top.kagg886.pmf.database.account.AppAccountDatabase
import top.kagg886.pmf.database.account.entity.IllustCache
import top.kagg886.pmf.database.account.entity.IllustCacheDisplayed
import top.kagg886.pmf.database.account.entity.IllustCacheLike

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/9/16 11:24
 * ================================================
 */
private val logger = Logger.withTag("IllustLikable")

interface IllustLikable<S : Any, E : Any> : OrbitContainerHost<S, S, E> {
    val database: AppAccountDatabase
    val account: PixivAccount

    /** The caller supplies the latest local snapshot; remote refreshes may overwrite it later. */
    fun processBookmark(
        illust: IllustCacheLike,
        options: BookmarkOptions.() -> Unit = {},
        onFailed: suspend Syntax<S, E>.() -> Unit = {}
    ) = intent {
        performIllustAction("bookmark", illust.illustId, onFailed) {
            check(account.bookmarkIllust(illust.illustId, options)) {
                "Bookmark request was rejected"
            }

            database.illustDao().upsert(illust.withBookmark(true))
        }
    }

    fun processUnbookmark(illust: IllustCacheLike, onFailed: suspend Syntax<S, E>.() -> Unit = {}) = intent {
        performIllustAction("unbookmark", illust.illustId, onFailed) {
            check(account.deleteBookmarkIllust(illust.illustId)) { "Unbookmark request was rejected" }
            database.illustDao().upsert(illust.withBookmark(false))
        }
    }
}


private suspend fun <S : Any, E : Any, T : Any> Syntax<S, E>.performIllustAction(
    operation: String,
    illustId: Long,
    onFailed: suspend Syntax<S, E>.() -> Unit,
    action: suspend () -> T,
): T? {
    logger.i { "$operation started (illustId=$illustId)" }
    val result = try {
        action()
    } catch (cancelled: CancellationException) {
        logger.d { "$operation cancelled (illustId=$illustId)" }
        throw cancelled
    } catch (error: Exception) {
        logger.e(error) { "$operation failed (illustId=$illustId); invoking failure callback" }
        onFailed()
        return null
    }
    logger.i { "$operation completed (illustId=$illustId)" }
    return result
}

private fun IllustCacheLike.withBookmark(bookmarked: Boolean): IllustCache {
    val cache = when (this) {
        is IllustCache -> this
        is IllustCacheDisplayed -> IllustCache(
            illustId = illustId,
            title = title,
            caption = caption,
            type = type,
            authorId = authorId,
            createTime = createTime,
            pageCount = pageCount,
            width = width,
            height = height,
            sanityLevel = sanityLevel,
            xRestrict = xRestrict,
            totalView = totalView,
            totalBookmarks = totalBookmarks,
            isBookmarked = isBookmarked,
            illustAiType = illustAiType,
            imageUrlsId = imageUrlsId,
            singlePageMetaJson = singlePageMetaJson,
        )
    }
    return cache.copy(
        isBookmarked = bookmarked,
        totalBookmarks = when {
            isBookmarked == bookmarked -> totalBookmarks
            bookmarked -> totalBookmarks + 1
            else -> (totalBookmarks - 1).coerceAtLeast(0)
        },
    )
}
