package top.kagg886.pmf.ui.util

import top.kagg886.pmf.translate.SentenceSegmenter
import top.kagg886.pmf.translate.SentenceTranslationState

/** 句首/句尾标点拆分结果；[core] 才是真正需要交给 AI 翻译的文本。 */
data class SentencePunctuationParts(
    val leading: String,
    val core: String,
    val trailing: String,
)

/** 括号占位保护中的一个 token：翻译完成后把 [token] 还原为 [replacement]。 */
data class BracketToken(
    val token: String,
    val replacement: String,
)

/**
 * 片段核心的括号占位保护结果。
 *
 * [source] 为发给 AI 的文本（括号已被私有区字符 token 替换，纯标点括号组整组隐藏）；
 * [tokens] 为还原表，翻译完成后按序还原。
 */
data class SentenceBracketProtection(
    val source: String,
    val tokens: List<BracketToken>,
)

/**
 * 句内片段：点击切换与翻译状态的最小单位。
 *
 * [original] 为片段原文（含自身首尾标点，来自原始核心按顿号切分）；
 * [sentenceLeading]/[sentenceTrailing] 为所属句子的句首/句尾标点，
 * 供括号保护做跨片段配对（如 `「はい、そうですね」と言った。` 中留在片段内的 `」`）。
 */
data class NovelFragmentSpan(
    val id: Int,
    val sentenceId: Int,
    val nodeIndex: Int,
    val original: String,
    val sentenceLeading: String,
    val sentenceTrailing: String,
) {
    val punctuation: SentencePunctuationParts = splitNovelSentencePunctuation(original)

    /** 核心文本的括号占位保护（token 化核心 + 还原表），索引构建时计算一次。 */
    val protection: SentenceBracketProtection =
        protectSentenceBrackets(punctuation.core, sentenceLeading, sentenceTrailing)

    /** 匹配与发送用的 token 化核心。 */
    val translationSource: String
        get() = protection.source

    val leadingPunctuation: String
        get() = punctuation.leading

    val trailingPunctuation: String
        get() = punctuation.trailing
}

/** 小说正文切句后的一个句子：请求/上下文单位；[fragments] 为句内片段（点击/状态单位）。 */
data class NovelSentenceSpan(
    val id: Int,
    val nodeIndex: Int,
    val original: String,
    val fragments: List<NovelFragmentSpan> = emptyList(),
) {
    val punctuation: SentencePunctuationParts = splitNovelSentencePunctuation(original)

    /** 核心文本的括号占位保护（token 化核心 + 还原表），索引构建时计算一次。 */
    val protection: SentenceBracketProtection =
        protectSentenceBrackets(punctuation.core, punctuation.leading, punctuation.trailing)

    /** 送 AI 的行文本：已剥离句首/句尾标点并对括号做了占位保护（顿号保留，供模型切分）。 */
    val translationSource: String
        get() = protection.source

    val leadingPunctuation: String
        get() = punctuation.leading

    val trailingPunctuation: String
        get() = punctuation.trailing
}

/**
 * 交给 AI 客户端的一个请求分段。
 *
 * [fragmentIds] 为本段包含的全部片段 id（阅读顺序，状态合并与点击切换 key）；
 * [sourceText] 为最终请求文本——可选段落上下文头 + 指令标记 + 每行一个片段，
 * 模型按 `原文片段⇔译文片段` 逐行返回；
 * [fragments] 为片段列表（贪心匹配用）；[contextText] 供测试与日志。
 */
data class NovelSentenceChunk(
    val fragmentIds: List<Int>,
    val sourceText: String,
    val fragments: List<NovelFragmentSpan> = emptyList(),
    val contextText: String = "",
    /** 重试场景下受保护的片段：整句重译失败时保留其旧 Complete 状态，避免连坐。 */
    val preserve: Set<Int> = emptySet(),
)

/** 每个请求分段最多包含的句子数（行）。 */
const val NOVEL_TRANSLATION_CHUNK_SIZE = 6

