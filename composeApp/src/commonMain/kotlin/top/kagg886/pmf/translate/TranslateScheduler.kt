package top.kagg886.pmf.translate

import kotlin.time.Clock
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
import kotlinx.coroutines.delay
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
fun isAiTranslateEnabled(): Boolean = AppConfig.aiTranslateEnabled && AppConfig.aiTranslateApiKey.isNotBlank()

/**
 * 日志打码：把配置的 API Key 替换为 `***`，避免密钥经异常消息/请求内容泄漏到日志。
 */
internal fun maskSecret(text: String): String {
    val key = AppConfig.aiTranslateApiKey
    return if (key.isNotBlank() && key.length >= 4) {
        text.replace(key, "***")
    } else {
        text
    }
}

/**
 * 翻译调度器：限制并发、按原文 single-flight 去重、会话级 LRU 缓存，
 * 可选 [TranslateCache] 持久缓存（受 AppConfig.aiTranslateCacheEnabled 控制），
 * 并以 [timeout] 兜底；失败时回退原文，[CancellationException] 正常传播。
 *
 * [configDrivenConcurrency] 为 true 时，并发上限随
 * [AppConfig.aiTranslateMaxConcurrency] 动态调整（设置修改即时生效）。
 *
 * 重试请求走独立的重试通道（[retryConcurrency] 个许可的专用信号量）：
 * 与普通请求完全隔离，拿到重试许可即发，不会被前序普通任务排队阻塞。
 */
