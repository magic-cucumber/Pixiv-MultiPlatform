package top.kagg886.pmf.repository

import androidx.paging.*
import top.kagg886.pmf.database.AppDatabase
import top.kagg886.pmf.database.dao.IllustFlow
import top.kagg886.pmf.database.dao.PageKeyRecord
import top.kagg886.pmf.database.dao.cache.IllustCache
import top.kagg886.pmf.database.util.RoomDataBaseLock

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/7/30 14:57
 * ================================================
 */
class IllustRepository internal constructor(
    val currentUserId: Int,
    val identifier: String,
    private val database: AppDatabase,
    private val loader: Loader,
) {
    private val flowId: String = "$currentUserId:$identifier"

    /** The database is the source of truth consumed by the UI. */
    @OptIn(ExperimentalPagingApi::class)
    fun pager(config: PagingConfig = PagingConfig(pageSize = DEFAULT_PAGE_SIZE)): Pager<Int, IllustCache> =
        Pager(
            config = config,
            remoteMediator = IllustRemoteMediator(
                database = database,
                flowId = flowId,
                pageSize = config.pageSize,
                loader = loader,
            ),
            pagingSourceFactory = { database.illustFlowDao().pagingSource(flowId) },
        )

    internal sealed interface Loader {
        suspend fun loadFirst(pageSize: Int): IllustNetworkPage

        suspend fun loadAppend(
            page: Int,
            pageSize: Int,
            nextUrl: String?,
        ): IllustNetworkPage

        fun endOfPaginationReached(page: IllustNetworkPage, pageSize: Int): Boolean
    }

    internal class PageSizeImpl(
        private val fetch: suspend (page: Int, pageSize: Int) -> IllustNetworkPage,
    ) : Loader {
        override suspend fun loadFirst(pageSize: Int): IllustNetworkPage = fetch(1, pageSize)

        override suspend fun loadAppend(page: Int, pageSize: Int, nextUrl: String?): IllustNetworkPage =
            fetch(page, pageSize)

        override fun endOfPaginationReached(page: IllustNetworkPage, pageSize: Int): Boolean =
            page.items.size < pageSize
    }

    internal class NextUrlImpl(
        private val fetchFirst: suspend (pageSize: Int) -> IllustNetworkPage,
        private val fetchNext: suspend (nextUrl: String) -> IllustNetworkPage,
    ) : Loader {
        override suspend fun loadFirst(pageSize: Int): IllustNetworkPage = fetchFirst(pageSize)

        override suspend fun loadAppend(page: Int, pageSize: Int, nextUrl: String?): IllustNetworkPage =
            fetchNext(requireNotNull(nextUrl) { "A next-url source needs a cursor for page $page" })

        override fun endOfPaginationReached(page: IllustNetworkPage, pageSize: Int): Boolean =
            page.nextUrl == null
    }

    @OptIn(ExperimentalPagingApi::class)
    private class IllustRemoteMediator(
        private val database: AppDatabase,
        private val flowId: String,
        private val pageSize: Int,
        private val loader: Loader,
    ) : RemoteMediator<Int, IllustCache>() {
        private val lock: RoomDataBaseLock = RoomDataBaseLock(database)

        override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, IllustCache>,
        ): MediatorResult = try {
            when (loadType) {
                LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
                LoadType.REFRESH -> loadFirst()
                LoadType.APPEND -> loadAppend()
            }
        } catch (throwable: Throwable) {
            MediatorResult.Error(throwable)
        }

        private suspend fun loadFirst(): MediatorResult {
            val result: IllustNetworkPage = loader.loadFirst(pageSize)
            val endReached: Boolean = loader.endOfPaginationReached(result, pageSize)
            persist(page = 1, result = result, endReached = endReached, replace = true)
            return MediatorResult.Success(endOfPaginationReached = endReached)
        }

        private suspend fun loadAppend(): MediatorResult {
            val previous: PageKeyRecord = lock.withReadLock {
                database.pageKeyRecordDao().findLatest(flowId)
            } ?: return MediatorResult.Success(endOfPaginationReached = true)

            if (previous.endOfPaginationReached) {
                return MediatorResult.Success(endOfPaginationReached = true)
            }

            val page: Int = previous.page + 1
            val result: IllustNetworkPage = loader.loadAppend(page, pageSize, previous.url)
            val endReached: Boolean = loader.endOfPaginationReached(result, pageSize)
            persist(page = page, result = result, endReached = endReached, replace = false)
            return MediatorResult.Success(endOfPaginationReached = endReached)
        }

        private suspend fun persist(
            page: Int,
            result: IllustNetworkPage,
            endReached: Boolean,
            replace: Boolean,
        ) {
            lock.withWriteLock {
                val flowDao = database.illustFlowDao()
                if (replace) {
                    flowDao.delete(flowId)
                    database.pageKeyRecordDao().delete(flowId)
                }

                val firstPosition: Int = flowDao.count(flowId)
                database.illustDao().upsert(result.items)
                flowDao.insert(
                    result.items.mapIndexed { index, illust ->
                        IllustFlow(
                            id = flowId,
                            position = firstPosition + index,
                            illustId = illust.id,
                        )
                    },
                )
                database.pageKeyRecordDao().upsert(
                    PageKeyRecord(
                        id = flowId,
                        page = page,
                        url = result.nextUrl,
                        endOfPaginationReached = endReached,
                    ),
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE: Int = 30
    }
}

/** The cache rows returned from one remote page. */
data class IllustNetworkPage(
    val items: List<IllustCache>,
    val nextUrl: String? = null,
)

@DslMarker
annotation class IllustRepositoryDslMarker

@IllustRepositoryDslMarker
class IllustRepositoryDsl {
    private var loader: IllustRepository.Loader? = null

    /** Configures an API whose continuation is expressed by page and page size. */
    fun pageSize(fetch: suspend (page: Int, pageSize: Int) -> IllustNetworkPage) {
        setLoader(IllustRepository.PageSizeImpl(fetch))
    }

    /** Configures an API whose continuation is the opaque next_url supplied by Pixiv. */
    fun nextUrl(
        first: suspend (pageSize: Int) -> IllustNetworkPage,
        next: suspend (nextUrl: String) -> IllustNetworkPage,
    ) {
        setLoader(IllustRepository.NextUrlImpl(first, next))
    }

    internal fun build(
        database: AppDatabase,
        currentUserId: Int,
        id: String,
    ): IllustRepository {
        require(currentUserId >= 0) { "currentUserId must be non-negative" }
        require(id.isNotBlank()) { "id must not be blank" }
        return IllustRepository(
            currentUserId = currentUserId,
            identifier = id,
            database = database,
            loader = requireNotNull(loader) { "Configure pageSize { } or nextUrl(first, next)" },
        )
    }

    private fun setLoader(value: IllustRepository.Loader) {
        check(loader == null) { "Only one pagination protocol may be configured" }
        loader = value
    }
}

fun illustRepository(
    database: AppDatabase,
    currentUserId: Int,
    id: String,
    block: IllustRepositoryDsl.() -> Unit,
): IllustRepository = IllustRepositoryDsl().apply(block).build(database, currentUserId, id)
