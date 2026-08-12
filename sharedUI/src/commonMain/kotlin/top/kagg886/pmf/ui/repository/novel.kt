@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.ui.repository

import androidx.paging.PagingSource
import top.kagg886.pixko.module.novel.Novel
import top.kagg886.pixko.module.novel.NovelResult
import top.kagg886.pmf.database.account.AppAccountDatabase
import top.kagg886.pmf.database.account.entity.ImageUrlsCache
import top.kagg886.pmf.database.account.entity.NovelCache
import top.kagg886.pmf.database.account.entity.NovelCacheDisplayed
import top.kagg886.pmf.database.account.entity.NovelFlow
import top.kagg886.pmf.database.account.entity.NovelSeriesCache
import top.kagg886.pmf.database.account.entity.NovelTagCrossRef
import top.kagg886.pmf.database.account.entity.TagCache
import top.kagg886.pmf.database.account.entity.UserCache
import top.kagg886.pmf.logger.Logger

/** A forward-only novel repository for APIs whose request is a numeric index. */
@Logger
abstract class NovelIndexedRepo(
    database: AppAccountDatabase,
    tag: String,
    private val networkPageSize: Int = DEFAULT_PAGE_SIZE,
) : BaseIndexedRepo<NovelCacheDisplayed>(database, tag, networkPageSize) {
    protected abstract suspend fun request(index: Int): List<Novel>

    protected open fun endOfPaginationReached(index: Int, novels: List<Novel>): Boolean =
        novels.size < networkPageSize

    final override suspend fun loadInitial(): LoadedPage<Int> = load(FIRST_INDEX)

    final override suspend fun loadNext(request: Int): LoadedPage<Int> = load(request)

    final override suspend fun clearFlow() = database.novelFlowDao().clean(flowTag)

    final override fun pagingSource(): PagingSource<Int, NovelCacheDisplayed> =
        database.novelFlowDao().query(flowTag)

    private suspend fun load(index: Int): LoadedPage<Int> {
        logger.i {
            "Loading indexed novel page (index: $index, pageSize: $networkPageSize, tagHash: ${flowTag.hashCode()})"
        }
        val novels = request(index)
        val endReached = endOfPaginationReached(index, novels)
        val nextIndex = if (endReached) null else index + 1
        if (novels.isEmpty() && !endReached) {
            logger.w {
                "Indexed novel response was empty but pagination remains open; committing the empty page and continuing with index ${index + 1}"
            }
        } else {
            logger.d {
                "Indexed novel response received (index: $index, itemCount: ${novels.size}, endReached: $endReached)"
            }
        }
        return loadedPage(nextIndex, novels.size) {
            val summary = database.persistNovelFlow(flowTag, novels)
            logger.d { summary.logMessage("Indexed novel page persisted") }
        }
    }

    private companion object {
        const val FIRST_INDEX = 1
        const val DEFAULT_PAGE_SIZE = 30
    }
}

