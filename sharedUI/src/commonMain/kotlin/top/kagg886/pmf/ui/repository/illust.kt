@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package top.kagg886.pmf.ui.repository

import androidx.paging.PagingSource
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.IllustResult
import top.kagg886.pmf.database.account.AppAccountDatabase
import top.kagg886.pmf.database.account.entity.IllustCache
import top.kagg886.pmf.database.account.entity.IllustCacheDisplayed
import top.kagg886.pmf.database.account.entity.IllustFlow
import top.kagg886.pmf.database.account.entity.IllustMetaPageCrossRef
import top.kagg886.pmf.database.account.entity.IllustTagCrossRef
import top.kagg886.pmf.database.account.entity.ImageUrlsCache
import top.kagg886.pmf.database.account.entity.TagCache
import top.kagg886.pmf.database.account.entity.UserCache
import top.kagg886.pmf.logger.Logger

/** A forward-only illustration repository for APIs whose request is a numeric index. */
@Logger("IllustIndexedRepo")
abstract class IllustIndexedRepo(
    database: AppAccountDatabase,
    tag: String,
    private val networkPageSize: Int = DEFAULT_PAGE_SIZE,
) : BaseIndexedRepo<IllustCacheDisplayed>(database, tag, networkPageSize) {
    protected abstract suspend fun request(index: Int): List<Illust>

    protected open fun endOfPaginationReached(index: Int, illusts: List<Illust>): Boolean =
        illusts.size < networkPageSize

    final override suspend fun loadInitial(): LoadedPage<Int> = load(FIRST_INDEX)

    final override suspend fun loadNext(request: Int): LoadedPage<Int> = load(request)

    final override suspend fun clearFlow() = database.illustFlowDao().clean(flowTag)

    final override fun pagingSource(): PagingSource<Int, IllustCacheDisplayed> =
        database.illustFlowDao().query(flowTag)

    private suspend fun load(index: Int): LoadedPage<Int> {
        logger.i {
            "Loading indexed illustration page (index: $index, pageSize: $networkPageSize, tagHash: ${flowTag.hashCode()})"
        }
        val illusts = request(index)
        val endReached = endOfPaginationReached(index, illusts)
        val nextIndex = if (endReached) null else index + 1
        logger.d {
            "Indexed illustration response received (index: $index, itemCount: ${illusts.size}, endReached: $endReached)"
        }
        return loadedPage(nextIndex, illusts.size) {
            val summary = database.persistIllustFlow(flowTag, illusts)
            logger.d { summary.logMessage("Indexed illustration page persisted") }
        }
    }

    private companion object {
        const val FIRST_INDEX = 1
        const val DEFAULT_PAGE_SIZE = 30
    }
}

