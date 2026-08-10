@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.ui.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.CancellationException
import top.kagg886.pixko.ImageUrls
import top.kagg886.pixko.PixivAccount
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.IllustResult
import top.kagg886.pmf.database.account.AppAccountDatabase
import top.kagg886.pmf.database.account.entity.IllustCache
import top.kagg886.pmf.database.account.entity.IllustFlow
import top.kagg886.pmf.database.account.entity.IllustMetaPageCrossRef
import top.kagg886.pmf.database.account.entity.IllustTagCrossRef
import top.kagg886.pmf.database.account.entity.ImageUrlsCache
import top.kagg886.pmf.database.account.entity.PageKey
import top.kagg886.pmf.database.account.entity.TagCache
import top.kagg886.pmf.database.account.entity.UserCache
import top.kagg886.pmf.logger.Logger
import kotlin.time.ExperimentalTime

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 13:31
 * ================================================
 */

/**
 * A forward-only, database-backed illustration repository.
 *
 * Network results, their normalized cache graph, flow positions, and page state are committed in a
 * single Room transaction. The flow records every response item in its original order.
 */
@Logger
sealed class IllustRepo<Request : Any> protected constructor(
    private val database: AppAccountDatabase,
    private val tag: String,
    pageSize: Int,
) {
    init {
        require(tag.isNotBlank()) { "tag must not be blank" }
        require(pageSize > 0) { "pageSize must be greater than 0" }
    }

    protected data class Page<Request : Any>(
        val illusts: List<Illust>,
        val nextRequest: Request?,
    )

    protected abstract suspend fun loadInitial(): Page<Request>

    protected abstract suspend fun loadNext(request: Request): Page<Request>

    protected abstract fun serializeRequest(request: Request): String

    protected abstract fun deserializeRequest(payload: String): Request

    @OptIn(ExperimentalPagingApi::class)
    val pager: Pager<Int, IllustCache> = Pager(
        config = PagingConfig(
            pageSize = pageSize,
            initialLoadSize = pageSize,
            enablePlaceholders = false,
        ),
        remoteMediator = object : RemoteMediator<Int, IllustCache>() {
            override suspend fun initialize(): InitializeAction {
                val savedState = database.pageKeyDao().last(tag)
                val action = if (savedState == null) {
                    InitializeAction.LAUNCH_INITIAL_REFRESH
                } else {
                    InitializeAction.SKIP_INITIAL_REFRESH
                }
                logger.i {
                    "Initializing illustration paging (tagHash: ${tag.hashCode()}, restoredPages: ${savedState?.page ?: 0}, action: $action)"
                }
                return action
            }

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, IllustCache>,
            ): MediatorResult {
                if (loadType == LoadType.PREPEND) {
                    logger.v { "Skipping illustration prepend because this repository only loads forward" }
                    return MediatorResult.Success(endOfPaginationReached = true)
                }

                val previousState: PageKey?
                val page: Page<Request>
                try {
                    when (loadType) {
                        LoadType.REFRESH -> {
                            previousState = null
                            page = loadInitial()
                        }

                        LoadType.APPEND -> {
                            previousState = database.pageKeyDao().last(tag)
                                ?: return MediatorResult.Error(
                                    IllegalStateException("Cannot append illustrations without saved page state"),
                                ).also {
                                    logger.e { "Illustration append failed because no saved page state exists" }
                                }
                            val payload = previousState.nextPayload
                                ?: return MediatorResult.Success(endOfPaginationReached = true).also {
                                    logger.d { "Skipping illustration append because pagination has reached the end" }
                                }
                            page = loadNext(deserializeRequest(payload))
                        }

                        LoadType.PREPEND -> error("PREPEND is handled before resolving a request")
                    }

                    logger.d {
                        "Persisting illustration page (loadType: $loadType, tagHash: ${tag.hashCode()}, cachedItems: ${state.pages.sumOf { it.data.size }}, itemCount: ${page.illusts.size})"
                    }
                    database.withWriteTransaction {
                        if (loadType == LoadType.REFRESH) {
                            database.illustFlowDao().clean(tag)
                            database.pageKeyDao().clean(tag)
                        }

                        database.cacheIllusts(page.illusts)
                        if (page.illusts.isNotEmpty()) {
                            database.illustFlowDao().insert(
                                page.illusts.map { illust ->
                                    IllustFlow(
                                        tag = tag,
                                        illustCacheId = illust.id.toLong(),
                                    )
                                },
                            )
                        }

                        database.pageKeyDao().insert(
                            PageKey(
                                tag = tag,
                                page = previousState?.page?.plus(1) ?: FIRST_LOCAL_PAGE,
                                nextPayload = page.nextRequest?.let(::serializeRequest),
                            ),
                        )
                    }

                    val endOfPaginationReached = page.nextRequest == null
                    logger.i {
                        "Illustration page committed (loadType: $loadType, page: ${previousState?.page?.plus(1) ?: FIRST_LOCAL_PAGE}, itemCount: ${page.illusts.size}, endReached: $endOfPaginationReached)"
                    }
                    return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
                } catch (e: CancellationException) {
                    logger.i { "Illustration page loading was cancelled (loadType: $loadType)" }
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Illustration page loading failed (loadType: $loadType); returning a mediator error" }
                    return MediatorResult.Error(e)
                }
            }
        },
        pagingSourceFactory = {
            database.illustFlowDao().query(tag)
        },
    )

    @OptIn(ExperimentalTime::class)
    private suspend fun AppAccountDatabase.cacheIllusts(illusts: List<Illust>) {
        if (illusts.isEmpty()) {
            return
        }

        val imageUrls = linkedMapOf<String, ImageUrlsCache>()
        val users = linkedMapOf<Long, UserCache>()
        val tags = linkedMapOf<String, TagCache>()
        val tagIdsByName = linkedMapOf<String, String>()
        val cachedIllusts = linkedMapOf<Long, IllustCache>()
        val tagCrossRefs = linkedSetOf<IllustTagCrossRef>()
        val metaPageCrossRefs = linkedSetOf<IllustMetaPageCrossRef>()

        illusts.forEach { illust ->
            val illustId = illust.id.toLong()
            val profileImageUrlsId = userProfileImageUrlsId(illust.user.id)
            val coverImageUrlsId = illustCoverImageUrlsId(illust.id)

            imageUrls[profileImageUrlsId] = illust.user.profileImageUrls.toCache(profileImageUrlsId)
            imageUrls[coverImageUrlsId] = illust.imageUrls.toCache(coverImageUrlsId)
            users[illust.user.id.toLong()] = UserCache(
                userId = illust.user.id.toLong(),
                name = illust.user.name,
                account = illust.user.account,
                profileImageUrlsId = profileImageUrlsId,
                isFollowed = illust.user.isFollowed,
                comment = illust.user.comment,
            )
            cachedIllusts[illustId] = IllustCache(
                illustId = illustId,
                title = illust.title,
                caption = illust.caption,
                type = illust.type,
                authorId = illust.user.id.toLong(),
                createTime = illust.createTime.toEpochMilliseconds(),
                pageCount = illust.pageCount,
                width = illust.width,
                height = illust.height,
                sanityLevel = illust.sanityLevel,
                xRestrict = illust.xRestrict,
                totalView = illust.totalView,
                totalBookmarks = illust.totalBookmarks,
                isBookmarked = illust.isBookMarked,
                illustAiType = illust.illustAiType,
                imageUrlsId = coverImageUrlsId,
                singlePageMetaJson = illust.singlePageMeta?.toString(),
            )

            illust.tags.forEach { pixivTag ->
                val tagId = tagIdsByName[pixivTag.name]
                    ?: tagDao().findByName(pixivTag.name)?.id
                    ?: tagCacheId(pixivTag.name)
                tagIdsByName[pixivTag.name] = tagId
                tags[tagId] = TagCache(
                    id = tagId,
                    name = pixivTag.name,
                    translatedName = pixivTag.translatedName,
                )
                tagCrossRefs += IllustTagCrossRef(illustId = illustId, tagId = tagId)
            }

            illust._metaPages.forEachIndexed { index, page ->
                val pageImageUrlsId = illustPageImageUrlsId(illust.id, index)
                imageUrls[pageImageUrlsId] = page.imageUrls.toCache(pageImageUrlsId)
                metaPageCrossRefs += IllustMetaPageCrossRef(
                    illustId = illustId,
                    imageUrlsId = pageImageUrlsId,
                    sortIndex = index,
                )
            }
        }

        imageUrls.values.forEach { imageUrlsDao().upsert(it) }
        users.values.forEach { userDao().upsert(it) }
        tags.values.forEach { tagDao().upsert(it) }
        illustDao().upsert(cachedIllusts.values.toList())

        cachedIllusts.keys.forEach { illustId ->
            illustTagCrossRefDao().deleteByIllustId(illustId)
            illustMetaPageCrossRefDao().deleteByIllustId(illustId)
        }
        if (tagCrossRefs.isNotEmpty()) {
            illustTagCrossRefDao().insert(tagCrossRefs.toList())
        }
        if (metaPageCrossRefs.isNotEmpty()) {
            illustMetaPageCrossRefDao().insert(metaPageCrossRefs.toList())
        }
    }

    private fun ImageUrls.toCache(id: String): ImageUrlsCache = ImageUrlsCache(
        id = id,
        squareMedium = squareMedium,
        medium = medium,
        large = large,
        original = original,
    )

    private companion object {
        const val FIRST_LOCAL_PAGE = 1

        fun userProfileImageUrlsId(userId: Int): String = "user:$userId:profile"

        fun illustCoverImageUrlsId(illustId: Int): String = "illust:$illustId:cover"

        fun illustPageImageUrlsId(illustId: Int, index: Int): String = "illust:$illustId:page:$index"

        fun tagCacheId(name: String): String = "tag:$name"
    }
}

