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

    fun align(original: List<String>, translated: List<String>): List<SentencePair>? {
        if (original.isEmpty()) return null
        if (original.size != translated.size) return null
        return original.zip(translated) { source, target -> SentencePair(source, target) }
    }
}

/** 非小说场景的整块展示：解析句子后按行拼接，避免把 JSON 原文直接展示给用户。 */
fun String.translationDisplayText(): String = SentenceTranslationParser.parse(this).joinToString("\n")
