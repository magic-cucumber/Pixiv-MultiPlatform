package top.kagg886.pmf.repository.illust

import top.kagg886.pmf.database.AppDatabase

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
        currentUserId: Long,
        identifier: String,
    ): IllustRepository {
        require(currentUserId >= 0) { "currentUserId must be non-negative" }
        require(identifier.isNotBlank()) { "identifier must not be blank" }
        return IllustRepository(
            currentUserId = currentUserId,
            identifier = identifier,
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
    currentUserId: Long,
    identifier: String,
    block: IllustRepositoryDsl.() -> Unit,
): IllustRepository = IllustRepositoryDsl().apply(block).build(database, currentUserId, identifier)
