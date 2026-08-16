package top.kagg886.pmf.translate

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import top.kagg886.pmf.backend.AppConfig

class KoogTranslatorTest {
    @Test
    fun testFilterTextOnlyEmitsIncrementalDeltas() = runBlocking {
        val frames = flowOf(
            StreamFrame.TextDelta("我"),
            StreamFrame.ReasoningDelta(text = "thinking"),
            StreamFrame.TextDelta("是"),
            StreamFrame.ToolCallDelta(id = "1", name = "f", content = "{}"),
            StreamFrame.TextDelta("koog"),
            StreamFrame.End(),
        )

        val result = frames.filterTextOnly().toList()

        assertEquals(listOf("我", "是", "koog"), result)
    }

    @Test
    fun testPresetDefaultsMatchOfficialModels() {
        assertEquals("gpt-5.4-mini", presetOf(AppConfig.AiTranslateProvider.OPENAI).defaultModel)
        assertEquals("deepseek-v4-flash", presetOf(AppConfig.AiTranslateProvider.DEEPSEEK).defaultModel)
        assertEquals("glm-5.2", presetOf(AppConfig.AiTranslateProvider.GLM).defaultModel)
        assertEquals("claude-sonnet-4-5", presetOf(AppConfig.AiTranslateProvider.ANTHROPIC).defaultModel)
        assertEquals("gemini-3.6-flash", presetOf(AppConfig.AiTranslateProvider.GOOGLE).defaultModel)
        assertEquals("gemma4", presetOf(AppConfig.AiTranslateProvider.OLLAMA).defaultModel)
    }

    @Test
    fun testPresetOpenAICompatiblePathsAndKeys() {
        assertEquals("v1/chat/completions", presetOf(AppConfig.AiTranslateProvider.OPENAI).chatCompletionsPath)
        assertEquals("chat/completions", presetOf(AppConfig.AiTranslateProvider.DEEPSEEK).chatCompletionsPath)
        assertEquals("chat/completions", presetOf(AppConfig.AiTranslateProvider.GLM).chatCompletionsPath)
        assertEquals("chat/completions", presetOf(AppConfig.AiTranslateProvider.GOOGLE).chatCompletionsPath)
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai",
            presetOf(AppConfig.AiTranslateProvider.GOOGLE).baseUrl,
        )
        assertEquals("https://open.bigmodel.cn/api/paas/v4", presetOf(AppConfig.AiTranslateProvider.GLM).baseUrl)
        assertTrue(presetOf(AppConfig.AiTranslateProvider.OPENAI).requiresApiKey)
        assertFalse(presetOf(AppConfig.AiTranslateProvider.OLLAMA).requiresApiKey)
    }

    @Test
    fun testEffectiveModelFallsBackToPresetDefault() {
        assertEquals("gpt-5.4-mini", effectiveModel(AppConfig.AiTranslateProvider.OPENAI, ""))
        assertEquals("gpt-5.4-mini", effectiveModel(AppConfig.AiTranslateProvider.OPENAI, "   "))
        assertEquals("custom-model", effectiveModel(AppConfig.AiTranslateProvider.OPENAI, "custom-model"))
    }

    @Test
    fun testBuildLlModelCapabilities() {
        val openAI = buildLlModel(AppConfig.AiTranslateProvider.OPENAI, "gpt-5.4-mini")
        assertEquals("gpt-5.4-mini", openAI.id)
        assertTrue(openAI.supports(LLMCapability.Completion))
        assertTrue(openAI.supports(LLMCapability.OpenAIEndpoint.Completions))

        val anthropic = buildLlModel(AppConfig.AiTranslateProvider.ANTHROPIC, "claude-sonnet-4-5")
        assertTrue(anthropic.supports(LLMCapability.Completion))
        // Anthropic execute() 无条件要求 Tools 能力（即使纯文本请求）
        assertTrue(anthropic.supports(LLMCapability.Tools))
        assertFalse(anthropic.supports(LLMCapability.OpenAIEndpoint.Completions))
    }

    @Test
    fun testEffectiveBaseUrlUsesOverrideForAnyProvider() {
        // 自定义 Base URL（镜像/中继）对任何提供商生效
        assertEquals(
            "https://relay.example.com",
            effectiveBaseUrl(AppConfig.AiTranslateProvider.DEEPSEEK, "https://relay.example.com"),
        )
        // 留空回退预设默认
        assertEquals(
            "https://api.deepseek.com",
            effectiveBaseUrl(AppConfig.AiTranslateProvider.DEEPSEEK, ""),
        )
    }

    @Test
    fun testPrewarmBuildsSessionWithoutRequest() = runBlocking {
        val previousProvider = AppConfig.aiTranslateProvider
        val previousKey = AppConfig.aiTranslateApiKey
        try {
            // Ollama 无需 API key，预热只构建客户端、不发起网络请求
            AppConfig.aiTranslateProvider = AppConfig.AiTranslateProvider.OLLAMA
            AppConfig.aiTranslateApiKey = ""
            val translator = KoogTranslator()
            translator.prewarm()
            // 无异常即说明客户端构建成功
        } finally {
            AppConfig.aiTranslateProvider = previousProvider
            AppConfig.aiTranslateApiKey = previousKey
        }
    }

    @Test
    fun testFingerprintInvalidatesOnProviderChange() = runBlocking {
        val previousProvider = AppConfig.aiTranslateProvider
        val previousModel = AppConfig.aiTranslateModel
        try {
            AppConfig.aiTranslateProvider = AppConfig.AiTranslateProvider.DEEPSEEK
            AppConfig.aiTranslateModel = "deepseek-v4-flash"
            val translator = CountingTranslator()
            val scheduler = TranslateScheduler(translator)

            scheduler.translate("hello", "中文")
            scheduler.translate("hello", "中文") // 缓存命中

            AppConfig.aiTranslateProvider = AppConfig.AiTranslateProvider.GLM
            scheduler.translate("hello", "中文") // provider 变化使指纹失效

            assertEquals(2, translator.calls, "provider 变化后应重新发起翻译请求")
        } finally {
            AppConfig.aiTranslateProvider = previousProvider
            AppConfig.aiTranslateModel = previousModel
        }
    }

    private class CountingTranslator : Translator {
        var calls = 0

        override suspend fun translate(text: String, targetLang: String): String {
            calls++
            return "译:$text"
        }

        override fun translateStream(text: String, targetLang: String): Flow<String> = flowOf("译:$text")
    }
}
