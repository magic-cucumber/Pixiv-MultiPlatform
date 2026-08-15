package top.kagg886.pmf.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import top.kagg886.pmf.backend.AppConfig

class TranslateSchedulerTest {
    private class FakeTranslator(
        private val delayMillis: Long = 0,
        private val fail: Boolean = false,
    ) : Translator {
        val calls = mutableListOf<String>()
        val streamCalls = mutableListOf<String>()
        private val concurrent = atomic(0)
        val maxConcurrent = atomic(0)
        private val streamConcurrent = atomic(0)
        val maxStreamConcurrent = atomic(0)

        override suspend fun translate(text: String, targetLang: String): String {
            val current = concurrent.incrementAndGet()
            maxConcurrent.value = maxOf(maxConcurrent.value, current)
            try {
                calls.add(text)
                if (delayMillis > 0) delay(delayMillis)
                if (fail) throw IllegalStateException("boom")
                return "translated"
            } finally {
                concurrent.decrementAndGet()
            }
        }

        override fun translateStream(text: String, targetLang: String): Flow<String> = flow {
            val current = streamConcurrent.incrementAndGet()
            maxStreamConcurrent.value = maxOf(maxStreamConcurrent.value, current)
            try {
                streamCalls.add(text)
                emit("t")
                if (delayMillis > 0) delay(delayMillis)
                emit("translated")
            } finally {
                streamConcurrent.decrementAndGet()
            }
        }
    }

    private class FakeCache : TranslateCache {
        val store = mutableMapOf<String, String>()
        var hitResult: String? = null
        var getCalls = 0
        var putCalls = 0

        override suspend fun get(key: String, fingerprint: String): String? {
            getCalls++
            return hitResult ?: store["$key|$fingerprint"]
        }

        override suspend fun put(key: String, fingerprint: String, value: String) {
            putCalls++
            store["$key|$fingerprint"] = value
        }
    }

    @Test
    fun testConcurrencyLimit() = runBlocking {
        val translator = FakeTranslator(delayMillis = 50)
        val scheduler = TranslateScheduler(translator, maxConcurrency = 2)

        val results = (0..7).map { i ->
            async { scheduler.translate("text$i", "中文") }
        }.awaitAll()

        assertTrue(translator.maxConcurrent.value <= 2, "max concurrent should be <= 2")
        assertEquals(8, results.size)
        assertTrue(results.all { it is TranslateResult.Success })
    }

    @Test
    fun testSingleFlightDedup() = runBlocking {
        val translator = FakeTranslator(delayMillis = 50)
        val scheduler = TranslateScheduler(translator, maxConcurrency = 2)

        val results = (0..5).map {
            async { scheduler.translate("same text", "中文") }
        }.awaitAll()

        assertEquals(1, translator.calls.size)
        assertTrue(results.all { it == TranslateResult.Success("translated") })
    }

    @Test
    fun testCacheHit() = runBlocking {
        val translator = FakeTranslator()
        val scheduler = TranslateScheduler(translator)

        scheduler.translate("hello", "中文")
        scheduler.translate("hello", "中文")

        assertEquals(1, translator.calls.size)
    }

    @Test
    fun testFailureFallbackToOriginal() = runBlocking {
        val translator = FakeTranslator(fail = true)
        val scheduler = TranslateScheduler(translator)

        val result = scheduler.translate("hello", "中文")

        assertEquals(TranslateResult.Failure("hello"), result)
    }

    @Test
    fun testPersistentCacheHitSkipsTranslator() = runBlocking {
        val translator = FakeTranslator()
        val cache = FakeCache()
        cache.hitResult = "cached"
        val scheduler = TranslateScheduler(translator, cache)

        val previous = AppConfig.aiTranslateCacheEnabled
        try {
            AppConfig.aiTranslateCacheEnabled = true
            val result = scheduler.translate("hello", "中文")
            assertEquals(TranslateResult.Success("cached"), result)
            assertEquals(0, translator.calls.size)
            assertTrue(cache.getCalls > 0)
        } finally {
            AppConfig.aiTranslateCacheEnabled = previous
        }
    }