private val LEADING_PUNCTUATION = "「『（([｛{〈《【〔〖〘〚⌈⌊“‘\"、，".toSet()
private val TRAILING_PUNCTUATION = "」』）)]｝}〉》】〕〗〙〛⌋⌉”’\"。．.！？!?；;…‥～〜♪♡♥☆★※、，".toSet()

/** 句内片段分隔符：顿号/逗号/分号。 */
private val FRAGMENT_SEPARATORS = "、，；;".toSet()

/**
 * 拆分句首/句尾标点，标点不交给 AI 翻译，译文完成后由本地原样拼回。
 *
 * 例如 `「こんにちは……⌋」` 拆为：
 * leading=`「`、core=`こんにちは`、trailing=`……⌋」`。
 * 若整句都是标点（如 `……⌋`），core 为空，调用方应跳过该句。
 */
fun splitNovelSentencePunctuation(sentence: String): SentencePunctuationParts {
    var start = 0
    var end = sentence.length
    while (start < end && sentence[start] in LEADING_PUNCTUATION) {
        start++
    }
    while (end > start && sentence[end - 1] in TRAILING_PUNCTUATION) {
        end--
    }
    return SentencePunctuationParts(
        leading = sentence.substring(0, start),
        core = sentence.substring(start, end),
        trailing = sentence.substring(end),
    )
}

/**
 * 句内片段切分：按顿号/逗号/分号切分，括号组内的分隔符不切
 * （如 `と⌈こんにちは、元気⌋と` 的 `、` 不产生片段边界）。
 */
