package top.kagg886.pmf.ui.util

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.toUri
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.flow.distinctUntilChanged
import top.kagg886.pixko.module.illust.Illust
import top.kagg886.pixko.module.illust.IllustImagesType
import top.kagg886.pixko.module.illust.get
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.Platform
import top.kagg886.pmf.backend.currentPlatform
import top.kagg886.pmf.translate.SentenceTranslationState
import top.kagg886.pmf.ui.component.ImagePreviewer
import top.kagg886.pmf.ui.component.translate.failedTranslationColor
import top.kagg886.pmf.ui.component.translate.translatingTranslationColor

sealed interface NovelNodeElement {
    data class Plain(val text: String) : NovelNodeElement
    data class JumpUri(val text: String, val uri: String) : NovelNodeElement
    data class Notation(val text: String, val notation: String) : NovelNodeElement
    data class UploadImage(val url: String, val size: androidx.compose.ui.geometry.Size) : NovelNodeElement
    data class PixivImage(val illust: Illust) : NovelNodeElement
    data class Title(val text: String) : NovelNodeElement
    data class NewPage(val index: Int) : NovelNodeElement
    data class JumpPage(val page: Int) : NovelNodeElement
}

fun AnnotatedString.Builder.withClickable(
    colors: ColorScheme,
    text: String,
    onClick: () -> Unit,
) {
    withLink(
        link = LinkAnnotation.Clickable(
            tag = Random(Clock.System.now().toEpochMilliseconds()).nextInt().toString(),
            styles = TextLinkStyles(
                hoveredStyle = SpanStyle(
                    color = colors.primaryContainer,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
            linkInteractionListener = {
                onClick()
            },
        ),
    ) {
        this.append(text)
    }
}

fun AnnotatedString.Builder.withLink(
    colors: ColorScheme,
    link: String,
    display: String = link,
) {
    withLink(
        link = LinkAnnotation.Url(
            url = link,
            styles = TextLinkStyles(
                style = SpanStyle(color = colors.primary),
                hoveredStyle = SpanStyle(
                    color = colors.primaryContainer,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
        ),
    ) {
        this.append(display)
    }
}

@Composable
fun RichText(
    state: List<NovelNodeElement>,
    modifier: Modifier = Modifier,
    onIllustClick: (Illust) -> Unit = {},
    sentenceTranslations: Map<Int, SentenceTranslationState> = emptyMap(),
    scrollState: ScrollState? = null,
    viewportHeightPx: Int = 0,
    onVisibleSentencesChanged: (Set<Int>) -> Unit = {},
    onRetrySentence: (Int) -> Unit = {},
    translationMode: Boolean = false,
) {
    val previews = remember {
        state.filterIsInstance<NovelNodeElement.UploadImage>().map { it.url }
    }
    var previewIndex by remember { mutableStateOf(-1) }
    if (previewIndex != -1) {
        ImagePreviewer(
            data = previews.map(String::toUri),
            onDismiss = { previewIndex = -1 },
            startIndex = previewIndex,
        )
    }
    val textSize = remember {
        AppConfig.textSize.sp
    }
    val defaultTextStyle = LocalTextStyle.current

    val density = LocalDensity.current
    val sentenceIndex = remember(state) {
        buildNovelSentenceIndex(state)
    }
    // 折叠后的节点文本与片段位置一起缓存，翻译路径渲染时直接复用，避免每次流式 flush 重复折叠
    val fragmentsByNode = remember(sentenceIndex, state) {
        sentenceIndex
            .flatMap { it.fragments }
            .groupBy { it.nodeIndex }
            .mapValues { (nodeIndex, fragments) ->
                val text =
                    when (val node = state.getOrNull(nodeIndex)) {
                        is NovelNodeElement.Plain -> node.text
                        is NovelNodeElement.Title -> node.text
                        else -> ""
                    }
                val prepared = collapseBigLines(text)
                PositionedNode(prepared, positionNovelFragments(prepared, fragments))
            }
    }
    var screenWidth by remember {
        mutableStateOf(0.sp)
    }
    var layoutResult by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }
    val inlineNode = remember(state, screenWidth, onIllustClick) {
        buildMap {
            for (i in state) {
                when (i) {
                    is NovelNodeElement.PixivImage -> {
                        put(
                            // iW   sW
                            // -- = --
                            // iH   ??
                            "pixiv_${i.illust.id}",
                            InlineTextContent(
                                Placeholder(
                                    screenWidth,
                                    with(density) { ((i.illust.height * (screenWidth * 0.85).toPx().absoluteValue / i.illust.width)).toSp() },
                                    PlaceholderVerticalAlign.Center,
                                ),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = i.illust.contentImages[IllustImagesType.LARGE, IllustImagesType.MEDIUM]?.get(0),
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                            .aspectRatio(i.illust.width.toFloat() / i.illust.height)
                                            .clickable { onIllustClick(i.illust) },
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }

                    is NovelNodeElement.UploadImage -> {
                        put(
                            "upload_${i.url.hashCode()}",
                            InlineTextContent(
                                Placeholder(
                                    screenWidth,
                                    with(density) { ((i.size.height * (screenWidth * 0.85).toPx().absoluteValue / i.size.width)).toSp() },
                                    PlaceholderVerticalAlign.Center,
                                ),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = i.url,
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .aspectRatio(i.size.width / i.size.height)
                                            .clickable { previewIndex = previews.indexOf(i.url) },
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }

                    is NovelNodeElement.NewPage -> {
                        put(
                            "page_${i.index}",
                            InlineTextContent(Placeholder(screenWidth, 2.sp, PlaceholderVerticalAlign.Top)) {
                                HorizontalDivider(Modifier.fillMaxWidth())
                            },
                        )
                    }

                    else -> continue
                }
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    val failureColor = failedTranslationColor()
    val translatingColor = translatingTranslationColor()
    // 关闭翻译模式时清空句级原文切换状态；重新开启后默认展示译文。
    var toggledSentences by remember(state, translationMode) {
        mutableStateOf(emptySet<Int>())
    }

    val annotatedState =
        remember(
            state,
            sentenceTranslations,
            toggledSentences,
            screenWidth,
            colors,
            failureColor,
            translatingColor,
            translationMode,
            onRetrySentence,
        ) {
            val builder = AnnotatedString.Builder()
            val fragmentRanges = mutableListOf<FragmentRange>()

            fun AnnotatedString.Builder.appendFragment(
                fragment: NovelFragmentSpan,
                fragmentState: SentenceTranslationState?,
            ) {
                val showOriginal = fragment.id in toggledSentences
                val translatedText =
                    when (fragmentState) {
                        is SentenceTranslationState.Complete -> fragmentState.translatedText
                        is SentenceTranslationState.Translating -> fragmentState.translatedText
                        else -> ""
                    }
                val displayText =
                    if (showOriginal || translatedText.isBlank()) {
                        fragment.original
                    } else {
                        translatedText
                    }
                val displayColor =
                    when {
                        showOriginal -> null

                        fragmentState is SentenceTranslationState.Failed -> failureColor

                        // Pending/Translating：正在请求或流式翻译中，着色标识"未就绪"，
                        // 与已翻译/原文区分（懒加载时整页会呈现"已译 + 加载中"的混合）
                        fragmentState is SentenceTranslationState.Pending ||
                            fragmentState is SentenceTranslationState.Translating -> translatingColor

                        else -> null
                    }
                val start = builder.length
                // 回显片段（译文 == 原文，如专名被模型保持）：显示原文，点击动作 = 重试
                // （没有译文可切换，重试给用户"再试一次拿真译文"的出路）
                val isEcho =
                    fragmentState is SentenceTranslationState.Complete &&
                        fragmentState.translatedText == fragment.original
                val clickable =
                    fragmentState is SentenceTranslationState.Complete ||
                        (fragmentState is SentenceTranslationState.Translating && translatedText.isNotBlank())
                if (fragmentState is SentenceTranslationState.Failed) {
                    // 失败片段显示淡红原文，点击后重新发起翻译
                    withStyle(SpanStyle(color = displayColor ?: failureColor)) {
                        withClickable(colors, displayText) {
                            onRetrySentence(fragment.id)
                        }
                    }
                } else if (isEcho) {
                    // 回显不标红，但点击重试（而非切换——切换无可见变化）
                    withClickable(colors, displayText) {
                        onRetrySentence(fragment.id)
                    }
                } else if (clickable) {
                    if (displayColor != null) {
                        withStyle(SpanStyle(color = displayColor)) {
                            withClickable(colors, displayText) {
                                toggledSentences =
                                    if (fragment.id in toggledSentences) {
                                        toggledSentences - fragment.id
                                    } else {
                                        toggledSentences + fragment.id
                                    }
                            }
                        }
                    } else {
                        withClickable(colors, displayText) {
                            toggledSentences =
                                if (fragment.id in toggledSentences) {
                                    toggledSentences - fragment.id
                                } else {
                                    toggledSentences + fragment.id
                                }
                        }
                    }
                } else {
                    if (displayColor != null) {
                        withStyle(SpanStyle(color = displayColor)) {
                            append(displayText)
                        }
                    } else {
                        append(displayText)
                    }
                }
                fragmentRanges += FragmentRange(start, builder.length, fragment.id)
            }

            fun AnnotatedString.Builder.appendPositionedFragmentsWithAutoTypo(
                sourceText: String,
                fragments: List<PositionedNovelFragment>,
            ) {
                val isAndroid = currentPlatform is Platform.Android
                // 与原文 autoTypo 路径共用同一行预处理（折叠空行、丢弃空行、trim），保证排版一致
                for (line in autoTypoLines(sourceText)) {
                    if (!isAndroid) {
                        // 与原文 autoTypo 排版一致：桌面端每行前保留 8 个空格缩进
                        append('\n')
                        append("        ")
                    }
                    var cursor = line.start
                    var appendedFragment = false
                    for (position in fragments) {
                        if (position.end <= line.start) continue
                        if (position.start >= line.end) break
                        if (appendedFragment) {
                            val gapEnd = minOf(position.start, line.end)
                            if (gapEnd > cursor) {
                                append(sourceText.substring(cursor, gapEnd))
                            }
                        } else {
                            // 去掉行首空白，但保留行首的轻小说装饰标点（如 ……⌋）
                            val leadEnd = minOf(position.start, line.end)
                            val lead = sourceText.substring(line.start, leadEnd).trimStart()
                            if (lead.isNotEmpty()) {
                                append(lead)
                            }
                        }
                        appendFragment(position.fragment, sentenceTranslations[position.fragment.id])
                        cursor = maxOf(position.end, line.start)
                        appendedFragment = true
                    }
                    if (!appendedFragment) {
                        append(line.text)
                    } else if (cursor < line.end) {
                        append(sourceText.substring(cursor, line.end))
                    }
                    append('\n')
                }
            }

            fun AnnotatedString.Builder.appendOriginalText(text: String, title: Boolean) {
                if (title) {
                    appendLine()
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp))) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = textSize * 1.5)) {
                            append(text)
                        }
                    }
                    appendLine()
                    return
                }

                if (AppConfig.autoTypo) {
                    with(collapseBigLines(text).lines()) {
                        filter { it.isNotBlank() }.map {
                            if (currentPlatform is Platform.Android) {
                                return@map it.trim()
                            }
                            // 8个空格
                            return@map "\n        ${it.trim()}"
                        }.forEach {
                            appendLine(it)
                        }
                    }
                    return
                }
                append(text)
            }

            fun AnnotatedString.Builder.appendTextNode(text: String, index: Int, title: Boolean) {
                val positioned = fragmentsByNode[index]
                if (translationMode && positioned != null && positioned.sentences.isNotEmpty()) {
                    // 复用 remember 中已折叠的文本与片段位置，避免每次流式 flush 重复折叠
                    val prepared = positioned.text
                    val fragments = positioned.sentences
                    fun appendPositionedFragments() {
                        var cursor = 0
                        for (position in fragments) {
                            if (position.start > cursor) {
                                append(prepared.substring(cursor, position.start))
                            }
                            appendFragment(position.fragment, sentenceTranslations[position.fragment.id])
                            cursor = position.end
                        }
                        if (cursor < prepared.length) {
                            append(prepared.substring(cursor))
                        }
                    }

                    if (title) {
                        appendLine()
                        withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp))) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = textSize * 1.5)) {
                                appendPositionedFragments()
                            }
                        }
                        appendLine()
                    } else if (AppConfig.autoTypo) {
                        appendPositionedFragmentsWithAutoTypo(prepared, fragments)
                    } else {
                        appendPositionedFragments()
                    }
                    return
                }
                appendOriginalText(text, title)
            }

            for ((index, i) in state.withIndex()) {
                when (i) {
                    is NovelNodeElement.JumpPage -> {}

                    is NovelNodeElement.JumpUri -> {
                        builder.withLink(
                            colors = colors,
                            link = i.uri,
                            display = i.text,
                        )
                    }

                    is NovelNodeElement.NewPage -> {
                        builder.withStyle(ParagraphStyle(lineHeight = screenWidth, textIndent = TextIndent(firstLine = 0.sp))) {
                            builder.appendInlineContent("page_${i.index}")
                        }
                    }

                    is NovelNodeElement.Notation -> {
                        builder.append(i.notation)
                    }

                    is NovelNodeElement.UploadImage -> {
                        builder.withStyle(ParagraphStyle(lineHeight = screenWidth, textIndent = TextIndent(firstLine = 0.sp))) {
                            builder.appendInlineContent("upload_${i.url.hashCode()}")
                        }
                    }

                    is NovelNodeElement.PixivImage -> {
                        builder.withStyle(ParagraphStyle(lineHeight = screenWidth, textIndent = TextIndent(firstLine = 0.sp))) {
                            builder.appendInlineContent("pixiv_${i.illust.id}")
                        }
                    }

                    is NovelNodeElement.Plain -> {
                        builder.appendTextNode(i.text, index, title = false)
                    }

                    is NovelNodeElement.Title -> {
                        builder.appendTextNode(i.text, index, title = true)
                    }
                }
            }
            builder.toAnnotatedString() to fragmentRanges
        }
    val annotateString = annotatedState.first
    val fragmentRanges = annotatedState.second

    val currentOnVisibleSentencesChanged by rememberUpdatedState(onVisibleSentencesChanged)
    val currentViewportHeightPx by rememberUpdatedState(viewportHeightPx)
    // 片段区间会随译文长度变化而变化；通过 rememberUpdatedState 在 snapshotFlow 内追踪读取，
    // 避免把 fragmentRanges 放进 LaunchedEffect key 导致每次流式更新都重启 effect。
    val currentFragmentRanges by rememberUpdatedState(fragmentRanges)
    LaunchedEffect(scrollState, translationMode) {
        val scroll = scrollState ?: return@LaunchedEffect
        snapshotFlow {
            val layout = layoutResult
            val viewport = currentViewportHeightPx
            val ranges = currentFragmentRanges
            if (layout == null || layout.lineCount <= 0 || viewport <= 0 || !translationMode) {
                emptySet<Int>()
            } else {
                val firstLine = layout.getLineForVerticalPosition(scroll.value.toFloat())
                val visibleEndLine = layout.getLineForVerticalPosition((scroll.value + viewport).toFloat())
                // "当前页 + 延后 2 页"换算为行：当前视口行数 * 3 的窗口
                val window = calculateTranslationLineWindow(firstLine, visibleEndLine, layout.lineCount)
                val windowStart = layout.getLineStart(window.first)
                val windowEnd = layout.getLineEnd(window.last)
                buildSet {
                    // 区间按正文顺序递增；越过窗口终点后可提前结束，避免长文每次滚动都全量扫描
                    for (range in ranges) {
                        if (range.end <= range.start) continue
                        if (range.start > windowEnd) break
                        if (range.end >= windowStart) {
                            add(range.fragmentId)
                        }
                    }
                }
            }
        }
            .distinctUntilChanged()
            .collect {
                currentOnVisibleSentencesChanged(it)
            }
    }

    val style = remember {
        when {
            AppConfig.autoTypo -> TextStyle(
                textIndent = TextIndent(firstLine = textSize * 2),
                fontSize = textSize,
                lineHeight = 1.5.em,
            )

            else -> defaultTextStyle.copy(
                fontSize = textSize,
                lineHeight = 1.5.em,
            )
        }
    }
    Text(
        text = annotateString,
        inlineContent = inlineNode,
        fontSize = textSize,
        style = style,
        onTextLayout = {
            layoutResult = it
        },
        modifier = modifier.onGloballyPositioned {
            screenWidth = with(density) {
                val offset = it.positionInParent()
                (it.boundsInParent().width - offset.x).toSp()
            }
        },
    )
}

