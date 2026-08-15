package top.kagg886.pmf.ui.util

import top.kagg886.pmf.translate.SentenceSegmenter
import top.kagg886.pmf.translate.SentenceTranslationState
import top.kagg886.pmf.translate.isIdentityTranslation

/** 句首/句尾标点拆分结果；[core] 才是真正需要交给 AI 翻译的文本。 */
data class SentencePunctuationParts(
    val leading: String,
    val core: String,
    val trailing: String,
)

/** 小说正文切句后的一个句子：全局 [id] 同时作为翻译状态与点击切换的 key。 */
data class NovelSentenceSpan(
    val id: Int,
    val nodeIndex: Int,
    val localIndex: Int,
    val original: String,
) {
    val punctuation: SentencePunctuationParts = splitNovelSentencePunctuation(original)

    /** 送 AI 的文本：已剥离句首/句尾标点，译文完成后再把标点原样拼回。 */
    val translationSource: String
        get() = punctuation.core

    val leadingPunctuation: String
        get() = punctuation.leading

    val trailingPunctuation: String
        get() = punctuation.trailing
}

/**
 * 交给 AI 客户端的一个句子分段。
 *
 * [sentenceIds] 为本段包含的全局句 id；[sourceText] 将剥离标点后的句子核心按行拼接，
 * 配合"每行一句"的 prompt 协议请求 AI 按行返回译文。
 */
data class NovelSentenceChunk(
    val sentenceIds: List<Int>,
    val sourceText: String,
    val sentences: List<NovelSentenceSpan> = emptyList(),
)

/** 每个翻译分段最多包含的句子数。 */
const val NOVEL_TRANSLATION_CHUNK_SIZE = 8

private val LEADING_PUNCTUATION = "「『（([｛{〈《【〔〖〘〚⌈⌊“‘\"、，".toSet()
private val TRAILING_PUNCTUATION = "」』）)]｝}〉》】〕〗〙〛⌋⌉”’\"。．.！？!?；;…‥～〜♪♡♥☆★※、，".toSet()

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
 * 将 Plain/Title 正文节点按句切分并编号。
 *
 * - 非正文节点（插图、分页、链接等）不参与翻译；
 * - 纯标点片段（如轻小说常用的 `……⌋`）不交给 AI，渲染时作为原文间隙保留。
 */

/**
 * 把 AI 返回的核心译文与本地保存的句首/句尾标点拼回。
 *
 * 先去掉模型可能自行补出的句末标点/引号，避免与本地标点重复。
 */
fun reattachNovelSentencePunctuation(span: NovelSentenceSpan, translated: String): String {
    var text = translated.trim()
    while (text.isNotEmpty() && text.first() in LEADING_PUNCTUATION) {
        text = text.drop(1)
    }
    while (text.isNotEmpty() && text.last() in TRAILING_PUNCTUATION) {
        text = text.dropLast(1)
    }
    return span.leadingPunctuation + text + span.trailingPunctuation
}

fun buildNovelSentenceIndex(nodes: List<NovelNodeElement>): List<NovelSentenceSpan> = buildList {
    var id = 0
    for ((nodeIndex, node) in nodes.withIndex()) {
        val text =
            when (node) {
                is NovelNodeElement.Plain -> node.text
                is NovelNodeElement.Title -> node.text
                else -> null
            }
        if (text == null) continue
        for ((localIndex, sentence) in SentenceSegmenter.split(text).withIndex()) {
            val span = NovelSentenceSpan(id, nodeIndex, localIndex, sentence)
            if (span.translationSource.isBlank()) continue
            add(span)
            id++
        }
    }
}

/**
 * 把待翻译句 id 组装为 AI 请求分段。
 *
 * 分段按全局 id 升序（即阅读顺序）排列，每段最多 [NOVEL_TRANSLATION_CHUNK_SIZE] 句；
 * 句子之间的换行由 prompt 约束为句边界。
 */
