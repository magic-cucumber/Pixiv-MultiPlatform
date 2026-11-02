package top.kagg886.pmf.translate

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import top.kagg886.pmf.backend.AppConfig

/** 提供商协议类型：决定使用哪个 koog 客户端。 */
enum class ProviderProtocol {
    /** OpenAI Chat Completions 兼容协议（OpenAI/DeepSeek/GLM/Gemini 兼容端点/自定义端点）。 */
    OPENAI_COMPATIBLE,

    /** Anthropic 原生 Messages API。 */
    ANTHROPIC,

    /** Ollama 本地 API。 */
    OLLAMA,
}

/** 单个 AI 翻译提供商的预设信息。 */
data class AiTranslatePreset(
    val provider: AppConfig.AiTranslateProvider,
    val protocol: ProviderProtocol,
    val baseUrl: String,
    /** OpenAI 兼容协议的 chat completions 路径（不含前导斜杠）；其他协议为 null。 */
    val chatCompletionsPath: String?,
    val defaultModel: String,
    val requiresApiKey: Boolean,
)

private val presets: Map<AppConfig.AiTranslateProvider, AiTranslatePreset> = listOf(
    AiTranslatePreset(
        provider = AppConfig.AiTranslateProvider.OPENAI,
        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
        baseUrl = "https://api.openai.com",
        chatCompletionsPath = "v1/chat/completions",
        defaultModel = "gpt-5.4-mini",
        requiresApiKey = true,
    ),
    AiTranslatePreset(
        provider = AppConfig.AiTranslateProvider.DEEPSEEK,
        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
        baseUrl = "https://api.deepseek.com",
        // DeepSeek 兼容端点无 v1 前缀，避免拼出 /v1/chat/completions
        chatCompletionsPath = "chat/completions",
        defaultModel = "deepseek-v4-flash",
        requiresApiKey = true,
    ),
    AiTranslatePreset(
        provider = AppConfig.AiTranslateProvider.GLM,
        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        // 智谱 v4 兼容端点无 v1 前缀
        chatCompletionsPath = "chat/completions",
        defaultModel = "glm-5.2",
        requiresApiKey = true,
    ),
    AiTranslatePreset(
        provider = AppConfig.AiTranslateProvider.ANTHROPIC,
        protocol = ProviderProtocol.ANTHROPIC,
        baseUrl = "https://api.anthropic.com",
        chatCompletionsPath = null,
        defaultModel = "claude-sonnet-4-5",
        requiresApiKey = true,
    ),
    AiTranslatePreset(
        provider = AppConfig.AiTranslateProvider.GOOGLE,
        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
        // Gemini 官方 OpenAI 兼容端点（原生 Google 客户端仅以 beta 发布，统一走兼容协议）
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        chatCompletionsPath = "chat/completions",
        defaultModel = "gemini-3.6-flash",
        requiresApiKey = true,
    ),
    AiTranslatePreset(
        provider = AppConfig.AiTranslateProvider.OLLAMA,
        protocol = ProviderProtocol.OLLAMA,
        baseUrl = "http://localhost:11434",
        chatCompletionsPath = null,
        defaultModel = "gemma4",
        requiresApiKey = false,
    ),
    AiTranslatePreset(
        provider = AppConfig.AiTranslateProvider.CUSTOM,
        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
        baseUrl = "",
        chatCompletionsPath = "v1/chat/completions",
        defaultModel = "",
        requiresApiKey = true,
    ),
).associateBy { it.provider }

/** 按提供商取预设；未知值回退到 DeepSeek（防御性兜底）。 */
fun presetOf(provider: AppConfig.AiTranslateProvider): AiTranslatePreset = presets[provider] ?: presets.getValue(AppConfig.AiTranslateProvider.DEEPSEEK)

/** 生效的 Base URL：用户自定义非空则优先（所有提供商均支持覆盖，如镜像/内网中继），否则用预设默认。 */
fun effectiveBaseUrl(provider: AppConfig.AiTranslateProvider, custom: String): String = custom.trim().ifBlank { presetOf(provider).baseUrl }

/** 生效的模型 id：用户配置非空则优先，否则用预设默认。 */
fun effectiveModel(provider: AppConfig.AiTranslateProvider, configured: String): String = configured.trim().ifBlank { presetOf(provider).defaultModel }

/**
 * 构造 koog 使用的 [LLModel]。
 *
 * 各客户端 execute/executeStreaming 均要求 [LLMCapability.Completion]；
 * OpenAI 兼容客户端对普通 LLMParams 还需 [LLMCapability.OpenAIEndpoint.Completions]；
 * Anthropic 的 execute 无条件要求 [LLMCapability.Tools]（即使纯文本请求）。
 */
fun buildLlModel(provider: AppConfig.AiTranslateProvider, modelId: String): LLModel {
    val preset = presetOf(provider)
    val koogProvider = when (preset.protocol) {
        ProviderProtocol.OPENAI_COMPATIBLE -> LLMProvider.OpenAI
        ProviderProtocol.ANTHROPIC -> LLMProvider.Anthropic
        ProviderProtocol.OLLAMA -> LLMProvider.Ollama
    }
    val capabilities = buildList {
        add(LLMCapability.Completion)
        add(LLMCapability.Temperature)
        when (preset.protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> add(LLMCapability.OpenAIEndpoint.Completions)
            ProviderProtocol.ANTHROPIC -> add(LLMCapability.Tools)
            ProviderProtocol.OLLAMA -> Unit
        }
    }
    return LLModel(provider = koogProvider, id = modelId, capabilities = capabilities)
}
