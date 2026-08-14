package top.kagg886.pmf.translate

import io.github.hatoyuze.deepseek.protocol.api.ChatChunk
import io.github.hatoyuze.deepseek.protocol.api.StatelessDeepseek
import io.github.hatoyuze.deepseek.protocol.api.collectResponse
import io.github.hatoyuze.deepseek.protocol.api.entity.ResponseFormat
import io.github.hatoyuze.deepseek.protocol.api.entity.ThinkingMode
import io.github.hatoyuze.deepseek.protocol.api.statelessDeepseek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.util.logger

/**
 * 基于 deepseek-helper 的无状态翻译实现。
 *
 * (apiKey, prompt, modelId) 任一变化时在 [Mutex] 保护下重建底层客户端；
 * 日志仅输出"已配置/未配置"与模型名，绝不打印密钥。始终关闭 ThinkingMode，
 * 并要求模型按句对句的 JSON 结构返回，供 [SentenceTranslationParser] 对齐。
 */
class DeepseekTranslator : Translator {
    private val mutex = Mutex()
    private var cachedApiKey = ""
    private var cachedPrompt = ""
    private var cachedModelId = ""
    private var cachedTargetLang = ""
    private var client: StatelessDeepseek? = null

    private fun buildPrompt(targetLang: String): String {
        val prompt = AppConfig.aiTranslatePrompt.replace("%lang%", targetLang)
        val properNouns = AppConfig.aiTranslateProperNouns.trim()
        return if (properNouns.isEmpty()) prompt else "$prompt\n$properNouns"
    }

    private suspend fun currentClient(targetLang: String): StatelessDeepseek {
        val apiKey = AppConfig.deepseekApiKey
        val prompt = buildPrompt(targetLang)
        val modelId = AppConfig.aiTranslateModel
        return mutex.withLock {
            if (client == null ||
                apiKey != cachedApiKey ||
                prompt != cachedPrompt ||
                modelId != cachedModelId ||
                targetLang != cachedTargetLang
            ) {
                if (apiKey.isBlank()) {
                    logger.w { "ai translate client not configured: api key is blank" }
                } else {
                    logger.i { "ai translate client configured, model=$modelId, target=$targetLang" }
                }
                cachedApiKey = apiKey
                cachedPrompt = prompt
                cachedModelId = modelId
                cachedTargetLang = targetLang
                client = statelessDeepseek(apiKey) {
                    this.prompt = prompt
                    model {
                        if (modelId.isBlank()) flash() else custom(modelId)
                    }
                    config {
                        thinkingMode = ThinkingMode.Disabled
                        temperature = 0.3
                        maxTokens = 4096
                        responseFormat = ResponseFormat.JSON_OBJECT
                        includeUsage = false
                    }
                }
            }
            client!!
        }
    }

    override suspend fun translate(text: String, targetLang: String): String {
        val c = currentClient(targetLang)
        val response = c.chatStream(text).collectResponse()
        return response.content.ifBlank { text }
    }

    override fun translateStream(text: String, targetLang: String): Flow<String> = flow {
        val c = currentClient(targetLang)
        val builder = StringBuilder()
        c.chatStream(text).collect { chunk ->
            if (chunk is ChatChunk.ContentDelta && chunk.content.isNotEmpty()) {
                builder.append(chunk.content)
                emit(builder.toString())
            }
        }
    }
}