fun buildNovelSentenceChunks(
    index: List<NovelSentenceSpan>,
    sentenceIds: Set<Int>,
    maxSentencesPerChunk: Int = NOVEL_TRANSLATION_CHUNK_SIZE,
): List<NovelSentenceChunk> = buildNovelSentenceChunks(index.associateBy { it.id }, sentenceIds, maxSentencesPerChunk)

/** [buildNovelSentenceChunks] 的 byId 版本：调用方已有 [Map]<id, span> 时避免每次 O(index) 重建。 */
fun buildNovelSentenceChunks(
    byId: Map<Int, NovelSentenceSpan>,
    sentenceIds: Set<Int>,
    maxSentencesPerChunk: Int = NOVEL_TRANSLATION_CHUNK_SIZE,
): List<NovelSentenceChunk> {
    require(maxSentencesPerChunk > 0) { "maxSentencesPerChunk must be positive" }
    return sentenceIds
        .sorted()
        .mapNotNull { byId[it] }
        .chunked(maxSentencesPerChunk)
        .map { sentences ->
            NovelSentenceChunk(
                sentenceIds = sentences.map { it.id },
                sourceText = sentences.joinToString("\n") { it.translationSource.trim() }.trim(),
                sentences = sentences,
            )
        }
        .filter { it.sourceText.isNotBlank() }
}

/**
 * 把一段句子的最新翻译结果合并进全局状态 map（单次构建，O(N+K)）。
 *
 * - [lines] 为 null（流失败/无最终文本）：本 chunk 全部标 [SentenceTranslationState.Failed]；
 * - [final] = true：按位置对齐，有效行标 Complete（拼回标点），缺失/空/回显标 Failed；
 * - [final] = false（流式中间状态）：有完整行的句子标 Translating（拼回标点），
 *   其余仍为 Pending 的句子转为 Translating("") 占位，已进入 Translating/Complete 的保持原状。
 */
fun mergeSentenceStates(
    old: Map<Int, SentenceTranslationState>,
    chunk: NovelSentenceChunk,
    lines: List<String>?,
    final: Boolean,
): Map<Int, SentenceTranslationState> {
    val capacity = old.size + chunk.sentenceIds.size
    if (lines == null) {
        return buildMap(capacity) {
            putAll(old)
            for (sentenceId in chunk.sentenceIds) {
                put(sentenceId, SentenceTranslationState.Failed)
            }
        }
    }
    return buildMap(capacity) {
        putAll(old)
        for ((index, sentenceId) in chunk.sentenceIds.withIndex()) {
            val span = chunk.sentences.getOrNull(index)
            val translated = lines.getOrNull(index)
            if (final) {
                val source = span?.translationSource.orEmpty()
                val valid =
                    translated != null &&
                        translated.isNotBlank() &&
                        !isIdentityTranslation(source, translated)
                put(
                    sentenceId,
                    if (valid) {
                        val display =
                            span?.let { reattachNovelSentencePunctuation(it, translated) }
                                ?: translated
                        SentenceTranslationState.Complete(display)
                    } else {
                        SentenceTranslationState.Failed
                    },
                )
            } else {
                val source = span?.translationSource.orEmpty()
                val usable =
                    translated != null &&
                        translated.isNotBlank() &&
                        !isIdentityTranslation(source, translated)
                val oldState = old[sentenceId]
                if (oldState is SentenceTranslationState.Complete) {
                    // 已完成的句子保持 Complete，不被流式中间态降级
                } else if (usable) {
                    val display =
                        span?.let { reattachNovelSentencePunctuation(it, translated) }
                            ?: translated
                    put(sentenceId, SentenceTranslationState.Translating(display))
                } else if (oldState == null || oldState is SentenceTranslationState.Pending) {
                    put(sentenceId, SentenceTranslationState.Translating(""))
                }
            }
        }
    }
}
