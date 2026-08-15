package top.kagg886.pmf.ui.route.main.detail.novel

import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Option
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size as CoilSize
import korlibs.time.seconds
import kotlin.collections.set
import kotlin.time.Clock
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.annotation.OrbitExperimental
import top.kagg886.pixko.Tag
import top.kagg886.pixko.anno.ExperimentalNovelParserAPI
import top.kagg886.pixko.module.illust.BookmarkVisibility
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.getIllustDetail
import top.kagg886.pixko.module.novel.Novel
import top.kagg886.pixko.module.novel.NovelData
import top.kagg886.pixko.module.novel.NovelImagesSize
import top.kagg886.pixko.module.novel.SeriesInfo
import top.kagg886.pixko.module.novel.bookmarkNovel
import top.kagg886.pixko.module.novel.deleteBookmarkNovel
import top.kagg886.pixko.module.novel.getNovelContent
import top.kagg886.pixko.module.novel.getNovelDetail
import top.kagg886.pixko.module.novel.getNovelSeries
import top.kagg886.pixko.module.novel.parser.v2.CombinedText
import top.kagg886.pixko.module.novel.parser.v2.JumpPageNode
import top.kagg886.pixko.module.novel.parser.v2.JumpUriNode
import top.kagg886.pixko.module.novel.parser.v2.NewPageNode
import top.kagg886.pixko.module.novel.parser.v2.PixivImageNode
import top.kagg886.pixko.module.novel.parser.v2.TextNode
import top.kagg886.pixko.module.novel.parser.v2.TitleNode
import top.kagg886.pixko.module.novel.parser.v2.UploadImageNode
import top.kagg886.pixko.module.novel.parser.v2.content
import top.kagg886.pixko.module.user.UserLikePublicity
import top.kagg886.pixko.module.user.followUser
import top.kagg886.pixko.module.user.unFollowUser
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.database.AppDatabase
import top.kagg886.pmf.backend.database.dao.BlackListItem
import top.kagg886.pmf.backend.database.dao.BlackListType
import top.kagg886.pmf.backend.database.dao.NovelHistory
import top.kagg886.pmf.backend.database.dao.WatchLaterItem
import top.kagg886.pmf.backend.database.dao.WatchLaterType
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.res.*
import top.kagg886.pmf.translate.IncrementalSentenceParser
import top.kagg886.pmf.translate.LanguageDetector
import top.kagg886.pmf.translate.SentenceTranslationParser
import top.kagg886.pmf.translate.SentenceTranslationState
import top.kagg886.pmf.translate.TranslateResult
import top.kagg886.pmf.translate.TranslateScheduler
import top.kagg886.pmf.translate.isAiTranslateEnabled
import top.kagg886.pmf.translate.translationDisplayText
import top.kagg886.pmf.translate.translationDisplayTextOrNull
import top.kagg886.pmf.ui.route.main.detail.illust.IllustDetailRoute
import top.kagg886.pmf.ui.util.NovelFragmentSpan
import top.kagg886.pmf.ui.util.NovelNodeElement
import top.kagg886.pmf.ui.util.NovelSentenceChunk
import top.kagg886.pmf.ui.util.NovelSentenceSpan
import top.kagg886.pmf.ui.util.RichSegment
import top.kagg886.pmf.ui.util.buildNovelSentenceChunkSource
import top.kagg886.pmf.ui.util.buildNovelSentenceChunks
import top.kagg886.pmf.ui.util.buildNovelSentenceIndex
import top.kagg886.pmf.ui.util.container
import top.kagg886.pmf.ui.util.mergeFragmentStates
import top.kagg886.pmf.ui.util.parseHtmlSegments
import top.kagg886.pmf.ui.util.stripNovelChunkContext
import top.kagg886.pmf.ui.util.translateRichSegments
import top.kagg886.pmf.util.getString
import top.kagg886.pmf.util.logger