/** A forward-only repository for APIs whose request is a numeric index. */
abstract class IllustIndexableRepo(
    database: AppAccountDatabase,
    tag: String,
    private val networkPageSize: Int = DEFAULT_PAGE_SIZE,
) : IllustRepo<Int>(
    database = database,
    tag = tag,
    pageSize = networkPageSize,
) {
    protected abstract suspend fun request(index: Int): List<Illust>

    protected open fun endOfPaginationReached(index: Int, illusts: List<Illust>): Boolean =
        illusts.size < networkPageSize

    final override suspend fun loadInitial(): Page<Int> = load(FIRST_INDEX)

    final override suspend fun loadNext(request: Int): Page<Int> = load(request)

    final override fun serializeRequest(request: Int): String = request.toString()

    final override fun deserializeRequest(payload: String): Int = payload.toInt()

    private suspend fun load(index: Int): Page<Int> {
        val illusts = request(index)
        val nextIndex = if (endOfPaginationReached(index, illusts)) null else index + 1
        return Page(illusts = illusts, nextRequest = nextIndex)
    }

    private companion object {
        const val FIRST_INDEX = 1
        const val DEFAULT_PAGE_SIZE = 30
    }
}

/** A forward-only repository for APIs whose response supplies an opaque next URL. */
abstract class IllustNextUrlRepo(
    database: AppAccountDatabase,
    tag: String,
    pageSize: Int = DEFAULT_PAGE_SIZE,
) : IllustRepo<String>(
    database = database,
    tag = tag,
    pageSize = pageSize,
) {
    protected abstract suspend fun requestInitial(): IllustResult
    protected abstract suspend fun requestNext(nextUrl: String): IllustResult

    final override suspend fun loadInitial(): Page<String> = requestInitial().toPage()

    final override suspend fun loadNext(request: String): Page<String> = requestNext(request).toPage()

    final override fun serializeRequest(request: String): String = request

    final override fun deserializeRequest(payload: String): String = payload

    private fun IllustResult.toPage(): Page<String> = Page(
        illusts = illusts,
        nextRequest = nextUrl,
    )

    private companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}