/** A forward-only novel repository for APIs whose response supplies an opaque next URL. */
@Logger
abstract class NovelNextUrlRepo(
    database: AppAccountDatabase,
    tag: String,
    pageSize: Int = DEFAULT_PAGE_SIZE,
) : BaseNextUrlRepo<NovelCacheDisplayed>(database, tag, pageSize) {
    protected abstract suspend fun requestInitial(): NovelResult

    protected abstract suspend fun requestNext(nextUrl: String): NovelResult

    final override suspend fun loadInitial(): LoadedPage<String> {
        logger.i { "Loading initial next-URL novel page (tagHash: ${flowTag.hashCode()})" }
        return requestInitial().toPage("Initial novel response received")
    }

    final override suspend fun loadNext(request: String): LoadedPage<String> {
        logger.i {
            "Loading continued next-URL novel page (nextUrlLength: ${request.length}, nextUrlHash: ${request.hashCode()}, tagHash: ${flowTag.hashCode()})"
        }
        return requestNext(request).toPage("Continued novel response received")
    }

    final override suspend fun clearFlow() = database.novelFlowDao().clean(flowTag)

    final override fun pagingSource(): PagingSource<Int, NovelCacheDisplayed> =
        database.novelFlowDao().query(flowTag)

    private fun NovelResult.toPage(responseLabel: String): LoadedPage<String> {
        if (novels.isEmpty() && nextUrl != null) {
            logger.w {
                "$responseLabel with no items but a continuation URL; committing the empty page and continuing with the supplied URL"
            }
        } else {
            logger.d { "$responseLabel (itemCount: ${novels.size}, endReached: ${nextUrl == null})" }
        }
        return loadedPage(nextUrl, novels.size) {
            val summary = database.persistNovelFlow(flowTag, novels)
            logger.d { summary.logMessage("Next-URL novel page persisted") }
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}

private data class NovelPersistenceSummary(
    val inputItems: Int,
    val cachedItems: Int,
    val users: Int,
    val imageUrls: Int,
    val series: Int,
    val preservedDetailedSeries: Int,
    val tags: Int,
    val reusedTags: Int,
    val tagReferences: Int,
    val flowItems: Int,
) {
    fun logMessage(operation: String): String =
        "$operation (inputItems: $inputItems, cachedItems: $cachedItems, users: $users, imageUrls: $imageUrls, series: $series, preservedDetailedSeries: $preservedDetailedSeries, tags: $tags, reusedTags: $reusedTags, tagReferences: $tagReferences, flowItems: $flowItems)"
}

private suspend fun AppAccountDatabase.persistNovelFlow(
    tag: String,
    novels: List<Novel>,
): NovelPersistenceSummary {
    val summary = cacheNovels(novels)
    val flowItems = appendNovelFlow(tag, novels)
    return summary.copy(flowItems = flowItems)
}

private suspend fun AppAccountDatabase.appendNovelFlow(tag: String, novels: List<Novel>): Int {
    if (novels.isEmpty()) return 0
    novelFlowDao().insert(
        novels.map { novel ->
            NovelFlow(tag = tag, novelCacheId = novel.id.toLong())
        },
    )
    return novels.size
}

private suspend fun AppAccountDatabase.cacheNovels(novels: List<Novel>): NovelPersistenceSummary {
    if (novels.isEmpty()) {
        return NovelPersistenceSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }

    val imageUrls = linkedMapOf<String, ImageUrlsCache>()
    val users = linkedMapOf<Long, UserCache>()
    val series = linkedMapOf<Long, NovelSeriesCache>()
    val tags = linkedMapOf<String, TagCache>()
    val cachedNovels = linkedMapOf<Long, NovelCache>()
    val tagCrossRefs = linkedSetOf<NovelTagCrossRef>()
    var preservedDetailedSeries = 0
    var reusedTags = 0

    novels.forEach { novel ->
        val cachedUser = UserCache.fromBean(novel.user)
        val cachedNovel = NovelCache.fromBean(novel)
        val profileImage = ImageUrlsCache.fromBean(novel.user.profileImageUrls, cachedUser.profileImageUrlsId)
        val coverImage = ImageUrlsCache.fromBean(novel.imageUrls, cachedNovel.imageUrlsId)

        users[cachedUser.userId] = cachedUser
        cachedNovels[cachedNovel.novelId] = cachedNovel
        imageUrls[profileImage.id] = profileImage
        imageUrls[coverImage.id] = coverImage

        val seriesId = novel.series.id
        if (seriesId != null && seriesId >= 0) {
            val cachedSeries = NovelSeriesCache.fromBean(novel.series, cachedUser.userId)
            val existing = series[cachedSeries.id] ?: novelSeriesDao().find(cachedSeries.id)
            val detailedSeries = existing?.takeIf {
                it.caption != null || it.contentCount != null || it.totalCharacterCount != null
            }
            if (detailedSeries != null) preservedDetailedSeries++
            series[cachedSeries.id] = detailedSeries ?: cachedSeries
        }

        novel.tags.forEach { bean ->
            val generated = TagCache.fromBean(bean)
            val existingTag = tagDao().findByName(bean.name)
            if (existingTag != null) reusedTags++
            val cachedTag = existingTag ?: generated
            tags[cachedTag.id] = cachedTag
            tagCrossRefs += NovelTagCrossRef(cachedNovel.novelId, cachedTag.id)
        }
    }

    imageUrls.values.forEach { imageUrlsDao().upsert(it) }
    users.values.forEach { userDao().upsert(it) }
    series.values.forEach { novelSeriesDao().upsert(it) }
    tags.values.forEach { tagDao().upsert(it) }
    novelDao().upsert(cachedNovels.values.toList())

    cachedNovels.keys.forEach { novelId -> novelTagCrossRefDao().deleteByNovelId(novelId) }
    if (tagCrossRefs.isNotEmpty()) novelTagCrossRefDao().insert(tagCrossRefs.toList())

    return NovelPersistenceSummary(
        inputItems = novels.size,
        cachedItems = cachedNovels.size,
        users = users.size,
        imageUrls = imageUrls.size,
        series = series.size,
        preservedDetailedSeries = preservedDetailedSeries,
        tags = tags.size,
        reusedTags = reusedTags,
        tagReferences = tagCrossRefs.size,
        flowItems = 0,
    )
}
