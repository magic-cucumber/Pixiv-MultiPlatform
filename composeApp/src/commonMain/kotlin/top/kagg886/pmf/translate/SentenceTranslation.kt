package top.kagg886.pmf.translate

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 一句原文与对应译文。 */
data class SentencePair(
    val original: String,
    val translated: String,
)

/** 模型返回的句对句 JSON 结构。 */
@Serializable
data class SentenceTranslationPayload(
    val sentences: List<String> = emptyList(),
)

/**
 * 解析并对齐模型的句对句翻译结果。
 *
 * 解析容忍裸数组与 {"sentences": [...]} 两种形态及 ```json 代码块包裹；
 * 解析失败时回退为整段文本（单句），对齐数量不一致时返回 null，由调用方降级。
 */
object SentenceTranslationParser {
    fun parse(raw: String): List<String> {
        val cleaned = raw.trim()
            .removeSurrounding("```json", "```")
            .removeSurrounding("```")
            .trim()
        if (cleaned.isEmpty()) return emptyList()
        val parsed = runCatching {
            val element = Json.parseToJsonElement(cleaned)
            val array =
                when (element) {
                    is JsonObject -> element["sentences"] as? JsonArray
                    is JsonArray -> element
                    else -> null
                }
            array?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
        }.getOrNull()
        return parsed?.takeIf { it.isNotEmpty() } ?: listOf(cleaned)
    }

    /**
     * 严格解析：仅接受合法的 JSON 句子数组（对象或裸数组形态），
     * 解析失败、句子数组为空时返回 null——绝不回退为原始文本。
     *
     * 用于小说页的最终结果：模型返回残缺/非 JSON 时由调用方标为失败，
     * 避免把原始 JSON 文本当作译文展示。
     */
    fun parseStrict(raw: String): List<String>? {
        val cleaned = raw.trim()
            .removeSurrounding("```json", "```")
            .removeSurrounding("```")
            .trim()
        if (cleaned.isEmpty()) return null
        val parsed = runCatching {
            val element = Json.parseToJsonElement(cleaned)
            val array =
                when (element) {
                    is JsonObject -> element["sentences"] as? JsonArray
                    is JsonArray -> element
                    else -> null
                } ?: return@runCatching null
            array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }.getOrNull()
        return parsed?.takeIf { it.isNotEmpty() }
    }

    fun align(original: List<String>, translated: List<String>): List<SentencePair>? {
        if (original.isEmpty()) return null
        if (original.size != translated.size) return null
        return original.zip(translated) { source, target -> SentencePair(source, target) }
    }
}

/**
 * 增量解析流式 JSON（`{"sentences":[...]}` 或裸数组）中已经完整闭合的句子字符串。
 *
 * 模型逐 token 累积的残缺 JSON 作为输入；本解析器只输出已闭合的字符串字面量，
 * 未闭合的尾串、未闭合的数组/对象、尾随垃圾一律忽略——GUI 层据此逐句展示流式译文，
 * 绝不把残缺 JSON 原文上屏。完整 JSON 的提取结果与 [SentenceTranslationParser.parse] 一致。
 */
object IncrementalSentenceParser {
    fun extractSentences(partial: String): List<String> {
        val trimmed = partial.trim()
            .removeSurrounding("```json", "```")
            .removeSurrounding("```")
            .trim()
        if (trimmed.isEmpty()) return emptyList()

        var index = 0
        val len = trimmed.length

        // 定位数组起始 '['：对象形态需先找到 "sentences" 键
        when (trimmed[index]) {
            '{' -> {
                index++
                val key = "\"sentences\""
                val keyIndex = trimmed.indexOf(key, index)
                if (keyIndex < 0) return emptyList()
                index = keyIndex + key.length
                while (index < len && trimmed[index].isWhitespace()) index++
                if (index >= len || trimmed[index] != ':') return emptyList()
                index++
                while (index < len && trimmed[index].isWhitespace()) index++
            }

            '[' -> {}

            else -> return emptyList()
        }
        // index 现在指向数组起始 '['
        if (index >= len || trimmed[index] != '[') return emptyList()
        index++

        val result = mutableListOf<String>()
        while (index < len) {
            while (index < len && trimmed[index].isWhitespace()) index++
            if (index >= len) break
            when (trimmed[index]) {
                ',' -> index++

                ']', '}' -> break

                '"' -> {
                    val sb = StringBuilder()
                    index++ // 跳过开引号
                    var closed = false
                    while (index < len) {
                        val c = trimmed[index]
                        if (c == '\\') {
                            // 处理 JSON 转义；残缺的转义序列视为未闭合
                            if (index + 1 >= len) {
                                index = len
                                break
                            }
                            val esc = trimmed[index + 1]
                            when (esc) {
                                'n' -> sb.append('\n')

                                't' -> sb.append('\t')

                                'r' -> sb.append('\r')

                                '"' -> sb.append('"')

                                '\\' -> sb.append('\\')

                                '/' -> sb.append('/')

                                'u' -> {
                                    if (index + 5 < len) {
                                        val hex = trimmed.substring(index + 2, index + 6)
                                        val code = hex.toIntOrNull(16)
                                        if (code != null) {
                                            sb.append(code.toChar())
                                            index += 6
                                            continue
                                        }
                                    }
                                    sb.append('u')
                                    index += 2
                                    continue
                                }

                                else -> sb.append(esc)
                            }
                            index += 2
                        } else if (c == '"') {
                            closed = true
                            index++
                            break
                        } else {
                            sb.append(c)
                            index++
                        }
                    }
                    if (closed && sb.isNotEmpty()) {
                        result += sb.toString()
                    }
                    // 未闭合的字符串不输出
                }

                else -> index++ // 忽略意外字符
            }
        }
        return result
    }
}

/** 归一化空白后比较译文与原文是否完全相同（模型"回显原文"时视为失败，避免缓存原文）。 */
fun isIdentityTranslation(original: String, translated: String): Boolean {
    if (original.isBlank() || translated.isBlank()) return false
    fun normalize(s: String) = s.trim().replace(WHITESPACE_REGEX, " ")
    return normalize(original) == normalize(translated)
}

private val WHITESPACE_REGEX = Regex("\\s+")

/** 非小说场景的整块展示：解析句子后按行拼接，避免把 JSON 原文直接展示给用户。 */
fun String.translationDisplayText(): String = SentenceTranslationParser.parse(this).joinToString("\n")

/** [translationDisplayText] 的判空版本：展示文本为空白时返回 null，供调用方按失败处理。 */
fun String.translationDisplayTextOrNull(): String? = translationDisplayText().takeIf { it.isNotBlank() }