private data class FragmentRange(
    val start: Int,
    val end: Int,
    val fragmentId: Int,
)

internal data class PositionedNovelFragment(
    val fragment: NovelFragmentSpan,
    val start: Int,
    val end: Int,
)

/** 节点级翻译渲染缓存：折叠后的节点文本与片段位置（避免每次流式 flush 重复折叠）。 */
internal data class PositionedNode(
    val text: String,
    val sentences: List<PositionedNovelFragment>,
)

/** 把切块结果定位回原文，保证翻译模式未翻译/占位时仍按原文格式渲染。 */
internal fun positionNovelFragments(
    text: String,
    fragments: List<NovelFragmentSpan>,
): List<PositionedNovelFragment> {
    val result = mutableListOf<PositionedNovelFragment>()
    var searchFrom = 0
    for (fragment in fragments) {
        val start = text.indexOf(fragment.original, searchFrom)
        if (start < 0) return emptyList()
        val end = start + fragment.original.length
        result += PositionedNovelFragment(fragment, start, end)
        searchFrom = end
    }
    return result
}

/** 折叠 3 个及以上连续换行（含周围空白）为单个换行，用于 autoTypo 排版。 */
internal fun collapseBigLines(text: String): String = text.replace(Regex("(\\s*\\r?\\n){2,}\\n"), "\n")

