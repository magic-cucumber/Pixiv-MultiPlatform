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
import top.kagg886.pmf.translate.PageTranslationState
import top.kagg886.pmf.translate.SentencePair
import top.kagg886.pmf.translate.SentenceSegmenter
import top.kagg886.pmf.translate.SentenceTranslationParser
import top.kagg886.pmf.translate.TranslateResult
import top.kagg886.pmf.translate.TranslateScheduler
import top.kagg886.pmf.translate.isAiTranslateEnabled
import top.kagg886.pmf.translate.isIdentityTranslation
import top.kagg886.pmf.translate.translationDisplayText
import top.kagg886.pmf.translate.translationDisplayTextOrNull
import top.kagg886.pmf.ui.route.main.detail.illust.IllustDetailRoute
import top.kagg886.pmf.ui.util.NovelNodeElement
import top.kagg886.pmf.ui.util.RichSegment
import top.kagg886.pmf.ui.util.buildPageIndex
import top.kagg886.pmf.ui.util.buildPageText
import top.kagg886.pmf.ui.util.container
import top.kagg886.pmf.ui.util.parseHtmlSegments
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
    private val activePages = mutableSetOf<Int>()

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

        reduce {
            NovelDetailViewState.Success(
                detail,
                content,
                nodeMap.toList().sortedBy { it.first }.map { it.second },
                seriesInfo = seriesInfo,
                itemInViewLater = itemInViewLater,
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

    /** 切换正文翻译模式；开启后由 [onVisiblePagesChanged] 懒触发可见页翻译。 */
    @OptIn(OrbitExperimental::class)
    fun toggleTranslateNovel() = intent {
        runOn<NovelDetailViewState.Success> {
            if (state.translationMode) {
                reduce {
                    state.copy(
                        translationMode = false,
                        pageTranslations = emptyMap(),
                        currentPage = -1,
                    )
                }
                return@runOn
            }
            if (!isAiTranslateEnabled()) return@runOn
            reduce { state.copy(translationMode = true, currentPage = -1) }
        }
    }

    /**
     * 可见页懒翻译：仅翻译"当前页 + 延后 [LOOKAHEAD_PAGES] 页"，
     * 进行中的页由 [activePages] 去重；失败页标 [PageTranslationState.Failed]（淡红原文）且不自动重试。
     *
     * 翻译在 [viewModelScope] 中异步进行、不阻塞本次 intent，
     * 避免滚动触发的新请求被上一批翻译串行等待而影响操作。
     */
    @OptIn(OrbitExperimental::class)
    fun onVisiblePagesChanged(visiblePages: Set<Int>) = intent {
        runOn<NovelDetailViewState.Success> {
            val current = state
            if (!current.translationMode) return@runOn
            val pageIndex = buildPageIndex(current.nodeMap)

            // 当前页 = 可见且含文本的页中最靠前的一页；无文本页不参与翻译也不显示加载态
            val currentPage = visiblePages
                .filter { buildPageText(current.nodeMap, pageIndex, it).isNotBlank() }
                .minOrNull() ?: -1

            val toStart = translationMutex.withLock {
                if (currentPage < 0) {
                    emptyList()
                } else {
                    (currentPage..currentPage + LOOKAHEAD_PAGES)
                        .filter { page ->
                            val translation = current.pageTranslations[page]
                            page !in activePages &&
                                (translation == null || translation is PageTranslationState.Pending) &&
                                buildPageText(current.nodeMap, pageIndex, page).isNotBlank()
                        }
                        .also { activePages += it }
                }
            }

            if (currentPage != current.currentPage) {
                reduce {
                    if (!state.translationMode) return@reduce state
                    state.copy(currentPage = currentPage)
                }
            }

            if (toStart.isEmpty()) return@runOn

            logger.i { "ai translate novel: currentPage=$currentPage, toStart=$toStart" }
            val target = LanguageDetector.targetLanguageName()
            for (page in toStart) {
                viewModelScope.launch {
                    var pageFailed = false
                    try {
                        val pageText = buildPageText(current.nodeMap, pageIndex, page)
                        var lastText: String? = null
                        logger.i { "ai translate novel: page=$page start, textLen=${pageText.length}" }
                        reduce {
                            if (!state.translationMode) return@reduce state
                            state.copy(
                                pageTranslations =
                                state.pageTranslations + (page to PageTranslationState.Translating("")),
                            )
                        }
                        translateScheduler.translateStream(pageText, target).collect { result ->
                            when (result) {
                                is TranslateResult.Success -> {
                                    lastText = result.text
                                    // 流式输出是残缺 JSON：只把已闭合的句子提取上屏，
                                    // 绝不把原始/未闭合的 JSON 显示给用户
                                    val streamedSentences =
                                        IncrementalSentenceParser.extractSentences(result.text)
                                            .joinToString("\n")
                                    reduce {
                                        if (!state.translationMode) return@reduce state
                                        state.copy(
                                            pageTranslations =
                                            state.pageTranslations +
                                                (page to PageTranslationState.Translating(streamedSentences)),
                                        )
                                    }
                                }

                                is TranslateResult.Failure -> pageFailed = true
                            }
                        }
                        val finalText = lastText
                        if (pageFailed || finalText == null) {
                            logger.w { "ai translate novel: page=$page failed (stream failure)" }
                            reduce {
                                if (!state.translationMode) return@reduce state
                                state.copy(
                                    pageTranslations = state.pageTranslations + (page to PageTranslationState.Failed),
                                )
                            }
                        } else {
                            val originalSentences = SentenceSegmenter.split(pageText)
                            // 严格解析：非 JSON / 空数组 / 回显原文一律视为失败，
                            // 避免把原始 JSON 或原文当作译文展示
                            val translatedSentences = SentenceTranslationParser.parseStrict(finalText)
                            if (translatedSentences == null ||
                                isIdentityTranslation(pageText, translatedSentences.joinToString("\n"))
                            ) {
                                logger.w { "ai translate novel: page=$page failed (unparseable or identity)" }
                                reduce {
                                    if (!state.translationMode) return@reduce state
                                    state.copy(
                                        pageTranslations = state.pageTranslations + (page to PageTranslationState.Failed),
                                    )
                                }
                            } else {
                                val pairs =
                                    SentenceTranslationParser.align(originalSentences, translatedSentences)
                                        ?: listOf(SentencePair(pageText, translatedSentences.joinToString("\n")))
                                logger.i { "ai translate novel: page=$page success, pairs=${pairs.size}" }
                                reduce {
                                    if (!state.translationMode) return@reduce state
                                    state.copy(
                                        pageTranslations =
                                        state.pageTranslations + (page to PageTranslationState.Complete(pairs)),
                                    )
                                }
                            }
                        }
                    } finally {
                        translationMutex.withLock { activePages -= page }
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
        val pageTranslations: Map<Int, PageTranslationState> = emptyMap(),
        // 当前视口最靠前的含文本页号；-1 表示尚未确定（供加载覆盖层判断）
        val currentPage: Int = -1,
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

/** 懒翻译时在当前页基础上向后预取的页数。 */
private const val LOOKAHEAD_PAGES = 2
