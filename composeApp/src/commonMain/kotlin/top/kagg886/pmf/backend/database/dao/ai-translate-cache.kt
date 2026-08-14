package top.kagg886.pmf.backend.database.dao

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlin.time.Clock

/**
 * AI 翻译持久化缓存。
 *
 * [textHash] 为原文哈希（key），[fingerprint] 为翻译配置指纹；
 * 两者任一变化都会使旧记录失效，避免 prompt/模型调整后命中过期译文。
 */
@Entity(primaryKeys = ["textHash", "fingerprint"])
data class AiTranslateCacheEntity(
    val textHash: String,
    val fingerprint: String,
    val resultText: String,
    val lastAccessAt: Long = Clock.System.now().toEpochMilliseconds(),
)

@Dao
interface AiTranslateCacheDao {
    @Query("SELECT resultText FROM AiTranslateCacheEntity WHERE textHash = :key AND fingerprint = :fingerprint")
    suspend fun get(key: String, fingerprint: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiTranslateCacheEntity)

    @Query("SELECT COUNT(*) FROM AiTranslateCacheEntity")
    suspend fun count(): Long

    @Query(
        """
        DELETE FROM AiTranslateCacheEntity
        WHERE rowid IN (
            SELECT rowid FROM AiTranslateCacheEntity
            ORDER BY lastAccessAt ASC
            LIMIT :removeCount
        )
        """,
    )
    suspend fun removeOldest(removeCount: Int)
}