/** autoTypo 行预处理结果：[text] 为 trim 后的行文本，[start]/[end] 为行在原文中的偏移。 */
internal data class AutoTypoLine(
    val text: String,
    val start: Int,
    val end: Int,
)

/**
 * autoTypo 模式的共享行预处理：折叠 3+ 连续换行、丢弃空行、逐行 trim。
 *
 * 原文渲染路径（[RichText] 的 appendOriginalText）与翻译渲染路径
 * （appendPositionedSentencesWithAutoTypo）共用本函数，保证两种模式下
 * 行间距、段落空行与缩进完全一致；偏移用于把句子定位回原文。
 */
internal fun autoTypoLines(sourceText: String): List<AutoTypoLine> {
    val result = mutableListOf<AutoTypoLine>()
    var start = 0
    while (start <= sourceText.length) {
        val newlineIndex = sourceText.indexOf('\n', start)
        val end = if (newlineIndex < 0) sourceText.length else newlineIndex
        val trimmed = sourceText.substring(start, end).trim()
        if (trimmed.isNotEmpty()) {
            result += AutoTypoLine(trimmed, start, end)
        }
        if (newlineIndex < 0) break
        start = newlineIndex + 1
    }
    return result
}

private const val TRANSLATION_LOOKAHEAD_PAGES = 2

