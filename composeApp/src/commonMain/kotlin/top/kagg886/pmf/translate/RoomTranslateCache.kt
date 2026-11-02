package top.kagg886.pmf.translate

import kotlin.time.Clock
import top.kagg886.pmf.backend.database.dao.AiTranslateCacheDao
import top.kagg886.pmf.backend.database.dao.AiTranslateCacheEntity

/** 基于 Room 的持久化翻译缓存，写入时按最近访问时间淘汰最旧记录。 */
class RoomTranslateCache(private val dao: AiTranslateCacheDao) : TranslateCache {
    override suspend fun get(key: String, fingerprint: String): String? = dao.get(key, fingerprint)

    override suspend fun put(key: String, fingerprint: String, value: String) {
        dao.upsert(
            AiTranslateCacheEntity(
                textHash = key,
                fingerprint = fingerprint,
                resultText = value,
                lastAccessAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        val count = dao.count()
        if (count > MAX_CACHE_ENTRIES) {
            dao.removeOldest((count - MAX_CACHE_ENTRIES).toInt().coerceAtLeast(MIN_REMOVE_OLDEST))
        }
    }

    private companion object {
        const val MAX_CACHE_ENTRIES = 2000L
        const val MIN_REMOVE_OLDEST = 200
    }
}