class NovelDetailViewModel(val id: Long, val seriesInfo: Option<SeriesInfo>) :
    ViewModel(),
    ContainerHost<NovelDetailViewState, NovelDetailSideEffect>,
    KoinComponent {
    override val container: Container<NovelDetailViewState, NovelDetailSideEffect> =
        container(NovelDetailViewState.Loading(MutableStateFlow("Loading...")))
    private val client = PixivConfig.newAccountFromConfig()
    private val database by inject<AppDatabase>()
    private val translateScheduler by inject<TranslateScheduler>()
    private val translationMutex = Mutex()
    private val activeSentenceEpochs = mutableMapOf<Int, Int>()

    /** 自动重试计数：每片段每 epoch 至多自动重试一次，防止与模型反复不一致时死循环。 */
    private val autoRetryCounts = mutableMapOf<Int, Int>()
    private var translationEpoch = 0
    private var fragmentByIdCache: Map<Int, NovelFragmentSpan> = emptyMap()
    private var sentenceByIdCache: Map<Int, NovelSentenceSpan> = emptyMap()

    private fun CombinedText.toPlainString() = this.joinToString("") { it.text }

    @OptIn(ExperimentalNovelParserAPI::class)
    fun reload(coil: PlatformContext) = intent {
        if (black.matchRules(BlackListType.AUTHOR_ID, id.toString())) {
            postSideEffect(
                NovelDetailSideEffect.Toast(
                    getString(
                        Res.string.blocking_because_black,
                        getString(Res.string.user),
                    ),
                ),
            )
            postSideEffect(NovelDetailSideEffect.NavigateBack)
            return@intent
        }

        val loading =
            NovelDetailViewState.Loading(MutableStateFlow(getString(Res.string.get_novel_detail)))
        reduce { loading }

        val result = kotlin.runCatching {
            client.getNovelDetail(id) to client.getNovelContent(id)
        }

        if (result.isFailure) {
            logger.e("get novel info failed:", result.exceptionOrNull())
            val err = getString(Res.string.load_failed)
            reduce { NovelDetailViewState.Error(err) }
            return@intent
        }
        val (detail, content) = result.getOrThrow()

        if (detail.tags.map { viewModelScope.async { black.matchRules(BlackListType.TAG_NAME, it.name) } }.awaitAll()
                .any { it }
        ) {
            postSideEffect(
                NovelDetailSideEffect.Toast(
                    getString(
                        Res.string.blocking_because_black,
                        getString(Res.string.tags),
                    ),
                ),
            )
            postSideEffect(NovelDetailSideEffect.NavigateBack)
            return@intent
        }

        val images = kotlin.runCatching { content.images }.getOrElse { emptyMap() }

        val nodeMap = linkedMapOf<Int, NovelNodeElement>()

        val data = kotlin.runCatching {
            content.content.value
        }

        if (data.isFailure) {
            val err = getString(Res.string.load_failed)
            reduce {
                NovelDetailViewState.Error(err)
            }
            return@intent
        }

        // 异步获取image
        coroutineScope {
            var parsed by atomic(0)
            for ((index, i) in data.getOrThrow().withIndex()) {
                when (i) {
                    is JumpUriNode -> {
                        nodeMap[index] = NovelNodeElement.JumpUri(i.text, i.uri)
                        parsed++
                        loading.text.emit(
                            getString(
                                Res.string.parse_novel_node,
                                parsed,
                                data.getOrThrow().size,
                            ),
                        )
                    }

                    is UploadImageNode -> {
                        val priority = listOf(
                            NovelImagesSize.N480Mw,
                            NovelImagesSize.N1200x1200,
                            NovelImagesSize.N128x128,
                            NovelImagesSize.NOriginal,
                            NovelImagesSize.N240Mw,
                        )
                        val img = priority.firstNotNullOf {
                            kotlin.runCatching {
                                images[i.url]!![it]
                            }.getOrNull()
                        }

                        val resp = SingletonImageLoader.get(coil).execute(
                            ImageRequest.Builder(context = coil).size(CoilSize.ORIGINAL).data(img)
                                .build(),
                        )
                        if (resp is SuccessResult) {
                            val info = resp.image
                            nodeMap[index] = NovelNodeElement.UploadImage(
                                img,
                                Size(info.width.toFloat(), info.height.toFloat()),
                            )

                            parsed++
                            loading.text.emit(
                                getString(
                                    Res.string.parse_novel_node,
                                    parsed,
                                    data.getOrThrow().size,
                                ),
                            )

                            continue
                        }
                        logger.w { "getting novel image: $img failed, msg:$resp" }
                    }

                    is PixivImageNode -> {
                        launch {
                            val illust = client.getIllustDetail(i.id.toLong())
                            nodeMap[index] = NovelNodeElement.PixivImage(
                                illust,
                            )
                            parsed++
                            loading.text.emit(
                                getString(
                                    Res.string.parse_novel_node,
                                    parsed,
                                    data.getOrThrow().size,
                                ),
                            )
                        }
                    }

                    is NewPageNode -> {
                        nodeMap[index] = NovelNodeElement.NewPage(index + 1)
                        parsed++
                        loading.text.emit(
                            getString(
                                Res.string.parse_novel_node,
                                parsed,
                                data.getOrThrow().size,
                            ),
                        )
                    }

                    is TextNode -> {
                        nodeMap[index] = NovelNodeElement.Plain(i.text.toPlainString())
                        parsed++
                        loading.text.emit(
                            getString(
                                Res.string.parse_novel_node,
                                parsed,
                                data.getOrThrow().size,
                            ),
                        )
                    }

                    is TitleNode -> {
                        nodeMap[index] = NovelNodeElement.Title(i.text.toPlainString())
                        parsed++
                        loading.text.emit(
                            getString(
                                Res.string.parse_novel_node,
                                parsed,
                                data.getOrThrow().size,
                            ),
                        )
                    }

                    is JumpPageNode -> {
                        nodeMap[index] = NovelNodeElement.JumpPage(i.page)
                        parsed++
                        loading.text.emit(
                            getString(
                                Res.string.parse_novel_node,
                                parsed,
                                data.getOrThrow().size,
                            ),
                        )
                    }
                }
            }
        }

        // 在传入seriesInfo时使用seriesInfo，否则拉取所有series。
        // 如果为null代表这个小说没有series
        loading.text.emit(getString(Res.string.parsing_novel_series))

        val seriesInfo =
            if (!AppConfig.enableFetchSeries) {
                null
            } else {
                seriesInfo.getOrNull() ?: detail.series.id?.let {
                    if (it == -1) return@let null // 默认值为-1
                    val seriesInfo = client.getNovelSeries(it)
                    val mutex = Mutex()
                    var progress = 0
                    val other = coroutineScope {
                        (2..<seriesInfo.novelSeriesDetail.pageCount).map { page ->
                            async {
                                client.getNovelSeries(it, page).novels.apply {
                                    mutex.withLock {
                                        loading.text.emit(
                                            getString(
                                                Res.string.parsing_novel_series_progress,
                                                ++progress,
                                                seriesInfo.novelSeriesDetail.pageCount - 1,
                                            ),
                                        )
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                    SeriesInfo(
                        novelSeriesDetail = seriesInfo.novelSeriesDetail,
                        novels = seriesInfo.novels + other.flatten(),
                    )
                }
            }

        val itemInViewLater = database.watchLaterDAO().exists(
            WatchLaterType.NOVEL,
            detail.id.toLong(),
        )
        val nodes = nodeMap.toList().sortedBy { it.first }.map { it.second }
        val sentenceIndex = buildNovelSentenceIndex(nodes)
        sentenceByIdCache = sentenceIndex.associateBy { it.id }
        fragmentByIdCache = sentenceIndex.flatMap { it.fragments }.associateBy { it.id }

        reduce {
            NovelDetailViewState.Success(
                detail,
                content,
                nodes,
                seriesInfo = seriesInfo,
                itemInViewLater = itemInViewLater,
                sentenceIndex = sentenceIndex,
            )
        }
        if (AppConfig.recordNovelHistory) {
            database.novelHistoryDAO()
                .insert(NovelHistory(id, detail, Clock.System.now().toEpochMilliseconds()))
        }
    }

    @OptIn(OrbitExperimental::class)
    fun addViewLater() = intent {
        runOn<NovelDetailViewState.Success> {
            database.watchLaterDAO().insert(
                WatchLaterItem(
                    type = WatchLaterType.NOVEL,
                    payload = state.novel.id.toLong(),
                    metadata = Json.encodeToJsonElement(state.novel).jsonObject,
                ),
            )

            reduce {
                state.copy(
                    itemInViewLater = true,
                )
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun removeViewLater() = intent {
        runOn<NovelDetailViewState.Success> {
            database.watchLaterDAO().delete(
                type = WatchLaterType.NOVEL,
                payload = state.novel.id.toLong(),
            )

            reduce {
                state.copy(
                    itemInViewLater = false,
                )
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun likeNovel(
        visibility: BookmarkVisibility = BookmarkVisibility.PUBLIC,
        tags: List<Tag>? = null,
    ) = intent {
        runOn<NovelDetailViewState.Success> {
            val result = kotlin.runCatching {
                client.bookmarkNovel(id) {
                    this.tags = tags
                    this.visibility = visibility
                }
            }

            if (result.isFailure) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.bookmark_failed)))
                return@runOn
            }
            reduce {
                state.copy(
                    novel = state.novel.copy(isBookmarked = true),
                )
            }
            postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.bookmark_success)))
        }
    }

    @OptIn(OrbitExperimental::class)
    fun disLikeNovel() = intent {
        runOn<NovelDetailViewState.Success> {
            val result = kotlin.runCatching {
                client.deleteBookmarkNovel(id)
            }

            if (result.isFailure) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.un_bookmark_failed)))
                return@runOn
            }
            reduce {
                state.copy(
                    novel = state.novel.copy(isBookmarked = false),
                )
            }
            postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.un_bookmark_success)))
        }
    }

    @OptIn(OrbitExperimental::class)
    fun followUser(private: Boolean = false) = intent {
        runOn<NovelDetailViewState.Success> {
            val result = kotlin.runCatching {
                client.followUser(
                    state.novel.user.id,
                    publicity = if (private) UserLikePublicity.PRIVATE else UserLikePublicity.PUBLIC,
                )
            }
            if (result.isFailure) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.follow_fail)))
                return@runOn
            }
            if (private) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.follow_success_private)))
            } else {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.follow_success)))
            }
            reduce {
                state.copy(
                    novel = state.novel.copy(
                        user = state.novel.user.copy(
                            isFollowed = true,
                        ),
                    ),
                )
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun unFollowUser() = intent {
        runOn<NovelDetailViewState.Success> {
            val result = kotlin.runCatching {
                client.unFollowUser(state.novel.user.id)
            }
            if (result.isFailure) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.unfollow_fail)))
                return@runOn
            }
            postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.unfollow_success)))
            reduce {
                state.copy(
                    novel = state.novel.copy(
                        user = state.novel.user.copy(
                            isFollowed = false,
                        ),
                    ),
                )
            }
        }
    }

    @OptIn(OrbitExperimental::class)
    fun navigateNextPage() = intent {
        runOn<NovelDetailViewState.Success> {
            val info = state.seriesInfo
            if (info == null) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.cant_jump_series_because_no_series)))
                return@runOn
            }

            val target = info.novels.indexOfFirst { it.id.toLong() == id }

            if (target == -1) {
                throw IllegalStateException("the series get failed")
            }

            if (target == info.novels.size - 1) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.cant_jump_series_because_page_incorrect_in_last)))
                return@runOn
            }

            postSideEffect(
                NovelDetailSideEffect.NavigateToOtherNovel(
                    id = info.novels[target + 1].id.toLong(),
                    seriesInfo = info,
                ),
            )
        }
    }

    @OptIn(OrbitExperimental::class)
    fun navigatePreviousPage() = intent {
        runOn<NovelDetailViewState.Success> {
            val info = state.seriesInfo
            if (info == null) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.cant_jump_series_because_no_series)))
                return@runOn
            }

            val target = info.novels.indexOfFirst { it.id.toLong() == id }

            if (target == -1) {
                throw IllegalStateException("the series get failed")
            }

            if (target == 0) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.cant_jump_series_because_page_incorrect_in_last)))
                return@runOn
            }

            postSideEffect(
                NovelDetailSideEffect.NavigateToOtherNovel(
                    id = info.novels[target - 1].id.toLong(),
                    seriesInfo = info,
                ),
            )
        }
    }

    private val black = database.blacklistDAO()

    @OptIn(OrbitExperimental::class)
    fun black() = intent {
        runOn<NovelDetailViewState.Success> {
            black.insert(BlackListItem(state.novel.user))
            postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.filter_add_user_tips)))
            delay(3.seconds)
            postSideEffect(NovelDetailSideEffect.NavigateBack)
        }
    }

    @OptIn(OrbitExperimental::class)
    fun blackTag(tag: Tag) = intent {
        runOn<NovelDetailViewState.Success> {
            black.insert(BlackListItem(tag.name))
            postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.filter_add_tags_tips)))
            delay(3.seconds)
            postSideEffect(NovelDetailSideEffect.NavigateBack)
        }
    }

    fun performClick(illust: Illust) = intent {
        database.illustGalleryDAO().insert(illust)
        postSideEffect(NovelDetailSideEffect.NavigateIllustDetail(IllustDetailRoute(illust.id)))
    }

    /** 切换正文翻译模式；开启后由 [onVisibleSentencesChanged] 懒触发可见句翻译。 */
    @OptIn(OrbitExperimental::class)
    fun toggleTranslateNovel() = intent {
        runOn<NovelDetailViewState.Success> {
            if (state.translationMode) {
                translationEpoch++
                translationMutex.withLock {
                    activeSentenceEpochs.clear()
                    autoRetryCounts.clear()
                }
                // 保留 sentenceTranslations：关闭时渲染层本就显示原文，
                // 重新开启后已完成的译文立即恢复显示，不再重新请求（避免再次失败回到红色）
                reduce {
                    state.copy(
                        translationMode = false,
                    )
                }
                return@runOn
            }
            if (!isAiTranslateEnabled()) return@runOn
            translationEpoch++
            translationMutex.withLock {
                activeSentenceEpochs.clear()
                autoRetryCounts.clear()
            }
            reduce {
                state.copy(
                    translationMode = true,
                )
            }
            // 重新开启翻译后，自动对失败片段重试（走专用重试通道、单片段即时重试）。
            // 可见窗口只触发 null/Pending，Failed 需要这里显式拉起。
            val failedIds =
                state.sentenceTranslations
                    .filterValues { it is SentenceTranslationState.Failed }
                    .keys
                    .sorted()
            if (failedIds.isNotEmpty()) {
                logger.i { "ai translate novel toggle-on auto retry: ${failedIds.size} failed fragments" }
                for ((index, fragmentId) in failedIds.withIndex()) {
                    if (index > 0) delay(AUTO_RETRY_STAGGER_MS)
                    retrySentence(fragmentId)
                }
            }
        }
    }

    /**
     * 可见句懒翻译。可见窗口已经由 UI 换算为"当前视口行 + 延后 2 页行数"对应的句子集合；
     * 这里把集合组装成小分段逐个交给 AI，进行中的句由 [activeSentenceEpochs] 去重。
     *
     * 分段创建后先标记 [SentenceTranslationState.Pending]（此时顶栏才显示请求等待动画），
     * 收到首个流式事件后转为 [SentenceTranslationState.Translating]。
     * 翻译在 [viewModelScope] 中异步进行、不阻塞本次 intent。
     */
    @OptIn(OrbitExperimental::class)
    fun onVisibleSentencesChanged(visibleFragmentIds: Set<Int>) = intent {
        runOn<NovelDetailViewState.Success> {
            val current = state
            if (!current.translationMode) return@runOn

            val byId = fragmentByIdCache
            val (epoch, chunks) = translationMutex.withLock {
                val epoch = translationEpoch
                val pending = visibleFragmentIds.filter { fragmentId ->
                    val translation = state.sentenceTranslations[fragmentId]
                    fragmentId in byId &&
                        fragmentId !in activeSentenceEpochs &&
                        (translation == null || translation is SentenceTranslationState.Pending)
                }
                val chunks = buildNovelSentenceChunks(
                    byId,
                    sentenceByIdCache,
                    current.novelNodeTexts(),
                    pending.toSet(),
                    withContext = AppConfig.aiTranslateParagraphContext,
                )
                for (chunk in chunks) {
                    for (fragmentId in chunk.fragmentIds) {
                        activeSentenceEpochs[fragmentId] = epoch
                    }
                }
                epoch to chunks
            }

            if (chunks.isEmpty()) return@runOn

            logger.i { "ai translate novel: visibleFragments=$visibleFragmentIds, chunks=${chunks.size}" }
            reduce {
                if (!state.translationMode) return@reduce state
                // 单次构建新 map（O(N+K)），避免逐片段 map + 造成 O(K·N) 复制
                state.copy(
                    sentenceTranslations =
                    buildMap(state.sentenceTranslations.size + chunks.sumOf { it.fragmentIds.size }) {
                        putAll(state.sentenceTranslations)
                        for (chunk in chunks) {
                            for (fragmentId in chunk.fragmentIds) {
                                val old = state.sentenceTranslations[fragmentId]
                                if (old == null || old is SentenceTranslationState.Pending) {
                                    put(fragmentId, SentenceTranslationState.Pending)
                                }
                            }
                        }
                    },
                )
            }
            launchNovelTranslationChunks(chunks, LanguageDetector.targetLanguageName(), epoch)
        }
    }

    /** 点击失败/回显片段（无译文可切换）后重试该片段。 */
    @OptIn(OrbitExperimental::class)
    fun retrySentence(fragmentId: Int): Job = intent {
        runOn<NovelDetailViewState.Success> {
            if (!state.translationMode) return@runOn
            val fragment = fragmentByIdCache[fragmentId] ?: return@runOn
            val currentState = state.sentenceTranslations[fragmentId]
            val isEcho =
                currentState is SentenceTranslationState.Complete &&
                    currentState.translatedText == fragment.original
            if (currentState !is SentenceTranslationState.Failed && !isEcho) return@runOn

            val (epoch, chunk) = translationMutex.withLock {
                if (fragmentId in activeSentenceEpochs) return@withLock translationEpoch to null
                val epoch = translationEpoch
                // 单失败片段重试：1 行输入必然 1 行输出——模型无法合并/漏行，
                // 消除整句重译时的 lineCountMismatch 死循环。
                // 不带上下文：避免"句上下文 + 单片段"让模型把整句含义套到该片段上（污染）。
                // retry=true 走调度器专用重试通道且绕过缓存：每次重试都真实发起 API 请求。
                val retryChunk =
                    NovelSentenceChunk(
                        fragmentIds = listOf(fragmentId),
                        sourceText =
                        buildNovelSentenceChunkSource(
                            null,
                            listOf(fragment.translationSource.trim()),
                        ),
                        fragments = listOf(fragment),
                        retry = true,
                    )
                activeSentenceEpochs[fragmentId] = epoch
                epoch to retryChunk
            }

            val retryChunk = chunk ?: return@runOn

            reduce {
                if (!state.translationMode) return@reduce state
                state.copy(
                    sentenceTranslations = state.sentenceTranslations + (fragmentId to SentenceTranslationState.Pending),
                )
            }
            logger.i { "ai translate novel retry: fragment=$fragmentId (sentence ${fragment.sentenceId})" }
            launchNovelTranslationChunks(listOf(retryChunk), LanguageDetector.targetLanguageName(), epoch)
        }
    }

    /**
     * 启动若干句子分段的流式翻译。进入 Pending 后调用；
     * 流式增量按 [STREAM_FLUSH_INTERVAL_MS] 节流合并进状态，避免逐 token 触发整篇 AnnotatedString 重建。
     */
    @OptIn(OrbitExperimental::class)
    private fun launchNovelTranslationChunks(
        chunks: List<NovelSentenceChunk>,
        target: String,
        epoch: Int,
    ): Job = intent {
        runOn<NovelDetailViewState.Success> {
            suspend fun flushChunk(
                chunk: NovelSentenceChunk,
                lines: List<String>?,
                final: Boolean,
            ) {
                reduce {
                    if (!state.translationMode || epoch != translationEpoch) return@reduce state
                    state.copy(
                        sentenceTranslations =
                        mergeFragmentStates(
                            state.sentenceTranslations,
                            chunk,
                            lines,
                            final,
                            chunk.preserve,
                        ),
                    )
                }
            }

            for (chunk in chunks) {
                viewModelScope.launch {
                    var streamFailed = false
                    var accumulated: String? = null
                    var lastFlushTime = 0L
                    try {
                        logger.i {
                            "ai translate novel chunk: ${chunk.fragmentIds.size} fragments, " +
                                "textLen=${chunk.sourceText.length}, retry=${chunk.retry}"
                        }
                        // 重试请求走专用通道（独立信号量），即时发起、不被普通请求排队阻塞
                        val stream =
                            if (chunk.retry) {
                                translateScheduler.translateStreamRetry(chunk.sourceText, target)
                            } else {
                                translateScheduler.translateStream(chunk.sourceText, target)
                            }
                        stream.collect { result ->
                            when (result) {
                                is TranslateResult.Success -> {
                                    accumulated = result.text
                                    val now = Clock.System.now().toEpochMilliseconds()
                                    if (now - lastFlushTime >= STREAM_FLUSH_INTERVAL_MS) {
                                        lastFlushTime = now
                                        // 流式输出：先剥离模型回显的上下文块，
                                        // 再把已闭合的译文行按位置合并上屏
                                        flushChunk(
                                            chunk,
                                            IncrementalSentenceParser.extractSentences(
                                                stripNovelChunkContext(result.text),
                                            ),
                                            final = false,
                                        )
                                    }
                                }

                                is TranslateResult.Failure -> streamFailed = true
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.e(e) { "ai translate novel chunk failed: ${e.message}" }
                        streamFailed = true
                    } finally {
                        val lastAccumulated = accumulated
                        val finalLines =
                            if (streamFailed || lastAccumulated == null) {
                                null
                            } else {
                                SentenceTranslationParser.parseForAlignment(
                                    stripNovelChunkContext(lastAccumulated),
                                )
                            }
                        flushChunk(chunk, finalLines, final = true)
                        // 失败原因日志：便于排查模型行为（空流/超时 vs 行数不匹配 vs 片段级失败）
                        val failReason =
                            when {
                                finalLines == null ->
                                    if (streamFailed) "streamFailedOrEmpty" else "noFinalText"

                                finalLines.filter { it.isNotBlank() }.size != chunk.fragmentIds.size ->
                                    "lineCountMismatch(${finalLines.size}/${chunk.fragmentIds.size})"

                                else -> "perFragment"
                            }
                        logger.i {
                            "ai translate novel chunk finished: valid=${finalLines?.size ?: 0}/${chunk.fragmentIds.size} " +
                                "fragments, reason=$failReason"
                        }
                        translationMutex.withLock {
                            for (fragmentId in chunk.fragmentIds) {
                                if (activeSentenceEpochs[fragmentId] == epoch) {
                                    activeSentenceEpochs.remove(fragmentId)
                                }
                            }
                        }
                        // 失败片段自动重试：最终合并后本 chunk 标为 Failed 的片段逐片段重试一次
                        // （每片段每 epoch 至多一次）。片段级失败（模型合并/漏译/回显等）不影响
                        // 其它片段——部分成功，仅对失败片段单独重试。
                        val failedIds = chunk.fragmentIds.filter { id ->
                            state.sentenceTranslations[id] is SentenceTranslationState.Failed
                        }
                        if (failedIds.isNotEmpty()) {
                            // 锁内完成 epoch/模式预检与重试预算检查，避免与切换翻译模式交错写入竞态
                            val retriable = translationMutex.withLock {
                                if (!state.translationMode || epoch != translationEpoch) {
                                    emptyList()
                                } else {
                                    takeRetryBudget(failedIds, autoRetryCounts)
                                }
                            }
                            if (retriable.isNotEmpty()) {
                                logger.w {
                                    "ai translate novel chunk failed fragments, auto retry: " +
                                        "${retriable.size}/${chunk.fragmentIds.size} reason=$failReason"
                                }
                                // 分批延迟启动，避免系统性失败时重试会话集中涌入调度器队列
                                for ((index, fragmentId) in retriable.withIndex()) {
                                    if (index > 0) delay(AUTO_RETRY_STAGGER_MS)
                                    retrySentence(fragmentId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 切换抽屉内标题+简介的译文显示。 */
    @OptIn(OrbitExperimental::class)
    fun toggleTranslateIntro() = intent {
        runOn<NovelDetailViewState.Success> {
            val current = state
            if (current.introTranslating) return@runOn
            if (current.introTitleTranslation != null || current.introCaptionTranslation != null) {
                reduce {
                    state.copy(
                        introTitleTranslation = null,
                        introCaptionTranslation = null,
                    )
                }
                return@runOn
            }
            if (!isAiTranslateEnabled()) return@runOn

            reduce { state.copy(introTranslating = true) }
            val target = LanguageDetector.targetLanguageName()

            suspend fun translateBlock(text: String): String? {
                val result = translateScheduler.translate(text, target)
                return (result as? TranslateResult.Success)?.text?.translationDisplayTextOrNull()
            }

            val (titleResult, captionSegments) = coroutineScope {
                val titleDeferred = async { translateScheduler.translate(current.novel.title, target) }
                val captionDeferred = async {
                    translateRichSegments(parseHtmlSegments(current.novel.caption)) { text ->
                        translateBlock(text)
                    }
                }
                titleDeferred.await() to captionDeferred.await()
            }

            if (titleResult is TranslateResult.Failure || captionSegments == null) {
                postSideEffect(NovelDetailSideEffect.Toast(getString(Res.string.ai_translate_failed)))
                reduce { state.copy(introTranslating = false) }
            } else {
                reduce {
                    state.copy(
                        introTranslating = false,
                        introTitleTranslation = (titleResult as TranslateResult.Success).text.translationDisplayText(),
                        introCaptionTranslation = captionSegments,
                    )
                }
            }
        }
    }
}

/** 正文节点 → 节点全文映射，供段落上下文 chunk 协议使用（Plain/Title 才参与翻译）。 */
private fun NovelDetailViewState.Success.novelNodeTexts(): Map<Int, String> = nodeMap.mapIndexedNotNull { index, node ->
    when (node) {
        is NovelNodeElement.Plain -> index to node.text
        is NovelNodeElement.Title -> index to node.text
        else -> null
    }
}.toMap()

/**
 * 自动重试预算选择：返回预算内（尚未达到每 epoch 上限）的失败片段，并累加其重试计数。
 *
 * 每片段每 epoch 至多自动重试 [MAX_AUTO_RETRIES_PER_EPOCH] 次，
 * 防止与模型反复不一致时死循环或无限消耗额度。
 */
internal fun takeRetryBudget(
    failedIds: List<Int>,
    retryCounts: MutableMap<Int, Int>,
): List<Int> = failedIds.filter { id ->
    val count = retryCounts.getOrDefault(id, 0)
    if (count < MAX_AUTO_RETRIES_PER_EPOCH) {
        retryCounts[id] = count + 1
        true
    } else {
        false
    }
}

sealed class NovelDetailViewState {
    data class Loading(val text: MutableStateFlow<String>) : NovelDetailViewState()
    data class Error(val cause: String) : NovelDetailViewState()
    data class Success(
        val novel: Novel,
        val core: NovelData,
        val nodeMap: List<NovelNodeElement>,
        val seriesInfo: SeriesInfo? = null,

        val itemInViewLater: Boolean,
        val translationMode: Boolean = false,
        val sentenceTranslations: Map<Int, SentenceTranslationState> = emptyMap(),
        val sentenceIndex: List<NovelSentenceSpan> = emptyList(),
        val introTitleTranslation: String? = null,
        val introCaptionTranslation: List<RichSegment>? = null,
        val introTranslating: Boolean = false,
    ) : NovelDetailViewState()
}

sealed class NovelDetailSideEffect {
    data class Toast(val msg: String) : NovelDetailSideEffect()
    data class NavigateIllustDetail(val route: IllustDetailRoute) : NovelDetailSideEffect()
    data class NavigateToOtherNovel(val id: Long, val seriesInfo: SeriesInfo?) :
        NovelDetailSideEffect()

    data object NavigateBack : NovelDetailSideEffect()
}

/** 流式译文合并进状态的最小间隔：超过该间隔才合并一次，避免逐 token 触发整篇 AnnotatedString 重建。 */
private const val STREAM_FLUSH_INTERVAL_MS = 100L

/** 自动重试启动间隔：分批延迟启动，避免失败会话集中涌入调度器队列。 */
private const val AUTO_RETRY_STAGGER_MS = 250L

/** 每片段每 epoch 的自动重试次数上限。 */
private const val MAX_AUTO_RETRIES_PER_EPOCH = 2
