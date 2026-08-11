package top.kagg886.pmf.ui.repository

import androidx.paging.PagingSource
import top.kagg886.pixko.User
import top.kagg886.pmf.database.account.AppAccountDatabase
import top.kagg886.pmf.database.account.entity.AuthorDisplayed
import top.kagg886.pmf.database.account.entity.AuthorFlow
import top.kagg886.pmf.database.account.entity.ImageUrlsCache
import top.kagg886.pmf.database.account.entity.UserCache
import top.kagg886.pmf.logger.Logger

/** A forward-only author repository for APIs whose request is a numeric index. */
@Logger("AuthorIndexedRepo")
abstract class AuthorIndexedRepo(
    database: AppAccountDatabase,
    tag: String,
    private val networkPageSize: Int = DEFAULT_PAGE_SIZE,
) : BaseIndexedRepo<AuthorDisplayed>(database, tag, networkPageSize) {
    protected abstract suspend fun request(index: Int): List<User>

    protected open fun endOfPaginationReached(index: Int, users: List<User>): Boolean =
        users.size < networkPageSize

    final override suspend fun loadInitial(): LoadedPage<Int> = load(FIRST_INDEX)

    final override suspend fun loadNext(request: Int): LoadedPage<Int> = load(request)

    final override suspend fun clearFlow() = database.authorFlowDao().clean(flowTag)

    final override fun pagingSource(): PagingSource<Int, AuthorDisplayed> =
        database.authorFlowDao().query(flowTag)

    private suspend fun load(index: Int): LoadedPage<Int> {
        logger.i {
            "Loading indexed author page (index: $index, pageSize: $networkPageSize, tagHash: ${flowTag.hashCode()})"
        }
        val users = request(index)
        val endReached = endOfPaginationReached(index, users)
        val nextIndex = if (endReached) null else index + 1
        if (users.isEmpty() && !endReached) {
            logger.w {
                "Indexed author response was empty but pagination remains open; committing the empty page and continuing with index ${index + 1}"
            }
        } else {
            logger.d {
                "Indexed author response received (index: $index, itemCount: ${users.size}, endReached: $endReached)"
            }
        }
        return loadedPage(nextIndex, users.size) {
            val summary = database.persistAuthorFlow(flowTag, users)
            logger.d { summary.logMessage("Indexed author page persisted") }
        }
    }

    private companion object {
        const val FIRST_INDEX = 1
        const val DEFAULT_PAGE_SIZE = 30
    }
}

/** A forward-only author repository for APIs whose response supplies an opaque next URL. */
@Logger("AuthorNextUrlRepo")
abstract class AuthorNextUrlRepo(
    database: AppAccountDatabase,
    tag: String,
    pageSize: Int = DEFAULT_PAGE_SIZE,
) : BaseNextUrlRepo<AuthorDisplayed>(database, tag, pageSize) {
    protected abstract suspend fun requestInitial(): LoadedPage<String>

    protected abstract suspend fun requestNext(nextUrl: String): LoadedPage<String>

    protected fun loadedPage(nextRequest: String?, users: List<User>): LoadedPage<String> =
        loadedPage(nextRequest, users.size) {
            val summary = database.persistAuthorFlow(flowTag, users)
            logger.d { summary.logMessage("Next-URL author page persisted") }
        }

    final override suspend fun loadInitial(): LoadedPage<String> {
        logger.i { "Loading initial next-URL author page (tagHash: ${flowTag.hashCode()})" }
        return requestInitial().also { page ->
            logLoadedPage(page, "Initial author response received")
        }
    }

    final override suspend fun loadNext(request: String): LoadedPage<String> {
        logger.i {
            "Loading continued next-URL author page (nextUrlLength: ${request.length}, nextUrlHash: ${request.hashCode()}, tagHash: ${flowTag.hashCode()})"
        }
        return requestNext(request).also { page ->
            logLoadedPage(page, "Continued author response received")
        }
    }

    final override suspend fun clearFlow() = database.authorFlowDao().clean(flowTag)

    final override fun pagingSource(): PagingSource<Int, AuthorDisplayed> =
        database.authorFlowDao().query(flowTag)

    private fun logLoadedPage(page: LoadedPage<String>, responseLabel: String) {
        if (page.itemCount == 0 && page.nextRequest != null) {
            logger.w {
                "$responseLabel with no items but a continuation URL; committing the empty page and continuing with the supplied URL"
            }
        } else {
            logger.d {
                "$responseLabel (itemCount: ${page.itemCount}, endReached: ${page.nextRequest == null})"
            }
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}

private data class AuthorPersistenceSummary(
    val inputItems: Int,
    val cachedUsers: Int,
    val imageUrls: Int,
    val preservedFollowStates: Int,
    val preservedComments: Int,
    val flowItems: Int,
) {
    fun logMessage(operation: String): String =
        "$operation (inputItems: $inputItems, cachedUsers: $cachedUsers, imageUrls: $imageUrls, preservedFollowStates: $preservedFollowStates, preservedComments: $preservedComments, flowItems: $flowItems)"
}

private suspend fun AppAccountDatabase.persistAuthorFlow(
    tag: String,
    users: List<User>,
): AuthorPersistenceSummary {
    if (users.isEmpty()) return AuthorPersistenceSummary(0, 0, 0, 0, 0, 0)

    val cachedUsers = linkedMapOf<Long, UserCache>()
    val imageUrls = linkedMapOf<String, ImageUrlsCache>()
    var preservedFollowStates = 0
    var preservedComments = 0

    users.forEach { user ->
        val incoming = UserCache.fromBean(user)
        val existing = cachedUsers[incoming.userId] ?: userDao().find(incoming.userId)
        if (incoming.isFollowed == null && existing?.isFollowed != null) preservedFollowStates++
        if (incoming.comment == null && existing?.comment != null) preservedComments++
        val merged = incoming.copy(
            isFollowed = incoming.isFollowed ?: existing?.isFollowed,
            comment = incoming.comment ?: existing?.comment,
        )
        cachedUsers[merged.userId] = merged
        imageUrls[merged.profileImageUrlsId] =
            ImageUrlsCache.fromBean(user.profileImageUrls, merged.profileImageUrlsId)
    }

    imageUrls.values.forEach { imageUrlsDao().upsert(it) }
    userDao().upsert(cachedUsers.values.toList())
    authorFlowDao().insert(
        users.map { user -> AuthorFlow(tag = tag, userCacheId = user.id.toLong()) },
    )

    return AuthorPersistenceSummary(
        inputItems = users.size,
        cachedUsers = cachedUsers.size,
        imageUrls = imageUrls.size,
        preservedFollowStates = preservedFollowStates,
        preservedComments = preservedComments,
        flowItems = users.size,
    )
}
