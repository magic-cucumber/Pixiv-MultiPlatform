package top.kagg886.pmf.ui.repository

import androidx.paging.PagingSource
import top.kagg886.pixko.User
import top.kagg886.pixko.module.user.RelatedUserResult
import top.kagg886.pmf.database.account.AppAccountDatabase
import top.kagg886.pmf.database.account.entity.AuthorFlow
import top.kagg886.pmf.database.account.entity.ImageUrlsCache
import top.kagg886.pmf.database.account.entity.UserCache
import top.kagg886.pmf.database.account.entity.UserDisplayed
import top.kagg886.pmf.logger.Logger

/** A forward-only author repository for APIs whose request is a numeric index. */
@Logger
abstract class AuthorIndexedRepo(
    database: AppAccountDatabase,
    tag: String,
    private val networkPageSize: Int = DEFAULT_PAGE_SIZE,
) : BaseIndexedRepo<UserDisplayed>(database, tag, networkPageSize) {
    protected abstract suspend fun request(index: Int): List<User>

    protected open fun endOfPaginationReached(index: Int, users: List<User>): Boolean =
        users.size < networkPageSize

    final override suspend fun loadInitial(): LoadedPage<Int> = load(FIRST_INDEX)

    final override suspend fun loadNext(request: Int): LoadedPage<Int> = load(request)

    final override suspend fun clearFlow() = database.authorFlowDao().clean(flowTag)

    final override fun pagingSource(): PagingSource<Int, UserDisplayed> =
        database.authorFlowDao().query(flowTag)

    private suspend fun load(index: Int): LoadedPage<Int> {
        logger.i {
            "Loading indexed author page (index: $index, pageSize: $networkPageSize, tagHash: ${flowTag.hashCode()})"
        }
        val users = request(index)
        val endReached = endOfPaginationReached(index, users)
        val nextIndex = if (endReached) null else index + 1
        logger.d {
            "Indexed author response received (index: $index, itemCount: ${users.size}, endReached: $endReached)"
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
@Logger
abstract class AuthorNextUrlRepo(
    database: AppAccountDatabase,
    tag: String,
    pageSize: Int = DEFAULT_PAGE_SIZE,
) : BaseNextUrlRepo<UserDisplayed>(database, tag, pageSize) {
    protected abstract suspend fun requestInitial(): RelatedUserResult

    protected abstract suspend fun requestNext(nextUrl: String): RelatedUserResult

    final override suspend fun loadInitial(): LoadedPage<String> {
        logger.i { "Loading initial next-URL author page (tagHash: ${flowTag.hashCode()})" }
        return requestInitial().toPage("Initial author response received")
    }

    final override suspend fun loadNext(request: String): LoadedPage<String> {
        logger.i {
            "Loading continued next-URL author page (nextUrlLength: ${request.length}, nextUrlHash: ${request.hashCode()}, tagHash: ${flowTag.hashCode()})"
        }
        return requestNext(request).toPage("Continued author response received")
    }

    final override suspend fun clearFlow() = database.authorFlowDao().clean(flowTag)

    final override fun pagingSource(): PagingSource<Int, UserDisplayed> =
        database.authorFlowDao().query(flowTag)

    private fun RelatedUserResult.toPage(responseLabel: String): LoadedPage<String> {
        if (user_previews.isEmpty() && next_url != null) {
            logger.w {
                "$responseLabel with no items but a continuation URL; committing the empty page and continuing with the supplied URL"
            }
        } else {
            logger.d { "$responseLabel (itemCount: ${user_previews.size}, endReached: ${next_url == null})" }
        }
        return loadedPage(next_url, user_previews.size) {
            val summary = database.persistAuthorFlow(flowTag, user_previews)
            logger.d { summary.logMessage("Next-URL author page persisted") }
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
    val summary = cacheAuthors(users)
    val flowItems = appendAuthorFlow(tag, users)
    return summary.copy(flowItems = flowItems)
}

private suspend fun AppAccountDatabase.appendAuthorFlow(tag: String, users: List<User>): Int {
    if (users.isEmpty()) return 0
    authorFlowDao().insert(
        users.map { user ->
            AuthorFlow(tag = tag, userCacheId = user.id.toLong())
        },
    )
    return users.size
}

private suspend fun AppAccountDatabase.cacheAuthors(users: List<User>): AuthorPersistenceSummary {
    if (users.isEmpty()) {
        return AuthorPersistenceSummary(0, 0, 0, 0, 0, 0)
    }

    val cachedUsers = linkedMapOf<Long, UserCache>()
    val imageUrls = linkedMapOf<String, ImageUrlsCache>()
    var preservedFollowStates = 0
    var preservedComments = 0

    users.forEach { user ->
        val incomingUser = UserCache.fromBean(user)
        val existingUser = cachedUsers[incomingUser.userId] ?: userDao().find(incomingUser.userId)
        if (incomingUser.isFollowed == null && existingUser?.isFollowed != null) preservedFollowStates++
        if (incomingUser.comment == null && existingUser?.comment != null) preservedComments++
        val cachedUser = incomingUser.copy(
            isFollowed = incomingUser.isFollowed ?: existingUser?.isFollowed,
            comment = incomingUser.comment ?: existingUser?.comment,
        )
        cachedUsers[cachedUser.userId] = cachedUser
        imageUrls[cachedUser.profileImageUrlsId] =
            ImageUrlsCache.fromBean(user.profileImageUrls, cachedUser.profileImageUrlsId)
    }

    imageUrls.values.forEach { imageUrlsDao().upsert(it) }
    userDao().upsert(cachedUsers.values.toList())

    return AuthorPersistenceSummary(
        inputItems = users.size,
        cachedUsers = cachedUsers.size,
        imageUrls = imageUrls.size,
        preservedFollowStates = preservedFollowStates,
        preservedComments = preservedComments,
        flowItems = 0,
    )
}