fun splitSentenceFragments(core: String): List<String> {
    if (core.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    for (c in core) {
        if (c in OPEN_TO_CLOSE) {
            depth++
        } else if (c in CLOSE_TO_OPEN) {
            depth = (depth - 1).coerceAtLeast(0)
        }
        if (c in FRAGMENT_SEPARATORS && depth == 0) {
            val fragment = current.toString().trim()
            if (fragment.isNotEmpty()) result += fragment
            current.clear()
        } else {
            current.append(c)
        }
    }
    val tail = current.toString().trim()
    if (tail.isNotEmpty()) result += tail
    return result
}

/**
 * 核心文本的括号占位保护。
 *
 * - 配对且内文含字母的括号组（对话，如 `⌈こんにちは⌋`）：仅把开/闭括号字符替换为
 *   私有区 token，内文照常交给 AI 翻译，避免模型丢弃引号；
 * - 配对且内文为纯标点/空白的括号组（如 `⌈……⌋`、`（…）`）：整组隐藏，绝不发给 AI；
 * - 与句首被剥离的开括号配对的闭括号、与句尾被剥离的闭括号配对的开括号（如
 *   `⌈こんにちは⌋と彼女は言った。` 中的 `⌋`）：括号字符同样 token 化，防止引号丢失。
 *
 * 私有区字符不在任何标点集合中，句首/句尾标点的剥/拼逻辑不受影响。
 * token 数量有上限（[MAX_BRACKET_TOKENS]）：超限的括号不再保护、原样保留，
 * 避免恶意构造的超长括号句触发 Char 码点越界崩溃。
 */
internal fun protectSentenceBrackets(
    core: String,
    leading: String,
    trailing: String,
): SentenceBracketProtection {
    if (core.isEmpty()) return SentenceBracketProtection("", emptyList())
    val tokens = mutableListOf<BracketToken>()
    val out = StringBuilder(core.length)
    // 句首被剥离的开括号（后剥的先配对）与句尾被剥离的闭括号
    val strippedOpeners = ArrayDeque<Char>()
    for (c in leading) if (c in OPEN_TO_CLOSE) strippedOpeners.addLast(c)
    val strippedClosers = ArrayDeque<Char>()
    for (c in trailing.reversed()) if (c in CLOSE_TO_OPEN) strippedClosers.addLast(c)

    /** 分配 token；超限或与核心文本码点冲突时返回 null，调用方原样保留括号。 */
    fun allocToken(replacement: String): String? {
        if (tokens.size >= MAX_BRACKET_TOKENS) return null
        var codePoint = BRACKET_TOKEN_BASE + tokens.size
        // 跳过核心文本中已出现的私有区码点，避免还原时误替换用户原文
        while (codePoint < BRACKET_TOKEN_MAX && core.indexOf(Char(codePoint)) >= 0) {
            codePoint++
        }
        if (codePoint >= BRACKET_TOKEN_MAX) return null
        val token = BracketToken(Char(codePoint).toString(), replacement)
        tokens.add(token)
        return token.token
    }

    val stack = ArrayDeque<OpenGroup>()
    var i = 0
    while (i < core.length) {
        val c = core[i]
        val close = OPEN_TO_CLOSE[c]
        if (close != null) {
            out.append(c)
            stack.addLast(OpenGroup(c, i, out.length - 1))
            i++
            continue
        }
        val openFor = CLOSE_TO_OPEN[c]
        if (openFor != null) {
            val top = stack.removeLastOrNull()
            if (top != null && top.open == openFor) {
                val inner = out.substring(top.outIndex + 1)
                if (inner.any { it.isLetter() }) {
                    // 文本组：仅把括号字符替换为 token，内文照常翻译
                    val openToken = allocToken(core[top.sourceIndex].toString())
                    val closeToken = allocToken(c.toString())
                    if (openToken != null && closeToken != null) {
                        out.setCharAt(top.outIndex, openToken[0])
                        out.append(closeToken)
                    } else {
                        // 超限保护：原样保留括号（退回不保护行为）
                        out.setCharAt(top.outIndex, core[top.sourceIndex])
                        out.append(c)
                    }
                } else {
                    // 纯标点组：整组隐藏，不发给 AI
                    out.setLength(top.outIndex)
                    val groupToken = allocToken(core.substring(top.sourceIndex, i + 1))
                    out.append(groupToken ?: core.substring(top.sourceIndex, i + 1))
                }
                i++
                continue
            }
            // 与句首剥掉的开括号配对的闭括号（如 `「こんにちは」と彼女は言った。` 的 `」`）
            val strippedOpen = strippedOpeners.lastOrNull()
            if (strippedOpen == openFor) {
                strippedOpeners.removeLast()
                out.append(allocToken(c.toString()) ?: c)
                i++
                continue
            }
            // 未匹配的闭括号：原样保留（作者笔误等）
            out.append(c)
            i++
            continue
        }
        out.append(c)
        i++
    }
    // 栈中未闭合的开括号：与句尾剥掉的闭括号配对（如 `彼は言った、「こんにちは` + trailing `」`）
    while (stack.isNotEmpty()) {
        val top = stack.removeLast()
        val strippedClose = strippedClosers.removeLastOrNull()
        if (strippedClose != null && CLOSE_TO_OPEN[strippedClose] == top.open) {
            val openToken = allocToken(core[top.sourceIndex].toString())
            if (openToken != null) {
                out.setCharAt(top.outIndex, openToken[0])
            }
        }
    }
    return SentenceBracketProtection(out.toString(), tokens)
}

/** 翻译完成后把 token 还原为原始括号字符/括号组。 */
internal fun restoreSentenceBracketTokens(
    text: String,
    protection: SentenceBracketProtection,
): String {
    var result = text
    for (token in protection.tokens) {
        result = result.replace(token.token, token.replacement)
    }
    return result
}

/**
 * 把 AI 返回的片段译文与本地片段首尾标点拼回。
 *
 * 先去掉模型可能自行补出的句末标点/引号，再还原括号 token，最后拼回本地标点。
 */
fun reattachNovelFragmentPunctuation(fragment: NovelFragmentSpan, translated: String): String {
    var text = translated.trim()
    while (text.isNotEmpty() && text.first() in LEADING_PUNCTUATION) {
        text = text.drop(1)
    }
    while (text.isNotEmpty() && text.last() in TRAILING_PUNCTUATION) {
        text = text.dropLast(1)
    }
    text = restoreSentenceBracketTokens(text, fragment.protection)
    return fragment.leadingPunctuation + text + fragment.trailingPunctuation
}

/**
 * 将 Plain/Title 正文节点按句切分、句内按顿号切分为片段并编号。
 *
 * - 非正文节点（插图、分页、链接等）不参与翻译；
 * - 纯标点句子/片段（如 `……⌋`）不交给 AI，渲染时作为原文间隙保留；
 * - 片段为点击切换与翻译状态的最小单位，句子为请求/上下文单位。
 */
fun buildNovelSentenceIndex(nodes: List<NovelNodeElement>): List<NovelSentenceSpan> = buildList {
    var sentenceId = 0
    var fragmentId = 0
    for ((nodeIndex, node) in nodes.withIndex()) {
        val text =
            when (node) {
                is NovelNodeElement.Plain -> node.text
                is NovelNodeElement.Title -> node.text
                else -> null
            }
        if (text == null) continue
        for (sentence in SentenceSegmenter.split(text)) {
            val span = NovelSentenceSpan(sentenceId, nodeIndex, sentence)
            if (span.translationSource.isBlank()) continue
            val fragments = buildList {
                for (raw in splitSentenceFragments(span.punctuation.core)) {
                    val fragment =
                        NovelFragmentSpan(
                            id = fragmentId,
                            sentenceId = sentenceId,
                            nodeIndex = nodeIndex,
                            original = raw,
                            sentenceLeading = span.leadingPunctuation,
                            sentenceTrailing = span.trailingPunctuation,
                        )
                    if (fragment.translationSource.isBlank()) continue
                    add(fragment)
                    fragmentId++
                }
            }
            add(span.copy(fragments = fragments))
            sentenceId++
        }
    }
}

/**
 * 把待翻译片段 id 组装为 AI 请求分段。
 *
 * 片段按阅读顺序归属句子，句子按节点（段落）分组、不跨节点，
 * 每请求最多 [NOVEL_TRANSLATION_CHUNK_SIZE] 句；[withContext] 开启时
 * [sourceText] 携带段落上下文头，为模型提供整段上下文以提升翻译质量。
 *
 * 请求行 = 客户端确定性切好的**片段**（模型只逐行翻译并回显 a 侧，
 * 无需自行按顿号切分——让模型切分不可靠，已由本地实测证实）。
 */
fun buildNovelSentenceChunks(
    fragmentById: Map<Int, NovelFragmentSpan>,
    sentences: Map<Int, NovelSentenceSpan>,
    nodeTexts: Map<Int, String>,
    fragmentIds: Set<Int>,
    maxSentencesPerChunk: Int = NOVEL_TRANSLATION_CHUNK_SIZE,
    withContext: Boolean = true,
): List<NovelSentenceChunk> {
    require(maxSentencesPerChunk > 0) { "maxSentencesPerChunk must be positive" }
    val orderedFragments = fragmentIds.sorted().mapNotNull { fragmentById[it] }
    val orderedSentences =
        orderedFragments.map { it.sentenceId }.distinct().mapNotNull { sentences[it] }
    return orderedSentences
        .groupBy { it.nodeIndex }
        .flatMap { (nodeIndex, nodeSentences) ->
            nodeSentences.chunked(maxSentencesPerChunk).map { chunkSentences ->
                val chunkSentenceIds = chunkSentences.map { it.id }.toSet()
                // 状态/匹配用片段 = 该句全部片段（阅读顺序）；请求行 = 片段核心
                val chunkFragments = orderedFragments.filter { it.sentenceId in chunkSentenceIds }
                val cores = chunkFragments.map { it.translationSource.trim() }
                val context = if (withContext) nodeTexts[nodeIndex].orEmpty() else ""
                NovelSentenceChunk(
                    fragmentIds = chunkFragments.map { it.id },
                    sourceText =
                    buildNovelSentenceChunkSource(context.takeIf { it.isNotBlank() }, cores),
                    fragments = chunkFragments,
                    contextText = context,
                )
            }
        }
        .filter { it.sourceText.isNotBlank() && it.fragmentIds.isNotEmpty() }
}

/**
 * 段落上下文协议：可选上下文块 + 指令标记 + 每行一个片段。
 *
 * 模型按 [NOVEL_CHUNK_MARKER] 的指示，把每句按顿号/逗号切分为片段，
 * 每行返回 `原文片段⇔译文片段`；若模型回显上下文块，由 [stripNovelChunkContext]
 * 在解析前剥离（按标记行精确匹配）。
 */
internal const val NOVEL_CHUNK_MARKER =
    "【待翻译句子：每行一个片段，逐片段翻译，每行只输出该片段的译文，保持顺序与行数，不要输出任何其它内容】 " +
        "Sentences to translate: each line is one fragment, translate it and output one translation per line, " +
        "in the same order and line count, nothing else."

/** 段落上下文最大长度（字符）：超长段落截断，避免长段落多 chunk 重复携带全文放大请求量。 */
internal const val MAX_CONTEXT_CHARS = 1500

internal fun buildNovelSentenceChunkSource(context: String?, cores: List<String>): String = buildString {
    if (!context.isNullOrBlank()) {
        append("【上下文，仅参考，不要翻译或输出】 Context for reference only — do not translate or output it\n")
        append(context.trim().take(MAX_CONTEXT_CHARS))
        append("\n\n")
    }
    append(NOVEL_CHUNK_MARKER)
    append('\n')
    append(cores.joinToString("\n"))
}

/**
 * 剥离模型回显的上下文块：从最后一个完整 [NOVEL_CHUNK_MARKER] 行之后开始返回；
 * 无完整标记则原样返回（模型未回显上下文，或回显被改写）。
 */
internal fun stripNovelChunkContext(raw: String): String {
    var lastMarker = -1
    var searchFrom = 0
    while (true) {
        val index = raw.indexOf(NOVEL_CHUNK_MARKER, searchFrom)
        if (index < 0) break
        lastMarker = index
        searchFrom = index + NOVEL_CHUNK_MARKER.length
    }
    if (lastMarker < 0) return raw
    val lineEnd = raw.indexOf('\n', lastMarker)
    return if (lineEnd < 0) "" else raw.substring(lineEnd + 1)
}

/**
 * 把一段请求的最新翻译结果合并进全局状态 map（单次构建，O(N+K)）。
 *
 * 请求行 = 客户端确定性切好的片段（每行一个），模型按行返回译文——
 * **按行号对齐**：非空行数必须与片段数一致（装饰性空行忽略），
 * 不一致时整 chunk 标 Failed（绝不按索引硬配对部分行，避免译文前移错位），
 * 由 [NovelDetailViewModel] 对失败片段逐片段自动重试。
 *
 * - [lines] 为 null（流失败/无最终文本）：本 chunk 全部标 [SentenceTranslationState.Failed]；
 * - [final] = true：对齐成功则按位置对齐，非空且非回显 → Complete（还原括号 token、拼回标点），
 *   回显（模型按专名规则保持原文）→ 显示原文不标红；
 * - [preserve]：重试场景下保护该集合内的已 Complete 片段——若本 chunk 结果失败，
 *   保留其旧状态，避免整句重译失败时把之前成功的片段一并标红；
 * - [final] = false（流式中间状态）：已闭合的行按位置标 Translating，
 *   其余仍为 Pending 的片段转为 Translating("") 占位，已进入 Translating/Complete 的保持原状。
 */
fun mergeFragmentStates(
    old: Map<Int, SentenceTranslationState>,
    chunk: NovelSentenceChunk,
    lines: List<String>?,
    final: Boolean,
    preserve: Set<Int> = emptySet(),
): Map<Int, SentenceTranslationState> {
    val capacity = old.size + chunk.fragmentIds.size
    if (lines == null) {
        return buildMap(capacity) {
            putAll(old)
            for (fragmentId in chunk.fragmentIds) {
                put(fragmentId, SentenceTranslationState.Failed)
            }
        }
    }
    val nonBlankLines = if (final) lines.filter { it.isNotBlank() } else emptyList()
    val alignable = final && nonBlankLines.size == chunk.fragmentIds.size
    return buildMap(capacity) {
        putAll(old)
        for ((index, fragment) in chunk.fragments.withIndex()) {
            if (final) {
                if (!alignable) {
                    // 保护已成功的片段不被整句重译失败连坐
                    if (fragment.id in preserve && old[fragment.id] is SentenceTranslationState.Complete) {
                        put(fragment.id, old.getValue(fragment.id))
                    } else {
                        put(fragment.id, SentenceTranslationState.Failed)
                    }
                    continue
                }
                val translated = nonBlankLines[index]
                if (translated.isBlank() || isFragmentEcho(fragment, translated)) {
                    // 回显视为成功：显示原文片段，不标红
                    put(fragment.id, SentenceTranslationState.Complete(fragment.original))
                } else {
                    put(
                        fragment.id,
                        SentenceTranslationState.Complete(
                            reattachNovelFragmentPunctuation(fragment, translated),
                        ),
                    )
                }
            } else {
                val translated = lines.getOrNull(index)
                val usable = translated != null && translated.isNotBlank() && !isFragmentEcho(fragment, translated)
                val oldState = old[fragment.id]
                if (oldState is SentenceTranslationState.Complete) {
                    // 已完成的片段保持 Complete，不被流式中间态降级
                } else if (usable) {
                    put(
                        fragment.id,
                        SentenceTranslationState.Translating(
                            reattachNovelFragmentPunctuation(fragment, translated),
                        ),
                    )
                } else if (oldState == null || oldState is SentenceTranslationState.Pending) {
                    put(fragment.id, SentenceTranslationState.Translating(""))
                }
            }
        }
    }
}

/**
 * 回显判定：译文与片段核心归一化后相等（模型按专名规则保持原文，如 `しろは`→`しろは`）。
 */
private fun isFragmentEcho(fragment: NovelFragmentSpan, translated: String): Boolean {
    val source = fragment.translationSource
    if (source.isBlank() || translated.isBlank()) return false
    return normalizeForEcho(source) == normalizeForEcho(translated)
}

private fun normalizeForEcho(text: String): String {
    var s = text.trim()
    while (s.isNotEmpty() && s.first() in LEADING_PUNCTUATION) {
        s = s.drop(1)
    }
    while (s.isNotEmpty() && s.last() in TRAILING_PUNCTUATION) {
        s = s.dropLast(1)
    }
    return s
}

private val BRACKET_PAIRS =
    listOf(
        '⌈' to '⌋', '⌊' to '⌉',
        '「' to '」', '『' to '』',
        '〔' to '〕', '【' to '】', '〖' to '〗',
        '〈' to '〉', '《' to '》',
        '（' to '）', '(' to ')',
    )
private val OPEN_TO_CLOSE = BRACKET_PAIRS.toMap()
private val CLOSE_TO_OPEN = BRACKET_PAIRS.associate { (open, close) -> close to open }

/** 括号占位保护使用的 token 起始码点（私有区，不在任何标点集合中）。 */
private const val BRACKET_TOKEN_BASE = 0xE000

/** token 码点上限（私有区 U+E000..U+F8FF 内），防超长括号句触发 Char 构造越界。 */
private const val BRACKET_TOKEN_MAX = 0xF000

/** 单句最多分配的括号 token 数；超限的括号不再保护、原样保留。 */
private const val MAX_BRACKET_TOKENS = 256

private class OpenGroup(
    val open: Char,
    val sourceIndex: Int,
    val outIndex: Int,
)