/**
 * 把"当前页 + 延后 [lookaheadPages] 页"换算为文本行区间。
 *
 * 小说阅读没有固定页单位，一页按当前视口行数估算：起点为当前可见首行，
 * 终点为当前可见末行再加两页（2 * 视口行数）的行，并夹在合法行号内。
 */
internal fun calculateTranslationLineWindow(
    firstLine: Int,
    visibleEndLine: Int,
    lineCount: Int,
    lookaheadPages: Int = TRANSLATION_LOOKAHEAD_PAGES,
): IntRange {
    require(lineCount > 0) { "lineCount must be positive" }
    require(lookaheadPages >= 0) { "lookaheadPages must be non-negative" }
    val start = firstLine.coerceIn(0, lineCount - 1)
    val visibleEnd = visibleEndLine.coerceIn(start, lineCount - 1)
    val viewportLines = (visibleEnd - start + 1).coerceAtLeast(1)
    val end = (visibleEnd + viewportLines * lookaheadPages).coerceAtMost(lineCount - 1)
    return start..end
}

/** 把富文本片段还原为带链接/加粗/换行的 [AnnotatedString]，链接可点击。 */
fun buildRichAnnotatedString(
    segments: List<RichSegment>,
    colors: ColorScheme,
): AnnotatedString = buildAnnotatedString {
    for (segment in segments) {
        when (segment) {
            is RichSegment.Text -> append(segment.text)

            is RichSegment.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(segment.text)
            }

            is RichSegment.Link -> withLink(colors, segment.url, segment.display)

            is RichSegment.Break -> appendLine()
        }
    }
}

@Composable
fun HTMLRichText(
    html: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    val scheme = MaterialTheme.colorScheme
    val segments = remember(html) { parseHtmlSegments(html) }
    Text(
        buildRichAnnotatedString(segments, scheme),
        style = style,
        color = color,
        modifier = modifier,
    )
}
