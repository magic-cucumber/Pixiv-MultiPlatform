package top.kagg886.pmf.translate

/** 一句原文与对应译文。 */
data class SentencePair(
    val original: String,
    val translated: String,
)

/**
 * 解析模型按行返回的句译文。
 *
 * 小说翻译采用"每行一句"纯文本协议：源句按行送入 AI，模型逐行返回译文。
 * 行协议在流式场景下每遇到一个换行即可闭合一句，避免 JSON 在流未结束时无法解析的问题。
 */
object SentenceTranslationParser {
    /** 解析完整译文：去代码块围栏后按非空行返回；空白输入返回空列表。 */
    fun parse(raw: String): List<String> {
        val cleaned = stripCodeFences(raw)
        if (cleaned.isBlank() || looksLikeJsonPayload(cleaned)) return emptyList()
        return cleaned.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** 严格解析：至少返回一个非空行；空白或无法解析时返回 null。 */
    fun parseStrict(raw: String): List<String>? = parse(raw).takeIf { it.isNotEmpty() }

    /**
     * 面向句对齐的完整解析：去掉首尾空行，但保留中间空行。
     *
     * 模型漏译某句时对应位置为空字符串，调用方可把该句判为失败，
     * 避免后续译文前移配对到错误的原句。
     */
    fun parseForAlignment(raw: String): List<String>? {
        val cleaned = stripCodeFences(raw)
        if (cleaned.isBlank() || looksLikeJsonPayload(cleaned)) return null
        val lines = cleaned.lines()
        val first = lines.indexOfFirst { it.isNotBlank() && !isCodeFenceLine(it) }
        if (first < 0) return null
        val last = lines.indexOfLast { it.isNotBlank() && !isCodeFenceLine(it) }
        return lines.subList(first, last + 1).map { line ->
            if (isCodeFenceLine(line)) "" else line.trim()
        }
    }

    fun align(original: List<String>, translated: List<String>): List<SentencePair>? {
        if (original.isEmpty()) return null
        if (original.size != translated.size) return null
        return original.zip(translated) { source, target -> SentencePair(source, target) }
    }
}

/**
 * 增量解析流式按行译文。
 *
 * 输入是模型逐 token 累积的纯文本；只有已经出现换行、完整闭合的行才会被输出，
 * 当前未闭合的最后一行保留在缓冲区等待后续 token。这样 GUI 可逐句上屏，
 * 不会把尚未完成的句子显示给用户。
 */
object IncrementalSentenceParser {
    fun extractSentences(partial: String): List<String> {
        // 结尾换行是"最后一句已闭合"的依据，不能在清理围栏时被 trim 掉
        val hasClosingLineBreak =
            partial.trimStart().endsWith('\n') || partial.trimStart().endsWith("\r\n")
        val cleaned = stripCodeFences(partial)
        if (cleaned.isBlank() || looksLikeJsonPayload(cleaned)) return emptyList()

        val lines = cleaned.lines()
        val complete = when {
            hasClosingLineBreak -> lines
            lines.size <= 1 -> emptyList()
            else -> lines.dropLast(1)
        }
        val first = complete.indexOfFirst { it.isNotBlank() && !isCodeFenceLine(it) }
        if (first < 0) return emptyList()
        val last = complete.indexOfLast { it.isNotBlank() && !isCodeFenceLine(it) }
        // 保留中间空行，供调用方按位置对齐；只去掉首尾空白/围栏行。
        return complete.subList(first, last + 1).map { line ->
            if (isCodeFenceLine(line)) "" else line.trim()
        }
    }
}

/** 归一化空白后比较译文与原文是否完全相同（模型"回显原文"时视为失败，避免缓存原文）。 */
fun isIdentityTranslation(original: String, translated: String): Boolean {
    if (original.isBlank() || translated.isBlank()) return false
    fun normalize(s: String) = s.trim().replace(WHITESPACE_REGEX, " ")
    return normalize(original) == normalize(translated)
}

private val WHITESPACE_REGEX = Regex("\\s+")

/** 移除首尾的 Markdown 代码块围栏。 */
internal fun stripCodeFences(raw: String): String {
    // 只去除首部空白，保留尾部换行供流式解析判断行是否闭合。
    var text = raw.trimStart()
    if (!text.startsWith("```")) return text

    val firstLineBreak = text.indexOf('\n')
    if (firstLineBreak >= 0) {
        text = text.substring(firstLineBreak + 1)
    } else {
        // 流式场景：围栏刚开始但还没有换行，内容视为空。
        return ""
    }
    text = text.trimStart('\n', '\r')
    return removeTrailingCodeFence(text)
}

private fun removeTrailingCodeFence(text: String): String {
    val trimmedEnd = text.trimEnd()
    if (!trimmedEnd.endsWith("```")) return text
    return trimmedEnd.dropLast(3).trimEnd()
}

/** 识别旧版 JSON 协议输出，宁可判失败也不把 JSON 原文展示给用户。 */
private fun looksLikeJsonPayload(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("{\"") ||
        (
            trimmed.startsWith("{") &&
                trimmed.contains("\"sentences\"")
            ) ||
        trimmed.startsWith("[\"") ||
        (
            trimmed.startsWith("[") &&
                trimmed.endsWith("]") &&
                trimmed.contains("\",\"")
            )
}

private fun isCodeFenceLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed == "```" || trimmed.startsWith("```")
}

/** 非小说场景的整块展示：保留译文内部的空行，只去掉首尾空行。 */
fun String.translationDisplayText(): String = SentenceTranslationParser.parseForAlignment(this)?.joinToString("\n").orEmpty()

/** [translationDisplayText] 的判空版本：展示文本为空白时返回 null，供调用方按失败处理。 */
fun String.translationDisplayTextOrNull(): String? = translationDisplayText().takeIf { it.isNotBlank() }