class TranslateScheduler(
    private val translator: Translator,
    private val cache: TranslateCache? = null,
    maxConcurrency: Int = 2,
    private val cacheSize: Int = 512,
    private val timeout: Duration = 90.seconds,
    /** 瞬态失败（网络异常/超时）的总尝试次数；空内容/回显等模型行为类失败不重试。 */
    private val retryAttempts: Int = 2,
    /** 重试退避基数（线性退避：第 n 次失败后等待 backoff * n）。 */
    private val retryBackoff: Duration = 1.seconds,
    /** 重试专用通道并发数：重试请求使用独立信号量，不参与普通请求排队。 */
    private val retryConcurrency: Int = 1,
    private val configDrivenConcurrency: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val semaphoreLock = Mutex()
    private var currentPermits = maxConcurrency.coerceAtLeast(1)
    private var semaphore = Semaphore(currentPermits)
    private val retrySemaphore = Semaphore(retryConcurrency.coerceAtLeast(1))
    private val lock = Mutex()
    private val memoryCache = LinkedHashMap<String, String>()
    private val inFlight = mutableMapOf<String, Deferred<TranslateResult>>()
    private val streamSessions = mutableMapOf<String, StreamSession>()

    /** 并发许可获取：配置驱动时每次读取设置，变更即换信号量（在途请求不受影响）。 */
    private suspend fun <T> withConcurrencyLimit(block: suspend () -> T): T {
        val sem =
            if (configDrivenConcurrency) {
                semaphoreLock.withLock {
                    val desired = AppConfig.aiTranslateMaxConcurrency.coerceAtLeast(1)
                    if (desired != currentPermits) {
                        currentPermits = desired
                        semaphore = Semaphore(desired)
                    }
                    semaphore
                }
            } else {
                semaphore
            }
        return sem.withPermit { block() }
    }

    /** 按通道获取许可：重试走专用信号量（即时可发），普通请求走并发限制。 */
    private suspend fun <T> withPermit(retry: Boolean, block: suspend () -> T): T = if (retry) retrySemaphore.withPermit { block() } else withConcurrencyLimit { block() }

    /** 配置指纹：provider/BaseURL/模型/专名要求/目标语言任一变化都会使旧缓存失效。 */
    private fun configFingerprint(targetLang: String): String {
        AppConfig.migrateLegacyAiTranslatePrompt()
        return translationHash(
            "${AppConfig.aiTranslateProvider}|${AppConfig.aiTranslateBaseUrl}|${AppConfig.aiTranslatePrompt}|" +
                "${AppConfig.aiTranslateProperNouns}|${AppConfig.aiTranslateModel}|$targetLang",
        )
    }

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

    /**
     * 一次性翻译（带瞬态失败重试）。
     *
     * 网络异常/超时视为瞬态失败，按 [retryAttempts] 重试并线性退避；
     * 空内容不重试；回显原文（模型偶发整段回显，非确定性）在尝试次数内重试——
     * 实测同一文本二次请求往往能正确翻译。
     */
    private suspend fun translateInternal(text: String, targetLang: String, retry: Boolean): TranslateResult {
        val attempts = currentRetryAttempts()
        var attempt = 0
        while (true) {
            attempt++
            try {
                val startedAt = Clock.System.now()
                val translated = withPermit(retry) {
                    withTimeout(timeout) {
                        translator.translate(text, targetLang)
                    }
                }
                val durationMs = (Clock.System.now() - startedAt).inWholeMilliseconds
                logger.i {
                    "ai translate done: textLen=${text.length} resultLen=${translated.length} " +
                        "durationMs=$durationMs req=${maskSecret(text.take(40))}"
                }
                when {
                    translated.isBlank() -> {
                        logger.w { "ai translate returned blank content, textLen=${text.length}" }
                        return TranslateResult.Failure(text)
                    }

                    // 模型"回显原文"：与原文相同的输出视为失败，绝不缓存；
                    // 但回显常为非确定性（二次请求可正确翻译），尝试次数内重试
                    isIdentityTranslation(text, translated) -> {
                        if (attempt >= attempts) {
                            logger.w { "ai translate returned identity text, treat as failure" }
                            return TranslateResult.Failure(text)
                        }
                        logger.w { "ai translate identity echo, retry $attempt/$attempts, textLen=${text.length}" }
                        delay(retryBackoff * attempt)
                    }

                    else -> return TranslateResult.Success(translated)
                }
            } catch (e: TimeoutCancellationException) {
                if (attempt >= attempts) {
                    logger.w { "ai translate timeout after ${timeout.inWholeSeconds}s, textLen=${text.length}" }
                    return TranslateResult.Failure(text)
                }
                logger.w { "ai translate transient timeout, retry $attempt/$attempts, textLen=${text.length}" }
                delay(retryBackoff * attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= attempts) {
                    logger.e(e) { "ai translate failed: ${maskSecret(e.message ?: "")}" }
                    return TranslateResult.Failure(text)
                }
                logger.w {
                    "ai translate transient error, retry $attempt/$attempts: ${maskSecret(e.message ?: "")}"
                }
                delay(retryBackoff * attempt)
            }
        }
    }

    /** 一次性翻译，带单飞去重与 LRU 缓存（普通通道）。 */
    suspend fun translate(text: String, targetLang: String): TranslateResult = translateImpl(text, targetLang, retry = false, bypassCacheRead = false)

    /**
     * 一次性翻译（重试专用通道）。
     *
     * 独立信号量（不参与普通请求排队）且**绕过缓存读取**——每次重试都真实发起请求，
     * 避免首次失败的（对调度器而言合法的）结果被缓存后，后续重试全部缓存命中、永不重试；
     * 成功的结果仍会写回缓存（供后续普通请求命中）。
     */
    suspend fun translateRetry(text: String, targetLang: String): TranslateResult = translateImpl(text, targetLang, retry = true, bypassCacheRead = true)

    private suspend fun translateImpl(
        text: String,
        targetLang: String,
        retry: Boolean,
        bypassCacheRead: Boolean,
    ): TranslateResult {
        if (text.isBlank()) return TranslateResult.Success(text)
        val key = cacheKey(text, targetLang)
        if (!bypassCacheRead) {
            cachedOrNull(key, text, targetLang)?.let {
                logger.d { "ai translate cache hit, textLen=${text.length}, target=$targetLang" }
                return TranslateResult.Success(it)
            }
        }

        logger.i { "ai translate start, textLen=${text.length}, target=$targetLang, retry=$retry" }
        val (deferred, isCreator) = lock.withLock {
            inFlight[key]?.let { it to false } ?: run {
                val created = scope.async { translateInternal(text, targetLang, retry) }
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
            // 只在成功时写回缓存（重试成功同样写回，供后续普通请求命中）
            storeResult(key, text, targetLang, result.text)
            logger.i { "ai translate success, textLen=${text.length}, resultLen=${result.text.length}" }
        } else {
            logger.w { "ai translate failure, textLen=${text.length}, target=$targetLang" }
        }
        return result
    }

    /**
     * 流式翻译，逐次 emit 累积译文；失败时最终 emit [TranslateResult.Failure]。
     * 同一原文的并发收集者共享底层会话，非属主收集者等待最终结果。
     */
    fun translateStream(text: String, targetLang: String): Flow<TranslateResult> = translateStreamImpl(text, targetLang, retry = false, bypassCacheRead = false)

    /**
     * 流式翻译（重试专用通道）。
     *
     * 独立信号量（不参与普通请求排队）且**绕过缓存读取**——每次重试都真实发起请求，
     * 避免首次失败的（对调度器而言合法的）结果被缓存后，后续重试全部缓存命中、永不重试；
     * 成功的结果仍会写回缓存（供后续普通请求命中）。
     */
    fun translateStreamRetry(text: String, targetLang: String): Flow<TranslateResult> = translateStreamImpl(text, targetLang, retry = true, bypassCacheRead = true)

    private fun translateStreamImpl(
        text: String,
        targetLang: String,
        retry: Boolean,
        bypassCacheRead: Boolean,
    ): Flow<TranslateResult> = flow {
        if (text.isBlank()) {
            emit(TranslateResult.Success(text))
            return@flow
        }
        val key = cacheKey(text, targetLang)
        if (!bypassCacheRead) {
            cachedOrNull(key, text, targetLang)?.let {
                logger.d { "ai translate stream cache hit, textLen=${text.length}, target=$targetLang" }
                emit(TranslateResult.Success(it))
                return@flow
            }
        }

        logger.i { "ai translate stream start, textLen=${text.length}, target=$targetLang, retry=$retry" }
        val session = lock.withLock {
            streamSessions.getOrPut(key) { startStreamSession(text, targetLang, key, retry) }
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
                    // 只在成功时写回缓存（重试成功同样写回，供后续普通请求命中）
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
                    // 只在成功时写回缓存（重试成功同样写回）
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

    private fun startStreamSession(text: String, targetLang: String, key: String, retry: Boolean): StreamSession {
        val channel = Channel<TranslateResult>(Channel.BUFFERED)
        val result = scope.async {
            try {
                runStreamInternal(text, targetLang, retry) { channel.send(it) }
            } finally {
                channel.close()
            }
        }
        return StreamSession(channel, result)
    }

    /**
     * 流式翻译（带瞬态失败重试）。
     *
     * 每次尝试使用独立 [StringBuilder]——重试从零开始累积，避免把失败前的半截
     * 内容与重试内容拼接；网络异常/超时视为瞬态失败重试，空内容不重试，
     * 回显原文（模型偶发整段回显，非确定性）在尝试次数内重试。
     */
    private suspend fun runStreamInternal(
        text: String,
        targetLang: String,
        retry: Boolean,
        emit: suspend (TranslateResult) -> Unit,
    ): TranslateResult {
        val attempts = currentRetryAttempts()
        var attempt = 0
        while (true) {
            attempt++
            try {
                val builder = StringBuilder()
                val startedAt = Clock.System.now()
                withPermit(retry) {
                    withTimeout(timeout) {
                        translator.translateStream(text, targetLang).collect { chunk ->
                            if (chunk.isNotEmpty()) {
                                builder.append(chunk)
                                emit(TranslateResult.Success(builder.toString()))
                            }
                        }
                    }
                }
                val durationMs = (Clock.System.now() - startedAt).inWholeMilliseconds
                logger.i {
                    "ai translate stream done: textLen=${text.length} resultLen=${builder.length} " +
                        "durationMs=$durationMs req=${maskSecret(text.take(40))}"
                }
                when {
                    builder.isEmpty() -> {
                        logger.w { "ai translate stream returned no content, textLen=${text.length}" }
                        return TranslateResult.Failure(text)
                    }

                    // 模型"回显原文"：累积结果与原文相同视为失败，绝不缓存；
                    // 回显常为非确定性（二次请求可正确翻译），尝试次数内重试
                    isIdentityTranslation(text, builder.toString()) -> {
                        if (attempt >= attempts) {
                            logger.w { "ai translate stream returned identity text, treat as failure" }
                            return TranslateResult.Failure(text)
                        }
                        logger.w { "ai translate stream identity echo, retry $attempt/$attempts, textLen=${text.length}" }
                        delay(retryBackoff * attempt)
                    }

                    else -> return TranslateResult.Success(builder.toString())
                }
            } catch (e: TimeoutCancellationException) {
                if (attempt >= attempts) {
                    logger.w { "ai translate stream timeout after ${timeout.inWholeSeconds}s, textLen=${text.length}" }
                    return TranslateResult.Failure(text)
                }
                logger.w { "ai translate stream transient timeout, retry $attempt/$attempts, textLen=${text.length}" }
                delay(retryBackoff * attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= attempts) {
                    logger.e(e) { "ai translate stream failed: ${maskSecret(e.message ?: "")}" }
                    return TranslateResult.Failure(text)
                }
                logger.w {
                    "ai translate stream transient error, retry $attempt/$attempts: ${maskSecret(e.message ?: "")}"
                }
                delay(retryBackoff * attempt)
            }
        }
    }

    /** 当前重试次数：配置驱动时随设置实时读取。 */
    private fun currentRetryAttempts(): Int = if (configDrivenConcurrency) {
        AppConfig.aiTranslateRetryAttempts.coerceAtLeast(1)
    } else {
        retryAttempts
    }
}
