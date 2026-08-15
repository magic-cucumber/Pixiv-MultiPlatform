package top.kagg886.pmf.ui.util

import top.kagg886.pmf.translate.SentenceSegmenter

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
): List<NovelSentenceChunk> {
    require(maxSentencesPerChunk > 0) { "maxSentencesPerChunk must be positive" }
    val byId = index.associateBy { it.id }
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
