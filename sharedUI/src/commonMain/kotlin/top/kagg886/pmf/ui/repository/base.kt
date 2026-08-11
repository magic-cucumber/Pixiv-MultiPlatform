package top.kagg886.pmf.ui.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.CancellationException
import top.kagg886.pmf.database.account.AppAccountDatabase
import top.kagg886.pmf.database.account.entity.PageKey
import top.kagg886.pmf.logger.Logger

/** Common forward-only, database-backed paging repository. */
@Logger
abstract class BaseRepo<Request : Any, Cache : Any> protected constructor(
    protected val database: AppAccountDatabase,
    protected val flowTag: String,
    pageSize: Int,
) {
    init {
        require(flowTag.isNotBlank()) { "flowTag must not be blank" }
        require(pageSize > 0) { "pageSize must be greater than 0" }
    }

    /** A fetched page whose persistence callback is executed inside the repository transaction. */
    protected class LoadedPage<Request : Any> internal constructor(
        internal val nextRequest: Request?,
        internal val itemCount: Int = 0,
        internal val persist: suspend () -> Unit = {},
    )

    /** Builds a page while keeping API-specific beans outside the base repository. */
    protected fun loadedPage(nextRequest: Request?, itemCount: Int, persist: suspend () -> Unit): LoadedPage<Request> =
        LoadedPage(nextRequest, itemCount, persist)

    protected abstract suspend fun loadInitial(): LoadedPage<Request>

    protected abstract suspend fun loadNext(request: Request): LoadedPage<Request>

    protected abstract fun serializeRequest(request: Request): String

    protected abstract fun deserializeRequest(payload: String): Request

    protected abstract suspend fun clearFlow()

    protected abstract fun pagingSource(): PagingSource<Int, Cache>

    @OptIn(ExperimentalPagingApi::class)
    val pager: Pager<Int, Cache> = Pager(
        config = PagingConfig(
            pageSize = pageSize,
            initialLoadSize = pageSize,
            enablePlaceholders = false,
        ),
        remoteMediator = object : RemoteMediator<Int, Cache>() {
            override suspend fun initialize(): InitializeAction {
                val savedState = database.pageKeyDao().last(flowTag)
                val action = if (savedState == null) {
                    InitializeAction.LAUNCH_INITIAL_REFRESH
                } else {
                    InitializeAction.SKIP_INITIAL_REFRESH
                }
                logger.i {
                    "Initializing cached flow (tagHash: ${flowTag.hashCode()}, restoredPages: ${savedState?.page ?: 0}, action: $action)"
                }
                return action
            }

            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, Cache>,
            ): MediatorResult {
                logger.i {
                    "Loading cached flow page (loadType: $loadType, tagHash: ${flowTag.hashCode()}, presentedItems: ${state.pages.sumOf { it.data.size }})"
                }
                if (loadType == LoadType.PREPEND) {
                    logger.v { "Skipping prepend because the repository only loads forward" }
                    return MediatorResult.Success(endOfPaginationReached = true)
                }

                try {
                    val previousState: PageKey?
                    val page: LoadedPage<Request>
                    when (loadType) {
                        LoadType.REFRESH -> {
                            previousState = null
                            logger.d { "Selecting the initial network request for cached flow refresh" }
                            page = loadInitial()
                        }

                        LoadType.APPEND -> {
                            previousState = database.pageKeyDao().last(flowTag)
                                ?: return MediatorResult.Error(
                                    IllegalStateException("Cannot append without saved page state"),
                                ).also {
                                    logger.e { "Append failed because no saved page state exists" }
                                }
                            val payload = previousState.nextPayload
                                ?: return MediatorResult.Success(endOfPaginationReached = true).also {
                                    logger.d { "Skipping append because pagination has reached the end" }
                                }
                            logger.d {
                                "Selecting the saved continuation request for cached flow append (savedPage: ${previousState.page}, payloadLength: ${payload.length})"
                            }
                            page = loadNext(deserializeRequest(payload))
                        }

                        LoadType.PREPEND -> error("PREPEND is handled before resolving a request")
                    }

                    val localPage = previousState?.page?.plus(1) ?: FIRST_LOCAL_PAGE
                    logger.d {
                        "Persisting cached page (loadType: $loadType, tagHash: ${flowTag.hashCode()}, cachedItems: ${state.pages.sumOf { it.data.size }}, itemCount: ${page.itemCount})"
                    }
                    database.withWriteTransaction {
                        if (loadType == LoadType.REFRESH) {
                            logger.d { "Clearing the previous flow and page keys before committing refreshed data" }
                            clearFlow()
                            database.pageKeyDao().clean(flowTag)
                        }
                        page.persist()
                        database.pageKeyDao().insert(
                            PageKey(
                                tag = flowTag,
                                page = localPage,
                                nextPayload = page.nextRequest?.let(::serializeRequest),
                            ),
                        )
                    }

                    val endReached = page.nextRequest == null
                    logger.i {
                        "Cached page committed (loadType: $loadType, page: $localPage, itemCount: ${page.itemCount}, endReached: $endReached)"
                    }
                    return MediatorResult.Success(endOfPaginationReached = endReached)
                } catch (error: CancellationException) {
                    logger.i { "Cached page loading was cancelled (loadType: $loadType)" }
                    throw error
                } catch (error: Exception) {
                    logger.e(error) { "Cached page loading failed (loadType: $loadType); returning a mediator error" }
                    return MediatorResult.Error(error)
                }
            }
        },
        pagingSourceFactory = ::pagingSource,
    )

    private companion object {
        const val FIRST_LOCAL_PAGE = 1
    }
}

/** Base repository whose next request is a numeric page index. */
abstract class BaseIndexedRepo<Cache : Any> protected constructor(
    database: AppAccountDatabase,
    flowTag: String,
    pageSize: Int,
) : BaseRepo<Int, Cache>(database, flowTag, pageSize) {
    final override fun serializeRequest(request: Int): String = request.toString()
    final override fun deserializeRequest(payload: String): Int = payload.toInt()
}

/** Base repository whose next request is an opaque URL. */
abstract class BaseNextUrlRepo<Cache : Any> protected constructor(
    database: AppAccountDatabase,
    flowTag: String,
    pageSize: Int,
) : BaseRepo<String, Cache>(database, flowTag, pageSize) {
    final override fun serializeRequest(request: String): String = request
    final override fun deserializeRequest(payload: String): String = payload
}
