package top.kagg886.pmf.translate

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import top.kagg886.pmf.backend.AppConfig
import top.kagg886.pmf.util.logger

/** 单次翻译的结果；[Failure.text] 为回退的原文。 */
sealed interface TranslateResult {
    val text: String

    data class Success(override val text: String) : TranslateResult

    data class Failure(override val text: String) : TranslateResult
}

/** 是否已开启 AI 翻译且配置了 API Key。 */
fun isAiTranslateEnabled(): Boolean = AppConfig.aiTranslateEnabled && AppConfig.deepseekApiKey.isNotBlank()

/**
 * 翻译调度器：限制并发、按原文 single-flight 去重、会话级 LRU 缓存，
 * 可选 [TranslateCache] 持久缓存（受 AppConfig.aiTranslateCacheEnabled 控制），
 * 并以 [timeout] 兜底；失败时回退原文，[CancellationException] 正常传播。
 */
class TranslateScheduler(
    private val translator: Translator,
    private val cache: TranslateCache? = null,
    private val maxConcurrency: Int = 2,
    private val cacheSize: Int = 512,
    private val timeout: Duration = 90.seconds,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val semaphore = Semaphore(maxConcurrency)
    private val lock = Mutex()
    private val memoryCache = LinkedHashMap<String, String>()
    private val inFlight = mutableMapOf<String, Deferred<TranslateResult>>()
    private val streamSessions = mutableMapOf<String, StreamSession>()

    /** 配置指纹：prompt/专名要求/模型/目标语言任一变化都会使旧缓存失效。 */
    private fun configFingerprint(targetLang: String): String = translationHash("${AppConfig.aiTranslatePrompt}|${AppConfig.aiTranslateProperNouns}|${AppConfig.aiTranslateModel}|$targetLang")

    private fun cacheKey(text: String, targetLang: String) = "$targetLang|${configFingerprint(targetLang)}|${text.trim()}"

    private suspend fun cachedOrNull(key: String, text: String, targetLang: String): String? {
        cacheGet(key)?.let { return it }
        if (AppConfig.aiTranslateCacheEnabled) {
            cache?.get(translationHash(text), configFingerprint(targetLang))?.let {
                cachePut(key, it)
                return it
            }
        }
        return null
    }

    private suspend fun storeResult(key: String, text: String, targetLang: String, value: String) {
        cachePut(key, value)
        if (AppConfig.aiTranslateCacheEnabled) {
            cache?.put(translationHash(text), configFingerprint(targetLang), value)
        }
    }

    private suspend fun cacheGet(key: String): String? = lock.withLock {
        val cached = memoryCache[key] ?: return@withLock null
        // LRU 命中后刷新顺序
        memoryCache.remove(key)
        memoryCache[key] = cached
        cached
    }

    private suspend fun cachePut(key: String, value: String) = lock.withLock {
        memoryCache.remove(key)
        memoryCache[key] = value
        while (memoryCache.size > cacheSize) {
            memoryCache.remove(memoryCache.keys.first())
        }
    }

    private suspend fun translateInternal(text: String, targetLang: String): TranslateResult = try {
        semaphore.withPermit {
            withTimeout(timeout) {
                val translated = translator.translate(text, targetLang)
                if (translated.isBlank()) TranslateResult.Failure(text) else TranslateResult.Success(translated)
            }
        }
    } catch (e: TimeoutCancellationException) {
        logger.w { "ai translate timeout after ${timeout.inWholeSeconds}s" }
        TranslateResult.Failure(text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.e(e) { "ai translate failed: ${e.message}" }
        TranslateResult.Failure(text)
    }

    /** 一次性翻译，带单飞去重与 LRU 缓存。 */
    suspend fun translate(text: String, targetLang: String): TranslateResult {
        if (text.isBlank()) return TranslateResult.Success(text)
        val key = cacheKey(text, targetLang)
        cachedOrNull(key, text, targetLang)?.let { return TranslateResult.Success(it) }

        val (deferred, isCreator) = lock.withLock {
            inFlight[key]?.let { it to false } ?: run {
                val created = scope.async { translateInternal(text, targetLang) }
                inFlight[key] = created
                created to true
            }
        }
        val result = try {
            deferred.await()
        } finally {
            // 只有创建者负责移除；非创建者复用同一 deferred，若由其移除，
            // 会在 remove 与 storeResult 之间留下窗口，导致新收集者重复发起请求。
            if (isCreator) {
                lock.withLock { inFlight.remove(key) }
            }
        }
        if (result is TranslateResult.Success) {
            storeResult(key, text, targetLang, result.text)
        }
        return result
    }

    /**
     * 流式翻译，逐次 emit 累积译文；失败时最终 emit [TranslateResult.Failure]。
     * 同一原文的并发收集者共享底层会话，非属主收集者等待最终结果。
     */
    fun translateStream(text: String, targetLang: String): Flow<TranslateResult> = flow {
        if (text.isBlank()) {
            emit(TranslateResult.Success(text))
            return@flow
        }
        val key = cacheKey(text, targetLang)
        cachedOrNull(key, text, targetLang)?.let {
            emit(TranslateResult.Success(it))
            return@flow
        }

        val session = lock.withLock {
            streamSessions.getOrPut(key) { startStreamSession(text, targetLang, key) }
        }
        val isOwner = lock.withLock {
            if (session.ownerTaken) {
                false
            } else {
                session.ownerTaken = true
                true
            }
        }
        try {
            if (isOwner) {
                var last: String? = null
                for (r in session.channel) {
                    emit(r)
                    if (r is TranslateResult.Success) last = r.text
                }
                val final = session.result.await()
                if (final is TranslateResult.Success) {
                    storeResult(key, text, targetLang, final.text)
                    if (final.text != last) emit(final)
                } else {
                    emit(final)
                }
            } else {
                val final = try {
                    session.result.await()
                } catch (e: CancellationException) {
                    if (session.result.isCancelled) {
                        // 属主取消底层流：降级为失败，而非把取消级联给非属主
                        TranslateResult.Failure(text)
                    } else {
                        // 非属主自身被取消：正常传播取消
                        throw e
                    }
                }
                if (final is TranslateResult.Success) {
                    cachePut(key, final.text)
                }
                emit(final)
            }
        } finally {
            if (isOwner) {
                // 正常完成时为 no-op；属主异常退出时终止底层流，避免泄漏
                session.result.cancel()
                // 只有属主负责清理会话；非属主仅借用会话等待结果，
                // 若由非属主移除会破坏 single-flight 去重、导致重复请求。
                lock.withLock { streamSessions.remove(key) }
            }
        }
    }

    private class StreamSession(
        val channel: Channel<TranslateResult>,
        val result: Deferred<TranslateResult>,
    ) {
        var ownerTaken = false
    }

    private fun startStreamSession(text: String, targetLang: String, key: String): StreamSession {
        val channel = Channel<TranslateResult>(Channel.BUFFERED)
        val result = scope.async {
            try {
                runStreamInternal(text, targetLang) { channel.send(it) }
            } finally {
                channel.close()
            }
        }
        return StreamSession(channel, result)
    }

    private suspend fun runStreamInternal(
        text: String,
        targetLang: String,
        emit: suspend (TranslateResult) -> Unit,
    ): TranslateResult = try {
        val builder = StringBuilder()
        semaphore.withPermit {
            withTimeout(timeout) {
                translator.translateStream(text, targetLang).collect { chunk ->
                    if (chunk.isNotEmpty()) {
                        builder.append(chunk)
                        emit(TranslateResult.Success(builder.toString()))
                    }
                }
            }
        }
        if (builder.isEmpty()) TranslateResult.Failure(text) else TranslateResult.Success(builder.toString())
    } catch (e: TimeoutCancellationException) {
        logger.w { "ai translate stream timeout after ${timeout.inWholeSeconds}s" }
        TranslateResult.Failure(text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.e(e) { "ai translate stream failed: ${e.message}" }
        TranslateResult.Failure(text)
    }
}
