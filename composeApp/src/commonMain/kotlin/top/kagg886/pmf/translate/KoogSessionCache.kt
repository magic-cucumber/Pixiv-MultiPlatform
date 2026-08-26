package top.kagg886.pmf.translate

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 按指纹 key 缓存客户端会话的 LRU 缓存（Mutex 保护）。
 *
 * 每个 key 独立持有客户端，配置/目标语言变化只会新增条目，绝不关闭其他协程正在使用的
 * 客户端；仅当条目数超过 [maxSessions] 时按插入顺序逐出最旧条目并 close，释放其 HTTP 引擎。
 */
internal class KoogSessionCache<C : AutoCloseable>(
    private val maxSessions: Int = 32,
) {
    private val mutex = Mutex()
    private val sessions = LinkedHashMap<String, C>()

    /** 已创建的会话数（测试用）。 */
    var createdCount = 0
        private set

    /** 已关闭的会话数（测试用）。 */
    var closedCount = 0
        private set

    /**
     * 取会话；不存在则由 [create] 构造并缓存。
     *
     * 构造发生在写缓存之前：若 [create] 抛异常，缓存保持原状（不产生"已提交新配置但无客户端"的半损坏状态）。
     */
    suspend fun getOrCreate(key: String, create: () -> C): C = mutex.withLock {
        sessions[key]?.let { return it }
        val created = create()
        sessions[key] = created
        createdCount++
        while (sessions.size > maxSessions) {
            val (oldestKey, oldest) = sessions.entries.first()
            oldest.close()
            closedCount++
            sessions.remove(oldestKey)
        }
        created
    }
}
