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
 * 使用纯文本响应格式：模型按句返回、每行一句译文，便于流式场景逐行闭合上屏，
 * 避免 JSON 在流未结束时无法闭合解析的问题。
 */
class DeepseekTranslator : Translator {
    private val mutex = Mutex()
    private var cachedApiKey = ""
    private var cachedPrompt = ""
    private var cachedModelId = ""
    private var cachedTargetLang = ""
    private var client: StatelessDeepseek? = null

    private fun buildPrompt(targetLang: String): String {
        AppConfig.migrateLegacyAiTranslatePrompt()
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
                        responseFormat = ResponseFormat.TEXT
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
        // 空内容如实返回（由 TranslateScheduler 判为 Failure），
        // 绝不把原文当作"译文"回退——否则会被当作成功结果写入缓存，导致后续永远显示原文。
        return response.content
    }

    override fun translateStream(text: String, targetLang: String): Flow<String> = flow {
        val c = currentClient(targetLang)
        c.chatStream(text).contentDeltas().collect { emit(it) }
    }
}

/**
 * 把 DeepSeek 聊天流转换为纯内容 delta。
 *
 * 每个事件只发射本次新增内容；历史累积由 [TranslateScheduler] 完成，
 * 避免下游再次 append 时出现"我/我是/我是Deepseek..."这类重复前缀。
 */
internal fun Flow<ChatChunk>.contentDeltas(): Flow<String> = flow {
    collect { chunk ->
        if (chunk is ChatChunk.ContentDelta && chunk.content.isNotEmpty()) {
            emit(chunk.content)
        }
    }
}