    @Test
    fun testPersistentCacheStoresSuccess() = runBlocking {
        val translator = FakeTranslator()
        val cache = FakeCache()
        val scheduler = TranslateScheduler(translator, cache)

        val previous = AppConfig.aiTranslateCacheEnabled
        try {
            AppConfig.aiTranslateCacheEnabled = true
            scheduler.translate("hello", "中文")
            assertTrue(cache.putCalls > 0)
        } finally {
            AppConfig.aiTranslateCacheEnabled = previous
        }
    }

    @Test
    fun testPersistentCacheDisabledSkipsCache() = runBlocking {
        val translator = FakeTranslator()
        val cache = FakeCache()
        val scheduler = TranslateScheduler(translator, cache)

        val previous = AppConfig.aiTranslateCacheEnabled
        try {
            AppConfig.aiTranslateCacheEnabled = false
            scheduler.translate("hello", "中文")
            scheduler.translate("world", "中文")
            assertEquals(2, translator.calls.size)
            assertEquals(0, cache.getCalls)
            assertEquals(0, cache.putCalls)
        } finally {
            AppConfig.aiTranslateCacheEnabled = previous
        }
    }

    @Test
    fun testMaskSecretReplacesApiKeyWithStars() {
        val previous = AppConfig.deepseekApiKey
        try {
            AppConfig.deepseekApiKey = "sk-test-secret-key-123"
            assertEquals("req *** tail", maskSecret("req sk-test-secret-key-123 tail"))
            assertTrue(!maskSecret("http error: sk-test-secret-key-123").contains("sk-test-secret-key-123"))
            assertTrue(maskSecret("http error: sk-test-secret-key-123").contains("***"))
        } finally {
            AppConfig.deepseekApiKey = previous
        }
    }

    @Test
    fun testMaskSecretShortKeyOrBlankIsUnchanged() {
        val previous = AppConfig.deepseekApiKey
        try {
            AppConfig.deepseekApiKey = "ab"
            assertEquals("ab", maskSecret("ab"), "过短的 key 不打码，避免误伤正常文本")
            AppConfig.deepseekApiKey = ""
            assertEquals("plain text", maskSecret("plain text"))
        } finally {
            AppConfig.deepseekApiKey = previous
        }
    }

    @Test
    fun testDefaultPromptUsesLineProtocolInsteadOfJson() {
        val prompt = AppConfig.DEFAULT_AI_TRANSLATE_PROMPT
        assertTrue(prompt.contains("one translated sentence per line"))
        assertTrue(prompt.contains("punctuation"))
        assertTrue(prompt.contains("line breaks"))
        assertFalse(prompt.contains("JSON object"))
        assertFalse(prompt.contains("{\"sentences\""))
    }

    @Test
    fun testLegacyJsonPromptIsMigratedToLineProtocol() {
        val previous = AppConfig.aiTranslatePrompt
        try {
            AppConfig.aiTranslatePrompt = AppConfig.LEGACY_JSON_AI_TRANSLATE_PROMPT
            AppConfig.migrateLegacyAiTranslatePrompt()
            assertEquals(AppConfig.DEFAULT_AI_TRANSLATE_PROMPT, AppConfig.aiTranslatePrompt)
        } finally {
            AppConfig.aiTranslatePrompt = previous
        }
    }

    @Test
    fun testSentenceSegmenterSplitsCjkAndEnglish() {
        assertEquals(
            listOf("こんにちは。", "元気ですか？"),
            SentenceSegmenter.split("こんにちは。元気ですか？"),
        )
        assertEquals(
            listOf("Hello world.", "How are you?"),
            SentenceSegmenter.split("Hello world. How are you?"),
        )
        // 顿号/分号是语块分隔符而非句末，不再切句（避免日文被切成无上下文的碎片）
        assertEquals(
            listOf("第一句。", "第二句！", "第三句", "第四句；第五句"),
            SentenceSegmenter.split("第一句。第二句！第三句\n第四句；第五句"),
        )
        assertTrue(SentenceSegmenter.split("3.14 is a number").size == 1)
        // 日本轻小说常见标点：连续省略号与地板括号/闭引号应并入前句
        assertEquals(
            listOf("「はい……⌋」"),
            SentenceSegmenter.split("「はい……⌋」"),
        )
        assertEquals(
            listOf("はい……", "いいえ。"),
            SentenceSegmenter.split("はい……\nいいえ。"),
        )
        // 逗号不切句：`こんにちは、世界。` 应整体作为一句翻译，保留内部逗号上下文
        assertEquals(
            listOf("こんにちは。", "、世界"),
            SentenceSegmenter.split("こんにちは。、世界"),
        )
        assertEquals(
            listOf("こんにちは、世界。"),
            SentenceSegmenter.split("こんにちは、世界。"),
        )
    }

