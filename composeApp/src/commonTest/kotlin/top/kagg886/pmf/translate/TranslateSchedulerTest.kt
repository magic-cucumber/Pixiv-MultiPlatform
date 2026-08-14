package top.kagg886.pmf.translate

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun testSentenceSegmenterSplitsCjkAndEnglish() {
        assertEquals(
            listOf("こんにちは。", "元気ですか？"),
            SentenceSegmenter.split("こんにちは。元気ですか？"),
        )
        assertEquals(
            listOf("Hello world.", "How are you?"),
            SentenceSegmenter.split("Hello world. How are you?"),
        )
        assertEquals(
            listOf("第一句。", "第二句！", "第三句", "第四句；", "第五句"),
            SentenceSegmenter.split("第一句。第二句！第三句\n第四句；第五句"),
        )
        assertTrue(SentenceSegmenter.split("3.14 is a number").size == 1)
    }

    @Test
    fun testSentenceTranslationParserAcceptsObjectArrayAndFences() {
        assertEquals(
            listOf("你好", "世界"),
            SentenceTranslationParser.parse("""{"sentences":["你好","世界"]}"""),
        )
        assertEquals(
            listOf("你好", "世界"),
            SentenceTranslationParser.parse(
                """```json
                {"sentences":["你好","世界"]}
                ```
                """.trimIndent(),
            ),
        )
        assertEquals(
            listOf("你好", "世界"),
            SentenceTranslationParser.parse("""["你好","世界"]"""),
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
}