/** A forward-only illustration repository for APIs whose response supplies an opaque next URL. */
@Logger("IllustNextUrlRepo")
abstract class IllustNextUrlRepo(
    database: AppAccountDatabase,
    tag: String,
    pageSize: Int = DEFAULT_PAGE_SIZE,
) : BaseNextUrlRepo<IllustCacheDisplayed>(database, tag, pageSize) {
    protected abstract suspend fun requestInitial(): IllustResult

    protected abstract suspend fun requestNext(nextUrl: String): IllustResult

    final override suspend fun loadInitial(): LoadedPage<String> {
        logger.i { "Loading initial next-URL illustration page (tagHash: ${flowTag.hashCode()})" }
        return requestInitial().toPage("Initial illustration response received")
    }

    final override suspend fun loadNext(request: String): LoadedPage<String> {
        logger.i {
            "Loading continued next-URL illustration page (nextUrlLength: ${request.length}, nextUrlHash: ${request.hashCode()}, tagHash: ${flowTag.hashCode()})"
        }
        return requestNext(request).toPage("Continued illustration response received")
    }

    final override suspend fun clearFlow() = database.illustFlowDao().clean(flowTag)

    final override fun pagingSource(): PagingSource<Int, IllustCacheDisplayed> =
        database.illustFlowDao().query(flowTag)

    private fun IllustResult.toPage(responseLabel: String): LoadedPage<String> {
        if (illusts.isEmpty() && nextUrl != null) {
            logger.w {
                "$responseLabel with no items but a continuation URL; committing the empty page and continuing with the supplied URL"
            }
        } else {
            logger.d { "$responseLabel (itemCount: ${illusts.size}, endReached: ${nextUrl == null})" }
        }
        return loadedPage(nextUrl, illusts.size) {
            val summary = database.persistIllustFlow(flowTag, illusts)
            logger.d { summary.logMessage("Next-URL illustration page persisted") }
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}

private data class IllustPersistenceSummary(
    val inputItems: Int,
    val cachedItems: Int,
    val users: Int,
    val imageUrls: Int,
    val tags: Int,
    val reusedTags: Int,
    val tagReferences: Int,
    val metaPages: Int,
    val flowItems: Int,
) {
    fun logMessage(operation: String): String =
        "$operation (inputItems: $inputItems, cachedItems: $cachedItems, users: $users, imageUrls: $imageUrls, tags: $tags, reusedTags: $reusedTags, tagReferences: $tagReferences, metaPages: $metaPages, flowItems: $flowItems)"
}

private suspend fun AppAccountDatabase.persistIllustFlow(
    tag: String,
    illusts: List<Illust>,
): IllustPersistenceSummary {
    val summary = cacheIllusts(illusts)
    val flowItems = appendIllustFlow(tag, illusts)
    return summary.copy(flowItems = flowItems)
}

private suspend fun AppAccountDatabase.appendIllustFlow(tag: String, illusts: List<Illust>): Int {
    if (illusts.isEmpty()) return 0
    illustFlowDao().insert(
        illusts.map { illust ->
            IllustFlow(tag = tag, illustCacheId = illust.id.toLong())
        },
    )
    return illusts.size
}

private suspend fun AppAccountDatabase.cacheIllusts(illusts: List<Illust>): IllustPersistenceSummary {
    if (illusts.isEmpty()) {
        return IllustPersistenceSummary(0, 0, 0, 0, 0, 0, 0, 0, 0)
    }

    val imageUrls = linkedMapOf<String, ImageUrlsCache>()
    val users = linkedMapOf<Long, UserCache>()
    val tags = linkedMapOf<String, TagCache>()
    val cachedIllusts = linkedMapOf<Long, IllustCache>()
    val tagCrossRefs = linkedSetOf<IllustTagCrossRef>()
    val metaPageCrossRefs = linkedSetOf<IllustMetaPageCrossRef>()
    var reusedTags = 0

    illusts.forEach { illust ->
        val cachedUser = UserCache.fromBean(illust.user)
        val cachedIllust = IllustCache.fromBean(illust)
        val profileImage = ImageUrlsCache.fromBean(illust.user.profileImageUrls, cachedUser.profileImageUrlsId)
        val coverImage = ImageUrlsCache.fromBean(illust.imageUrls, cachedIllust.imageUrlsId)

        users[cachedUser.userId] = cachedUser
        cachedIllusts[cachedIllust.illustId] = cachedIllust
        imageUrls[profileImage.id] = profileImage
        imageUrls[coverImage.id] = coverImage

        illust.tags.forEach { bean ->
            val generated = TagCache.fromBean(bean)
            val existingTag = tagDao().findByName(bean.name)
            if (existingTag != null) reusedTags++
            val cachedTag = existingTag ?: generated
            tags[cachedTag.id] = cachedTag
            tagCrossRefs += IllustTagCrossRef(cachedIllust.illustId, cachedTag.id)
        }

        illust._metaPages.forEachIndexed { index, page ->
            val imageId = "illust:${illust.id}:page:$index"
            imageUrls[imageId] = ImageUrlsCache.fromBean(page.imageUrls, imageId)
            metaPageCrossRefs += IllustMetaPageCrossRef(
                illustId = cachedIllust.illustId,
                imageUrlsId = imageId,
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
    if (tagCrossRefs.isNotEmpty()) illustTagCrossRefDao().insert(tagCrossRefs.toList())
    if (metaPageCrossRefs.isNotEmpty()) illustMetaPageCrossRefDao().insert(metaPageCrossRefs.toList())

    return IllustPersistenceSummary(
        inputItems = illusts.size,
        cachedItems = cachedIllusts.size,
        users = users.size,
        imageUrls = imageUrls.size,
        tags = tags.size,
        reusedTags = reusedTags,
        tagReferences = tagCrossRefs.size,
        metaPages = metaPageCrossRefs.size,
        flowItems = 0,
    )
}