    @Test
    fun testSentenceTranslationParserAcceptsLineProtocolAndFences() {
        assertEquals(
            listOf("你好", "世界"),
            SentenceTranslationParser.parse("你好\n世界"),
        )
        assertEquals(
            listOf("你好", "世界"),
            SentenceTranslationParser.parse(
                """```text
                你好
                世界
                ```
                """.trimIndent(),
            ),
        )
        assertEquals(
            listOf("plain fallback"),
            SentenceTranslationParser.parse("plain fallback"),
        )
    }

    @Test
    fun testSentenceAlignmentMismatchReturnsNull() {
        assertEquals(
            null,
            SentenceTranslationParser.align(
                listOf("a", "b"),
                listOf("x"),
            ),
        )
        assertEquals(
            listOf("a" to "x", "b" to "y"),
            SentenceTranslationParser.align(
                listOf("a", "b"),
                listOf("x", "y"),
            )?.map { it.original to it.translated },
        )
    }

    @Test
    fun testTimeoutFallbackToOriginal() = runBlocking {
        val translator = FakeTranslator(delayMillis = 10_000)
        val scheduler = TranslateScheduler(translator, timeout = 100.milliseconds)

        val result = scheduler.translate("hello", "中文")

        assertEquals(TranslateResult.Failure("hello"), result)
    }

    @Test
    fun testBlankTranslationIsFailureAndNotCached() = runBlocking {
        var calls = 0
        val translator = object : Translator {
            override suspend fun translate(text: String, targetLang: String): String {
                calls++
                return ""
            }

            override fun translateStream(text: String, targetLang: String): Flow<String> = flow {}
        }
        val scheduler = TranslateScheduler(translator)

        // 空内容不得被当作成功译文（否则原文会被缓存，之后永远显示原文）
        assertEquals(TranslateResult.Failure("hello"), scheduler.translate("hello", "中文"))
        // 失败不缓存：再次调用必须重新请求 translator
        assertEquals(TranslateResult.Failure("hello"), scheduler.translate("hello", "中文"))
        assertEquals(2, calls, "失败结果不应被缓存，重试必须重新请求")
    }

    @Test
    fun testIdentityTranslationIsFailureAndNotCached() = runBlocking {
        var calls = 0
        val translator = object : Translator {
            override suspend fun translate(text: String, targetLang: String): String {
                calls++
                return text // 模型回显原文
            }

            override fun translateStream(text: String, targetLang: String): Flow<String> = flow { emit(text) }
        }
        val scheduler = TranslateScheduler(translator)

        assertEquals(TranslateResult.Failure("hello"), scheduler.translate("hello", "中文"))
        assertEquals(TranslateResult.Failure("hello"), scheduler.translate("hello", "中文"))
        assertEquals(2, calls, "回显原文的结果不应被缓存")
    }

    @Test
    fun testStreamDeltaChunksAreAccumulatedOnlyOnce() = runBlocking {
        val translator = object : Translator {
            override suspend fun translate(text: String, targetLang: String): String = "unused"

            override fun translateStream(text: String, targetLang: String): Flow<String> = flow {
                emit("我")
                emit("是")
                emit("Deepseek")
                emit("了")
            }
        }
        val scheduler = TranslateScheduler(translator)

        val results = scheduler.translateStream("hello", "中文").toList()

        assertEquals(
            listOf(
                TranslateResult.Success("我"),
                TranslateResult.Success("我是"),
                TranslateResult.Success("我是Deepseek"),
                TranslateResult.Success("我是Deepseek了"),
            ),
            results,
        )
    }

