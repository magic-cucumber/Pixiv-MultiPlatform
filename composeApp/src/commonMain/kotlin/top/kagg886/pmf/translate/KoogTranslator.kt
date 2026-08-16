package top.kagg886.pmf.translate

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.filterTextOnly
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.backend.PlatformConfig0
import top.kagg886.pmf.backend.PlatformEngine
import top.kagg886.pmf.util.logger

/**
 * 基于 koog 的多提供商无状态翻译实现。
 *
 * 按 [AppConfig.AiTranslateProvider] 选择协议客户端（OpenAI 兼容 / Anthropic / Ollama）；
 * 会话按 (provider, apiKey, baseUrl, modelId) 缓存在 [KoogSessionCache] 中：
 * 配置变化只新增条目，绝不关闭其他协程正在使用的客户端；prompt/targetLang 为按请求参数，
 * 不参与会话 key。日志仅输出"已配置/未配置"与提供商、模型名，绝不打印密钥。
 */
class KoogTranslator : Translator {
    private val sessionCache = KoogSessionCache<Session>()

    private class Session(
        val client: LLMClient,
        val model: LLModel,
    ) : AutoCloseable {
        override fun close() = client.close()
    }

    private fun buildPrompt(targetLang: String): String {
        AppConfig.migrateLegacyAiTranslatePrompt()
        val prompt = AppConfig.aiTranslatePrompt.replace("%lang%", targetLang)
        val properNouns = AppConfig.aiTranslateProperNouns.trim()
        return if (properNouns.isEmpty()) prompt else "$prompt\n$properNouns"
    }

    private fun buildRequestPrompt(systemPrompt: String, text: String) = prompt(
        id = "ai-translate",
        params = LLMParams(temperature = 0.3, maxTokens = 4096),
    ) {
        system(systemPrompt)
        user(text)
    }

    /** 每次重建使用全新 Ktor 引擎，close 旧客户端即完整释放，避免共享引擎误关。 */
    private fun createHttpFactory(): KoogHttpClient.Factory = KtorKoogHttpClient.Factory(
        baseClient = HttpClient(PlatformEngine) {
            // AI 翻译客户端绝不继承 SNIReplace 的 trust-all TLS/SNI 改写：
            // 该模式仅适配 pixiv 域名，对 AI 提供商主机反而会 DNS NPE 且使密钥流量走不受信 TLS；
            // 代理模式（Proxy）仍复用，便于访问境外提供商。
            if (AppConfig.bypassSettings !is AppConfig.BypassSetting.SNIReplace) {
                PlatformConfig0()
            }
        },
    )

    private fun createClient(
        provider: AppConfig.AiTranslateProvider,
        apiKey: String,
        baseUrl: String,
        model: LLModel,
        factory: KoogHttpClient.Factory,
    ): LLMClient {
        // 校验 scheme，防止任意 scheme（如 ftp://）把带 Authorization 的请求发往意外目标
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://")) {
            "invalid AI base url: $baseUrl"
        }
        return when (presetOf(provider).protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> OpenAILLMClient(
                apiKey = apiKey,
                settings = OpenAIClientSettings(
                    baseUrl = baseUrl,
                    chatCompletionsPath = presetOf(provider).chatCompletionsPath ?: "v1/chat/completions",
                ),
                httpClientFactory = factory,
            )

            ProviderProtocol.ANTHROPIC -> AnthropicLLMClient(
                apiKey = apiKey,
                // Anthropic 客户端要求模型出现在 modelVersionsMap，恒等映射自定义模型
                settings = AnthropicClientSettings(
                    baseUrl = baseUrl,
                    modelVersionsMap = mapOf(model to model.id),
                ),
                httpClientFactory = factory,
            )

            ProviderProtocol.OLLAMA -> OllamaClient(
                httpClientFactory = factory,
                baseUrl = baseUrl,
            )
        }
    }

    private suspend fun currentSession(): Session {
        val provider = AppConfig.aiTranslateProvider
        val apiKey = AppConfig.aiTranslateApiKey
        val baseUrl = effectiveBaseUrl(provider, AppConfig.aiTranslateBaseUrl)
        val modelId = effectiveModel(provider, AppConfig.aiTranslateModel)
        // koog 客户端只消费 provider/apiKey/baseUrl/model，prompt 与 targetLang 为按请求参数
        val key = "$provider|$apiKey|$baseUrl|$modelId"
        return sessionCache.getOrCreate(key) {
            if (apiKey.isBlank() && presetOf(provider).requiresApiKey) {
                logger.w { "ai translate client not configured: api key is blank" }
            } else {
                logger.i { "ai translate client configured, provider=$provider, model=$modelId" }
            }
            val model = buildLlModel(provider, modelId)
            Session(
                client = createClient(provider, apiKey, baseUrl, model, createHttpFactory()),
                model = model,
            )
        }
    }

    /**
     * 预构建当前配置的客户端会话（不发起任何请求），把引擎/客户端初始化移出首次翻译路径。
     *
     * 在 [Dispatchers.Default] 上执行，避免阻塞调用方；无有效配置或构造失败时仅记录日志，
     * 后续翻译仍会按需重建。
     */
    suspend fun prewarm() = withContext(Dispatchers.Default) {
        if (presetOf(AppConfig.aiTranslateProvider).requiresApiKey && AppConfig.aiTranslateApiKey.isBlank()) {
            return@withContext
        }
        try {
            currentSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "ai translate prewarm failed: ${maskSecret(e.message ?: "")}" }
        }
    }

    override suspend fun translate(text: String, targetLang: String): String {
        val session = currentSession()
        val startedAt = Clock.System.now()
        val response = session.client.execute(buildRequestPrompt(buildPrompt(targetLang), text), session.model)
        val content = response.parts.filterIsInstance<MessagePart.Text>().joinToString("") { it.text }
        logger.i {
            "koog request: provider=${AppConfig.aiTranslateProvider} model=${AppConfig.aiTranslateModel} target=$targetLang " +
                "textLen=${text.length} durationMs=${(Clock.System.now() - startedAt).inWholeMilliseconds} " +
                "req=${maskSecret(text.take(40))} resp=${maskSecret(content.take(40))}"
        }
        // 空内容如实返回（由 TranslateScheduler 判为 Failure），
        // 绝不把原文当作"译文"回退——否则会被当作成功结果写入缓存，导致后续永远显示原文。
        return content
    }

    override fun translateStream(text: String, targetLang: String): Flow<String> = flow {
        val session = currentSession()
        val systemPrompt = buildPrompt(targetLang)
        session.client.executeStreaming(buildRequestPrompt(systemPrompt, text), session.model)
            .filterTextOnly()
            .collect { emit(it) }
    }
}