    @Test
    fun testBlankStreamIsFailureAndNotCached() = runBlocking {
        var calls = 0
        val translator = object : Translator {
            override suspend fun translate(text: String, targetLang: String): String = ""

            override fun translateStream(text: String, targetLang: String): Flow<String> = flow {
                calls++
                // 空流：不发射任何内容
            }
        }
        val scheduler = TranslateScheduler(translator)

        val results = scheduler.translateStream("hello", "中文").toList()
        assertTrue(results.isNotEmpty(), "流式失败应以 Failure 结尾而非静默结束")
        assertEquals(TranslateResult.Failure("hello"), results.last())

        scheduler.translateStream("hello", "中文").toList()
        assertEquals(2, calls, "流式失败不应被缓存")
    }

    @Test
    fun testIdentityStreamIsFailure() = runBlocking {
        val translator = object : Translator {
            override suspend fun translate(text: String, targetLang: String): String = text

            override fun translateStream(text: String, targetLang: String): Flow<String> = flow {
                emit(text)
            }
        }
        val scheduler = TranslateScheduler(translator)

        val results = scheduler.translateStream("hello", "中文").toList()
        assertEquals(TranslateResult.Failure("hello"), results.last(), "流式回显原文应以 Failure 结尾")
    }

    @Test
    fun testStreamConcurrentCollectorsShareSession() = runBlocking {
        val translator = FakeTranslator(delayMillis = 50)
        val scheduler = TranslateScheduler(translator)

        // 两个收集者并发收集同一原文的流，应共享底层会话（single-flight）
        val results = (0..1).map {
            async { scheduler.translateStream("same text", "中文").toList() }
        }.awaitAll()

        assertEquals(1, translator.streamCalls.size, "同一原文的并发流式收集者应共享底层会话")
        assertEquals(2, results.size)
        assertTrue(results.all { it.isNotEmpty() })
    }

    @Test
    fun testConcurrentCancellationReleasesResources() = runBlocking {
        val translator = FakeTranslator(delayMillis = 200)
        val scheduler = TranslateScheduler(translator, maxConcurrency = 2)

        // 启动 8 个并发任务，取消前 4 个
        val jobs = (0 until 8).map { i ->
            async { scheduler.translate("text$i", "中文") }
        }
        jobs.take(4).forEach { it.cancel() }

        // 未被取消的任务应正常完成
        val results = jobs.drop(4).awaitAll()
        assertEquals(4, results.size)
        assertTrue(results.all { it is TranslateResult.Success }, "未被取消的任务应正常完成")

        // 取消后新任务应立即完成，证明信号量 permit 未被永久占用
        val after = withTimeout(5_000) { scheduler.translate("after", "中文") }
        assertEquals(TranslateResult.Success("translated"), after)
    }

    @Test
    fun testStreamCancellationStormReleasesSessions() = runBlocking {
        val translator = FakeTranslator(delayMillis = 200)
        val scheduler = TranslateScheduler(translator, maxConcurrency = 2)

        // 多个收集者并发收集同一原文（single-flight 共享会话），随后批量取消
        val jobs = (0 until 8).map {
            async { scheduler.translateStream("same text", "中文").toList() }
        }
        jobs.take(6).forEach { it.cancel() }

        // 未被取消的收集者必须在时限内结束（不因其他收集者取消而挂起/泄漏）
        val results = withTimeout(5_000) { jobs.drop(6).awaitAll() }
        assertEquals(2, results.size)
        assertTrue(results.all { it.isNotEmpty() }, "未被取消的流式收集者应正常完成")

        // 同一原文再次收集：命中缓存或新会话均可，关键是必须能正常完成（不挂起）
        val sameText = withTimeout(5_000) { scheduler.translateStream("same text", "中文").toList() }
        assertTrue(sameText.isNotEmpty(), "取消风暴后同一原文应能再次收集")

        // 新文本发起请求：证明信号量 permit 与流会话未被取消风暴泄漏
        val callsBefore = translator.streamCalls.size
        val fresh = withTimeout(5_000) { scheduler.translateStream("another text", "中文").toList() }
        assertTrue(fresh.isNotEmpty(), "取消风暴后新文本应能正常完成")
        assertTrue(translator.streamCalls.size > callsBefore, "取消风暴后新文本应能发起新请求")
    }
}
